package tufa;

import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.model.*;
import alien4cloud.rest.utils.ResponseUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.SneakyThrows;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import tufa.model.slurm.SLURMWorkflow;
import tufa.model.soe.SerranoApp;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.collect.Maps.newHashMap;

public abstract class TUFAProvider implements IConfigurablePaaSProvider<Configuration> {
    private static final Logger log = LoggerFactory.getLogger(TUFAProvider.class);
    private static final Map<String, String> LOCATION_TYPES = new HashMap<>();

    protected Configuration configuration;

    protected KubernetesClient kubeClient;
    protected SerranoRestClient aisoClient;
    protected SerranoRestClient roClient;
    protected SerranoRestClient telemetryClient;




    protected Map<String,PaaSTopologyDeploymentContext> knownDeployments = Maps.newConcurrentMap();
    protected Map<String, Optional<DeploymentStatus>> deploymentStatuses = Maps.newConcurrentMap();


    @Autowired
    private EventService eventService;


    @Autowired
    @Qualifier("alien-es-dao")
    private IGenericSearchDAO alienDAO;


    @Autowired
    private BeanFactory beanFactory;

    @Autowired
    protected SerranoMappingService serranoMappingService;

    ThreadLocal<ClassLoader> oldContextClassLoader = new ThreadLocal<ClassLoader>();
    private Map<String, SLURMWorkflow> slurmDeployments = Maps.newConcurrentMap();
    private Map<String, SerranoApp> serranoDeployments = Maps.newConcurrentMap();


    protected void useLocalContextClassLoader() {
        if (oldContextClassLoader.get()==null) {
            oldContextClassLoader.set( Thread.currentThread().getContextClassLoader() );
        }
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
    }
    protected void revertContextClassLoader() {
        if (oldContextClassLoader.get()==null) {
            log.warn("No local context class loader to revert");
        }
        Thread.currentThread().setContextClassLoader(oldContextClassLoader.get());
        oldContextClassLoader.remove();
    }


    @Override
    @SneakyThrows
    public void deploy(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        log.info("DEPLOY "+deploymentContext+" / "+callback);
        String topologyId = deploymentContext.getDeploymentTopology().getId();
        String topologyVer = deploymentContext.getDeploymentTopology().getVersionId();

        Location location = deploymentContext.getLocations().values().stream().findFirst().orElse(null);
        if (location == null) {
            callback.onFailure(new Exception("Cannot retrieve location"));
            return;
        }

        LOCATION_TYPES.put(location.getId(), location.getInfrastructureType());



//        SLURMWorkflow workflow = slurmMappingService.buildWorkflowDefinition(deploymentContext);

        knownDeployments.put(deploymentContext.getDeploymentId(), deploymentContext);
        deploymentStatuses.put(deploymentContext.getDeploymentId(), Optional.of(DeploymentStatus.DEPLOYMENT_IN_PROGRESS));


        PaaSDeploymentStatusMonitorEvent updatedStatus = new PaaSDeploymentStatusMonitorEvent();
        updatedStatus.setDeploymentId(deploymentContext.getDeploymentId());
        updatedStatus.setDeploymentStatus(DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
        eventService.registerEvent(new Date(System.currentTimeMillis()), updatedStatus);

        log.info("Building defintions...");
        SerranoApp serranoApp = serranoMappingService.buildSerranoDefinition(deploymentContext);
        serranoDeployments.put(deploymentContext.getDeploymentId(), serranoApp);
        boolean deployed = false;
        try {
            if (location.getInfrastructureType().equals(TUFAOrchestrator.SERRANO_LOCATION)) {
                deployed = startSerranoApp(deploymentContext, serranoApp);
            } else {
                deployed = startKubeApp(deploymentContext, serranoApp);
            }
            if(deployed) {
                deploymentStatuses.put(deploymentContext.getDeploymentId(), Optional.of(DeploymentStatus.DEPLOYED));
//                serranoDeployments.put(deploymentContext.getDeploymentId(), serranoApp);
                updatedStatus = new PaaSDeploymentStatusMonitorEvent();
                updatedStatus.setDeploymentId(deploymentContext.getDeploymentId());
                updatedStatus.setDeploymentStatus(DeploymentStatus.DEPLOYED);
                eventService.registerEvent(new Date(System.currentTimeMillis()), updatedStatus);
                if (callback != null) callback.onSuccess(null);
            }
        }catch (Exception e){
//            knownDeployments.remove(deploymentContext.getDeploymentId());
//            serranoDeployments.remove(deploymentContext.getDeploymentId());
            deploymentStatuses.put(deploymentContext.getDeploymentId(), Optional.of(DeploymentStatus.FAILURE));
            PaaSDeploymentStatusMonitorEvent status = new PaaSDeploymentStatusMonitorEvent();
            status.setDeploymentId(deploymentContext.getDeploymentId());
            status.setDeploymentStatus(DeploymentStatus.FAILURE);
            eventService.registerEvent(new Date(System.currentTimeMillis()), status);
            if (callback != null) callback.onFailure (new Exception("Cannot deploy topology\nCaused by: " + e.getMessage()));
        }
    }

    private boolean startSerranoApp(PaaSTopologyDeploymentContext paaSTopologyDeploymentContext, SerranoApp serranoApp) {
        Map<String, Object> deployedApps = new HashMap<>();

        paaSTopologyDeploymentContext.getDeployment().setOrchestratorDeploymentId(paaSTopologyDeploymentContext.getDeploymentId());
        alienDAO.save(paaSTopologyDeploymentContext.getDeployment());
        deploymentStatuses.put(paaSTopologyDeploymentContext.getDeploymentId(), Optional.<DeploymentStatus>absent());

        //Prepare data for AI-SO
        JsonObject intent = new JsonObject();
        intent.addProperty("user_id", "SerranoOrchestratorPlugin");
        JsonArray constraints = new JsonArray();
        JsonArray workflow = new JsonArray();

        StringBuilder sb = new StringBuilder();
        for(Deployment deployment : serranoApp.getDeployments()){
            String nodeName = deployment.getMetadata().getName();
            nodeName = nodeName.substring(0, nodeName.indexOf('-'));
            if (serranoApp.getVolumes().get(nodeName) != null)
                for(PersistentVolume vol:  serranoApp.getVolumes().get(nodeName)) {
                    sb.append(Serialization.asYaml(vol));
                    sb.append('\n');
                }
            if(serranoApp.getVolumeClaims().get(nodeName) != null)
                for(PersistentVolumeClaim claim : serranoApp.getVolumeClaims().get(nodeName)){
                    sb.append(Serialization.asYaml(claim));
                    sb.append('\n');
                }
            ConfigMap configMap = serranoApp.getConfigMaps().get(nodeName);
            sb.append(Serialization.asYaml(configMap));
            sb.append('\n');

            if(serranoApp.getServices().get(nodeName) != null)
                for(Map.Entry<String, Service> entry : serranoApp.getServices().get(nodeName).entrySet()){
                    Service service = entry.getValue();
                    sb.append(Serialization.asYaml(service));
                    sb.append('\n');
                }
            sb.append(Serialization.asYaml(deployment));
            sb.append('\n');

            if(serranoApp.getConstraints().get(nodeName) != null){
                constraints.add(serranoApp.getConstraints().get(nodeName));
            }

            JsonObject dependency = new JsonObject();
            dependency.addProperty("component_id", nodeName);
            if(serranoApp.getDependencies().get(nodeName) != null){
                JsonArray array = new JsonArray();
                for (String dep :  serranoApp.getDependencies().get(nodeName)){
                    array.add(dep);
                }
                dependency.add("previous_component", array);
            }
            workflow.add(dependency);

        }
        intent.add("application_constraints", constraints);

        intent.add("application_workflow", workflow);
        String kubeYaml = sb.toString();
        intent.addProperty("deployment_descriptor_yaml", kubeYaml);

        log.info("Generated Kube YAML:\n" + kubeYaml);

        log.info("Generated intent JSON:\n" + intent.toString());
        List<NameValuePair> headers = new ArrayList<>();
        headers.add(new BasicHeader("Content-Type", "application/json"));


        try (CloseableHttpResponse response = aisoClient.postJSon("/ApplicationDeploymentThroughRO", intent.toString())) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    //                String string = ResponseUtil.toString(response);
                    log.info("Got OK result from AI-SO");
                    String string = EntityUtils.toString(response.getEntity());
                    log.info(string);
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> dep = mapper.readValue(string, Map.class);
                    String uuid = (String) dep.get("deployment_uuid");
                    serranoApp.setSerranoUUID(uuid);
                    return true;
                }else{
                    log.info("Got %d result from AI-SO: %s".formatted(response.getStatusLine().getStatusCode(),
                            EntityUtils.toString(response.getEntity())));
                }

        } catch (IOException e) {
            log.info("Error occurred during application deployment using AI-SO");
            log.info(e.toString());
        }


        return false;
    }

    private boolean startKubeApp(PaaSTopologyDeploymentContext paaSTopologyDeploymentContext, SerranoApp serranoApp) {
        Map<String, Object> deployedApps = new HashMap<>();

        paaSTopologyDeploymentContext.getDeployment().setOrchestratorDeploymentId(paaSTopologyDeploymentContext.getDeploymentId());
        alienDAO.save(paaSTopologyDeploymentContext.getDeployment());
        deploymentStatuses.put(paaSTopologyDeploymentContext.getDeploymentId(), Optional.<DeploymentStatus>absent());

        String dockerCFG = serranoApp.getDockerCFG();
        Secret regcred = kubeClient.secrets().withName("regcred").get();
        if(regcred == null){
            Secret credentials = new SecretBuilder().withType("kubernetes.io/dockerconfigjson")
                    .withNewMetadata()
                    .withName("regcred")
                    .endMetadata()
                    .withData(Map.of(".dockerconfigjson", dockerCFG))
                    .build();
            kubeClient.secrets().resource(credentials).create();
        }

//        StringBuilder sb = new StringBuilder();
        for(Deployment deployment : serranoApp.getDeployments()){
            String nodeName = deployment.getMetadata().getName();
            nodeName = nodeName.substring(0, nodeName.indexOf('-'));

            if(serranoApp.getVolumeClaims().get(nodeName) != null) {
                List<StorageClass> storageClasses = kubeClient.storage().v1().storageClasses().list().getItems();
                boolean pvManagedStorage = false;
                if(storageClasses != null && storageClasses.size() > 0){
                    if(storageClasses.size() == 1 && storageClasses.get(0).getMetadata().getName().equals("local-storage"))
                        pvManagedStorage = true;
                }else{
                    StorageClass storageClass = new StorageClassBuilder().withNewMetadata()
                            .withName("local-storage")
                            .endMetadata()
                            .withProvisioner("kubernetes.io/no-provisioner")
                            .withVolumeBindingMode("WaitForFirstConsumer")
                            .build();
                    kubeClient.storage().v1().storageClasses().resource(storageClass).create();
                }


                List<PersistentVolumeClaim> pvcs = serranoApp.getVolumeClaims().get(nodeName);
                for (int i = 0; i < pvcs.size(); i++) {
                    PersistentVolumeClaim claim = pvcs.get(i);


                    if(pvManagedStorage){
                        PersistentVolume pv = serranoApp.getVolumes().get(nodeName).get(i);
                        pv.getSpec().setStorageClassName("local-storage");
                        claim.getSpec().setStorageClassName("local-storage");
                        if(claim.getSpec().getStorageClassName().equals("persistent")) {
                            pv.getSpec().setPersistentVolumeReclaimPolicy("Retain");
                        }
                        PersistentVolume existingPV = kubeClient.persistentVolumes().withName(pv.getMetadata().getName()).get();
                        if(existingPV == null){
                            PersistentVolume cpv = kubeClient.persistentVolumes().resource(pv).create();
                            log.info("Persistent Volume '" + cpv.getMetadata().getName() + "' created.");
                        }
                    }else{
                        AtomicReference<String> className = new AtomicReference<>("local-storage");
                        kubeClient.storage().v1().storageClasses().list().getItems().forEach(storageClass -> {
                            if(claim.getSpec().getStorageClassName().equals("persistent")) {
                                if (storageClass.getMetadata().getName().equals("nlsas-01-retain")){
                                    className.set(storageClass.getMetadata().getName());
                                }
                            }else{
                                if (storageClass.getMetadata().getName().equals("nlsas-01")){
                                    className.set(storageClass.getMetadata().getName());
                                }
                            }
                        });
                        log.info("Using storageClassName: " + className.get());
                        claim.getSpec().setStorageClassName(className.get());
                    }

                    PersistentVolumeClaim existingPVC = kubeClient.persistentVolumeClaims().withName(claim.getMetadata().getName()).get();

                    if (existingPVC != null) {
                        if(!existingPVC.equals(claim)) {
                            kubeClient.persistentVolumeClaims().resource(existingPVC).delete();
                            boolean deleted = false;
                            while(!deleted) {
                                List<PersistentVolumeClaim> items = kubeClient.persistentVolumeClaims().list().getItems();
                                if(items.stream().noneMatch(pvc -> pvc.getMetadata().getName().equals(claim.getMetadata().getName()))){
                                    deleted = true;
                                }else{
                                    try {
                                        Thread.sleep(3000);
                                    } catch (InterruptedException e) {
                                        log.info("Error while sleeping");
                                    }
                                }

                            }
                            kubeClient.persistentVolumeClaims().resource(claim).create();
                            log.info("Persistent Volume Claim '" + claim.getMetadata().getName() + "' deleted and recreated.");
                        }
                        log.info("Persistent Volume Claim '" + claim.getMetadata().getName() + "' exists.");
                    } else {
                        // Create the PVC
                        existingPVC = kubeClient.persistentVolumeClaims().resource(claim).create();
                        log.info("Persistent Volume Claim '" + existingPVC.getMetadata().getName() + "' has been created.");
                    }
                }
            }

            ConfigMap configMap = serranoApp.getConfigMaps().get(nodeName);
            ConfigMap existingCM = kubeClient.configMaps().withName(configMap.getMetadata().getName()).get();
            if (existingCM != null) {
                kubeClient.configMaps().resource(configMap).update();
                log.info("Config Map '" + configMap.getMetadata().getName() + "' updated");
            } else {
                // Create the PV

                existingCM = kubeClient.configMaps().resource(configMap).create();
                log.info("Config Map '" + existingCM.getMetadata().getName() + "' has been created.");
            }



            Deployment existingDeployment = kubeClient.apps ().deployments().withName(deployment.getMetadata().getName()).get();
            if (existingDeployment != null) {
                kubeClient.apps().deployments().resource(deployment).update();
                log.info("Deployment '" + deployment.getMetadata().getName() + "' updated ");
            } else {
                // Create the PV

                existingDeployment = kubeClient.apps().deployments().resource(deployment).create();
                log.info("Deployment '" + existingDeployment.getMetadata().getName() + "' has been created.");
            }

            if(serranoApp.getServices().get(nodeName) != null)
                for(Map.Entry<String, Service> entry : serranoApp.getServices().get(nodeName).entrySet()){
                    Service service = entry.getValue();

                    Service existingService = kubeClient.services().withName(service.getMetadata().getName()).get();
                    if (existingService != null) {
                        kubeClient.services().resource(service).update();
                        log.info("Service '" + service.getMetadata().getName() + "' updated.");
                    } else {
                        // Create the Service
                        existingService = kubeClient.services().resource(service).create();
                        log.info("Service '" + existingService.getMetadata().getName() + "' has been created.");
                    }
                }
        }
        log.info("Deployed all Kube components...");

        return true;
    }

    private int computeMemoryMB(String mem) {
        String[] tokens = mem.split(" ");
        int size = Integer.parseInt(tokens[0]);
        String type = tokens[1];
        int k = 1024;
        if(type.equalsIgnoreCase("mb"))
            return size;
        else if (type.equalsIgnoreCase("gb"))
            return size * k;
        else if (type.equalsIgnoreCase("tb"))
            return size * k * k;
        return size;
    }


    @Override
    public void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback callback, boolean force) {
        log.info("UNDEPLOY " + deploymentContext + " / " + callback);

        SerranoApp serranoApp = serranoDeployments.get(deploymentContext.getDeploymentId());

        if(serranoApp != null) {

            String location = Arrays.stream(deploymentContext.getDeployment().getLocationIds()).findFirst().orElse(null);
            if (location == null) {
                callback.onFailure(new Exception("Cannot retrieve location"));
                return;
            }
            if(LOCATION_TYPES.get(location).equals(TUFAOrchestrator.KUBE_LOCATION)) {

                for (Deployment deployment : serranoApp.getDeployments()) {
                    String nodeName = deployment.getMetadata().getName();
                    nodeName = nodeName.substring(0, nodeName.indexOf('-'));

                    if (serranoApp.getVolumeClaims().get(nodeName) != null) {
                        List<PersistentVolumeClaim> pvcs = serranoApp.getVolumeClaims().get(nodeName);
                        List<PersistentVolume> persistentVolumes = serranoApp.getVolumes().get(nodeName);
                        for (int i = 0; i < pvcs.size(); i++) {
                            PersistentVolumeClaim claim = pvcs.get(i);

                            PersistentVolumeClaim existingPVC = kubeClient.persistentVolumeClaims().withName(claim.getMetadata().getName()).get();

                            if (existingPVC != null) {
                                kubeClient.persistentVolumeClaims().resource(existingPVC).delete();
                                log.info("Persistent Volume Claim '" + existingPVC.getMetadata().getName() + "' deleted.");
                            }
                            PersistentVolume pv = persistentVolumes.get(i);

                            try {
                                PersistentVolume existingPV = kubeClient.persistentVolumes().withName(pv.getMetadata().getName()).get();
                                if (existingPV != null) {
                                    kubeClient.persistentVolumes().resource(existingPV).delete();
                                    log.info("Perxistent Volume '" + existingPV.getMetadata().getName() + "' deleted.");
                                }
                            } catch (Exception e) {
                                log.info("Cannot check if a PV has been created due to permission error.");
                            }
                        }
                    }

                    ConfigMap configMap = serranoApp.getConfigMaps().get(nodeName);
                    ConfigMap existingCM = kubeClient.configMaps().withName(configMap.getMetadata().getName()).get();
                    if (existingCM != null) {
                        kubeClient.configMaps().resource(existingCM).delete();
                        log.info("Config Map '" + configMap.getMetadata().getName() + "' deleted");
                    }


                    Deployment existingDeployment = kubeClient.apps().deployments().withName(deployment.getMetadata().getName()).get();
                    if (existingDeployment != null) {
                        kubeClient.apps().deployments().resource(existingDeployment).delete();
                        log.info("Deployment '" + deployment.getMetadata().getName() + "' deleted ");
                    }

                    if (serranoApp.getServices().get(nodeName) != null) {
                        for (Map.Entry<String, Service> entry : serranoApp.getServices().get(nodeName).entrySet()) {
                            Service service = entry.getValue();

                            Service existingService = kubeClient.services().withName(service.getMetadata().getName()).get();
                            if (existingService != null) {
                                kubeClient.services().resource(existingService).delete();
                                log.info("Service '" + service.getMetadata().getName() + "' deleted.");
                            }
                        }
                    }

                }
            }else if (LOCATION_TYPES.get(location).equals(TUFAOrchestrator.SERRANO_LOCATION)){

                List<NameValuePair> headers = new ArrayList<>();
                headers.add(new BasicHeader("Content-Type", "application/json"));

                JsonObject info = new JsonObject();
                info.addProperty("appid", serranoApp.getSerranoUUID());
                info.addProperty("action", "UNDEPLOY");

                try (CloseableHttpResponse response = aisoClient.postJSon("/ApplicationManagement", info.toString())) {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        log.info("Successfully undeployed components using AI-SO");
                    }
                }catch (IOException e){
                    log.info("Problem undeploying through AI-SO");
                }
            }
        }


        serranoDeployments.remove(deploymentContext.getDeploymentId());
        deploymentStatuses.put(deploymentContext.getDeploymentId(), Optional.of(DeploymentStatus.UNDEPLOYED));
        PaaSDeploymentStatusMonitorEvent updatedStatus = new PaaSDeploymentStatusMonitorEvent();
        updatedStatus.setDeploymentId(deploymentContext.getDeploymentId());
        updatedStatus.setDeploymentStatus(DeploymentStatus.UNDEPLOYED);
        eventService.registerEvent(new Date(System.currentTimeMillis()), updatedStatus);

        if(callback != null) callback.onSuccess(null);

    }

    @Override
    public void scale(PaaSDeploymentContext deploymentContext, String nodeTemplateId, int instances, IPaaSCallback<?> callback) {
        log.warn("SCALE not supported");
    }

    @Override
    public void getStatus(PaaSDeploymentContext deploymentContext, IPaaSCallback<DeploymentStatus> callback) {
        if (callback!=null) {
            String entityId = deploymentContext.getDeploymentId();
            log.info("GET STATUS - " + entityId);
            Optional<DeploymentStatus> deploymentStatus = deploymentStatuses.get(entityId);
            if(deploymentStatus.isPresent()) {
                callback.onSuccess(deploymentStatus.get());
                return;
            }
            DeploymentStatus status = DeploymentStatus.UNDEPLOYED;
            if(serranoDeployments.get(deploymentContext.getDeploymentId()) != null){
                status = DeploymentStatus.DEPLOYED;
            }
            callback.onSuccess(status);
        }
    }

    @Override
    public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext,
            IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {

        log.info("Get instance info: " +  deploymentContext.getDeploymentId());

        SerranoApp serranoApp = serranoDeployments.get(deploymentContext.getDeploymentId());


        if(serranoApp != null) {
            Location location = deploymentContext.getLocations().values().stream().findFirst().orElse(null);
            if (location == null) {
                callback.onFailure(new Exception("Cannot retrieve location"));
                return;
            }

            Map<String, Map<String, InstanceInformation>> topology = Maps.newHashMap();

            final List<PaaSNodeTemplate> nodes = deploymentContext.getPaaSTopology().getNonNatives();




            for (PaaSNodeTemplate node : nodes) {
                Map<String, InstanceInformation> instancesInfo = new HashMap<>();
                // We lookup Entities based on tosca.id (getDeploymentId())
//            String appId = deploymentContext.getDeployment().getOrchestratorDeploymentId();
                InstanceInformation ins = null;
                if(node.getIndexedToscaElement().isAbstract()){
                    ins = this.getAbstractServiceInfo(serranoApp.getId());
                }else {

                    ins = this.getInstanceInformation(serranoApp, node, location);

                }
                if(ins != null) {
                    instancesInfo.put(node.getId(), ins);
                    PaaSInstanceStateMonitorEvent istat = new PaaSInstanceStateMonitorEvent();
                    istat.setOrchestratorId(deploymentContext.getDeployment().getOrchestratorId());
                    istat.setInstanceId(deploymentContext.getDeploymentId());
                    istat.setNodeTemplateId(node.getId());
                    istat.setInstanceState(ins.getState());
                    istat.setInstanceStatus(ins.getInstanceStatus());
                    istat.setAttributes(ins.getAttributes());
                    istat.setRuntimeProperties(ins.getRuntimeProperties());
                    istat.setDeploymentId(deploymentContext.getDeploymentId());

                    eventService.registerEvent(new Date(System.currentTimeMillis()), istat);
                    if(istat.getInstanceStatus().equals(InstanceStatus.FAILURE)){
                        deploymentStatuses.put(deploymentContext.getDeploymentId(), Optional.of(DeploymentStatus.WARNING));
                    }
                }


                topology.put(node.getId(), instancesInfo);


            }

            callback.onSuccess(topology);

        }
    }

    private InstanceInformation getInstanceInformation(SerranoApp id, PaaSNodeTemplate node, Location location) {
        Map<String, String> runtimeProps = new HashMap<>();


        InstanceStatus instanceStatus = InstanceStatus.PROCESSING;
        String state = "processing";

//        Map<String, String> slurmInfo = slurmClient.getInfo(configuration, SLURM_JOB_IDS.get(id), info.getAttributes().get("SLURM_ID"));

        Map<String, String> instanceInfo = null;
        if(location.getInfrastructureType().equals(TUFAOrchestrator.KUBE_LOCATION)) {
            instanceInfo = getKubeInfo(id, node);
        }else if(location.getInfrastructureType().equals(TUFAOrchestrator.SERRANO_LOCATION)){
            instanceInfo = getSerranoInfo(id, node);
        }



        if(instanceInfo != null){
            runtimeProps.putAll(instanceInfo);
            if(instanceInfo.get("status") != null){
                state = instanceInfo.get("status");
                instanceStatus = InstanceStatus.valueOf(state);
            }
        }

        return new InstanceInformation(state, instanceStatus, newHashMap(), runtimeProps, newHashMap());
    }

    private Map<String, String> getKubeInfo(SerranoApp app, PaaSNodeTemplate node) {
        Map<String, String> result = new HashMap<>();
            String nodeName = node.getTemplate().getName().toLowerCase();
        String deploymentName = nodeName + "-" + app.getId();
        Deployment deployment = app.getDeployments().stream().filter(
                d -> d.getMetadata().getName().equals(deploymentName)).findFirst().orElse(null);
//        String deploymentName = deployment.getMetadata().getName();

        Deployment existingDeployment = kubeClient.apps().deployments().withName(deploymentName).get();
        if (existingDeployment != null) {
            io.fabric8.kubernetes.api.model.apps.DeploymentStatus status = existingDeployment.getStatus();
            if (status.getAvailableReplicas() != null){
                result.put("status", InstanceStatus.SUCCESS.name());
                if(app.getServices().get(nodeName) != null) {
                    for (Map.Entry<String, Service> entry : app.getServices().get(nodeName).entrySet()) {
                        Service service = entry.getValue();

                        Service existingService = kubeClient.services().withName(service.getMetadata().getName()).get();
                        if (existingService != null) {

                        } else {



                        }
                    }
                }
            }else{
                boolean progressing = false;
                boolean available = false;

                for (DeploymentCondition condition : status.getConditions()) {
                    if(condition.getType().equals("Progressing")){
                        progressing =  Boolean.parseBoolean(condition.getStatus());
                    }else if (condition.getType().equals("Available")){
                        available = Boolean.parseBoolean(condition.getStatus());
                    }
                    String cName = condition.getType() + "_" + condition.getStatus();
                    result.put(cName, condition.getMessage());
                }
                if (progressing){
                    if(available)
                        result.put("status", InstanceStatus.SUCCESS.name());
                    else
                        result.put("status", InstanceStatus.PROCESSING.name());
                }else {
                    if(available)
                        result.put("status", InstanceStatus.SUCCESS.name());
                    else
                        result.put("status", InstanceStatus.FAILURE.name());
                }

                if(!result.get("status").equals(InstanceStatus.SUCCESS.name())){
                    for (Pod pod : kubeClient.pods().withLabels(deployment.getSpec().getSelector().getMatchLabels()).list().getItems()) {
                        for (ContainerStatus containerStatus : pod.getStatus().getContainerStatuses()) {
                            result.put(pod.getMetadata().getName() + "_" + containerStatus.getName(), containerStatus.getState().toString());
                        }
                        for (PodCondition condition : pod.getStatus().getConditions()) {
                            result.put(pod.getMetadata().getName() + "_" + condition.getType() + "_message" , condition.getMessage());
                            result.put(pod.getMetadata().getName() + "_" + condition.getType() + "_reason" , condition.getReason());
                        }

                    }
                }

            }
        } else {
            result.put("status", InstanceStatus.FAILURE.name());
        }
        return result;
    }

    private Map<String, String> getSerranoInfo(SerranoApp app, PaaSNodeTemplate node) {
        Map<String, String> result = new HashMap<>();
        result.put("status", InstanceStatus.PROCESSING.name());

        String nodeName = node.getTemplate().getName().toLowerCase();
        String deploymentName = nodeName + "-" + app.getId();
        String deploymentUUID = app.getSerranoUUID();
        if(deploymentUUID == null){
            return result;
        }
        List<NameValuePair> headers = new ArrayList<>();
        headers.add(new BasicHeader("Content-Type", "application/json"));
        try {
            CloseableHttpResponse response = roClient.getUrlEncoded("/orchestrator/deployments/logs/" + deploymentUUID, headers);
            if(response.getStatusLine().getStatusCode() == 200){
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> map = mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
                Map<String, Object> deployments = (Map<String, Object>) map.get("deployments");

                int status = (int) deployments.get("status");
                if(status < 2)
                    result.put("status", InstanceStatus.FAILURE.name());
                else if(status < 6)
                    result.put("status", InstanceStatus.PROCESSING.name());
                else
                    result.put("status", InstanceStatus.SUCCESS.name());

                List logs = (List) deployments.get("logs");
                StringBuilder sb = new StringBuilder();
                for(Object o : logs){
                    sb.append(o.toString());
                    sb.append("\n");
                }
                result.put("events", sb.toString());
            }
            response.close();
        } catch (IOException e) {
            log.info("Error occured when contacting the Resource Orchestrator");
        } catch (URISyntaxException e) {
            log.info(e.toString());
        }

        return result;
    }

    private InstanceInformation getAbstractServiceInfo(String id) {
        Map<String, String> runtimeProps = new HashMap<>();


        InstanceStatus instanceStatus = InstanceStatus.SUCCESS;

        String state = "success";

        return new InstanceInformation(state, instanceStatus, newHashMap(), runtimeProps, newHashMap());
    }

    @Override
    public synchronized void getEventsSince(Date date, int maxEvents, IPaaSCallback<AbstractMonitorEvent[]> eventCallback) {
        eventService.getEventsSince(date, maxEvents, eventCallback);
    }

    @Override
    public void executeOperation(PaaSTopologyDeploymentContext deploymentContext, NodeOperationExecRequest request,
            IPaaSCallback<Map<String, String>> operationResultCallback) throws OperationExecutionException {
        log.warn("EXEC OP not supported: " + request);

    }

    @Override
    public void switchMaintenanceMode(PaaSDeploymentContext deploymentContext, boolean maintenanceModeOn) throws MaintenanceModeException {
        log.info("MAINT MODE (ignored): " + maintenanceModeOn);
    }

    @Override
    public void switchInstanceMaintenanceMode(PaaSDeploymentContext deploymentContext, String nodeId, String instanceId, boolean maintenanceModeOn)
            throws MaintenanceModeException {
        log.info("MAINT MODE for INSTANCE (ignored): " + maintenanceModeOn);
    }

}
