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

package alien4cloud.sampleplugin.rest;

import alien4cloud.model.AdditionalOperation;
import alien4cloud.model.NodeBuilder;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.sampleplugin.repositories.OptimizationRepository;
import alien4cloud.sampleplugin.services.A4CRestTemplateFactory;
import alien4cloud.sampleplugin.services.BlueprintScheduler;
import alien4cloud.sampleplugin.services.RestTemplateFactory;
import alien4cloud.tufa.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static alien4cloud.sampleplugin.ContextConfiguration.A4CEndpoint;
import static alien4cloud.sampleplugin.services.A4CRestTemplateFactory.createCookieHeaders;

/**
 * Created by adrian on 09.03.2017.
 */
@RestController
@RequestMapping({"/rest/soe", "/rest/latest/soe"})
public class OptimizationController {

    private static final Logger log = LoggerFactory.getLogger(OptimizationController.class);

    @Resource
    OptimizationRepository optimizationRepository;
    @Resource
    A4CRestTemplateFactory a4CrestTemplateFactory;
    @Resource
    RestTemplateFactory restTemplateFactory;
    @Resource
    BlueprintScheduler blueprintScheduler;

//    @RequestMapping(value= "/sde/**", method=RequestMethod.OPTIONS)
//    public void corsHeaders(HttpServletResponse response) {
//        response.addHeader("Access-Control-Allow-Origin", "*");
//        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
//        response.addHeader("Access-Control-Allow-Headers", "origin, content-type, accept, x-requested-with");
//        response.addHeader("Access-Control-Max-Age", "3600");
//    }

//    @CrossOrigin(origins = "*")
    @RequestMapping(value = "optimize", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public DeferredResult<ResponseEntity<Optimization>> optimize(@RequestBody ApplicationRequest appRequest){
        HttpStatus conflict = HttpStatus.CONFLICT;

        if(appRequest == null){
            return new DeferredResult<ResponseEntity<Optimization>>(1000L,
                    new ResponseEntity<List<ServiceResponse>>(HttpStatus.BAD_REQUEST));
        }

        List<ServiceRequest> serviceRequests = appRequest.getServices();
        if(serviceRequests == null || serviceRequests.size() < 1){
            return new DeferredResult<ResponseEntity<Optimization>>(1000L,
                    new ResponseEntity<Optimization>(HttpStatus.BAD_REQUEST));
        }
//        Optimization opti = optimizationRepository.findByApplicationNameAndApplicationVersion(appRequest.getName(),
//                appRequest.getVersion());
//        if(opti != null){
//            optimizationRepository.delete(opti);
//        }
        Optimization optimization = new Optimization();
        optimization.setApplicationName(appRequest.getName());
        optimization.setApplicationVersion(appRequest.getVersion());
        optimization.setStatus(RequestStatus.PROCESSING);
        optimization.setId(UUID.randomUUID().toString());
        optimization.setRequestList(serviceRequests);
        optimizationRepository.save(optimization);
        BlueprintResourcedTemplate brt = blueprintScheduler.submitRequestToCellManager(optimization);

        boolean ok = processSOSMResult(optimization.getId(),  brt);

//        ResponseEntity<Optimization> result = new ResponseEntity<Optimization>(optimization, HttpStatus.CREATED);
        if(ok) {
            DeferredResult<ResponseEntity<Optimization>> result = new DeferredResult<ResponseEntity<Optimization>>();
            prepareEnv(optimization.getId(), result);

            result.setResult(new ResponseEntity<Optimization>(optimization, HttpStatus.OK));

            return result;
        }else{
            return new DeferredResult<ResponseEntity<Optimization>>(1000L,
                    new ResponseEntity<Optimization>(HttpStatus.BAD_REQUEST));
        }
    }

    private void prepareEnv(String id, DeferredResult<ResponseEntity<Optimization>> result) {
        Optimization opti = optimizationRepository.findById(id);
        if(opti == null){
            //in this case the request was canceled by the gw-ui
            Optimization o = new Optimization();
            o.setResponseList(new ArrayList<ServiceResponse>());
            result.setResult(new ResponseEntity<Optimization>(o, HttpStatus.OK));
            return;
        }

        if(opti.getStatus() == RequestStatus.FAIL){
            opti.setResponseList(new ArrayList<ServiceResponse>());
        }else{
            //Create orchestrator
            log.info("Creating orchestrator");

            String locationId = "tufa-"+opti.getApplicationName()+"-" +opti.getApplicationVersion();
            String infraType = "TUFA";
            String orchestratorId = createAndEnableOrchestrator(/*resource*/);
            if(orchestratorId != null) {
                log.info("Updating location policies");
                opti.setOrchestratorId(orchestratorId);
                String environmentId = retrieveDefaultEnvironment(opti.getApplicationName(), opti.getApplicationVersion());
                String appLocation = updateLocationPolicies(opti.getApplicationName(), environmentId, opti.getOrchestratorId(), locationId, infraType);
                opti.setEnvironmentId(environmentId);
                opti.setAppLocation(appLocation);

            }else{
                log.debug("Cannot enable orchestrator!");
            }

            for(int i = 0; i < opti.getResourceList().size(); i++){
                ServiceResource resource = opti.getResourceList().get(i);

                if (!resource.getResourceType().equals(ResourceType.MARATHON) && !resource.getResourceType().equals(ResourceType.KUBERNETES)) {

                    //Create location in Brooklyn
                    locationId = createOrchestratorLocation(resource);
                    resource.setLocationId(locationId);
                }
            }

//            opti.setResourceList(resources);
            optimizationRepository.save(opti);
        }

        result.setResult(new ResponseEntity<Optimization>(opti, HttpStatus.OK));
    }

//    @CrossOrigin(origins = "*")
    @RequestMapping(value = "optimize/{id}/{version}/locations", method = RequestMethod.GET)
    public ResponseEntity<Map<String, ServiceResource>> getServicesLocations(@PathVariable("id") String id, @PathVariable("version") String version){

        Optimization opti = optimizationRepository.findByApplicationNameAndApplicationVersion(id, version);

        if(opti == null){
            return new ResponseEntity<Map<String, ServiceResource>>(new HashMap(), HttpStatus.NOT_FOUND);
        }

        Map<String, ServiceResource> resMap = new HashMap<>();
        for(int i = 0; i < opti.getRequestList().size(); i++){
            resMap.put(opti.getRequestList().get(i).getName(), opti.getResourceList().get(i));
        }

        ResponseEntity<Map<String, ServiceResource>> result = new ResponseEntity<Map<String, ServiceResource>>(resMap, HttpStatus.OK);
        return result;
    }

//    @CrossOrigin(origins = "*")
    @RequestMapping(value = "optimize/{id}", method = RequestMethod.GET)
    public ResponseEntity<Optimization> optimize(@PathVariable("id") String id){

//        Optimization byId = optimizationRepository.findById(id);
        Optimization byId = null;
        if(byId == null){
            return new ResponseEntity<Optimization>(HttpStatus.NOT_FOUND);
        }

        ResponseEntity<Optimization> result = new ResponseEntity<Optimization>(byId, HttpStatus.OK);
        return result;
    }

//    @CrossOrigin(origins = "*")
    @RequestMapping(value = "optimize/{appName}/{appVersion:.+}", method = RequestMethod.GET)
    public ResponseEntity<Optimization> optimize(@PathVariable("appName") String appName,
                                                 @PathVariable("appVersion") String appVersion){

        Optimization app = optimizationRepository.findByApplicationNameAndApplicationVersion(appName, appVersion);
//        Optimization app = null;
        if(app == null){
            return new ResponseEntity<Optimization>(HttpStatus.NO_CONTENT);
        }


        return new ResponseEntity<Optimization>(app, HttpStatus.OK);
    }

//    @CrossOrigin(origins = "*")
    @RequestMapping(value = "optimize/{appName}/{appVersion:.+}", method = RequestMethod.DELETE)
    public DeferredResult<ResponseEntity<ReleaseResponse>> deleteA4CRequest(@PathVariable("appName") String appName,
                                                         @PathVariable("appVersion") String appVersion){

        Optimization opti = optimizationRepository.findByApplicationNameAndApplicationVersion(appName, appVersion);
//        Optimization opti = null;
        if(opti == null){
            return new DeferredResult<ResponseEntity<ReleaseResponse>>(1000L, new ResponseEntity<ReleaseResponse>(HttpStatus.NOT_FOUND));
        }
        if(opti.getStatus().equals(RequestStatus.FAIL) || opti.getStatus().equals(RequestStatus.PROCESSING)){
            optimizationRepository.delete(opti);
            return new DeferredResult<ResponseEntity<ReleaseResponse>>(1000L, new ResponseEntity<ReleaseResponse>(HttpStatus.NO_CONTENT));
        }

        BlueprintResourceDecommissionTemplate blueprint = new BlueprintResourceDecommissionTemplate();
        blueprint.setBlueprintId(opti.getBlueprintRequestId());
        blueprint.setTimestamp(System.currentTimeMillis());
        blueprint.setResourcedServiceElements(new ArrayList<ResourcedServiceElement>());
        for(int i = 0; i < opti.getResourceList().size(); i++) {
            ResourcedServiceElement rse = new ResourcedServiceElement();

            ServiceResource serviceResource = opti.getResourceList().get(i);
            ServiceRequest serviceRequest = opti.getRequestList().get(i);
            rse.setCreatorId(serviceResource.getCreatorId());
            rse.setResources(serviceResource.getResources());
            rse.setServiceElementId(serviceRequest.getName());
            rse.setStatus("FINISHED");
            blueprint.getResourcedServiceElements().add(rse);
        }

        boolean code = blueprintScheduler.decommission(blueprint);

        if(code){
            log.info("Decomission message sent. Deleting orchestrator and optimization data...");
            String orchestratorId = opti.getOrchestratorId();
//            deleteOrchestrator(orchestratorId);
            deleteOrchestratorLocation(orchestratorId, opti.getAppLocation());
//            deleteBrooklynLocations(opti);
            optimizationRepository.delete(opti);

            List<ServiceRequest> requests = new ArrayList<>();
            List<ServiceResponse> responses = new ArrayList<>();
            for(int i = 0; i < opti.getRequestList().size(); i++){
                ServiceRequest sreq = opti.getRequestList().get(i);
                ServiceResponse sres = opti.getResponseList().get(i);
                ServiceRequest newReq = new ServiceRequest();
                ServiceResponse newRes = new ServiceResponse();
//            sres.setId(sreq.getName());
                newReq.setId(sres.getType());
                newReq.setType(sres.getType());
                newReq.setName(sres.getId());

                newRes.setId(sres.getId()); // this is the name of the service
                newRes.setType(sreq.getId()); // this is the type of the first request

                requests.add(newReq);
                responses.add(newRes);
            }
            ReleaseResponse result = new ReleaseResponse();
            result.setServices(requests);

            result.setResponse(responses);

            List<String> nodesToDelete = new ArrayList<String>();
            for(int i = 0; i < opti.getAdditionalOperations().size(); i++){
                AdditionalOperation ao = opti.getAdditionalOperations().get(i);
                if(ao.get("type").equals("org.alien4cloud.tosca.editor.operations.nodetemplate.AddNodeOperation")){
                    nodesToDelete.add(ao.get("nodeName"));
                }
            }

            result.setToDelete(nodesToDelete);

            return new DeferredResult<ResponseEntity<ReleaseResponse>>(1000L,
                    new ResponseEntity<ReleaseResponse>(result, HttpStatus.OK));
        } // if instruction
        return new DeferredResult<ResponseEntity<ReleaseResponse>>(1000L, new ResponseEntity<ReleaseResponse>(HttpStatus.NOT_FOUND));
    }

//    private void deleteBrooklynLocations(Optimization opti) {
//        for(ServiceResource r : opti.getResourceList()){
//            if(r.getLocationId() != null){
//                getNewBrooklynApi().getLocationApi()
//                        .delete(r.getLocationId());
//
//            }
//        }
//    }

    private void deleteOrchestratorLocation(String orchestratorId, String appLocation) {
        URI locURI = null, appURI = null;
//        String locationId = null;
        //Create orchestrator location in A4C
        log.info("Creating location");
        //TODO Make this operation using a4c services since this is a plugin
        try {
            locURI = new URI(A4CEndpoint + "/rest/v1/orchestrators/" + orchestratorId + "/locations/" + appLocation);
            AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
            RestTemplate restTemplate = authRestTemplate.getRestTemplate();
            HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
            RequestEntity<Map> compRequest = new RequestEntity<Map>(cookieHeaders, HttpMethod.DELETE, locURI);
            ResponseEntity<RestResponse> response = restTemplate.exchange(compRequest, RestResponse.class);
            RestResponse stringRestResponse = response.getBody();
            if(stringRestResponse.getError() != null){
                log.error("got error" + stringRestResponse.getError().toString());
                return ;
            }
            Boolean deleted = (Boolean) stringRestResponse.getData();
            if(!deleted){
                log.error("Location cannot be deleted");
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

    }

    private void deleteOrchestrator(String orchestratorId) {

        UriComponentsBuilder url = UriComponentsBuilder.fromHttpUrl(A4CEndpoint + "/rest/v1/orchestrators/" + orchestratorId);
//            orchURI = new URI(A4CEndpoint + "/rest/v1/orchestrators");
        AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
        RestTemplate restTemplate = authRestTemplate.getRestTemplate();
        HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
        HttpEntity<String> entity = new HttpEntity<String>(cookieHeaders);
        boolean enabled = false;
        log.info("Checking orchestrator status..");
        ResponseEntity<RestResponse> response = restTemplate.exchange(url.build().encode().toUri(),
                HttpMethod.GET,  entity, RestResponse.class);
        if (response.getStatusCode().equals(HttpStatus.OK)) {
            RestResponse<Map> stringRestResponse = response.getBody();
            if (stringRestResponse.getError() != null) {
                log.error("got error" + stringRestResponse.getError().toString());
            }else {


                    Map map = (Map) stringRestResponse.getData();
                    orchestratorId = (String) map.get("id");
                    if (map.get("state").equals("CONNECTED")) {
                        enabled = true;
                    }

            }
        }
        if(enabled) {
            try {
                log.info("Disabling orchestrator");
                URI orchURI = new URI(A4CEndpoint + "/rest/v1/orchestrators/" + orchestratorId + "/instance");
                authRestTemplate = a4CrestTemplateFactory.getObject();
                restTemplate = authRestTemplate.getRestTemplate();
                cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
                RequestEntity<String> compRequest = new RequestEntity<String>("", cookieHeaders, HttpMethod.DELETE, orchURI);
                response = restTemplate.exchange(compRequest, RestResponse.class);
                RestResponse stringRestResponse = response.getBody();
                if (stringRestResponse.getError() != null) {
                    log.error("got error while updating orchestrator configuration" + stringRestResponse.getError().toString());
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        URI orchURI = null;
        //Delete orchestrator in A4C
        try {
            log.info("Deleting orchestrator");
            orchURI = new URI(A4CEndpoint + "/rest/v1/orchestrators/" + orchestratorId);
            authRestTemplate = a4CrestTemplateFactory.getObject();
            restTemplate = authRestTemplate.getRestTemplate();
            cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
            RequestEntity<Object> compRequest = new RequestEntity<Object>(cookieHeaders, HttpMethod.DELETE, orchURI);
            response = restTemplate.exchange(compRequest, RestResponse.class);
            RestResponse stringRestResponse = response.getBody();
            if(stringRestResponse.getError() != null){
                log.error("got error" + stringRestResponse.getError().toString());
            }else{
                log.info("Deleted orchestrator: {}", orchestratorId);
            }

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

//    @CrossOrigin(origins = "*")
//    @RequestMapping(value = "/sde/optimize/{id}", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<Object> processSOSMResult(@PathVariable("id") String id, @RequestBody BlueprintResourcedTemplate blueprint){
    public boolean processSOSMResult(String id, BlueprintResourcedTemplate blueprint){


        Optimization opti = optimizationRepository.findById(id);
        if(opti == null){
            return false;
        }

        if(blueprint.getStatus().equals("FAILED")){
            opti.setStatus(RequestStatus.FAIL);
            optimizationRepository.save(opti);
            return false;
        }
//        ObjectMapper mapper = new ObjectMapper();
//        try {
//            String value = mapper.writeValueAsString(blueprint);
//            log.info("Resourced Blueprint received {}", value);
//        } catch (JsonProcessingException e) {
//            log.debug(e.getMessage());
//        }

        List<ServiceResponse> returnedImplementations = new ArrayList<ServiceResponse>();
        List<ServiceResource> resources = new ArrayList<ServiceResource>();

        //Add services in the order they were received
        int k = 0;
        for(ServiceRequest s : opti.getRequestList()) {
            //Search for the service element corresponding
            for (ResourcedServiceElement se : blueprint.getResourcedServiceElements()) {
                if(se.getServiceElementId().equals(s.getName())) {
                    ///Found service element, search for implementation that corresponds to SOSM implementation type
                    ServiceResponse r = new ServiceResponse();
                    r.setId(s.getName());
                    for(ImplementationRequest implReq : s.getImplementationList()){
                        if(implReq.getSOSMTypes().contains(se.getImplementationType())){
                            //This implementation can handle the returned resource placement. The UI needs its id.
                            r.setType(implReq.getId());
                            List<AdditionalOperation> additionalNodes = createAdditionalNodes(
                                    se.getImplementationType(), s.getName(), k, se.getResources().get(0));
                            opti.getAdditionalOperations().addAll(additionalNodes);
                            break;
                        }
                    }
                    returnedImplementations.add(r);
                    //Add resource descriptor for this service
                    ServiceResource resource = new ServiceResource();
                    resource.setResourceType(se.getResourceType());
                    resource.setCreatorId(se.getCreatorId());
                    resource.setResources(se.getResources());
                    resources.add(resource);
                }
            }
            k++;
        }
        opti.setResponseList(returnedImplementations);
        opti.setResourceList(resources);

        opti.setStatus(RequestStatus.SUCCESS);
        optimizationRepository.save(opti);

        return true;
    }

    private String createOrchestratorLocation(ServiceResource resource) {
        Map<String, String> locationSpec = new HashMap<String, String>();
        String result = "sosm" + UUID.randomUUID().toString();
        Map<String, Object> config = new HashMap<String, Object>();
        locationSpec.put("name", result);
        config.put("displayName", result);
        String s = resource.getResources().get(0).getResourceDescriptor();
        if(resource.getResourceType().equals(ResourceType.OPENSTACK_ACCOUNT)){
            ObjectMapper objectMapper = new ObjectMapper();
            locationSpec.put("spec", "jclouds:openstack-nova");
            try {
                OpenStackAccountResource os = objectMapper.readValue(s, OpenStackAccountResource.class);
                config.put("endpoint", os.getAuthEndpoint());// "http://10.171.92.11:5000/v2.0");
                config.put("identity", os.getProject() + ":" + os.getUsername());//"scramble:scramble");
                config.put("credential", os.getPassword());//"scramble");
                config.put("securityGroups", "default");
                config.put("loginUser",  "ubuntu");
                config.put("jclouds.openstack-nova.auto-generate-keypairs", true);
                config.put("networkName", "50e35dee-a121-4e58-bc00-8f60bfd16880");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else if(resource.getResourceType().equals(ResourceType.BAREMETAL)){
            //this is byon case
            locationSpec.put("spec", "byon");
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                BareMetalResource server = objectMapper.readValue(s, BareMetalResource.class);
                config.put("user", server.getUsername());
                List<String> hosts = new ArrayList<>();
                hosts.add(server.getIpAddress());
                config.put("hosts", hosts);
                if(server.getPassword() != null && server.getPassword().length() > 0) {
                    config.put("password", server.getPassword());
                }
                if(server.getSshKey() != null && server.getSshKey().length() > 0){
                    config.put("privateKeyFile", server.getSshKey());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }else if(resource.getResourceType().equals(ResourceType.OPENSTACK_VM)){
            locationSpec.put("spec", "byon");
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                OpenStackVMResource vm = objectMapper.readValue(s, OpenStackVMResource.class);
                if(vm.getUsername().equals("sosm"))
                    config.put("user", "root");
                else
                    config.put("user", vm.getUsername());
                List<String> hosts = new ArrayList<>();
                hosts.add(vm.getIpAddress());
                config.put("hosts", hosts);
                if(vm.getPassword() != null && vm.getPassword().length() > 0) {
                    config.put("password", vm.getPassword());
                }
                if(vm.getSshKey() != null && vm.getSshKey().length() > 0){
                    config.put("privateKeyFile", vm.getSshKey());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else if(resource.getResourceType().equals(ResourceType.OPENSTACK_RESOURCE)){
            locationSpec.put("spec", "byon");
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                OpenStackResource vm = objectMapper.readValue(s, OpenStackResource.class);
                if(vm.getUsername().equals("sosm"))
                    config.put("user", "root");
                else
                    config.put("user", vm.getUsername());
                List<String> hosts = new ArrayList<>();
                hosts.add(vm.getIpAddress());
                config.put("hosts", hosts);
                if(vm.getPassword() != null && vm.getPassword().length() > 0) {
                    config.put("password", vm.getPassword());
                }
//                if(vm.getSshKey() != null && vm.getSshKey().length() > 0){
//                    config.put("privateKeyFile", vm.getSshKey());
//                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            log.error("Not implemented! " + resource.getResourceType());
        }

//        locationSpec.put("config", config);
//        LocationSpec spec1 = new LocationSpec(result, locationSpec.get("spec"), config);
//        Response spec = getNewBrooklynApi().getLocationApi()
//                .create(spec1);
//        if(spec.getStatus() == 201){
//            return result;
//        }
        return null;
    }

    private List<AdditionalOperation> createAdditionalNodes(String implementationType, String serviceName, int number, Resources resources) {

        String[] split = implementationType.split("_");
        String acc = split[0];
        String virt = split[1];
        List<AdditionalOperation> result = new ArrayList<AdditionalOperation>();

        if(virt.equals("CONTAINER")){
            if(acc.equals("CPU")){
                //cpu container, do nothing
                log.info("Simplest case, no additional nodes required");
            }else{
                List<AdditionalOperation> container = NodeBuilder.basicContainer(serviceName, number);
                result.addAll(container);
                String containerName = container.get(0).get("nodeName");
                String s = resources.getResourceDescriptor();

//                ObjectMapper objectMapper = new ObjectMapper();
//                String mountingPoint = "";
//                //TODO implement for kubernetes
//                try {
//                    MarathonResourceManagerResource mr = objectMapper.readValue(s, MarathonResourceManagerResource.class);
//                    mountingPoint = mr.getAcceleratorMountingPoint();
//
//                } catch (JsonMappingException e) {
//                    e.printStackTrace();
//                } catch (JsonParseException e) {
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                addAcceleratorOps(serviceName, number, acc, result, containerName);
            }
        }else{
            List<AdditionalOperation> container = NodeBuilder.basicResource(serviceName, number);
            result.addAll(container);
            String containerName = container.get(0).get("nodeName");
//            String s = resources.getResourceDescriptor();
            ObjectMapper objectMapper = new ObjectMapper();
            String mountingPoint = "";
//            try {
//                if(virt.equals("BM")) {
//                    BareMetalResource mr = objectMapper.readValue(s, BareMetalResource.class);
//                    mountingPoint = mr.getAcceleratorMountingPoint();
//                }
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
            if(acc.equals("CPU")){
                //cpu container, do nothing
                log.info("Simplest case, no additional nodes required");
            }else{
                log.info("Simplest case, no additional nodes required");
                addAcceleratorOps(serviceName, number, acc, result, containerName);

            }
        }


        return result;
    }

    private void addAcceleratorOps(String serviceName, int number, String acc, List<AdditionalOperation> result, String containerName) {
        if(acc.equals("MIC")){
            List<AdditionalOperation> accelerator = NodeBuilder.basicMIC(containerName, serviceName, number);
            Map<String, String> updateReq = new HashMap<String, String>();

            result.addAll(accelerator);
        }
        else if(acc.equals("GPU")){
            List<AdditionalOperation> accelerator = NodeBuilder.basicGPU(containerName, number, serviceName);

            result.addAll(accelerator);
        }
        else if(acc.equals("DFE")){
            List<AdditionalOperation> accelerator = NodeBuilder.basicDFE(containerName, number, serviceName);

            result.addAll(accelerator);
        }
    }

    private String updateLocationPolicies(String applicationName, String environmentId, String orchestratorId, String locationId, String infrastructureType) {
        //First create a location inside orchestrator, called SOSM
        Map<String, String> l = new HashMap<String, String>();
        l.put("name", locationId != null ? locationId : "SOSM");
        l.put("infrastructureType", infrastructureType);

        URI locURI = null, appURI = null;
//        String locationId = null;
        //Create orchestrator location in A4C
        log.info("Creating location");
        try {
            locURI = new URI(A4CEndpoint + "/rest/v1/orchestrators/" + orchestratorId + "/locations");
            AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
            RestTemplate restTemplate = authRestTemplate.getRestTemplate();
            HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
            RequestEntity<Map> compRequest = new RequestEntity<Map>(l, cookieHeaders, HttpMethod.POST, locURI);
            ResponseEntity<RestResponse> response = restTemplate.exchange(compRequest, RestResponse.class);
            if(response.getStatusCode().equals(HttpStatus.CONFLICT)){
                log.error("Location already exists...");
            }else {
                RestResponse stringRestResponse = response.getBody();
                if (stringRestResponse.getError() != null) {
                    log.error("got error" + stringRestResponse.getError().toString());
                    return null;
                }
                locationId = (String) stringRestResponse.getData();
            }

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }


        //Then set that location
        log.info("Setting topology dependency on location");
        if(locationId != null){
            Map<String, Object> policyUpdateRequest = new HashMap<String, Object>();
            Map<String, String> groupsToLocations = new HashMap<String, String>();
            groupsToLocations.put("_A4C_ALL", locationId);
            policyUpdateRequest.put("groupsToLocations", groupsToLocations);
            policyUpdateRequest.put("orchestratorId", orchestratorId);
            try {
                appURI = new URI(A4CEndpoint + "/rest/v1/applications/" + applicationName + "/environments/" +
                            environmentId + "/deployment-topology/location-policies");
                AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
                RestTemplate restTemplate = authRestTemplate.getRestTemplate();
                HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
                RequestEntity<Map> compRequest = new RequestEntity<Map>(policyUpdateRequest, cookieHeaders, HttpMethod.POST, appURI);
                ResponseEntity<RestResponse> response = restTemplate.exchange(compRequest, RestResponse.class);
                RestResponse stringRestResponse = response.getBody();
                if(stringRestResponse.getError() != null){
                    log.error("got error" + stringRestResponse.getError().toString());
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }


        }
        //TODO the following should be applied for the current user, not for the generic "enduser" account
//        if(locationId != null){
//            try {
//                appURI = new URI(A4CEndpoint + "/rest/v1/orchestrators/" + orchestratorId + "/locations/" +
//                        locationId + "/roles/users/enduser/DEPLOYER");
//                AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
//                RestTemplate restTemplate = authRestTemplate.getRestTemplate();
//                HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
//                RequestEntity<Object> compRequest = new RequestEntity<Object>(null, cookieHeaders, HttpMethod.PUT, appURI);
//                ResponseEntity<RestResponse> response = restTemplate.exchange(compRequest, RestResponse.class);
//                RestResponse stringRestResponse = response.getBody();
//                if(stringRestResponse.getError() != null){
//                    log.error("got error" + stringRestResponse.getError().toString());
//                }
//                else{
//                    log.info("Successfully added enduser authorization to Location...");
//                }
//            } catch (URISyntaxException e) {
//                e.printStackTrace();
//            }
//        }
        return locationId;
    }

    private String createSOSMEnvironment(String applicationName, String applicationVersion) {

        Map<String, String> createReq = new HashMap<String, String>();
        createReq.put("name", "SOSMEnvironment");
        createReq.put("environmentType", "PRODUCTION");
        createReq.put("description", "Environment automatically created by CL-SDE in order to match with the" +
                " Orchestrator and Location, created based on the response from the Self Organising Self Managing platform ");
        createReq.put("applicationId", applicationName);
        createReq.put("versionId", applicationName + ":" + applicationVersion);
        URI envURI = null;
        String environmentId = null;
        //Create orchestrator in A4C
        try {
            envURI = new URI(A4CEndpoint + "/rest/v1/applications/" + applicationName + "/environments");
            AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
            RestTemplate restTemplate = authRestTemplate.getRestTemplate();
            HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
            RequestEntity<Map> compRequest = new RequestEntity<Map>(createReq, cookieHeaders, HttpMethod.POST, envURI);
            ResponseEntity<RestResponse> response = restTemplate.exchange(compRequest, RestResponse.class);
            RestResponse stringRestResponse = response.getBody();
            if(stringRestResponse.getError() != null){
                log.error("got error" + stringRestResponse.getError().toString());
                return environmentId;
            }
            environmentId = (String) stringRestResponse.getData();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return environmentId;
    }

    private String retrieveDefaultEnvironment(String applicationName, String applicationVersion) {

        Map<String, String> createReq = new HashMap<String, String>();

        URI envURI = null;
        String environmentId = null;
        //Create orchestrator in A4C
        try {
            envURI = new URI(A4CEndpoint + "/rest/v1/applications/" + applicationName + "/environments/search");
            AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
            RestTemplate restTemplate = authRestTemplate.getRestTemplate();
            HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
            RequestEntity<Map> compRequest = new RequestEntity<Map>(createReq, cookieHeaders, HttpMethod.POST, envURI);
            ResponseEntity<RestResponse> response = restTemplate.exchange(compRequest, RestResponse.class);
            RestResponse<Map<String, Map>> stringRestResponse = response.getBody();
            if(stringRestResponse.getError() != null){
                log.error("got error" + stringRestResponse.getError().toString());
                return null;
            }
            List list = (List) stringRestResponse.getData().get("data");
            Map map = (Map) list.get(0);
            environmentId = (String) map.get("id");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return environmentId;
    }

    private String createAndEnableOrchestrator(/*ServiceResource resource*/) {
        Map<String, Object> orchestratorReq = new HashMap<String, Object>();
        boolean enabled = false;
        boolean exists = false;
        String orchestratorId = null;

        UriComponentsBuilder orchURI = UriComponentsBuilder.fromHttpUrl(A4CEndpoint + "/rest/v1/orchestrators");
//                .queryParam("query", query);
//            orchURI = new URI(A4CEndpoint + "/rest/v1/orchestrators");
        AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
        RestTemplate restTemplate = authRestTemplate.getRestTemplate();
        HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
        HttpEntity<String> entity = new HttpEntity<String>(cookieHeaders);
        ResponseEntity<RestResponse> response = restTemplate.exchange(orchURI.build().encode().toUri(),
                HttpMethod.GET,  entity, RestResponse.class);
        if (response.getStatusCode().equals(HttpStatus.OK)) {
            RestResponse<Map> stringRestResponse = response.getBody();
            if (stringRestResponse.getError() != null) {
                log.error("got error" + stringRestResponse.getError().toString());
            }else {

                List list = (List) stringRestResponse.getData().get("data");
                if(list.size() > 0) {
                    for(int i = 0; i < list.size(); i++) {
                        Map map = (Map) list.get(i);
                        if(map.get("name").equals("TUFA-Provider")) {
                            exists = true;
                            orchestratorId = (String) map.get("id");
//                    resource.setOrchestratorId(orchestratorId);
                            if (map.get("state").equals("CONNECTED")) {
                                enabled = true;
                            }
                        }
                    }
                }
            }
        }

        if(!exists){
            //create orchestrator
            OrchestratorRequest request = new OrchestratorRequest();
            request.setName("TUFA-Provider");
            request.setPluginBean("tufa-orchestrator-factory");
            request.setPluginId("a4c-tufa-provider");
            //Create orchestrator in A4C

            orchURI = UriComponentsBuilder.fromHttpUrl(A4CEndpoint + "/rest/v1/orchestrators");
            authRestTemplate = a4CrestTemplateFactory.getObject();
            restTemplate = authRestTemplate.getRestTemplate();
            cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
            RequestEntity<OrchestratorRequest> compRequest = new RequestEntity<OrchestratorRequest>(request,
                    cookieHeaders, HttpMethod.POST, orchURI.build().encode().toUri());
            ResponseEntity<RestResponse> exchange = restTemplate.exchange(compRequest, RestResponse.class);
            RestResponse stringRestResponse = exchange.getBody();
            if(stringRestResponse.getError() != null){
                log.error("got error" + stringRestResponse.getError().toString());
                return null;
            }
            orchestratorId = (String) stringRestResponse.getData();
//            resource.setOrchestratorId(orchestratorId);

        }

        if(!enabled){
            //Enable orchestrator

                orchURI = UriComponentsBuilder.fromHttpUrl(A4CEndpoint + "/rest/v1/orchestrators/"+orchestratorId+"/instance");
                authRestTemplate = a4CrestTemplateFactory.getObject();
                restTemplate = authRestTemplate.getRestTemplate();
                cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
                RequestEntity<String> compRequest = new RequestEntity<String>("", cookieHeaders, HttpMethod.POST,
                        orchURI.build().encode().toUri());
                response = restTemplate.exchange(compRequest, RestResponse.class);
                RestResponse stringRestResponse = response.getBody();
                if(stringRestResponse.getError() != null){
                    log.error("got error while updating orchestrator configuration" + stringRestResponse.getError().toString());
                }
//                enabled = true;

        }

        return orchestratorId;
    }

//    private boolean createMarathonOrchestrator(ServiceResource resource) {
//        OrchestratorRequest request = new OrchestratorRequest();
//        request.setName("Marathon-"+UUID.randomUUID().toString());
//        request.setPluginBean("marathon-orchestrator-factory");
//        request.setPluginId("gateway-plugin-marathon");
//
//        URI orchURI = null;
//        String orchestratorId = null;
//        //Create orchestrator in A4C
//        try {
//            orchURI = new URI(A4CEndpoint + "/rest/v1/orchestrators");
//            log.info("Creating  Marathon orchestrator");
//            AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
//            RestTemplate restTemplate = authRestTemplate.getRestTemplate();
//            HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
//            RequestEntity<OrchestratorRequest> compRequest = new RequestEntity<OrchestratorRequest>(request, cookieHeaders, HttpMethod.POST, orchURI);
//            ResponseEntity<RestResponse> response = restTemplate.exchange(compRequest, RestResponse.class);
//            RestResponse stringRestResponse = response.getBody();
//            if(stringRestResponse.getError() != null){
//                log.error("got error" + stringRestResponse.getError().toString());
//                return false;
//            }
//            orchestratorId = (String) stringRestResponse.getData();
////            resource.setOrchestratorId(orchestratorId);
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }
//
//        //Update orchestrator with marathon information
//        log.info("Updating orchestrator with marathon endpoint");
//        Map<String, String> updateReq = new HashMap<String, String>();
////        if(resource.getResources().size() < 1){
////            log.error("Marathon manager found, but missing resources");
////            return false;
////        }
////        String s = resource.getResources().get(0).getResourceDescriptor();
////        ObjectMapper objectMapper = new ObjectMapper();
////        try {
////            MarathonResourceManagerResource mr = objectMapper.readValue(s, MarathonResourceManagerResource.class);
////            updateReq.put("marathonURL", mr.getEndpoint());
////        } catch (IOException e) {
////            e.printStackTrace();
////        }
//
//
//        try {
//            orchURI = new URI(A4CEndpoint + "/rest/v1/orchestrators/"+orchestratorId+"/configuration");
//            AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
//            RestTemplate restTemplate = authRestTemplate.getRestTemplate();
//            HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
//            RequestEntity<Map> compRequest = new RequestEntity<Map>(updateReq, cookieHeaders, HttpMethod.PUT, orchURI);
//            ResponseEntity<RestResponse> response = restTemplate.exchange(compRequest, RestResponse.class);
//            RestResponse stringRestResponse = response.getBody();
//            if(stringRestResponse.getError() != null){
//                log.error("got error while updating orchestrator configuration" + stringRestResponse.getError().toString());
//            }
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }
//
//        //Enable orchestrator
//        log.info("Enabling orchestrator");
//        RestTemplate restTemplate2 = new RestTemplate();
//        ((SimpleClientHttpRequestFactory)restTemplate2.getRequestFactory()).setConnectTimeout(5 * 1000);
//        boolean isOnline = true;
//        try {
//            ResponseEntity<String> marathonURL = restTemplate2.getForEntity(updateReq.get("marathonURL"), String.class);
//        }catch (Exception e){
//            log.info("Could not connect to Marathon {}", e.getMessage());
//            isOnline = false;
//        }
//
//        if(isOnline) {
//            try {
//                orchURI = new URI(A4CEndpoint + "/rest/v1/orchestrators/" + orchestratorId + "/instance");
//                AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
//                RestTemplate restTemplate = authRestTemplate.getRestTemplate();
//                HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
//                RequestEntity<String> compRequest = new RequestEntity<String>("", cookieHeaders, HttpMethod.POST, orchURI);
//                ResponseEntity<RestResponse> response = restTemplate.exchange(compRequest, RestResponse.class);
//                RestResponse stringRestResponse = response.getBody();
//                if (stringRestResponse.getError() != null) {
//                    log.error("got error while updating orchestrator configuration" + stringRestResponse.getError().toString());
//                }
//            } catch (URISyntaxException e) {
//                e.printStackTrace();
//            }
//        }else{
//            return false;
//        }
//        return true;
//    }

}
