package alien4cloud.tufa.model;

import java.util.ArrayList;
import java.util.List;

public class Resources {
    private String resourceDescriptor;
    private String resourceCreationId;
    private List<String> recommendations;

    public Resources() {
	this.resourceDescriptor = "";
	this.resourceCreationId = "";
    this.recommendations = new ArrayList<>();
    }

    /**
     * @return the resourceDescriptor
     */
    public String getResourceDescriptor() {
	return resourceDescriptor;
    }

    /**
     * @param resourceDescriptor
     *            the resourceDescriptor to set
     */
    public void setResourceDescriptor(String resourceDescriptor) {
	this.resourceDescriptor = resourceDescriptor;
    }

    /**
     * @return the resourceCreationId
     */
    public String getResourceCreationId() {
	return resourceCreationId;
    }

    /**
     * @param resourceCreationId
     *            the resourceCreationId to set
     */
    public void setResourceCreationId(String resourceCreationId) {
	this.resourceCreationId = resourceCreationId;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public void addRecommendation(String rec){
        recommendations.add(rec);
    }
}
