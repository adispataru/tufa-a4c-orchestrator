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

import tufa.model.events.MarathonEventService;
import tufa.model.soe.SOEGroup;
import tufa.model.soe.ServiceResource;
import tufa.model.soe.CustomMarathon;
import tufa.model.soe.CustomMarathonClient;
import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import mesosphere.marathon.client.model.v2.*;
import org.alien4cloud.tosca.model.definitions.*;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.RelationshipTemplate;
import org.alien4cloud.tosca.model.templates.ScalingPolicy;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service for transformation of Alien PaaSTopologies into Marathon Groups and Apps definitions.
 * Currently only Docker containers are supported.
 *
 *
 * @author adrian
 *
 */
@Service
@Scope("prototype")
public class SOEMappingService {

    /**
     * We allocate Service Ports starting from 31001
     */
    private AtomicInteger servicePortIncrement = new AtomicInteger(31001);

    /**
     * Map to store service ports allocated to marathon endpoints. Used to fulfill relationships requirements.
     */
    private Map<String, Integer> mapPortEndpoints = Maps.newHashMap();
    private Logger log = LoggerFactory.getLogger(SOEMappingService.class);

    /**
     * Map an Alien deployment context to a Marathon group definition.
     *
     * @param paaSTopologyDeploymentContext the deployment to process
     * @param servicesLocation
     * @return A Marathon CLGroup definition
     */
    public SOEGroup buildGroupDefinition(PaaSTopologyDeploymentContext paaSTopologyDeploymentContext, Map<String, ServiceResource> servicesLocation) {
        //Setting clGroup
        SOEGroup clGroup = new SOEGroup();

        // Setup parent marathon group
        Group marathonGroup = new Group();
        // CLGroup id == pass topology deployment id.
        marathonGroup.setId(paaSTopologyDeploymentContext.getDeploymentPaaSId().toLowerCase());
        marathonGroup.setApps(Lists.newArrayList());

        // Marathon topologies contain only non-natives nodes (eg. apps) and volumes.
        // Each non-native node (and eventually, its attached volumes) are converted to a Marathon App
        final List<PaaSNodeTemplate> nonNatives = paaSTopologyDeploymentContext.getPaaSTopology().getNonNatives();
        final List<PaaSNodeTemplate> volumes = paaSTopologyDeploymentContext.getPaaSTopology().getVolumes();
        List<PaaSNodeTemplate> iaasNodes = nonNatives.stream().filter(node ->
                node.getTemplate().getRequirements().get("host") != null &&
                        !node.getTemplate().getType().equals("tufa.nodes.Compute.Container") &&
                        node.getTemplate().getRequirements().get("host").getType().equals("tosca.capabilities.Container")
        ).collect(Collectors.toList());
        nonNatives.removeAll(iaasNodes);
        final List<App> configureOps = new ArrayList<>();
        nonNatives.forEach(node -> {
            // Find volumes attached to the node
            if(node.getTemplate().getCapabilities().containsKey("host"))
                return;
            if(node.getTemplate().getCapabilities().containsKey("accelerator"))
                return;
            final List<PaaSNodeTemplate> attachedVolumes = volumes.stream()
                    .filter(paaSNodeTemplate -> paaSNodeTemplate.getRelationshipTemplates().stream()
                            .filter(paaSRelationshipTemplate -> paaSRelationshipTemplate.instanceOf("alien.relationships.MountDockerVolume")).findFirst()
                            .map(paaSRelationshipTemplate -> paaSRelationshipTemplate.getTemplate().getTarget()).orElse("").equals(node.getId()))
                    .collect(Collectors.toList());
            final List<PaaSNodeTemplate> attachedAccelerators = nonNatives.stream()
                    .filter(paaSNodeTemplate -> paaSNodeTemplate.getRelationshipTemplates().stream()
                            .filter(paaSRelationshipTemplate -> paaSRelationshipTemplate.instanceOf("tufa.rel.AcceleratedBy")).findFirst()
                            .map(paaSRelationshipTemplate -> paaSRelationshipTemplate.getTemplate().getTarget()).orElse("").equals(node.getId()))
                    .collect(Collectors.toList());
            final List<PaaSNodeTemplate> hosts = nonNatives.stream()
                    .filter(paaSNodeTemplate -> paaSNodeTemplate.getRelationshipTemplates().stream()
                            .filter(paaSRelationshipTemplate -> paaSRelationshipTemplate.instanceOf("tosca.relationships.HostedOn")).findFirst()
                            .map(paaSRelationshipTemplate -> paaSRelationshipTemplate.getTemplate().getTarget()).orElse("").equals(node.getId()))
                    .collect(Collectors.toList());
            nonNatives.removeAll(attachedAccelerators);
            nonNatives.removeAll(hosts);
            // Build the app definition and add it to the group
            App app = buildAppDefinition(node, paaSTopologyDeploymentContext.getPaaSTopology(), attachedVolumes, attachedAccelerators, clGroup);

            marathonGroup.getApps().add(app);
        });

        // Clean the port endpoints map
        mapPortEndpoints.clear();


        clGroup.getMarathonServices().add(marathonGroup);
        Set<String> set = new HashSet<>(servicesLocation.keySet());
        for(String s : set){
            servicesLocation.put(s.toLowerCase(), servicesLocation.get(s));
        }
        for(Group g : clGroup.getMarathonServices()){
            for(App app : g.getApps()){
                Map resource = (Map) servicesLocation.get(app.getId());
                String resourceDescriptor = ((Map<String, String>) ((List) resource.get("resources")).get(0))
                        .get("resourceDescriptor");
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    Map<String, String> mr = objectMapper.readValue(resourceDescriptor, Map.class);
                    CustomMarathon client = CustomMarathonClient.getInstanceWithBasicAuth(mr.get("endpoint"), mr.get("username"), mr.get("password"));
                    clGroup.getMarathonClients().put(app.getId(), client);
                    clGroup.getMarathonEvents().putIfAbsent(app.getId(), new MarathonEventService(mr.get("endpoint").concat("/v2")));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        for(PaaSNodeTemplate t : iaasNodes){
            clGroup.getBrooklynServices().put(t.getId(), t);
            Map resource = (Map) servicesLocation.get(t.getId());
            String brooklynLocation = (String) resource.get("locationId");
            clGroup.getBrooklynLocation().put(t.getId(), brooklynLocation);
            NodeTemplate nodeTemplate = t.getTemplate();
            if (nodeTemplate.getRelationships() != null) { // Get all the relationships this node is a source of
                nodeTemplate.getRelationships().forEach((key, relationshipTemplate) -> {
                    if ("tosca.relationships.connectsto".equalsIgnoreCase(relationshipTemplate.getType())) {
                        clGroup.getDependencies().putIfAbsent(t.getId(), new ArrayList<>());
                        clGroup.getDependencies().get(t.getId()).add(relationshipTemplate.getTarget());
                    }
                });
            }
        }

        return clGroup;

    }

    /**
     * Map an alien PaaSNodeTemplate to a Marathon App Definition.
     *
     * @param paaSNodeTemplate the node template to map
     * @param paaSTopology the topology the node belongs to
     * @param acceleratorsNodeTemplates
     * @param clGroup
     * @return a Marathon App definition
     */
    private App buildAppDefinition(PaaSNodeTemplate paaSNodeTemplate, PaaSTopology paaSTopology, List<PaaSNodeTemplate>
            volumeNodeTemplates, List<PaaSNodeTemplate> acceleratorsNodeTemplates, SOEGroup clGroup) {
        final NodeTemplate nodeTemplate = paaSNodeTemplate.getTemplate();
        //TODO Parse hosts, eventually
        /**
         * Init app structure
         */
        App appDef = new App();
        appDef.setInstances(Optional.ofNullable(paaSNodeTemplate.getScalingPolicy()).orElse(ScalingPolicy.NOT_SCALABLE_POLICY).getInitialInstances());
        appDef.setId(paaSNodeTemplate.getId().toLowerCase());
        // Only accepted special chars in app ids are hyphens and dots
        if (!appDef.getId().matches("^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])|(\\.|\\.\\.)$")) {
            throw new IllegalArgumentException("Node ID is invalid. Allowed: lowercase letters, digits, hyphens, \".\", \"..\"");
        }
        appDef.addLabel("name", nodeTemplate.getName());


        Container container = new Container();
        Docker docker = new Docker();
        container.setType("DOCKER");
        container.setDocker(docker);
        appDef.setContainer(container);
        docker.setPortMappings(Lists.newArrayList());
        docker.setParameters(Lists.newArrayList());
        docker.setPrivileged(true);
        appDef.setEnv(Maps.newHashMap());

        /**
         * CREATE OPERATION
         * Map Docker image
         */
        // Only the create operation is supported
        final Operation createOperation = paaSNodeTemplate.getInterfaces().get("tosca.interfaces.node.lifecycle.Standard").getOperations().get("create");

        // Retrieve docker image from the Create operation implementation artifact
        final ImplementationArtifact implementationArtifact = createOperation.getImplementationArtifact();
        if (implementationArtifact != null)
            docker.setImage(implementationArtifact.getArtifactRef());
        else
            throw new NotImplementedException("Create implementation artifact should specify the image");

        /**
         * External persistent Docker volumes using the RexRay driver
         */
        container.setVolumes(new ArrayList<>());
        volumeNodeTemplates.forEach(volumeTemplate -> {
            final Map<String, AbstractPropertyValue> volumeTemplateProperties = volumeTemplate.getTemplate().getProperties();
            // Build volume definition

            // Find containerPath - a property of the relationship
            final String containerPath = volumeTemplate.getRelationshipTemplates().stream()
                    .filter(paaSRelationshipTemplate -> paaSRelationshipTemplate.instanceOf("alien.relationships.MountDockerVolume")).findFirst()
                    .map(PaaSRelationshipTemplate::getTemplate).map(RelationshipTemplate::getProperties)
                    .map(relationshipProps -> ((ScalarPropertyValue) relationshipProps.get("container_path"))).map(ScalarPropertyValue::getValue)
                    .orElseThrow(() -> new InvalidArgumentException("A container path must be provided to mount a volume to a container."));

            final String hostPath = volumeTemplate.getRelationshipTemplates().stream()
                    .filter(paaSRelationshipTemplate -> paaSRelationshipTemplate.instanceOf("alien.relationships.MountDockerVolume")).findFirst()
                    .map(PaaSRelationshipTemplate::getTemplate).map(RelationshipTemplate::getProperties)
                    .map(relationshipProps -> ((ScalarPropertyValue) relationshipProps.get("host_path"))).map(ScalarPropertyValue::getValue)
                    .orElseThrow(() -> new InvalidArgumentException("A host path must be provided to mount a volume to a container."));

            final LocalVolume volume = new LocalVolume();
            volume.setHostPath(hostPath);
            volume.setContainerPath(containerPath);
            volume.setMode("RW");

            // Volume size is not supported with Docker containers ATM
//            if ("MESOS".equals(container.getType()))
//                externalVolume.setSize(Integer.valueOf(volumeSize.filter(s -> s.matches("^[1-9][0-9]*\\s(GiB|GB)$")).map(s -> s.split("\\s")[0]).orElse("1")));

            container.getVolumes().add(volume);
        });




        /**
         * RELATIONSHIPS
         * Only connectsTo relationships are supported : an app can only connect to a container endpoint.
         * Each relationship implies the need to create a service port for the targeted capability.
         * We keep track of service ports allocated to relationships' targets in order to allocate only one port per capability.
         */
        AtomicBoolean isDFE = new AtomicBoolean(false);
        if (nodeTemplate.getRelationships() != null) { // Get all the relationships this node is a source of
            nodeTemplate.getRelationships().forEach((key, relationshipTemplate) -> {
                // TODO: We should validate that the targeted node is of Docker type and better check the relationship type
                if ("tosca.relationships.connectsto".equalsIgnoreCase(relationshipTemplate.getType())) {
                    if (!mapPortEndpoints.containsKey(relationshipTemplate.getTarget().concat(relationshipTemplate.getTargetedCapabilityName()))) {
                        // We haven't processed the target already: we pre-allocate a service port
                        mapPortEndpoints.put(relationshipTemplate.getTarget().concat(relationshipTemplate.getTargetedCapabilityName()),
                                this.servicePortIncrement.getAndIncrement());
                    }
                    // Add a dependency to the target
                    appDef.addDependency(relationshipTemplate.getTarget().toLowerCase());
                    clGroup.getDependencies().putIfAbsent(paaSNodeTemplate.getId(), new ArrayList<>());
                    clGroup.getDependencies().get(paaSNodeTemplate.getId()).add(relationshipTemplate.getTarget());


                }else if((relationshipTemplate.getType().equals("cloudlightning.relationships.AcceleratedByMIC"))){
                    /**
                     * CloudLightning Accelerator
                     */
                    AbstractPropertyValue contPathVal =  relationshipTemplate.getProperties().get("container_path");
                    addVolume(container, relationshipTemplate, contPathVal);

                }else if((relationshipTemplate.getType().equals("cloudlightning.relationships.AcceleratedByGPU"))) {
                    /**
                     * CloudLightning Accelerators
                     */
                    AbstractPropertyValue contPathVal = relationshipTemplate.getProperties().get("container_path");
                    if (contPathVal != null) {
                        final String containerPath = ((ScalarPropertyValue) contPathVal).getValue();

                        final String mode = ((ScalarPropertyValue) relationshipTemplate.getProperties().get("mode")).getValue();

                        final String hostPath = ((ScalarPropertyValue) relationshipTemplate.getProperties().get("host_path")).getValue();

                        container.getDocker().getParameters().add(new Parameter("volume", hostPath + ":" + containerPath + ":" + mode));

                    }

                }else if((relationshipTemplate.getType().equals("cloudlightning.relationships.AcceleratedByDFE"))){
                    /**
                     * CloudLightning DFE
                     * Mount volume with data
                     */
                    AbstractPropertyValue contPathVal =  relationshipTemplate.getProperties().get("container_path");
                    addVolume(container, relationshipTemplate, contPathVal);

                }
            });
        }

        /*
         * INPUTS from the Create operation
         */
        /* Prefix-based mapping : ENV_ => Env var, OPT_ => docker option, ARG_ => Docker run args */
        if (createOperation.getInputParameters() != null) {
            createOperation.getInputParameters().forEach((key, val) -> {

                // Inputs can either be a ScalarValue or a pointer to a capability targeted by one of the node's requirements
                String value = retrieveValue(paaSNodeTemplate, paaSTopology, val);
                addInputParameter(appDef, key, value);
            });
        }

        /**
         * CAPABILITIES
         * Turn Alien endpoints capabilities into a PortMapping definition and attribute a service port to each endpoint.
         * This means that this node CAN be targeted by a ConnectsTo relationship.
         * Register the app into the internal service discovery group.
         */

        AtomicInteger readinessPort = new AtomicInteger(0);
        nodeTemplate.getCapabilities().forEach((name, capability) -> {
            if (capability.getType().contains("capabilities.endpoint")) { // FIXME : better check of capability types...
                // Retrieve port mapping for the capability - note : if no port is specified then let marathon decide.
                Port port = new Port();
                Integer integerPort = capability.getProperties().get("port") != null
                        ? Integer.valueOf(((ScalarPropertyValue) capability.getProperties().get("port")).getValue()) : 0;
                port.setContainerPort(integerPort);


                // TODO: Attribute service port only if necessary, eg. the capability is NOT targeted or if ports are statically allocated
                // If this node's capability is targeted by a relationship, we may already have pre-allocated a service port for it
                final String endpointID = paaSNodeTemplate.getId().concat(name);
                final Integer servicePort = mapPortEndpoints.getOrDefault(endpointID, this.servicePortIncrement.getAndIncrement());
                port.setServicePort(servicePort);
                mapPortEndpoints.put(endpointID, servicePort); // Store the endpoint for further use by other apps

                // TODO: set haproxy group only if necessary
                // The HAPROXY_GROUP label indicates which load balancer group this application should register to.
                // For now this default to internal.
                appDef.addLabel("HAPROXY_GROUP", "internal");
                // If the capability has a "docker_bridge_port_mapping" property, then use Docker bridge networking
                if (capability.getProperties().containsKey("docker_bridge_port_mapping")) {
                    docker.setNetwork("BRIDGE");
                    port.setHostPort(Integer.valueOf(((ScalarPropertyValue) capability.getProperties().get("docker_bridge_port_mapping")).getValue()));
                    port.setProtocol("tcp");
//                    healthPort.set(port.getHostPort());
                } else {
                    docker.setNetwork("HOST");
                    int hostPort = Integer.valueOf(((ScalarPropertyValue) capability.getProperties().get("port")).getValue());
                    if(hostPort == 0){
                        hostPort = servicePortIncrement.getAndIncrement();
                        mapPortEndpoints.put(endpointID, hostPort);
                    }
                    String portName = ((ScalarPropertyValue) capability.getProperties().get("name")).getValue();
                    appDef.getEnv().put(portName, String.valueOf(hostPort));
                    readinessPort.set(hostPort);
                }

                docker.getPortMappings().add(port);
            }
        });

        /**
         * USER DEFINED PROPERTIES
         */
        final Map<String, AbstractPropertyValue> nodeTemplateProperties = nodeTemplate.getProperties();

        /* Resources Marathon should allocate the container - default 1.0 cpu 256 MB ram */
        final Optional<String> cpu_share = Optional.ofNullable(((ScalarPropertyValue) nodeTemplateProperties.get("cpu_share")).getValue());
        final Optional<String> mem_share = Optional.ofNullable(((ScalarPropertyValue) nodeTemplateProperties.get("mem_share")).getValue());
        appDef.setCpus(Double.valueOf(cpu_share.orElse("1.0")));
        String mem_clean = mem_share.orElse("256.0 MB").split("MB")[0];
        Double mem = Double.valueOf(mem_clean);
        appDef.setMem(mem);

        /* Docker command */
        if (nodeTemplateProperties.get("docker_run_cmd") != null) {
            appDef.setCmd(((ScalarPropertyValue) nodeTemplateProperties.get("docker_run_cmd")).getValue());
        }

        if (nodeTemplateProperties.get("docker_force_pull_image") != null) {
            docker.setForcePullImage(Boolean.valueOf((String) ((PropertyValue) nodeTemplateProperties.get("docker_force_pull_image")).getValue()));
        }

        /* Env variables ==> Map of String values */
        if (nodeTemplateProperties.get("docker_env_vars") != null) {
            ((ComplexPropertyValue) nodeTemplateProperties.get("docker_env_vars")).getValue().forEach((var, val) -> {
                // Mapped property expected as String. Deal with the property as a environment variable
                appDef.getEnv().put(var, String.valueOf(val)); // TODO Replace by MapUtil || JsonUtil
            });
        }

        /* Docker options ==> Map of String values */
        if (nodeTemplateProperties.get("docker_options") != null) {
            ((ComplexPropertyValue) nodeTemplateProperties.get("docker_options")).getValue().forEach((var, val) -> {
                docker.getParameters().add(new Parameter(var, String.valueOf(val)));
            });
        }

        /* Docker run args */
        if (nodeTemplateProperties.get("docker_run_args") != null) {
            if (appDef.getArgs() == null) {
                appDef.setArgs(Lists.newArrayList());
            }
            appDef.getArgs().addAll(
                    ((ListPropertyValue) nodeTemplateProperties.get("docker_run_args")).getValue().stream().map(String::valueOf).collect(Collectors.toList()));
        }

        /* Create a basic TCP health check */
        HealthCheck healthCheck = new HealthCheck();
        healthCheck.setPortIndex(0);
        healthCheck.setProtocol("TCP");
        healthCheck.setGracePeriodSeconds(300);
        healthCheck.setIntervalSeconds(15);
        healthCheck.setMaxConsecutiveFailures(1);
        appDef.setHealthChecks(Lists.newArrayList(healthCheck));

        if(readinessPort.get() != 0){
            healthCheck.setPort(readinessPort.get());
        }


        return appDef;
    }

    private void addVolume(Container container, RelationshipTemplate relationshipTemplate, AbstractPropertyValue contPathVal) {
        if(contPathVal != null) {
            final String containerPath = ((ScalarPropertyValue) contPathVal).getValue();

            final String mode = ((ScalarPropertyValue) relationshipTemplate.getProperties().get("mode")).getValue();

            final String hostPath = ((ScalarPropertyValue) relationshipTemplate.getProperties().get("host_path")).getValue();

            // For now only ExternalVolumes are supported
            final LocalVolume volume = new LocalVolume();
            volume.setContainerPath(containerPath);
            volume.setMode(mode);
            volume.setHostPath(hostPath);
            container.getVolumes().add(volume);

        }
    }

    public void addInputParameter(App appDef, String key, String value) {
        if (key.startsWith("OPT_")) {
            // Input as a docker option given to the docker cli
            if(appDef.getContainer() != null && appDef.getContainer().getDocker() != null) {
                if(key.contains("nvidia_device")){
                    appDef.getContainer().getDocker().getParameters().add(new Parameter("device", value));
                }else {
                    appDef.getContainer().getDocker().getParameters().add(new Parameter(key.replaceFirst("OPT_", "").toLowerCase(), value));
                }
            }


        }
        else if (key.startsWith("ENV_")) {
            // Input as environment variable within the container
            appDef.getEnv().put(key.replaceFirst("^ENV_", ""), value);
        } else if (key.startsWith("ARG_")) {
            // Input as an argument to the docker run command
            appDef.getArgs().add(value); // Arguments are unnamed
        } else if (key.startsWith("CONSTRAINT_")) {
            // Input as an argument to the docker run command
            List<String> constraints = new ArrayList<String>();

            String[] tokens = key.replaceFirst("CONSTRAINT_", "").split("_");
            Collections.addAll(constraints, tokens);
            constraints.add(value);
            appDef.setConstraints(new ArrayList<>());
            appDef.getConstraints().add(constraints);
        }
//        else
//            log.warn("Unrecognized prefix for input : <" + key + ">");
    }

    public String retrieveValue(PaaSNodeTemplate paaSNodeTemplate, PaaSTopology paaSTopology, IValue val) {
        String value = ""; // TODO: This should be generalized into Alien parser
        if (val instanceof FunctionPropertyValue && "get_property".equals(((FunctionPropertyValue) val).getFunction())){
            if("REQ_TARGET".equals(((FunctionPropertyValue) val).getTemplateName())) {
                // Get property of a requirement's targeted capability
                value = getPropertyFromReqTarget(paaSNodeTemplate, paaSTopology, (FunctionPropertyValue) val);
            }else if("SELF".equals(((FunctionPropertyValue) val).getTemplateName())){
                // Get property of a capability
                value = getPropertyFromSelf(paaSNodeTemplate, paaSTopology, (FunctionPropertyValue) val);
            }
        } else if (val instanceof ScalarPropertyValue)
            value = ((ScalarPropertyValue) val).getValue();
        else if(val instanceof ConcatPropertyValue){
            StringBuilder sb = new StringBuilder("");
            for( IValue iValue : ((ConcatPropertyValue) val).getParameters()){
                sb.append(retrieveValue(paaSNodeTemplate, paaSTopology, iValue));
            }
            value = sb.toString();
        }
        return value;
    }

    public String retrieveValue(PaaSNodeTemplate paaSNodeTemplate, PaaSTopology paaSTopology, IValue val, Task task) {
        String value = ""; // TODO: This should be generalized into Alien parser
        if (val instanceof FunctionPropertyValue && "get_property".equals(((FunctionPropertyValue) val).getFunction())){
            if("REQ_TARGET".equals(((FunctionPropertyValue) val).getTemplateName())) {
                // Get property of a requirement's targeted capability
                value = getPropertyFromReqTarget(paaSNodeTemplate, paaSTopology, (FunctionPropertyValue) val, task);
            }else if("SELF".equals(((FunctionPropertyValue) val).getTemplateName())){
                // Get property of a capability
                value = getPropertyFromSelf(paaSNodeTemplate, paaSTopology, (FunctionPropertyValue) val, task);
            }
        } else if (val instanceof ScalarPropertyValue)
            value = ((ScalarPropertyValue) val).getValue();
        else if(val instanceof ConcatPropertyValue){
            StringBuilder sb = new StringBuilder("");
            for( IValue iValue : ((ConcatPropertyValue) val).getParameters()){
                sb.append(retrieveValue(paaSNodeTemplate, paaSTopology, iValue, task));
            }
            value = sb.toString();
        }
        return value;
    }

    /**
     * Search for a property of a capability being required as a target of a relationship.
     *
     * @param paaSNodeTemplate The source node of the relationships, wich defines the requirement.
     * @param paaSTopology the topology the node belongs to.
     * @param params the function parameters, e.g. the requirement name & property name to lookup.
     * @return a String representing the property value.
     */
    private String getPropertyFromReqTarget(PaaSNodeTemplate paaSNodeTemplate, PaaSTopology paaSTopology, FunctionPropertyValue params) {
        // Search for the requirement's target by filter the relationships' templates of this node.
        // If a target is found, then lookup for the given property name in its capabilities.
        // For Docker containers X Marathon, the orchestrator replaces the PORT and IP_ADDRESS by the target's service port and the load balancer hostname
        // respectively.

        String requirementName = params.getCapabilityOrRequirementName();
        String propertyName = params.getElementNameToFetch();

        return paaSNodeTemplate.getRelationshipTemplates().stream()
                .filter(item -> paaSNodeTemplate.getId().equals(item.getSource()) && requirementName.equals(item.getTemplate().getRequirementName()))
                .findFirst() // Find the first relationship template which fulfills the given requirement, for this source
                .map(relationshipTemplate -> {

                    if (relationshipTemplate.instanceOf("tosca.relationships.ConnectsTo")) { // - TODO/FIXME : check target derived from docker type
                        // Special marathon case: use service ports if the "Port" property is required.

                        if ("port".equalsIgnoreCase(propertyName))
                            // TODO: Retrieve service port if exists - if not, get capability value (for use cases where ports are statically defined)
                            return String.valueOf( // Service ports are mapped using the targetName + capabilityName
                                    mapPortEndpoints.getOrDefault(
                                            relationshipTemplate.getTemplate().getTarget() + relationshipTemplate.getTemplate().getTargetedCapabilityName(),
                                            0));
                        else if ("ip_address".equalsIgnoreCase(propertyName))
                            // TODO: If there is no service port, return <target_app_id>.marathon.mesos for DNS resolution

                            // Special marathon case: return marathon-lb hostname if an ip_address is required.
//                            return relationshipTemplate.getTemplate().getTarget() + ".marathon.mesos";
                            return "marathon-lb.marathon.mesos";
                    }
                    // Nominal case : get the requirement's targeted capability property.
                    // TODO: Add the REQ_TARGET keyword in the evaluateGetProperty function so this is evaluated at parsing
                    PaaSNodeTemplate target = paaSTopology.getAllNodes().get(relationshipTemplate.getTemplate().getTarget());
                    log.info(String.format("Target %s; property: %s", target.getTemplate().getName(),
                            target.getTemplate().getProperties()));
                    return ((ScalarPropertyValue) target.getTemplate().getProperties().get(propertyName)).getValue();
                }).orElse("");
    }

    private String getPropertyFromReqTarget(PaaSNodeTemplate paaSNodeTemplate, PaaSTopology paaSTopology, FunctionPropertyValue params, Task task) {
        // Search for the requirement's target by filter the relationships' templates of this node.
        // If a target is found, then lookup for the given property name in its capabilities.
        // For Docker containers X Marathon, the orchestrator replaces the PORT and IP_ADDRESS by the target's service port and the load balancer hostname
        // respectively.

        String requirementName = params.getCapabilityOrRequirementName();
        String propertyName = params.getElementNameToFetch();

        return paaSNodeTemplate.getRelationshipTemplates().stream()
                .filter(item -> paaSNodeTemplate.getId().equals(item.getSource()) && requirementName.equals(item.getTemplate().getRequirementName()))
                .findFirst() // Find the first relationship template which fulfills the given requirement, for this source
                .map(relationshipTemplate -> {

                    if (relationshipTemplate.instanceOf("tosca.relationships.ConnectsTo")) {

                        if ("port".equalsIgnoreCase(propertyName))
                            return String.valueOf(Iterables.get(task.getPorts(), 0));
                        else if ("ip_address".equalsIgnoreCase(propertyName))
                            return task.getHost();
                    }
                    // Nominal case : get the requirement's targeted capability property.
                    PaaSNodeTemplate target = paaSTopology.getAllNodes().get(relationshipTemplate.getTemplate().getTarget());
                    log.info(String.format("Target %s; property: %s", target.getTemplate().getName(),
                            target.getTemplate().getProperties()));
                    return ((ScalarPropertyValue) target.getTemplate().getProperties().get(propertyName)).getValue();

                }).orElse("");
    }

    public String getPropertyFromSelf(PaaSNodeTemplate paaSNodeTemplate, PaaSTopology paaSTopology, FunctionPropertyValue params) {
        // Search for the requirement's target by filter the relationships' templates of this node.
        // If a target is found, then lookup for the given property name in its capabilities.
        // For Docker containers X Marathon, the orchestrator replaces the PORT and IP_ADDRESS by the target's service port and the load balancer hostname
        // respectively.

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
                            portProp = value.getProperties().get("docker_bridge_port_mapping");
                            if (portProp instanceof ScalarPropertyValue)
                                port = ((ScalarPropertyValue) portProp).getValue();
                            if (!port.equals("0")) {
                                return port;
                            }

                            return String.valueOf( // Service ports are mapped using the targetName + capabilityName
                                    mapPortEndpoints.getOrDefault(
                                            paaSNodeTemplate.getId().concat(capabilityEntry.getKey()), 0));
                        }
                    }
                    else if ("ip_address".equalsIgnoreCase(propertyName))
                        // TODO: If there is no service port, return <target_app_id>.marathon.mesos for DNS resolution

                        // Special marathon case: return marathon-lb hostname if an ip_address is required.
//                            return relationshipTemplate.getTemplate().getTarget() + ".marathon.mesos";
                        return "marathon-lb.marathon.mesos";

                    // Nominal case : get the requirement's targeted capability property.
                    // TODO: Add the REQ_TARGET keyword in the evaluateGetProperty function so this is evaluated at parsing
                    return FunctionEvaluator.evaluateGetPropertyFunction(params, paaSNodeTemplate, paaSTopology.getAllNodes());
                }).orElse("");
    }

    public String getPropertyFromSelf(PaaSNodeTemplate paaSNodeTemplate, PaaSTopology paaSTopology, FunctionPropertyValue params, Task task) {
        // Search for the requirement's target by filter the relationships' templates of this node.
        // If a target is found, then lookup for the given property name in its capabilities.
        // For Docker containers X Marathon, the orchestrator replaces the PORT and IP_ADDRESS by the target's service port and the load balancer hostname
        // respectively.

        String capabilityName = params.getCapabilityOrRequirementName();
        String propertyName = params.getElementNameToFetch();

        return paaSNodeTemplate.getTemplate ().getCapabilities().entrySet().stream()
                .filter(item -> capabilityName.equals(item.getKey()))
                .findFirst() // Find the first relationship template which fulfills the given requirement, for this source
                .map(capabilityEntry -> {
                    Capability value = capabilityEntry.getValue();
                    // Special marathon case: use service ports if the "Port" property is required.
                    if ("port".equalsIgnoreCase(propertyName)) {
                        if(value.getType().contains("cloudlightning.capabilities.endpoint.DockerHOST")){
                            return ((ScalarPropertyValue) value.getProperties().get("port")).getValue();
                        }
                        return String.valueOf(Iterables.get(task.getPorts(), 0));
                    }
                    else if ("ip_address".equalsIgnoreCase(propertyName))
                        return task.getHost();
                    // Nominal case : get the requirement's targeted capability property.
                    // TODO: Add the REQ_TARGET keyword in the evaluateGetProperty function so this is evaluated at parsing
                    return FunctionEvaluator.evaluateGetPropertyFunction(params, paaSNodeTemplate, paaSTopology.getAllNodes());
                }).orElse("");
    }

}
