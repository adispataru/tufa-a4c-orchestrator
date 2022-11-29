package tufa.model.soe;

//import eu.cloudlightning.bcri.blueprint.Resources;

/**
 * Created by adrian on 04.09.2017.
 */
public class ServiceResource {
//    List<Resources> resources;
    String resourceType;
    String creatorId;
    String locationId;

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

//    public List<Resources> getResources() {
//        return resources;
//    }

//    public void setResources(List<Resources> resources) {
//        this.resources = resources;
//    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }
}
