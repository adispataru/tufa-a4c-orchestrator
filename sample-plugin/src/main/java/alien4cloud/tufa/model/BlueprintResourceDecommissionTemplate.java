package alien4cloud.tufa.model;

import java.util.ArrayList;
import java.util.List;

public class BlueprintResourceDecommissionTemplate {
    private String blueprintId;
    private long timestamp;
    private List<ResourcedServiceElement> resourcedServiceElements;

    public BlueprintResourceDecommissionTemplate() {
	this.blueprintId = "";
	this.timestamp = 0;
	resourcedServiceElements = new ArrayList<ResourcedServiceElement>();
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
