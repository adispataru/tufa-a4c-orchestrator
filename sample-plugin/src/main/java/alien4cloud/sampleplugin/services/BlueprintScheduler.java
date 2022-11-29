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

package alien4cloud.sampleplugin.services;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.rest.model.RestResponse;
import alien4cloud.sampleplugin.repositories.OptimizationRepository;
import alien4cloud.tufa.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static alien4cloud.sampleplugin.ContextConfiguration.*;
import static alien4cloud.sampleplugin.services.A4CRestTemplateFactory.createCookieHeaders;


/**
 * Created by adrian on 10.03.2017.
 */
@Service
public class BlueprintScheduler {

    private static final Logger log = LoggerFactory.getLogger(BlueprintScheduler.class);

    private static final String resultEndpoint = callBackAddress + "/sde/optimize/{id}";


    @Resource
    A4CRestTemplateFactory a4CrestTemplateFactory;
    @Resource
    RestTemplateFactory restTemplateFactory;
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;
    @Resource
    OptimizationRepository optimizationRepository;
    @Resource
    ResourceManagerService resourceManagerService;

    public BlueprintResourcedTemplate submitRequestToCellManager(Optimization o) {

        BlueprintResourceTemplate blueprint = new BlueprintResourceTemplate();
        blueprint.setBlueprintId(o.getId());
        blueprint.setCallbackEndpoint(resultEndpoint.replace("{id}", o.getId()));
        blueprint.setTimestamp(System.currentTimeMillis());
        blueprint.setCost(0.0);
        List<ServiceElement> serviceElements = new ArrayList<ServiceElement>();


        for(ServiceRequest s : o.getRequestList()){
            ServiceElement serviceElement = new ServiceElement();
            serviceElement.setServiceElementId(s.getName());
            List<Implementation> implementations = new ArrayList<Implementation>();

            for(ImplementationRequest impl : s.getImplementationList()) {
                URI compURI = null;
                try {
                    compURI = new URI(A4CEndpoint + "/rest/v1/components/element/" + impl.getElementId() + "/version/" + impl.getArchiveVersion());
                } catch (URISyntaxException e) {
                    log.debug(e.getMessage());
                }
                AuthRestTemplate authRestTemplate = a4CrestTemplateFactory.getObject();
                RestTemplate restTemplate = authRestTemplate.getRestTemplate();
                HttpHeaders cookieHeaders = createCookieHeaders(authRestTemplate.getCookies());
                RequestEntity<String> compRequest = new RequestEntity<String>(null, cookieHeaders, HttpMethod.GET, compURI);
                ResponseEntity<RestResponse> response = restTemplate.exchange(compRequest, RestResponse.class);
                RestResponse nodeTypeRestResponse = response.getBody();
                if(nodeTypeRestResponse.getError() != null){
                    log.error("got error" + nodeTypeRestResponse.getError().toString());
                    return null;
                }
                Map nodeMap = (Map) nodeTypeRestResponse.getData();

                addImplementation(nodeMap, implementations, impl);
            }
            serviceElement.setImplementations(implementations);
            serviceElements.add(serviceElement);

        }

        blueprint.setServiceElements(serviceElements);
        ObjectMapper mapper = new ObjectMapper();
        try {
            String blueprintString = mapper.writeValueAsString(blueprint);
            String optiString = mapper.writeValueAsString(o);


            log.info("Optimization {}", optiString);
            log.info("Blueprint {}", blueprintString);

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        o.setStatus(RequestStatus.PROCESSING);
        optimizationRepository.save(o);

        BlueprintResourcedTemplate brt = resourceManagerService.findResourcesForBlueprint(blueprint);

        return brt;

    }

    private List<Implementation> addImplementation(Map nodeMap, List<Implementation> implementations, ImplementationRequest impl) {


        List<String> virtualizationTypes = new ArrayList<String>();
        String acceleratorReq = null;
        String acceleratorCap = null;
        List requirements = (List) nodeMap.get("requirements");

        for(Object r : requirements){
            Map req  = (Map) r;
            if("host".equals(req.get("id"))){
                String type = (String) req.get("type");
                if(type.contains("Compute")){
                    virtualizationTypes.add("VM");
                    virtualizationTypes.add("BM");
                }else if(type.contains("Docker")){
                    virtualizationTypes.add("CONTAINER");
                }
            }else if("accelerator".equals(req.get("id"))){
                String type = (String) req.get("type");
                if(type.equals("tufa.cap.MIC")){
                    acceleratorReq = "MIC";
                }else if(type.contains("tufa.cap.GPU")){
                    acceleratorReq = "GPU";
                }else if(type.contains("tufa.cap.FPGA")){
                    acceleratorReq = "FPGA";
                }

            }
        }

        List capabilities = (List) nodeMap.get("capabilities");
        for(Object c : capabilities){
            Map cap  = (Map) c;
            String type = (String) cap.get("type");
            if(type.equals("tufa.cap.MIC")){
                acceleratorCap = "MIC";
            }
            if(type.equals("tufa.cap.GPU")){
                acceleratorCap = "GPU";
            }
            if(type.equals("tufa.cap.FPGA")){
                acceleratorCap = "FPGA";
            }

        }

        for(String virt : virtualizationTypes){
            Implementation i = basicImplementation();

            if(acceleratorReq == null && acceleratorCap == null){
                acceleratorReq = "CPU";
            }else if(acceleratorReq == null){
                acceleratorReq = acceleratorCap;
            }

            if(acceleratorReq.equals("CPU")){
                if(TELEMETRY) {
                    i.getAcceleratorRange().add(0);
                    i.getAcceleratorRange().add(0);
                }
            }else{

                    i.getAcceleratorRange().add(1);
                    i.getAcceleratorRange().add(1);

            }
            i.setImplementationType(acceleratorReq +  "_" + virt);

            implementations.add(i);
            impl.getSOSMTypes().add(i.getImplementationType());
        }
        return implementations;
    }

    private Implementation basicImplementation() {
        Implementation i = new Implementation();
        i.setAcceleratorRange(new ArrayList<Integer>());
        i.setBandwidthRange(new ArrayList<Double>());
        i.setComputationRange(new ArrayList<Integer>());
        i.setMemoryRange(new ArrayList<Double>());
        i.setStorageRange(new ArrayList<Double>());
        i.setRequiredResourceUnit(1);



        if(TELEMETRY) {
            i.getComputationRange().add(1);
            i.getComputationRange().add(1);
            i.getMemoryRange().add(100.0);
            i.getMemoryRange().add(1000.0);
            i.getBandwidthRange().add(100.0);
            i.getBandwidthRange().add(1000.0);

            i.getStorageRange().add(5.0);
            i.getStorageRange().add(50.0);
        }
        return i;
    }

    public boolean decommission(BlueprintResourceDecommissionTemplate blueprint) {

        return true;
    }
}
