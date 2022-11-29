package tufa;

import alien4cloud.application.ApplicationService;

import tufa.model.soe.SOEGroup;
import tufa.model.slurm.SLURMTask;
import tufa.model.slurm.SLURMWorkflow;
import tufa.model.slurm.TaskInfo;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.application.Application;
import alien4cloud.model.common.Tag;
import alien4cloud.orchestrators.locations.services.LocationService;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.MaintenanceModeException;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.SneakyThrows;
import mesosphere.marathon.client.MarathonException;
import mesosphere.marathon.client.model.v2.*;
import org.alien4cloud.tosca.model.definitions.FunctionPropertyValue;
import org.alien4cloud.tosca.model.definitions.IValue;
import org.alien4cloud.tosca.model.definitions.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import tufa.model.soe.ServiceResource;

import javax.ws.rs.core.Response;
import java.util.*;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.newHashMap;

public abstract class TUFAProvider implements IConfigurablePaaSProvider<Configuration> {
    private static final Logger log = LoggerFactory.getLogger(TUFAProvider.class);

    protected Configuration configuration;




    protected Map<String,PaaSTopologyDeploymentContext> knownDeployments = Maps.newConcurrentMap();
    protected Map<String, Optional<DeploymentStatus>> deploymentStatuses = Maps.newConcurrentMap();
    protected Map<String, String> SLURM_JOB_IDS = Maps.newConcurrentMap();

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private EventService eventService;

    @Autowired
    private LocationService locationService;


    @Autowired
    @Qualifier("alien-es-dao")
    private IGenericSearchDAO alienDAO;

    @Autowired
    private BeanFactory beanFactory;

    @Autowired
    private SLURMOrcClient slurmClient;

    @Autowired
    private SOEClient soeClient;

    @Autowired
    private SLURMMappingService slurmMappingService;

    @Autowired
    private SOEMappingService soeMappingService;

    ThreadLocal<ClassLoader> oldContextClassLoader = new ThreadLocal<ClassLoader>();
    private Map<String, SLURMWorkflow> slurmDeployments = Maps.newConcurrentMap();


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
        String appId = topologyVer.split(":")[0];


//        SLURMWorkflow workflow = slurmMappingService.buildWorkflowDefinition(deploymentContext);

        knownDeployments.put(deploymentContext.getDeploymentId(), deploymentContext);
        deploymentStatuses.put(deploymentContext.getDeploymentId(), Optional.of(DeploymentStatus.DEPLOYMENT_IN_PROGRESS));

        PaaSDeploymentStatusMonitorEvent updatedStatus = new PaaSDeploymentStatusMonitorEvent();
        updatedStatus.setDeploymentId(deploymentContext.getDeploymentId());
        updatedStatus.setDeploymentStatus(DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
        eventService.registerEvent(new Date(System.currentTimeMillis()), updatedStatus);

        // OLD CloudLightning deploy code
//        log.info("DEPLOY "+deploymentContext+" / "+callback);
        String appVer = topologyVer.split(":")[1];
//
        Map<String, ServiceResource> servicesLocation = soeClient.getServicesLocation(appId, appVer);
//        // Here check dependencies and cache service to orchestrator clients
        SOEGroup group = soeMappingService.buildGroupDefinition(deploymentContext, servicesLocation);

//        knownDeployments.put(deploymentContext.getDeploymentId(), deploymentContext);
//
//        startAppsAfterDependencies(group.getMarathonServices().get(0), deploymentContext, group);

        //END OLD CloudLightning deploy code


//        boolean deployed = startWorkflow(deploymentContext, workflow);
        boolean deployed = true;
        if(deployed) {
            deploymentStatuses.put(deploymentContext.getDeploymentId(), Optional.of(DeploymentStatus.DEPLOYED));
//            slurmDeployments.put(deploymentContext.getDeploymentId(), workflow);
            if (callback != null) callback.onSuccess(null);
        }else{
//            knownDeployments.remove(deploymentContext.getDeploymentId());
//            slurmDeployments.remove(deploymentContext.getDeploymentId());
            deploymentStatuses.put(deploymentContext.getDeploymentId(), Optional.of(DeploymentStatus.UNDEPLOYED));

            PaaSDeploymentStatusMonitorEvent status = new PaaSDeploymentStatusMonitorEvent();
            status.setDeploymentId(deploymentContext.getDeploymentId());
            status.setDeploymentStatus(DeploymentStatus.UNDEPLOYED);
            eventService.registerEvent(new Date(System.currentTimeMillis()), status);

            if (callback != null) callback.onFailure (new Exception("Cannot start workflow"));
        }


    }

    private void startAppsAfterDependencies(Group group, PaaSTopologyDeploymentContext paaSTopologyDeploymentContext, SOEGroup clGroup) {
        Map<String, Object> deployedApps = new HashMap<>();
        List<Object> apps = new ArrayList<>(group.getApps());
        for(Object t : clGroup.getBrooklynServices().values()){
            apps.add(t);
        }
        for(Object o : apps){
            if(o instanceof App) {
                App app = (App) o;
                app.setId("/" + group.getId() + "/" + app.getId());
            }
        }
        group.setApps(new ArrayList<>());
        Result groupResult = null;
//        try {
//            App app = (App) apps.stream().filter(t -> t instanceof App).findFirst().orElse(null);
//            String[] appIdTokens = apps.get(0).getId().split("/");
//            String appId = appIdTokens[appIdTokens.length - 1];
//            CLMarathon clMarathon = clGroup.getMarathonClients().get(appId);
//            groupResult = clMarathon.createGroup(group);
//            // Store the deployment ID to handle event mapping
//            if(groupResult != null){
//
//                clGroup.getMarathonEvents().get(appId).registerDeployment(groupResult.getDeploymentId(), paaSTopologyDeploymentContext.getDeploymentId(), DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
//            }
//        } catch (MarathonException e) {
//            log.error("Failure while deploying - Got error code [" + e.getStatus() + "] with message: " + e.getMessage());
//        }
        List<App> toStart = new ArrayList<>();
        paaSTopologyDeploymentContext.getDeployment().setOrchestratorDeploymentId(paaSTopologyDeploymentContext.getDeploymentId());
        alienDAO.save(paaSTopologyDeploymentContext.getDeployment());
        // (the result is a 204 creating, whose entity is a TaskSummary
        // with an entityId of the entity which is created and id of the task)
        deploymentStatuses.put(paaSTopologyDeploymentContext.getDeploymentId(), Optional.<DeploymentStatus>absent());
        while(!apps.isEmpty()){
            Object o = apps.remove(0);
            if(o instanceof App) {
                group = deployMarathonApp(group, paaSTopologyDeploymentContext, clGroup, deployedApps, apps, (App) o);

            }else{
//                deployBrooklynApp(group, paaSTopologyDeploymentContext, clGroup, deployedApps, apps, (PaaSNodeTemplate) o);
            }
            if(!clGroup.getDeployedApps().equals(deployedApps)){
                clGroup.setDeployedApps(deployedApps);
                //FixMe: Check how this will be handled in tufa
//                cloudLightningDeployments.put(paaSTopologyDeploymentContext.getDeploymentId(), clGroup);
//                tufaDeployments.put(paaSTopologyDeploymentContext.getDeploymentId(), clGroup);
                deploymentStatuses.put(paaSTopologyDeploymentContext.getDeploymentId(), Optional.of(DeploymentStatus.DEPLOYED));
            }

        }
    }

    private Group deployMarathonApp(Group group, PaaSTopologyDeploymentContext deploymentContext, SOEGroup clGroup, Map<String, Object> deployedApps, List<Object> apps, App app) {

        String[] appIdTokens = app.getId().split("/");
        String appId = appIdTokens[appIdTokens.length - 1];
        if (app.getDependencies() == null) {
            App result = clGroup.getMarathonClients().get(appId).createApp(app);
            clGroup.getMarathonEvents().get(appId).registerDeployment(result.getDeployments().get(0).getId(), deploymentContext.getDeploymentId(), DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
            deployedApps.put(app.getId().toLowerCase(), result);

        } else {
            boolean depMet = true;
            for (String dep : clGroup.getDependencies().get(app.getLabels().get("name"))) {
                Object depApp = deployedApps.get(dep);
                if(depApp == null){
                    //no cloudlightning service deployed
                    //try to see if a marathon app is deployed
                    String dep2 = "/" + group.getId() + "/" + dep.toLowerCase();
                    depApp = deployedApps.get(dep2);
                }
                if (depApp == null) {
                    //dependency is not currently deployed; add app back to queue
                    apps.add(app);
                    depMet = false;
                    break;
                } else {
                    if (depApp instanceof App) {
                        App mApp = (App) depApp;
                        List<Deployment> deployments = clGroup.getMarathonClients().get(dep.toLowerCase()).getDeployments();
                        while (deployMentsContainDeps(deployments, app, group)) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            deployments = clGroup.getMarathonClients().get(dep.toLowerCase()).getDeployments();
                        }
                        final List<Task> tasks = clGroup.getMarathonClients().get(dep.toLowerCase()).getAppTasks(mApp.getId()).getTasks();
                        boolean isUp = isServiceUp(tasks);

                        if(!isUp){
                            depMet = false;
                            apps.add(app);
                            break;
                        }

                        app = replaceDependentEndpoints(app, mApp, deploymentContext, clGroup);
                    }else{
                        //TODO Check for a deployed cloudlightning app an get endpoints
                    }

                }
            }
            if (depMet) {

//                group = clGroup.getMarathonClients().get(app.getId().split("/")[2]).getGroup(group.getId());
//                group.getApps().add(app);
//                group.setVersion(null);
                App app1 = clGroup.getMarathonClients().get(app.getId().split("/")[2]).createApp(app);
                clGroup.getMarathonEvents().get(appId).registerDeployment(app1.getDeployments().get(0).getId(), deploymentContext.getDeploymentId(), DeploymentStatus.DEPLOYMENT_IN_PROGRESS);
                deployedApps.put(app.getId().toLowerCase(), app1);
            }
        }
        return group;
    }

    private boolean deployMentsContainDeps(List<Deployment> deployments, App app, Group group) {
        for(Deployment d : deployments){
            for (String a : app.getDependencies()){
                String dep = "/" + group.getId() + "/" + a  ;
                if(d.getAffectedApps().contains(dep))
                    return true;
            }
        }
        return false;
    }

    private boolean isServiceUp(List<Task> tasks) {
        boolean depMet = true;
        if (tasks.size() == 0) {
//                                    try {
//                                        Thread.sleep(1000);
//                                    } catch (InterruptedException e) {
//                                        e.printStackTrace();
//                                    }
            depMet = false;
        } else {
            boolean breaking = false;
            for (Task t : tasks) {
                if (t.getHealthCheckResults() == null || t.getHealthCheckResults().stream().filter(c -> c.getAlive()).count() < t.getHealthCheckResults().size()) {
                    breaking = true;
                    break;
                }
            }
            if (breaking) {
                depMet = false;
            }
        }
        return depMet;
    }

    private App replaceDependentEndpoints(App app, App appDependency, PaaSTopologyDeploymentContext paaSTopologyDeploymentContext, SOEGroup clGroup) {
        java.util.Optional<PaaSNodeTemplate> app1Node = paaSTopologyDeploymentContext.getPaaSTopology().getNonNatives().stream()
                .filter(p -> p.getId().toLowerCase().equals(app.getId().split("/")[2]))
                .findFirst();
        java.util.Optional<PaaSNodeTemplate> depNode = paaSTopologyDeploymentContext.getPaaSTopology().getNonNatives().stream()
                .filter(p -> p.getId().toLowerCase().equals(appDependency.getId().split("/")[2]))
                .findFirst();
        if(!(app1Node.isPresent() && depNode.isPresent()))
            return app;

        final Operation createOperation = app1Node.get().getInterfaces().get("tosca.interfaces.node.lifecycle.Standard").getOperations().get("create");
        if (createOperation.getInputParameters() != null) {
            createOperation.getInputParameters().forEach((key, val) -> {

                // Inputs can either be a ScalarValue or a pointer to a capability targeted by one of the node's requirements
                final List<Task> tasks = clGroup.getMarathonClients().get(appDependency.getId().split("/")[2]).getAppTasks(appDependency.getId()).getTasks();
                Task task = tasks.get(0);
                String value = soeMappingService.retrieveValue(app1Node.get(), paaSTopologyDeploymentContext.getPaaSTopology(), val, task);
                soeMappingService.addInputParameter(app, key, value);
            });
        }

        return app;
    }

    private Map<String, String> addDependentPropsFromMarathon(PaaSNodeTemplate brNode, App appDependency,
                                                              PaaSTopologyDeploymentContext deploymentContext, Task task) {
//        java.util.Optional<PaaSNodeTemplate> app1Node = deploymentContext.getPaaSTopology().getAllNodes().values().stream()
//                .filter(p -> p.getId().toLowerCase().equals(brNode.getId()))
//                .findFirst();
        PaaSNodeTemplate app1Node = deploymentContext.getPaaSTopology().getAllNodes().get(brNode.getId());
        java.util.Optional<PaaSNodeTemplate> depNode = deploymentContext.getPaaSTopology().getNonNatives().stream()
                .filter(p -> p.getId().toLowerCase().equals(appDependency.getId().split("/")[2]))
                .findFirst();
        if(!(app1Node != null && depNode.isPresent()))
            return null;

        final Operation createOperation = app1Node.getInterfaces().get("tosca.interfaces.node.lifecycle.Standard").getOperations().get("create");
        if (createOperation.getInputParameters() != null) {
            Map<String, String> property = new HashMap<>();

            createOperation.getInputParameters().forEach((key, val) -> {

                // Inputs can either be a ScalarValue or a pointer to a capability targeted by one of the node's requirements
//                final List<Task> tasks = clGroup.getMarathonClients().get(brNode.getId()).getAppTasks(appDependency.getId()).getTasks();
//                Task task = tasks.get(0);
                if (val instanceof FunctionPropertyValue && "get_property".equals(((FunctionPropertyValue) val).getFunction())) {
                    if ("REQ_TARGET".equals(((FunctionPropertyValue) val).getTemplateName())) {
                        String requirementName = ((FunctionPropertyValue) val).getCapabilityOrRequirementName();
                        String propertyName = ((FunctionPropertyValue) val).getElementNameToFetch();
                        String value = soeMappingService.retrieveValue(app1Node, deploymentContext.getPaaSTopology(), val, task);
//                cloudLightningMappingService.addInputParameter(brNode, key, value);

                        property.put(requirementName + "_" + propertyName, value);

                    }
                }

            });
            return property;
        }

        return null;
    }


    private Object deployBrooklynApp(Group group, PaaSTopologyDeploymentContext deploymentContext, SOEGroup clGroup, Map<String, Object> deployedApps, List<Object> apps, PaaSNodeTemplate brooklynNode) {
        Map<String, Object> campYaml = createBasicBrooklynService(deploymentContext, clGroup, brooklynNode);

        if (clGroup.getDependencies().get(brooklynNode.getId()) == null) {
            campYaml.put("cloudlightning.config", ImmutableMap.of("tosca.deployment.id", deploymentContext.getDeploymentId()));
            try {
                String entityId = deployBrooklyn(deploymentContext, null, campYaml);
                deployedApps.put(brooklynNode.getId(), entityId);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

        } else {
            boolean depMet = true;
            for (String dep : clGroup.getDependencies().get(brooklynNode.getId())) {

                Object depApp = deployedApps.get(dep);
                if(depApp == null){
                    //no cloudlightning service deployed
                    //try to see if a marathon app is deployed
                    String dep2 = "/" + group.getId() + "/" + dep.toLowerCase();
                    depApp = deployedApps.get(dep2);
                }
                if (depApp == null) {
                    //dependency is not currently deployed; add app back to queue
                    apps.add(brooklynNode);
                    depMet = false;
                    break;
                } else if (depApp instanceof App) {
                    App mApp = (App) depApp;
                    final List<Task> tasks = clGroup.getMarathonClients().get(dep.toLowerCase()).getAppTasks(mApp.getId()).getTasks();
                    boolean isUp = isServiceUp(tasks);

                    if(!isUp){
                        depMet = false;
                        apps.add(brooklynNode);
                        break;
                    }
                    Task task = tasks.get(0);
                    Map<String, String> brooklynConfig = addDependentPropsFromMarathon(brooklynNode,
                            mApp, deploymentContext, task);
                    if(brooklynConfig != null) {
                        brooklynConfig.put("tosca.deployment.id", deploymentContext.getDeploymentId());
                        campYaml.put("brooklyn.config", brooklynConfig);
                    }else{
                        campYaml.put("brooklyn.config", ImmutableMap.of("tosca.deployment.id", deploymentContext.getDeploymentId()));
                    }
                }else{
                    //TODO Retrieve details from brooklyn and add up Dependent endpoints
                    log.info("Not implemented yet!");
                }


            }
            if (depMet) {
//                List<Deployment> deployments = clGroup.getMarathonClients().get(app.getId()).getDeployments();
//                while (deployMentsContainDeps(deployments, app, group)) {
//                    try {
//                        Thread.sleep(1000);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    deployments = clGroup.getMarathonClients().get(app.getId()).getDeployments();
//                    log.info("Deployments: {}", deployments.size());
//                }

//                group = clGroup.getMarathonClients().get(app.getId()).getGroup(group.getId());
//                group.getApps().add(app);
//                group.setVersion(null);
//                clGroup.getMarathonClients().get(app.getId()).updateGroup(group);
//                campYaml.put("cloudlightning.config", ImmutableMap.of("tosca.deployment.id", deploymentContext.getDeploymentId()));
                try {
                    String entityId = deployBrooklyn(deploymentContext, null, campYaml);
                    deployedApps.put(brooklynNode.getId(), entityId);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        }
        return group;
    }

    private Map<String, Object> createBasicBrooklynService(PaaSTopologyDeploymentContext deploymentContext, SOEGroup clGroup, PaaSNodeTemplate brooklynNode) {
        String topologyId = deploymentContext.getDeploymentTopology().getId();
        Map<String,Object> campYaml = Maps.newLinkedHashMap();
        addRootPropertiesAsCamp(deploymentContext, campYaml);

        List<Object> svcs = Lists.newArrayList();
        String nodeId = brooklynNode.getId();
        Map<String, Object> svc = Maps.newHashMap();
        svc.put("type", "alien4cloud_deployment_topology:" + topologyId + "$"+nodeId);
        svc.put("location", clGroup.getBrooklynLocation().get(nodeId));
//        Map<String, String> env = new HashMap();
////        env.put("RT_ENGINE", "$cloudlightning:config(\"raytrace_engine_ip_address\")");
////        svc.put("env", env);

//        svc.put("cloudlightning.parameters", props);
        svcs.add(svc);

        campYaml.put("services", svcs);
        return campYaml;
    }


    private void validate(Response r) {
        if (r==null) return;
        if ((r.getStatus() / 100)==2) return;
        throw new IllegalStateException("Server returned "+r.getStatus() + " message " + r.getEntity());
    }

    private void addRootPropertiesAsCamp(PaaSTopologyDeploymentContext deploymentContext, Map<String,Object> result) {
        if (applicationService!=null) {
            try {
                Application app = applicationService.getOrFail(deploymentContext.getDeployment().getSourceId());
                if (app!=null) {
                    result.put("name", app.getName());
                    if (app.getDescription()!=null) result.put("description", app.getDescription());

                    List<String> tags = Lists.newArrayList();
                    for (Tag tag: app.getTags()) {
                        tags.add(tag.getName()+": "+tag.getValue());
                    }
                    if (!tags.isEmpty())
                        result.put("tags", tags);

                    // TODO icon, from app.getImageId());
                    return;
                }
                log.warn("Application null when deploying "+deploymentContext+"; using less information");
            } catch (NotFoundException e) {
                // ignore, fall through to below
                log.warn("Application instance not found when deploying "+deploymentContext+"; using less information");
            }
        } else {
            log.warn("Application service not available when deploying "+deploymentContext+"; using less information");
        }

        // no app or app service - use what limited information we have
        result.put("name", "A4C: "+deploymentContext.getDeployment().getSourceName());
        result.put("description", "Created by Alien4Cloud from application "+deploymentContext.getDeployment().getSourceId());
    }

    private String deployBrooklyn(PaaSTopologyDeploymentContext deploymentContext, IPaaSCallback<?> callback, Map<String, Object> campYaml) throws JsonProcessingException {
        try {
            useLocalContextClassLoader();
            String campYamlString = new ObjectMapper().writeValueAsString(campYaml);
            log.info("DEPLOYING: "+campYamlString);
//            Response result = getNewBrooklynApi().getApplicationApi().createFromYaml( campYamlString );
//            TaskSummary createAppSummary = BrooklynApi.getEntity(result, TaskSummary.class);
//            log.info("RESULT: "+result.getEntity());
//            validate(result);
//            String entityId = createAppSummary.getEntityId();
            String entityId = "dummy entity id; brooklyn is being replaced with jclouds or similar";

            // inital entry which will immediately trigger an event in getEventsSince()
            return entityId;
        } catch (Throwable e) {
            log.warn("ERROR DEPLOYING", e);
//            knownDeployments.remove(deploymentContext.getDeploymentId());
            if (callback!=null) callback.onFailure(e);
            throw e;
        } finally { revertContextClassLoader(); }
    }

    private boolean startWorkflow(PaaSTopologyDeploymentContext paaSTopologyDeploymentContext, SLURMWorkflow workflow) {
        Map<String, Object> deployedApps = new HashMap<>();


        //create slurm script
        StringBuilder sb = new StringBuilder("#!/bin/bash\n");
        sb.append(String.format("#SBATCH -J %s\n", workflow.getId()));



        List<SLURMTask> finalTasks = new ArrayList<>();
        Map<String, List<String>> dependencies = new HashMap<>();
        for(String x : workflow.getDependencies().keySet()){
            dependencies.put(x, new ArrayList<>());
            dependencies.get(x).addAll(workflow.getDependencies().get(x));
        }

        Queue<SLURMTask> Q = new LinkedList<>(workflow.getTasks());
        while(!Q.isEmpty()){
            SLURMTask t = Q.remove();
            if(dependencies.get(t.getId()) != null && dependencies.get(t.getId()).size() > 0) {
                Q.add(t);

            }else{
                finalTasks.add(t);
                for(String x : dependencies.keySet()){
                    dependencies.get(x).remove(t.getId());
                }
            }
        }

        AtomicInteger numNodes = new AtomicInteger(0);
        AtomicInteger memory = new AtomicInteger(0);
        finalTasks.forEach(t -> {


            if(workflow.getDependencies().containsKey(t.getId())){
                numNodes.set(Math.max(numNodes.get(), t.getNumNodes()));
                if(t.getMem() != null) {
                    int mem = computeMemoryMB(t.getMem());
                    memory.set(Math.max(memory.get(), mem));
                }
            }else{
                numNodes.addAndGet(t.getNumNodes());
                if(t.getMem() != null) {

                    int mem = computeMemoryMB(t.getMem());
                    memory.addAndGet(mem);
                }

            }
        });


        sb.append(String.format("#SBATCH -N %d\n", numNodes.get()));
        sb.append(String.format("#SBATCH --mem=%dmb\n", memory.get()));

        int N = finalTasks.size();
        workflow.setTasks(finalTasks);

        String moduleLoads = "\n";
        sb.append(moduleLoads);

        for(int i = 0; i < N; i++){
            SLURMTask t = finalTasks.get(i);
            String taskName = t.getId();
            String impl = t.getImplementation();
            slurmClient.compileImplementationSSH(configuration, workflow.getId(), taskName, impl);
            Map<String, String> attrs = new HashMap<>();
            attrs.put("SLURM_ID", String.valueOf(i));
            workflow.getTaskInformation().put(t.getId(), new TaskInfo(t.getId(), "DEPLOYING", attrs));

            if(t.getMem()!= null) {
                sb.append(String.format("srun -n %d --mem=%dmb ./%s", t.getNumNodes(), computeMemoryMB(t.getMem()), taskName));
            }else{
                sb.append(String.format("srun -n %d ./%s", t.getNumNodes(), taskName));
            }

            if(i < N - 1){
                SLURMTask nextTask = finalTasks.get(i+1);
                boolean depends = false;
                for(int j = 0; j <= i; j++) {
                    List<String> deps = workflow.getDependencies().get(nextTask.getId());
                    if (deps != null && deps.contains(finalTasks.get(j).getId())) {
                        //next task is dependent on this one
                        depends = true;
                        break;
                    }
                }
                if(!depends){
                    sb.append(" & ");
                }

            }
            sb.append("\n");

        }

        slurmClient.copySLURMScript(configuration, workflow.getId(), sb.toString());

        String sBatch = slurmClient.sBatch(configuration, workflow.getId());

        PaaSDeploymentStatusMonitorEvent updatedStatus = new PaaSDeploymentStatusMonitorEvent();
        if(sBatch.contains("Submitted batch job")) {
            String jobNumber = sBatch.split("\\s+")[3];
            SLURM_JOB_IDS.put(workflow.getId(), jobNumber);


            updatedStatus.setDeploymentId(paaSTopologyDeploymentContext.getDeploymentId());
            updatedStatus.setDeploymentStatus(DeploymentStatus.DEPLOYED);

            eventService.registerEvent(new Date(System.currentTimeMillis()), updatedStatus);
            return true;
        }else{

            updatedStatus.setDeploymentId(paaSTopologyDeploymentContext.getDeploymentId());
            updatedStatus.setDeploymentStatus(DeploymentStatus.UNDEPLOYED);
            eventService.registerEvent(new Date(System.currentTimeMillis()), updatedStatus);
            return false;
        }

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
    public void undeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback, boolean force) {
//        log.info("UNDEPLOY " + deploymentContext + " / " + callback);
        SLURMWorkflow workflow = slurmDeployments.get(deploymentContext.getDeploymentId());

        if(workflow != null) {
            slurmClient.cancelJob(configuration, SLURM_JOB_IDS.get(workflow.getId()));

            //TODO clean folder?

        }
        String deploymentId = deploymentContext.getDeploymentId();
        slurmDeployments.remove(deploymentId);
        this.deploymentStatuses.put(deploymentId, Optional.fromNullable(DeploymentStatus.UNDEPLOYED));

//        knownDeployments.remove(deploymentContext.getDeploymentId());
        PaaSDeploymentStatusMonitorEvent updatedStatus = new PaaSDeploymentStatusMonitorEvent();
        updatedStatus.setDeploymentId(deploymentId);
        updatedStatus.setDeploymentStatus(DeploymentStatus.UNDEPLOYED);
        eventService.registerEvent(new Date(System.currentTimeMillis()), updatedStatus);
        if (callback != null) {
            callback.onSuccess(null);
        }
    }

//    @Override
//    public void cloudlightningUndeploy(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
//        log.info("UNDEPLOY " + deploymentContext + " / " + callback);
//        CLGroup clGroup = cloudLightningDeployments.get(deploymentContext.getDeploymentId());
//
//        if(clGroup != null) {
//            for (String id : clGroup.getDeployedApps().keySet()) {
//                Object o = clGroup.getDeployedApps().get(id);
//                if (o instanceof App) {
//                    App app = (App) o;
//                    if (id.contains("/")) {
//                        id = id.split("/")[2];
//                    }
//                    Result result = clGroup.getMarathonClients().get(id).deleteApp(app.getId());
//                    clGroup.getMarathonEvents().get(id).registerDeployment(result.getDeploymentId(), deploymentContext.getDeploymentId(), DeploymentStatus.UNDEPLOYED);
//                } else if (o instanceof String) {
//                    //this is a Brooklyn entity id
//                    String entityId = (String) o;
//                    Response result = getNewBrooklynApi().getEntityApi().expunge(entityId, entityId, true);
//                    validate(result);
//                }
//            }
//            cloudLightningDeployments.remove(deploymentContext.getDeploymentId());
//            this.deploymentStatuses.put(deploymentContext.getDeploymentId(), Optional.fromNullable(DeploymentStatus.UNDEPLOYED));
//            knownDeployments.remove(deploymentContext.getDeploymentId());
//            if (callback != null) {
//                callback.onSuccess(null);
//                this.deploymentStatuses.remove(deploymentContext.getDeploymentId());
//            }
//        }
//    }

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
            DeploymentStatus status = DeploymentStatus.UNDEPLOYED;
            if(slurmDeployments.get(deploymentContext.getDeploymentId()) != null){
                status = DeploymentStatus.DEPLOYED;
            }
            callback.onSuccess(deploymentStatus.isPresent() ? deploymentStatus.get() : DeploymentStatus.UNDEPLOYED);
        }
    }

    @Override
    public void getInstancesInformation(PaaSTopologyDeploymentContext deploymentContext,
            IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {


        SLURMWorkflow workflow = slurmDeployments.get(deploymentContext.getDeploymentId());

        if(workflow != null) {
            Map<String, Map<String, InstanceInformation>> topology = Maps.newHashMap();
            final List<PaaSNodeTemplate> computes =   deploymentContext.getPaaSTopology().getComputes();
            final List<PaaSNodeTemplate> tasks = deploymentContext.getPaaSTopology().getNonNatives();
            List<PaaSNodeTemplate> datanodes = tasks.stream().filter(node ->
                    node.getTemplate().getType().equals("aspide.nodes.Data") ||
                            node.getDerivedFroms().contains("aspide.nodes.Data"))
                    .collect(Collectors.toList());

            tasks.removeAll(datanodes);

            computes.forEach(node -> {
                if(SLURM_JOB_IDS.get(workflow.getId()) != null){
                    Map<String, InstanceInformation> attrs = new HashMap<>();

                    InstanceInformation ii = new InstanceInformation("success", InstanceStatus.SUCCESS, newHashMap(), newHashMap(), newHashMap());
                    attrs.put(node.getId(), ii);

                    topology.put(node.getId(), attrs);
                }else{
                    Map<String, InstanceInformation> attrs = new HashMap<>();

                    InstanceInformation ii = new InstanceInformation("processing", InstanceStatus.PROCESSING, newHashMap(), newHashMap(), newHashMap());
                    attrs.put(node.getId(), ii);

                    topology.put(node.getId(), attrs);
                }
            });

            for (SLURMTask task : workflow.getTasks()) {
                // We lookup Entities based on tosca.id (getDeploymentId())
//            String appId = deploymentContext.getDeployment().getOrchestratorDeploymentId();
                TaskInfo taskInfo = workflow.getTaskInformation().get(task.getId());
                String slurmId = taskInfo.getAttributes().get("SLURM_ID");

                Map<String, InstanceInformation> instancesInfo = newHashMap();

                PaaSNodeTemplate nodeTemplate = tasks.stream()
                        .filter(p -> p.getId().equals(taskInfo.getId()))
                        .findFirst().orElse(null);
                if (nodeTemplate == null) {
                    continue;
                }


                Map<String, String> attr = new HashMap<>();
                attr.put("slurmJobID", SLURM_JOB_IDS.get(workflow.getId()));
                attr.put("slurmStep", slurmId);
                if(!taskInfo.getAttributes().containsKey("slurmJobID")){
                    taskInfo.getAttributes().putAll(attr);
                }

//                TaskInfo info = new TaskInfo(task.getId(), InstanceStatus.SUCCESS.name(), attr);
//                info.getAttributes().putAll(attr);
                final InstanceInformation ins = this.getInstanceInformation(workflow.getId(), taskInfo);
                instancesInfo.put(taskInfo.getId(), ins);
//                        });

//                if(!taskInfo.getState().equals(ins.getState())) {
                    PaaSInstanceStateMonitorEvent istat = new PaaSInstanceStateMonitorEvent();
                    istat.setInstanceId(task.getId());
                    istat.setNodeTemplateId(nodeTemplate.getId());
                    istat.setInstanceState(ins.getState());
                    istat.setInstanceStatus(ins.getInstanceStatus());
                    istat.setAttributes(ins.getAttributes());
                    istat.setRuntimeProperties(ins.getRuntimeProperties());
                    istat.setDeploymentId(deploymentContext.getDeploymentId());

                    eventService.registerEvent(new Date(System.currentTimeMillis()), istat);
                    workflow.getTaskInformation().put(task.getId(), taskInfo);
                    taskInfo.getAttributes().putAll(ins.getRuntimeProperties());
                    taskInfo.setState(ins.getState());
//                }


                topology.put(nodeTemplate.getId(), instancesInfo);

                PaaSRelationshipTemplate outputRel = nodeTemplate.getRelationshipTemplates().stream()
                        .filter(rel -> rel.instanceOf("aspide.relationships.OutputTo") || rel.instanceOf("aspide.relationships.InputFrom"))
                        .findFirst().orElse(null);
                if(outputRel != null) {

                    PaaSNodeTemplate outNode = datanodes.stream()
                            .filter(p -> p.getId().equals(outputRel.getTemplate().getTarget()))
                            .findFirst().orElse(null);
                    if(outNode != null){
                        Map<String, InstanceInformation> attrs = new HashMap<>();

                        InstanceInformation ii = new InstanceInformation(ins.getState(), ins.getInstanceStatus(), newHashMap(), newHashMap(), newHashMap());
                        attrs.put(outNode.getId(), ii);

                        topology.put(outNode.getId(), attrs);
                    }
                }






            }

            callback.onSuccess(topology);
        }
    }


//    @Override
    //FixMe: Use this for apps.
    public void cloudLightninggetInstancesInformation(PaaSTopologyDeploymentContext deploymentContext,
                                        IPaaSCallback<Map<String, Map<String, InstanceInformation>>> callback) {

//        BrooklynApi brooklynApi = getNewBrooklynApi();
//        CLGroup clGroup = cloudLightningDeployments.get(deploymentContext.getDeploymentId());
        SOEGroup clGroup = new SOEGroup();
        Map<String, Map<String, InstanceInformation>> topology = Maps.newHashMap();
        for(String id : clGroup.getDeployedApps().keySet()) {
            // We lookup Entities based on tosca.id (getDeploymentId())
//            String appId = deploymentContext.getDeployment().getOrchestratorDeploymentId();
            Object o = clGroup.getDeployedApps().get(id);
//            if(o instanceof String) {
//                String appId = (String) o;
//                List<EntitySummary> descendants = brooklynApi.getEntityApi().getDescendants(appId, appId, ".*");
//
//                // TODO: Either get all sensors for all descendants, or iterate through the descendants,
//                // building an InstanceInformation, and populating the topology
//
//                for (EntitySummary descendant : descendants) {
//                    String entityId = descendant.getId();
//                    if (entityId.equals(appId)) {
//                        continue;
//                    }
//                    String templateId = String.valueOf(brooklynApi.getEntityConfigApi().get(appId, entityId, "tosca.template.id", false));
//                    // TODO: Work out what to do with clusters, for now assume it's all flat
//                    InstanceInformation instanceInformation = new InstanceInformation();
//                    Map<String, Object> sensorValues = brooklynApi.getSensorApi().batchSensorRead(appId, entityId, null);
//                    ImmutableMap.Builder<String, String> sensorValuesStringBuilder = ImmutableMap.builder();
//                    ImmutableMap.Builder<String, String> attributeValuesStringBuilder = ImmutableMap.builder();
//                    for (Map.Entry<String, Object> entry : sensorValues.entrySet()) {
//                        if (entry.getKey().startsWith("tosca.attribute")) {
//                            attributeValuesStringBuilder.put(entry.getKey(), String.valueOf(entry.getValue()));
//                        } else {
//                            sensorValuesStringBuilder.put(entry.getKey(), String.valueOf(entry.getValue()));
//                        }
//                    }
//                    instanceInformation.setRuntimeProperties(sensorValuesStringBuilder.build());
//                    instanceInformation.setAttributes(attributeValuesStringBuilder.build());
//                    String serviceState = String.valueOf(sensorValues.get("service.state"));
//                    instanceInformation.setState(serviceState);
//                    instanceInformation.setInstanceStatus(LIFECYCLE_TO_INSTANCE_STATUS.get(Lifecycle.valueOf(serviceState)));
//                    topology.put(templateId, ImmutableMap.of(entityId, instanceInformation));
//                }
//            }else
                if(o instanceof App){
                App app = (App) o;

                try {
                    // Marathon tasks are alien instances
                    Map<String, InstanceInformation> instancesInfo = newHashMap();

                    PaaSNodeTemplate nodeTemplate2 = deploymentContext.getPaaSTopology().getAllNodes().get(id);
                    java.util.Optional<PaaSNodeTemplate> nodeTemplate = deploymentContext.getPaaSTopology().getNonNatives().stream()
                            .filter(p -> p.getId().toLowerCase().equals(app.getId().split("/")[2]))
                            .findFirst();
                    if(!nodeTemplate.isPresent()){
                        continue;
                    }
                    Map<String, IValue> attributes = nodeTemplate.get().getTemplate().getAttributes();
                    final Collection<Task> tasks = clGroup.getMarathonClients().get(nodeTemplate.get().getId().toLowerCase())
                            .getAppTasks(app.getId()).getTasks();
                    tasks.forEach(task -> {
                        final InstanceInformation instanceInformation = this.getInstanceInformation(task, attributes, deploymentContext.getPaaSTopology(), nodeTemplate.get());
                        instancesInfo.put(task.getId(), instanceInformation);
                    });

                    topology.put(nodeTemplate.get().getId(), instancesInfo);
                } catch (MarathonException e) {
                    switch (e.getStatus()) {
                        case 404: // The app cannot be found in marathon - we display no information
                            break;
                        default:
                            callback.onFailure(e);
                    }
                }
            }
        }

        callback.onSuccess(topology);
    }

    private InstanceInformation getInstanceInformation(Task task, Map<String, IValue> attributes, PaaSTopology paaSTopology, PaaSNodeTemplate paaSNodeTemplate) {
        InstanceInformation result = getInstanceInformation(task);
        for(String key : attributes.keySet()){
            String val = soeMappingService.retrieveValue(paaSNodeTemplate, paaSTopology, attributes.get(key), task);
            result.getAttributes().put(key, val);
        }
        return result;
    }

    /**
     * Get instance information, eg. status and runtime properties, from a Marathon Task.
     * @param task A Marathon Task
     * @return An InstanceInformation
     */
    private InstanceInformation getInstanceInformation(Task task) {
        final Map<String, String> runtimeProps = newHashMap();

        // Outputs Marathon endpoints as host:port1,port2, ...
        final Collection<String> ports = Collections2.transform(task.getPorts(), Functions.toStringFunction());
        runtimeProps.put("endpoint",
                "http://".concat(task.getHost().concat(":").concat(String.join(",", ports))));

        InstanceStatus instanceStatus;
        String state;

        // Leverage Mesos's TASK_STATUS - TODO: add Mesos 1.0 task states
        if(task.getState() != null) {
            switch (task.getState()) {
                case "TASK_RUNNING":
                    state = "started";
                    // Retrieve health checks results - if no healthcheck then assume healthy
                    instanceStatus =
                            java.util.Optional.ofNullable(task.getHealthCheckResults())
                                    .map(healthCheckResults ->
                                            healthCheckResults
                                                    .stream()
                                                    .findFirst()
                                                    .map(HealthCheckResults::getAlive)
                                                    .map(alive -> alive ? InstanceStatus.SUCCESS : InstanceStatus.FAILURE)
                                                    .orElse(InstanceStatus.PROCESSING)
                                    ).orElse(InstanceStatus.SUCCESS);
                    break;
                case "TASK_STARTING":
                    state = "starting";
                    instanceStatus = InstanceStatus.PROCESSING;
                    break;
                case "TASK_STAGING":
                    state = "creating";
                    instanceStatus = InstanceStatus.PROCESSING;
                    break;
                case "TASK_ERROR":
                    state = "stopped";
                    instanceStatus = InstanceStatus.FAILURE;
                    break;
                default:
                    state = "uninitialized"; // Unknown
                    instanceStatus = InstanceStatus.PROCESSING;
            }
        }else{
            state = "uninitialized";
            instanceStatus =
                    java.util.Optional.ofNullable(task.getHealthCheckResults())
                            .map(healthCheckResults ->
                                    healthCheckResults
                                            .stream()
                                            .findFirst()
                                            .map(HealthCheckResults::getAlive)
                                            .map(alive -> alive ? InstanceStatus.SUCCESS : InstanceStatus.FAILURE)
                                            .orElse(InstanceStatus.PROCESSING)
                            ).orElse(InstanceStatus.SUCCESS);
        }

        return new InstanceInformation(state, instanceStatus, newHashMap(), runtimeProps, newHashMap());
    }

    /**
     * Get instance information, eg. status and runtime properties, from an ASPIDE TaskInfo.
     * @param info A @TaskInfo object
     * @return An InstanceInformation
     */
    private InstanceInformation getInstanceInformation(String id, TaskInfo info) {
        Map<String, String> runtimeProps = newHashMap(info.getAttributes());


        InstanceStatus instanceStatus = InstanceStatus.PROCESSING;
        String state = "processing";

        Map<String, String> slurmInfo = slurmClient.getInfo(configuration, SLURM_JOB_IDS.get(id), info.getAttributes().get("SLURM_ID"));



        if(slurmInfo != null){
            runtimeProps.putAll(slurmInfo);
            if(slurmInfo.get("status") != null){
                state = slurmInfo.get("status");
                instanceStatus = InstanceStatus.valueOf(state);
            }
        }



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
