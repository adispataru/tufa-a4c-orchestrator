/*
 * Copyright 2017 Institute e-Austria Timisoara.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tufa;

import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import com.google.gson.JsonObject;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategyBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.jsonwebtoken.lang.Maps;
import org.alien4cloud.tosca.model.definitions.*;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.tosca.utils.InterfaceUtils;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import tufa.model.soe.SerranoApp;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for transformation of Alien SERRANO components into SERRANO Kubernetes descriptions.
 *
 * @author adrian
 *
 */
@Service
@Scope("prototype")
public class SerranoMappingService {

    private final Logger log = LoggerFactory.getLogger(SerranoMappingService.class);

    protected Configuration configuration;


    public Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Map a TUFA deployment context to a Serrano App definition.
     *
     * @param paaSTopologyDeploymentContext the deployment to process
     * @return A Serrano App definition
     */
    public SerranoApp buildSerranoDefinition(PaaSTopologyDeploymentContext paaSTopologyDeploymentContext) {
        //Setting serranoApp
        SerranoApp serranoApp = new SerranoApp();
        serranoApp.setId(paaSTopologyDeploymentContext.getDeploymentTopology().getId().toLowerCase().split(":")[0]);


        // Serrano topologies contain only non-natives nodes (eg. apps) and volumes.
        // Each is converted to a Kubernetes Depployment
        final List<PaaSNodeTemplate> nonNatives = paaSTopologyDeploymentContext.getPaaSTopology().getNonNatives();
        final List<PaaSNodeTemplate> volumes = paaSTopologyDeploymentContext.getPaaSTopology().getVolumes();
        List<PaaSNodeTemplate> iaasNodes = nonNatives.stream().filter(node ->
                node.getTemplate().getRequirements().get("host") != null &&
                        !node.getTemplate().getType().equals("tufa.nodes.Compute.Container") &&
                        node.getTemplate().getRequirements().get("host").getType().equals("tosca.capabilities.Container")
        ).collect(Collectors.toList());
        nonNatives.removeAll(iaasNodes);
        Map<String, NodeTemplate> matchedNodes = paaSTopologyDeploymentContext.getDeploymentTopology().getMatchReplacedNodes();


        nonNatives.forEach(node -> {
            // Find volumes attached to the node
            if(node.getTemplate().getCapabilities().containsKey("host"))
                return;
            if(node.getTemplate().getCapabilities().containsKey("accelerator"))
                return;

            if(node.getIndexedToscaElement().isAbstract())
                return;
            final List<PaaSNodeTemplate> attachedVolumes = volumes.stream()
                    .filter(paaSNodeTemplate -> paaSNodeTemplate.getRelationshipTemplates().stream()
                            .filter(paaSRelationshipTemplate -> paaSRelationshipTemplate.instanceOf("tufa.rel.MountPersistentVolume")).findFirst()
                            .map(paaSRelationshipTemplate -> paaSRelationshipTemplate.getTemplate().getTarget()).orElse("").equals(node.getId()))
                    .collect(Collectors.toList());
            buildDeployment(node, paaSTopologyDeploymentContext.getPaaSTopology(), attachedVolumes, serranoApp);
        });

        nonNatives.forEach(node -> {
            // Find volumes attached to the node
            if(node.getTemplate().getCapabilities().containsKey("host"))
                return;
            if(node.getTemplate().getCapabilities().containsKey("accelerator"))
                return;
            if(node.getIndexedToscaElement().isAbstract())
                return;
            updateAppDefinitionWithDependencies(node, paaSTopologyDeploymentContext, serranoApp);
        });

        return serranoApp;

    }

    /**
     * Map an alien PaaSNodeTemplate to a Marathon App Definition.
     *
     * @param paaSNodeTemplate the node template to map
     * @param paaSTopology the topology the node belongs to
     * @param serranoApp the application where definitions will be added
     */
    private void buildDeployment(PaaSNodeTemplate paaSNodeTemplate, PaaSTopology paaSTopology, List<PaaSNodeTemplate>
            volumeNodeTemplates, SerranoApp serranoApp) {
        final NodeTemplate nodeTemplate = paaSNodeTemplate.getTemplate();


        String nodeName = nodeTemplate.getName().toLowerCase();
        final String deploymentName = nodeName + "-" + serranoApp.getId();
        log.info("Building defintion for node: " + nodeName);
        int replicas = 1;
        String image = "";
        String repo = "public";
        String dockerCFG = null;
        List<Integer> ports = new ArrayList<>();

        AbstractPropertyValue labels = nodeTemplate.getProperties().get("labels");
        ComplexPropertyValue val = (ComplexPropertyValue) labels;
        Map<String, Object> vals = val.getValue();
        Map<String, String> labelsMap = new HashMap<>();
        for(String k : vals.keySet()){
            labelsMap.put(k, (String) vals.get(k));
        }
        labelsMap.put("app", nodeName);
        //Done group all parts of the same component with a group ID
        // group_id: s2
//        labelsMap.put("group_id", serranoApp.getId());

        // Only the create operation is supported
        final Operation createOperation = paaSNodeTemplate.getInterfaces().get("tosca.interfaces.node.lifecycle.Standard").getOperations().get("create");

        // Retrieve docker image from the Create operation implementation artifact
        final ImplementationArtifact implementationArtifact = createOperation.getImplementationArtifact();
        if (implementationArtifact != null) {
            image = implementationArtifact.getRepositoryURL().replace("https://", "") + "/" + implementationArtifact.getArtifactRef();
            if (implementationArtifact.getRepositoryURL().contains("serrano")) {
                Map<String, Object> repositoryCredential = implementationArtifact.getRepositoryCredential();
                dockerCFG = (String) repositoryCredential.get("token");
                serranoApp.setDockerCFG(dockerCFG);
                repo = "serrano";
            }
        }
        else
            throw new NotImplementedException("Create implementation artifact should specify the image");


        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(deploymentName)
                .withLabels(Maps.of("group_id", nodeName).build())
                .endMetadata()
                .withNewSpec()
                .withReplicas(replicas)
                .withNewSelector()
                .withMatchLabels(labelsMap)
                .endSelector()
                .withStrategy(new DeploymentStrategyBuilder().withType("Recreate").build())
                .withNewSelector()
                .withMatchLabels(labelsMap)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labelsMap)
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(nodeName)
                .withImage(image)
                .withImagePullPolicy("Always")
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        Container kubeContainer = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        PodSpec podSpec = deployment.getSpec().getTemplate().getSpec();

        if(repo.equals("serrano")){
            //TODO In the long run, find a better solution for private repositories
            // the following assumes the repository is already registered with the kubernetes cluster used for deployment
            podSpec.setImagePullSecrets(List.of(new LocalObjectReferenceBuilder().withName("regcred").build()));
        }




        Map<String, Object> configMap = ((ComplexPropertyValue) nodeTemplate.getProperties().get("config_map")).getValue();
        String configName = serranoApp.getId() + (String) configMap.get("name");
        String mountPath = (String) configMap.get("mount_path");
        Map<String, Object> configData = (Map<String, Object>) configMap.get("data");
        Map<String, String> data = new HashMap<>();

        for(String key : configData.keySet()){
            data.put(key, (String) configData.get(key));
        }



        ConfigMap kubeConfigMap = new ConfigMapBuilder().withNewMetadata()
                .withName(configName)
                .withLabels(Maps.of("group_id", nodeName).build())
                .endMetadata()
                .withData(data).build();


        serranoApp.getConfigMaps().put(nodeName, kubeConfigMap);
        kubeContainer.getVolumeMounts().add(new VolumeMountBuilder()
                .withName(configName)
                .withMountPath(mountPath)
                .build());
        podSpec.getVolumes().add(new VolumeBuilder().withName(configName)
                .withConfigMap(new ConfigMapVolumeSourceBuilder().withName(configName).build())
                .build());

        volumeNodeTemplates.forEach(volumeTemplate -> {
            final Map<String, AbstractPropertyValue> volumeTemplateProperties = volumeTemplate.getTemplate().getProperties();
            // Build volume definition

            Optional<PaaSRelationshipTemplate> relationshipTemplate = volumeTemplate.getRelationshipTemplates().stream()
                    .filter(paaSRelationshipTemplate -> paaSRelationshipTemplate.instanceOf("tufa.rel.MountPersistentVolume") && paaSRelationshipTemplate.getTemplate().getTarget().toLowerCase().equals(nodeName)).findFirst();
            if(relationshipTemplate.isPresent()) {

                String volName = deploymentName + "-" + volumeTemplate.getTemplate().getName().toLowerCase();
                // Find containerPath - a property of the relationship
                final String containerPath = ((ScalarPropertyValue) relationshipTemplate.get().getTemplate().getProperties().get("container_path")).getValue();
                Map<String, AbstractPropertyValue> volProps = volumeTemplate.getTemplate().getProperties();
                String storageClass = ((ScalarPropertyValue)volProps.get("storageClassName")).getValue();
                List<String> accessModes = ((ListPropertyValue) volProps.get("accessModes")).getValue().stream().map(Object::toString).collect(Collectors.toList());
                String size = ((ScalarPropertyValue) volProps.get("size")).getValue().split(" ")[0];

                String claim = "";

                if(volProps.get("storage_claim") != null){
                     claim = ((ScalarPropertyValue)volProps.get("storage_claim")).getValue();
                }


                if(!"".equals(claim)){
                    String claimSize = claim.split(" ")[0];
                    PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder().withNewMetadata()
                            .withName(volName + "claim")
                            .withLabels(Maps.of("group_id", nodeName).build())
                            .endMetadata()
                            .withNewSpec()
                            .withStorageClassName(storageClass)
                            .withAccessModes(accessModes)
                            .withNewResources()
                            .withRequests(Map.of("storage", new Quantity(claimSize, "Gi")))
                            .endResources()
                            .endSpec().build();

                    PersistentVolume pv = new PersistentVolumeBuilder().withNewMetadata()
                            .withName(volName)
                            .withLabels(Map.of("type", "local", "group_id", nodeName))
//                            .withLabels(Map.of("group_id", nodeName))
                            .endMetadata()
                            .withNewSpec()
                            .withStorageClassName(storageClass)
                            .withAccessModes(accessModes)
                            .withHostPath(new HostPathVolumeSource("/mnt/data-"+volName, "DirectoryOrCreate"))
                            .withCapacity(Map.of("storage", new Quantity(size, "Gi")))
                                    .endSpec().build();

                    podSpec.getVolumes().add(new VolumeBuilder().withName(volName+"-storage")
                            .withNewPersistentVolumeClaim()
                            .withClaimName(pvc.getMetadata().getName()).endPersistentVolumeClaim().build());
                    kubeContainer.getVolumeMounts().add(new VolumeMountBuilder()
                            .withName(volName+"-storage")
                            .withMountPath(containerPath)
                            .build());
                    serranoApp.getVolumes().putIfAbsent(nodeName, new ArrayList<>());
                    serranoApp.getVolumes().get(nodeName).add( pv);

                    serranoApp.getVolumeClaims().putIfAbsent(nodeName, new ArrayList<>());
                    serranoApp.getVolumeClaims().get(nodeName).add(pvc);

                    log.info("Generated volumes");
                    log.info(Serialization.asYaml(pv));


                }


            }else{
                throw new InvalidArgumentException("A container path must be provided to mount a volume to a container.");
            }


        });

        nodeTemplate.getCapabilities().forEach((name, capability) -> {
            if (capability.getType().contains("capabilities.endpoint")) { // FIXME : better check of capability types...
                // Retrieve port mapping for the capability - note : if no port is specified then let marathon decide.

                Integer port = capability.getProperties().get("port") != null
                        ? Integer.valueOf(((ScalarPropertyValue) capability.getProperties().get("port")).getValue()) : 0;

                if(port != 0) {
                    // If this node's capability is targeted by a relationship, we may already have pre-allocated a service port for it
                    final String serviceID = nodeName.concat(name);

                    //TODO HEre a kube Ingress must also be created to deal with the ports and DNS in case of public IPs
                    io.fabric8.kubernetes.api.model.Service tcp = new ServiceBuilder().withNewMetadata()
                            .withName(serviceID)
                            .endMetadata()
                            .withNewSpec().withSelector(deployment.getSpec().getSelector().getMatchLabels())
                            .withPorts(new ServicePortBuilder().withProtocol("TCP").withPort(port).build())
                            .endSpec()
                            .build();
                    serranoApp.getServices().putIfAbsent(nodeName, new HashMap<>());
                    serranoApp.getServices().get(nodeName).put(name, tcp);
                }
            }
        });


        if (nodeTemplate.getRelationships() != null) { // Get all the relationships this node is a source of
            nodeTemplate.getRelationships().forEach((key, relationshipTemplate) -> {
                if ("tosca.relationships.connectsto".equalsIgnoreCase(relationshipTemplate.getType())) {

                    // Here a service definition is required, the one above.
                    // for now we set the dependency, which will be updated once
                    // all nodes have been processed

                    serranoApp.getDependencies().putIfAbsent(nodeName, new ArrayList<>());
                    serranoApp.getDependencies().get(nodeName).add(relationshipTemplate.getTarget());

                }
            });
        }



        /**
         * USER DEFINED PROPERTIES
         */
        final Map<String, AbstractPropertyValue> nodeTemplateProperties = nodeTemplate.getProperties();

        final Optional<String> cpu_share = Optional.ofNullable(((ScalarPropertyValue) nodeTemplateProperties.get("cpu")).getValue());
        final Optional<String> mem_share = Optional.ofNullable(((ScalarPropertyValue) nodeTemplateProperties.get("mem")).getValue());


        Double cpu = Double.parseDouble(cpu_share.orElse("1.0"))*1000;
        String[] mem_clean = mem_share.orElse("256.0 MB").split(" ");
        Double mem = Double.valueOf(mem_clean[0]);
//        kubeContainer.setResources(new ResourceRequirementsBuilder().withRequests(Map.of("cpu", new Quantity(String.valueOf(cpu), "m"),
//                "memory", new Quantity(String.valueOf(mem), mem_clean[1].charAt(0)+"i"))).build());

        kubeContainer.setImagePullPolicy("Always");
        serranoApp.getDeployments().add(deployment);


        //Create SERRANO constraint JSON
        JsonObject constraint = new JsonObject();

        constraint.addProperty("component_id", nodeName);
        AbstractPropertyValue toscaIntent = nodeTemplateProperties.get("intent");
        Map<String, Object> values = ((ComplexPropertyValue) toscaIntent).getValue();
        for(String key: values.keySet()){
            if(values.get(key) instanceof Map) {
                Map<String, Object> keyValues = (Map<String, Object>) values.get(key);
                for (String keyKey : keyValues.keySet()) {
                    String serranoKey = key + "_" + keyKey;
                    String keyValue = (String) keyValues.get(keyKey);
                    if(keyKey.endsWith("Volume"))
                        keyValue = "</= " + keyValue;
                    if(serranoKey.equals("Application_Performance_Response_Latency") || serranoKey.equals("Application_Performance_Total_Execution_Time") ){
                        keyValue = "</= " + keyValue + " sec";
                    }


                    constraint.addProperty(serranoKey, keyValue);

                }
            }else{
                String vali = (String) values.get(key);
                constraint.addProperty(key, vali);
            }
        }
        serranoApp.getConstraints().put(nodeName, constraint);
    }

    private void updateAppDefinitionWithDependencies(PaaSNodeTemplate paaSNodeTemplate, PaaSTopologyDeploymentContext deploymentContext, SerranoApp serranoApp) {
        PaaSTopology paaSTopology = deploymentContext.getPaaSTopology();
        final NodeTemplate nodeTemplate = paaSNodeTemplate.getTemplate();
        String nodeName = nodeTemplate.getName().toLowerCase();
        String deploymentName = nodeName + "-" + serranoApp.getId();
        Deployment deployment = serranoApp.getDeployments().stream().filter(d -> d.getMetadata().getName().equals(deploymentName)).findFirst().orElse(null);
        if(deployment == null)
            return;


        io.fabric8.kubernetes.api.model.Container kubeContainer = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        PodSpec podSpec = deployment.getSpec().getTemplate().getSpec();
        ConfigMap kubeConfigMap = serranoApp.getConfigMaps().get(nodeName);

        // Only the create operation is supported
        log.info("Create operation inputs");
        /*
         * INPUTS from the Create operation
         */
        /* Prefix-based mapping : ENV_ => Env var, OPT_ => docker option, ARG_ => Docker run args */
//        final Operation createOperation = paaSNodeTemplate.getInterfaces().get("tosca.interfaces.node.lifecycle.Standard").getOperations().get("create");
        final Operation createOperation = InterfaceUtils.getOperationIfArtifactDefined(paaSNodeTemplate.getInterfaces(), ToscaNodeLifecycleConstants.STANDARD, ToscaNodeLifecycleConstants.CREATE);
        if (createOperation.getInputParameters() != null) {
            createOperation.getInputParameters().forEach((key, value) -> {
                log.info(key + ": " + value);
                if(key.startsWith("INPUT_")) {

                    // Inputs can either be a ScalarValue or a pointer to a capability targeted by one of the node's requirements
                    String actualValue = retrieveValue(paaSNodeTemplate, deploymentContext, value, serranoApp);

                    addInputParameter(kubeConfigMap, key, actualValue);
                }
            });
        }
        serranoApp.getConfigMaps().put(nodeName, kubeConfigMap);

    }

    private void addInputParameter(ConfigMap kubeConfigMap, String key, String actualValue) {
        if(key.startsWith("INPUT_")) {
            for (String fileName : kubeConfigMap.getData().keySet()) {
                String content = kubeConfigMap.getData().get(fileName);
                content = content.replace(key, actualValue);
                kubeConfigMap.getData().put(fileName, content);
            }
        }
    }


    public String retrieveValue(PaaSNodeTemplate paaSNodeTemplate, PaaSTopologyDeploymentContext deploymentContext, IValue val, SerranoApp serranoApp) {
        PaaSTopology paaSTopology = deploymentContext.getPaaSTopology();
        String value = "";

        if (val instanceof FunctionPropertyValue && "get_property".equals(((FunctionPropertyValue) val).getFunction())){
            FunctionPropertyValue fVal = (FunctionPropertyValue) val;
            if("REQ_TARGET".equals(fVal.getTemplateName())) {
                // Get property of a requirement's targeted capability
                value = getPropertyFromReqTarget(paaSNodeTemplate, deploymentContext, fVal, serranoApp);
            }else if("SELF".equals(fVal.getTemplateName())){
                // Get property of a capability
                value = getPropertyFromSelf(paaSNodeTemplate, deploymentContext, fVal, serranoApp);
            }
        } else if (val instanceof ScalarPropertyValue)
            value = ((ScalarPropertyValue) val).getValue();
        else if(val instanceof ConcatPropertyValue){
            StringBuilder sb = new StringBuilder("");
            for( IValue iValue : ((ConcatPropertyValue) val).getParameters()){
                sb.append(retrieveValue(paaSNodeTemplate, deploymentContext, iValue, serranoApp));
            }
            value = sb.toString();
        }
        return value;
    }

    /**
     * Search for a property of a capability being required as a target of a relationship.
     *
     * @param paaSNodeTemplate The source node of the relationships, wich defines the requirement.
     * @param deploymentContext     the topology the node belongs to.
     * @param params           the function parameters, e.g. the requirement name & property name to lookup.
     * @param serranoApp
     * @return a String representing the property value.
     */
    private String getPropertyFromReqTarget(PaaSNodeTemplate paaSNodeTemplate, PaaSTopologyDeploymentContext deploymentContext, FunctionPropertyValue params, SerranoApp serranoApp) {
        // Search for the requirement's target by filter the relationships' templates of this node.
        // If a target is found, then lookup for the given property name in its capabilities.
        // For Docker containers X Marathon, the orchestrator replaces the PORT and IP_ADDRESS by the target's service port and the load balancer hostname
        // respectively.

        PaaSTopology paaSTopology = deploymentContext.getPaaSTopology();
        Map<String, NodeTemplate> matchedNodes = deploymentContext.getDeploymentTopology().getMatchReplacedNodes();

        String requirementName = params.getCapabilityOrRequirementName();
        String propertyName = params.getElementNameToFetch();

        return paaSNodeTemplate.getRelationshipTemplates().stream()
                .filter(item -> paaSNodeTemplate.getId().equals(item.getSource()) && requirementName.equals(item.getTemplate().getRequirementName()))
                .findFirst() // Find the first relationship template which fulfills the given requirement, for this source
                .map(relationshipTemplate -> {
                    PaaSNodeTemplate target = paaSTopology.getAllNodes().get(relationshipTemplate.getTemplate().getTarget());
                    String targetedCapabilityName = relationshipTemplate.getTemplate().getTargetedCapabilityName().toLowerCase();

                    log.info("Relationship: " + relationshipTemplate.getIndexedToscaElement().getId());

                    if (relationshipTemplate.getIndexedToscaElement().getId().contains("tosca.relationships.ConnectsTo")) {

                        log.info("ConnectTo relationship");
                        String url = null;
                        String port = null;
                        String user = null;
                        String password = null;
                        String nodeName = paaSNodeTemplate.getTemplate().getName().toLowerCase();
                        if (target.getIndexedToscaElement().isAbstract()) {
                            //The target service is provided by Serrano
                            //we need to remove the dependency
                            if(serranoApp.getDependencies().get(nodeName) != null ) {
                                serranoApp.getDependencies().get(nodeName).remove(target.getId());
                            }

                            if(matchedNodes.containsKey(target.getId())){
                                NodeTemplate node = matchedNodes.get(target.getId());
                                if(node instanceof ServiceNodeTemplate) {
                                    Map<String, String> attributes = ((ServiceNodeTemplate) node).getAttributeValues();
                                    url = attributes.get("endpoint");
                                    user = attributes.get("username");
                                    password = attributes.get("password");

                                }
                            }

                        } else {
                            // TODO If the IP is public, then I can get it from kubernetes
//                            String frontendServiceIP = client.services().withName("frontend-service").get().getStatus().getLoadBalancer().getIngress().get(0).getIp();
//                            System.out.println("Frontend service IP: " + frontendServiceIP);

//                            url = serranoApp.getServices().get(target.getTemplate().getName()+targetedCapabilityName).getMetadata().getName();
                            url = target.getTemplate().getName().toLowerCase() + targetedCapabilityName;
                        }
                        if ("port".equalsIgnoreCase(propertyName)) {
                            port = "80";
                            if (url != null) {
                                String[] tokens = url.split(":");
                                if (url.startsWith("http")) {
                                    if (tokens.length > 2)
                                        port = tokens[2];
                                } else {
                                    if (tokens.length > 1)
                                        port = tokens[1];

                                }
                            }
                            return port;
                        } else if ("ip_address".equalsIgnoreCase(propertyName)) {
                            String address = "0.0.0.0";
                            if (url != null) {
                                String[] tokens = url.split(":");
                                if (url.startsWith("http")) {
                                    if (tokens.length > 2)
                                        address = tokens[1];
                                    else
                                        address = tokens[0];
                                } else {
                                    address = tokens[0];
                                }
                            }
                            return address;
                        } else if ("url".equalsIgnoreCase(propertyName)) {
                            return url;
                        } else if ("user".equalsIgnoreCase(propertyName)){
                            return user;
                        } else if ("password".equalsIgnoreCase(propertyName)){
                            return password;
                        }
                        return target.getTemplate().getName().toLowerCase() + targetedCapabilityName;
                    }else {

                        log.info(String.format("Target %s; property: %s", target.getTemplate().getName(),
                                target.getTemplate().getProperties()));
                        return ((ScalarPropertyValue) target.getTemplate().getProperties().get(propertyName)).getValue();
                    }

                }).orElse("");
    }


    public String getPropertyFromSelf(PaaSNodeTemplate paaSNodeTemplate, PaaSTopologyDeploymentContext deploymentContext, FunctionPropertyValue params, SerranoApp serranoApp) {
        // Search for the requirement's target by filter the relationships' templates of this node.
        // If a target is found, then lookup for the given property name in its capabilities.
        // For Docker containers X Marathon, the orchestrator replaces the PORT and IP_ADDRESS by the target's service port and the load balancer hostname
        // respectively.

        PaaSTopology paaSTopology = deploymentContext.getPaaSTopology();

        String capabilityName = params.getCapabilityOrRequirementName();
        String propertyName = params.getElementNameToFetch();

        return paaSNodeTemplate.getTemplate ().getCapabilities().entrySet().stream()
                .filter(item -> capabilityName.equals(item.getKey()))
                .findFirst() // Find the first relationship template which fulfills the given requirement, for this source
                .map(capabilityEntry -> {
                    Capability value = capabilityEntry.getValue();
                    // Special marathon case: use service ports if the "Port" property is required.
                    if ("port".equalsIgnoreCase(propertyName)) {
                        // TODO: Retrieve service port if exists - if not, get capability value (for use cases where ports are statically defined)
                        AbstractPropertyValue portProp = value.getProperties().get("port");
                        String port = "0";
                        if(portProp instanceof ScalarPropertyValue)
                            port = ((ScalarPropertyValue) portProp).getValue();
                        if(!port.equals("0")){
                            return port;
                        }else {
                            portProp = value.getProperties().get("port");
                            if (portProp instanceof ScalarPropertyValue)
                                port = ((ScalarPropertyValue) portProp).getValue();
                            if (!port.equals("0")) {
                                return port;
                            }

                            return String.valueOf(  0);
                        }
                    }
                    else if ("ip_address".equalsIgnoreCase(propertyName))
                        // TODO: If there is no service port, return <target_app_id>.marathon.mesos for DNS resolution


                        return paaSNodeTemplate.getTemplate().getName() + capabilityName;

                    // Nominal case : get the requirement's targeted capability property.
                    // TODO: Add the REQ_TARGET keyword in the evaluateGetProperty function so this is evaluated at parsing
                    return FunctionEvaluator.evaluateGetPropertyFunction(params, paaSNodeTemplate, paaSTopology.getAllNodes());
                }).orElse("");
    }

}
