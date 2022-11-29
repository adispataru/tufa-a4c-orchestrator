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

import java.util.List;

/**
 * Created by adrian on 08.11.2017.
 */
public class ReleaseResponse {

    private List<ServiceRequest> services;
    private List<ServiceResponse> response;
    private List<String> toDelete;

    public List<ServiceRequest> getServices() {
        return services;
    }

    public List<ServiceResponse> getResponse() {
        return response;
    }

    public void setServices(List<ServiceRequest> services) {
        this.services = services;
    }

    public void setResponse(List<ServiceResponse> response) {
        this.response = response;
    }

    public void setToDelete(List<String> toDelete) {
        this.toDelete = toDelete;
    }

    public List<String> getToDelete() {
        return toDelete;
    }
}
