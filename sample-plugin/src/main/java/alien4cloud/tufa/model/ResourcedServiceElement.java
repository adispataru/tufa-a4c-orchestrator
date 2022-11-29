package alien4cloud.tufa.model;

import java.util.ArrayList;
import java.util.List;

public class ResourcedServiceElement {
    private String serviceElementId;
    private String status;
    private String creatorId;
    private ResourceType resourceType;
    private String implementationType;
    private List<Resources> resources;


    public ResourcedServiceElement() {
	this.creatorId = "";
	this.serviceElementId = "";
	this.resources = new ArrayList<Resources>();
	this.resourceType = ResourceType.BAREMETAL;
	this.status = ProcessingStatus.PENDING.name();
    }

    /**
     * @return the serviceElementId
     */
    public String getServiceElementId() {
	return serviceElementId;
    }

    /**
     * @param serviceElementId
     *            the serviceElementId to set
     */
    public void setServiceElementId(String serviceElementId) {
	this.serviceElementId = serviceElementId;
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
     * @return the resources
     */
    public List<Resources> getResources() {
	return resources;
    }

    /**
     * @param resources
     *            the resources to set
     */
    public void setResources(List<Resources> resources) {
	this.resources = resources;
    }

    /**
     * @return the creatorId
     */
    public String getCreatorId() {
	return creatorId;
    }

    /**
     * @param creatorId
     *            the creatorId to set
     */
    public void setCreatorId(String creatorId) {
	this.creatorId = creatorId;
    }

    /**
     * @return the resourceType
     */
    public ResourceType getResourceType() {
	return resourceType;
    }

    /**
     * @param resourceType
     *            the resourceType to set
     */
    public void setResourceType(ResourceType resourceType) {
	this.resourceType = resourceType;
    }

    public String getImplementationType() {
        return implementationType;
    }

    public void setImplementationType(String implementationType) {
        this.implementationType = implementationType;
    }


}
