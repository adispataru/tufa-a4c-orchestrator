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

package alien4cloud.tufa.model;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Created by adrian on 22.03.2017.
 */
public class AuthRestTemplate {
    private RestTemplate restTemplate;
    private List<String> cookies;

    public AuthRestTemplate(){
        restTemplate = new RestTemplate();
        cookies = null;
    }

    public boolean authenticate(String url, String user, String password){
        String e="username=" + user + "&password=" + password + "&submit=Login";
        URI uri = null;
        try {
             uri = new URI(url);
        } catch (URISyntaxException e1) {
            e1.printStackTrace();
            return false;
        }

        RequestEntity<String> req = new RequestEntity<String>(e, createHeaders(), HttpMethod.POST, uri);
        ResponseEntity<Object> exchange = restTemplate.exchange(req, Object.class);
        if(exchange.getStatusCode().is3xxRedirection()) {
            cookies = exchange.getHeaders().get("Set-Cookie");
            return true;
        }
        return false;
    }

    private HttpHeaders createHeaders(){
        return new HttpHeaders() {{
            set( "Content-Type", "application/x-www-form-urlencoded" );
        }};
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public List<String> getCookies() {
        return cookies;
    }
}
