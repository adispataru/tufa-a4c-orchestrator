package alien4cloud.tufa.model;

import java.util.ArrayList;
import java.util.List;

public class ServiceElement {
    private String serviceElementId;
    private List<Implementation> implementations;

    public ServiceElement() {
	this.serviceElementId = "";
	this.implementations = new ArrayList<Implementation>();
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
     * @return the implementations
     */
    public List<Implementation> getImplementations() {
	return implementations;
    }

    /**
     * @param implementations
     *            the implementations to set
     */
    public void setImplementations(List<Implementation> implementations) {
	this.implementations = implementations;
    }
}
