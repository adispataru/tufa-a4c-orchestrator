package tufa.model.soe;

import java.util.List;

/**
 * Created by adrian on 22.03.2017.
 */
public class ServiceRequest {

    private String name;
    private String id;
    private String type;
    private List<ImplementationRequest> implementationList;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<ImplementationRequest> getImplementationList() {
        return implementationList;
    }

    public void setImplementationList(List<ImplementationRequest> implementationList) {
        this.implementationList = implementationList;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
