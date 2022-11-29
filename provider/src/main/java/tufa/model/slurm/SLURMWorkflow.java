package tufa.model.slurm;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by adrian on 10.10.2019.
 */
public class SLURMWorkflow {
    private String id;
    private List<SLURMTask> tasks;
    private Map<String, List<String>> dependencies;
    private Map<String, TaskInfo> taskInformation;

    public SLURMWorkflow(String id, List<SLURMTask> tasks, Map<String, List<String>> dependencies, Map<String, TaskInfo> taskInformation) {
        this.id = id;
        this.tasks = tasks;
        this.dependencies = dependencies;
        this.taskInformation = taskInformation;
    }

    public SLURMWorkflow(){
        this.tasks = new ArrayList<>();
        this.dependencies = new HashMap<>();

        this.taskInformation = new HashMap<>();

    }

    public List<SLURMTask> getTasks() {
        return tasks;
    }

    public void setTasks(List<SLURMTask> tasks) {
        this.tasks = tasks;
    }

    public Map<String, List<String>> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Map<String, List<String>> dependencies) {
        this.dependencies = dependencies;
    }

    public Map<String, TaskInfo> getTaskInformation() {
        return taskInformation;
    }

    public void setTaskInformation(Map<String, TaskInfo> taskInformation) {
        this.taskInformation = taskInformation;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
