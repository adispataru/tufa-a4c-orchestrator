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
import alien4cloud.tufa.model.AuthRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static alien4cloud.sampleplugin.ContextConfiguration.A4CEndpoint;

/**
 * Created by adrian on 22.03.2017.
 */
@Service
public class A4CRestTemplateFactory {

    private AuthRestTemplate a4cRestTemplate;
    private static final String userName = "admin";
    private static final String password = "admin";
    private static final String loginURL = A4CEndpoint + "/login";
    public static final long autheticationInterval = 900 * 1000; // 1/4 hour;
    private static long lastAuthenticated;


    public AuthRestTemplate getObject() {
        long now = System.currentTimeMillis();
        if(now - lastAuthenticated > autheticationInterval) {
            a4cRestTemplate = new AuthRestTemplate();
            a4cRestTemplate.authenticate(loginURL, userName, password);
        }
        return a4cRestTemplate;
    }
    public Class<RestTemplate> getObjectType() {
        return RestTemplate.class;
    }
    public boolean isSingleton() {
        return true;
    }

    public void afterPropertiesSet() {
        a4cRestTemplate = new AuthRestTemplate();
        a4cRestTemplate.authenticate(loginURL, userName, password);
        lastAuthenticated = System.currentTimeMillis();

    }

    public static HttpHeaders createCookieHeaders(final List<String> cookie){
        HttpHeaders header = new HttpHeaders();
        for(String s : cookie){
            header.add("Cookie", s);
        }
        return header;
    }
}
