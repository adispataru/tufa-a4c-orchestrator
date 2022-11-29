package tufa.model.soe;

/**
 * Created by adrian on 04.09.2017.
 */
public class OrchestratorRequest {
    private String name;
    private String pluginId;
    private String pluginBean;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginBean() {
        return pluginBean;
    }

    public void setPluginBean(String pluginBean) {
        this.pluginBean = pluginBean;
    }
}
