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


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tufa.model.soe.ServiceResource;

import java.util.Map;

/**
 * Created by adrian on 10.10.2017.
 */
@Component
public class SOEClient {
    private static final String address = "http://localhost:8088";

    public Map<String, ServiceResource>  getServicesLocation(String appId, String appVersion){
        RestTemplate restTemplate = new RestTemplate();
        String path = address + "/rest/soe/optimize/" + appId + "/" + appVersion + "/locations";
        try {
            ResponseEntity<Map> forEntity = restTemplate.getForEntity(path, Map.class);
            if(forEntity.getStatusCode().equals(HttpStatus.OK)) {
                Map body = forEntity.getBody();
                Map<String, ServiceResource> result = (Map<String, ServiceResource>) body;
                return result;
            }
        } catch (HttpClientErrorException ex)   {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw ex;
            }
        }

        return null;

    }

}
