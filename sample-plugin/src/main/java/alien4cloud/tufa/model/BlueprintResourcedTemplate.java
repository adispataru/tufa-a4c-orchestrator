package alien4cloud.tufa.model;

import java.util.ArrayList;
import java.util.List;

public class BlueprintResourcedTemplate {
    private String blueprintId;
    private long timestamp;
    private String status;
    private String callbackEndpoint;
    private List<ResourcedServiceElement> resourcedServiceElements;

    public BlueprintResourcedTemplate() {
	this.blueprintId = "";
	this.timestamp = 0;
	this.status = ProcessingStatus.PENDING.name();
	this.callbackEndpoint = "";
	this.resourcedServiceElements = new ArrayList<ResourcedServiceElement>();
    }

    /**
     * @return the blueprintId
     */
    public String getBlueprintId() {
	return blueprintId;
    }

    /**
     * @param blueprintId
     *            the blueprintId to set
     */
    public void setBlueprintId(String blueprintId) {
	this.blueprintId = blueprintId;
    }

    /**
     * @return the timestamp
     */
    public long getTimestamp() {
	return timestamp;
    }

    /**
     * @param timestamp
     *            the timestamp to set
     */
    public void setTimestamp(long timestamp) {
	this.timestamp = timestamp;
    }

    /**
     * @return the status
     */
    public String getStatus() {
	return status;
    }

    /**
     * @param status
     *            the status to set
     */
    public void setStatus(String status) {
	this.status = status;
    }

    /**
     * @return the callbackEndpoint
     */
    public String getCallbackEndpoint() {
	return callbackEndpoint;
    }

    /**
     * @param callbackEndpoint
     *            the callbackEndpoint to set
     */
    public void setCallbackEndpoint(String callbackEndpoint) {
	this.callbackEndpoint = callbackEndpoint;
    }

    /**
     * @return the resourcedServiceElements
     */
    public List<ResourcedServiceElement> getResourcedServiceElements() {
	return resourcedServiceElements;
    }

    /**
     * @param resourcedServiceElements
     *            the resourcedServiceElements to set
     */
    public void setResourcedServiceElements(List<ResourcedServiceElement> resourcedServiceElements) {
	this.resourcedServiceElements = resourcedServiceElements;
    }
}
