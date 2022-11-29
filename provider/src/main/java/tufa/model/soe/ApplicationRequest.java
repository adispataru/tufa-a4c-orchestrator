package tufa.model.soe;

import java.util.List;

/**
 * Created by adrian on 06.09.2017.
 */
public class ApplicationRequest {
    private String name;
    private String version;
    private List<ServiceRequest> services;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ServiceRequest> getServices() {
        return services;
    }

    public void setServices(List<ServiceRequest> services) {
        this.services = services;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
