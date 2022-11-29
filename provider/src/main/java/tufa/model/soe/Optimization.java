package tufa.model.soe;


import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by adrian on 08.03.2017.
 */
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
}
