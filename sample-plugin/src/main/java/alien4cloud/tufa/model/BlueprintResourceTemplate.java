package alien4cloud.tufa.model;

import java.util.ArrayList;
import java.util.List;

/*
 * Receiving by Cell Manager
 */
public class BlueprintResourceTemplate {
    private String blueprintId;
    private long timestamp;
    private double cost;
    private String callbackEndpoint;
    private List<ServiceElement> serviceElements;

    public BlueprintResourceTemplate() {
	this.blueprintId = "";
	this.timestamp = 0;
	this.cost = 0;
	this.callbackEndpoint = "";
	this.serviceElements = new ArrayList<ServiceElement>();
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
     * @return the cost
     */
    public double getCost() {
	return cost;
    }

    /**
     * @param cost
     *            the cost to set
     */
    public void setCost(double cost) {
	this.cost = cost;
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
     * @return the serviceElements
     */
    public List<ServiceElement> getServiceElements() {
	return serviceElements;
    }

    /**
     * @param serviceElements
     *            the serviceElements to set
     */
    public void setServiceElements(List<ServiceElement> serviceElements) {
	this.serviceElements = serviceElements;
    }
}
