package tufa.model.soe;


import com.google.gson.JsonObject;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import tufa.model.events.MarathonEventService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by adrian on 18.05.2023.
 */
public class SerranoApp {
    private List<Deployment> deployments;
    private Map<String, List<String>> dependencies;

    private Map<String, ConfigMap> configMaps;

    private Map<String, JsonObject> constraints;

    private Map<String, List<PersistentVolume>> volumes;
    private Map<String, List<PersistentVolumeClaim>> volumeClaims;
    private String dockerCFG;

    private Map<String, Map<String, Service>> services;
    private Map<String, Object> deployedApps;
    private String id   ;
    private String serranoUUID;

    public String getSerranoUUID() {
        return serranoUUID;
    }

    public void setSerranoUUID(String serranoUUID) {
        this.serranoUUID = serranoUUID;
    }

    public SerranoApp(){
        this.deployments = new ArrayList<>();
        this.dependencies = new HashMap<>();
        this.deployedApps = new HashMap<>();
        this.configMaps = new HashMap<>();
        this.volumes = new HashMap<>();
        this.services = new HashMap<>();
        this.volumeClaims = new HashMap<>();
        this.constraints = new HashMap<>();

    }


    public String getDockerCFG() {
        return dockerCFG;
    }

    public void setDockerCFG(String dockerCFG) {
        this.dockerCFG = dockerCFG;
    }

    public Map<String, List<PersistentVolumeClaim>> getVolumeClaims() {
        return volumeClaims;
    }

    public void setVolumeClaims(Map<String, List<PersistentVolumeClaim>> volumeClaims) {
        this.volumeClaims = volumeClaims;
    }

    public Map<String, ConfigMap> getConfigMaps() {
        return configMaps;
    }

    public void setConfigMaps(Map<String, ConfigMap> configMaps) {
        this.configMaps = configMaps;
    }

    public Map<String, List<PersistentVolume>> getVolumes() {
        return volumes;
    }

    public void setVolumes(Map<String, List<PersistentVolume>> volumes) {
        this.volumes = volumes;
    }

    public Map<String, Map<String, Service>> getServices() {
        return services;
    }

    public void setServices(Map<String, Map<String, Service>> services) {
        this.services = services;
    }

    public List<Deployment> getDeployments() {
        return deployments;
    }

    public void setDeployments(List<Deployment> deployments) {
        this.deployments = deployments;
    }

    public Map<String, List<String>> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Map<String, List<String>> dependencies) {
        this.dependencies = dependencies;
    }

    public Map<String, Object> getDeployedApps() {
        return deployedApps;
    }

    public void setDeployedApps(Map<String, Object> deployedApps) {
        this.deployedApps = deployedApps;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Map<String, JsonObject> getConstraints() {
        return constraints;
    }
}
