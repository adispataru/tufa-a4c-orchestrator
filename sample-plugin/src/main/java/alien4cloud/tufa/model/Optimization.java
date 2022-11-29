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


import alien4cloud.model.AdditionalOperation;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.elasticsearch.annotation.ESAll;
import org.elasticsearch.annotation.ESObject;
import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by adrian on 08.03.2017.
 */

@ESObject
//@Getter
//@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@ESAll(analyser = "simple")
public class Optimization implements Serializable{
    @Id
    private String id;
    private String blueprintRequestId;
    private String applicationName;
    private String applicationVersion;
    private List<ServiceRequest> requestList;
    private List<ServiceResponse> responseList;
    private List<ServiceResource> resourceList;
    private RequestStatus status;
    private List<AdditionalOperation> additionalOperations;
    private String orchestratorId;
    private String appLocation;
    private String environmentId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBlueprintRequestId() {
        return blueprintRequestId;
    }

    public void setBlueprintRequestId(String blueprintRequestId) {
        this.blueprintRequestId = blueprintRequestId;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public List<ServiceRequest> getRequestList() {
        return requestList;
    }

    public void setRequestList(List<ServiceRequest> requestList) {
        this.requestList = requestList;
    }

    public List<ServiceResponse> getResponseList() {
        return responseList;
    }

    public void setResponseList(List<ServiceResponse> responseList) {
        this.responseList = responseList;
    }

    public List<ServiceResource> getResourceList() {
        return resourceList;
    }

    public void setResourceList(List<ServiceResource> resourceList) {
        this.resourceList = resourceList;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    public void setApplicationVersion(String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    public List<AdditionalOperation> getAdditionalOperations() {
        if(additionalOperations == null){
            additionalOperations = new ArrayList<AdditionalOperation>();
        }
        return additionalOperations;
    }

    public void setAdditionalOperations(List<AdditionalOperation> additionalOperations) {
        this.additionalOperations = additionalOperations;
    }

    public String getOrchestratorId() {
        return orchestratorId;
    }

    public void setOrchestratorId(String orchestratorId) {
        this.orchestratorId = orchestratorId;
    }

    public String getAppLocation() {
        return appLocation;
    }

    public void setAppLocation(String appLocation) {
        this.appLocation = appLocation;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }
}
