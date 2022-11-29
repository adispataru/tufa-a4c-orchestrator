package tufa.model.soe;


import mesosphere.marathon.client.model.v2.Group;
import tufa.model.events.MarathonEventService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by adrian on 10.10.2017.
 */
public class SOEGroup {
    private List<Group> marathonServices;
    private Map<String, Object> brooklynServices;
    private Map<String, List<String>> dependencies;
    private Map<String, CustomMarathon> marathonClients;
    private Map<String, MarathonEventService> marathonEvents;
    private Map<String, String> brooklynLocation;
    private Map<String, Object> deployedApps;

    public SOEGroup(){
        this.marathonServices = new ArrayList<>();
        this.brooklynServices = new HashMap<>();
        this.dependencies = new HashMap<>();
        marathonClients = new HashMap<>();
        brooklynLocation = new HashMap<>();
        this.marathonEvents = new HashMap<>();
        this.deployedApps = new HashMap<>();

    }

    public List<Group> getMarathonServices() {
        return marathonServices;
    }

    public void setMarathonServices(List<Group> marathonServices) {
        this.marathonServices = marathonServices;
    }

    public Map<String, Object> getBrooklynServices() {
        return brooklynServices;
    }

    public void setBrooklynServices(Map<String, Object> brooklynServices) {
        this.brooklynServices = brooklynServices;
    }

    public Map<String, List<String>> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Map<String, List<String>> dependencies) {
        this.dependencies = dependencies;
    }

    public Map<String, CustomMarathon> getMarathonClients() {
        return marathonClients;
    }

    public void setMarathonClients(Map<String, CustomMarathon> marathonClients) {
        this.marathonClients = marathonClients;
    }

    public Map<String, String> getBrooklynLocation() {
        return brooklynLocation;
    }

    public void setBrooklynLocation(Map<String, String> brooklynLocation) {
        this.brooklynLocation = brooklynLocation;
    }

    public Map<String, MarathonEventService> getMarathonEvents() {
        return marathonEvents;
    }

    public void setMarathonEvents(Map<String, MarathonEventService> marathonEvents) {
        this.marathonEvents = marathonEvents;
    }

    public Map<String, Object> getDeployedApps() {
        return deployedApps;
    }

    public void setDeployedApps(Map<String, Object> deployedApps) {
        this.deployedApps = deployedApps;
    }
}
