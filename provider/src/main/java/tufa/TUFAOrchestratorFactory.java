package tufa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.alien4cloud.tosca.model.definitions.PropertyDefinition;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import alien4cloud.model.orchestrators.ArtifactSupport;
import alien4cloud.model.orchestrators.locations.LocationSupport;
import alien4cloud.orchestrators.plugin.IOrchestratorPluginFactory;

@Slf4j
@Component("tufa-orchestrator-factory")
public class TUFAOrchestratorFactory implements IOrchestratorPluginFactory<TUFAOrchestrator, Configuration> {

    public static final String[] TYPES = {"SERRANO", "Kubernetes"};

    @Autowired
    private BeanFactory beanFactory;


//    @Override
//    public ASPIDEOrchestrator newInstance() {
//        ASPIDEOrchestrator instance = beanFactory.getBean(ASPIDEOrchestrator.class);
//        log.info("Init ASPIDE provider and beanFactory is {}", beanFactory);
//        return instance;
//    }

    @Override
    public TUFAOrchestrator newInstance(Configuration configuration) {
        TUFAOrchestrator instance = beanFactory.getBean(TUFAOrchestrator.class);
        log.info("Init TUFA provider and beanFactory is {}", beanFactory);
        return instance;
    }

    @Override
    public void destroy(TUFAOrchestrator instance) {
        log.info("DESTROYING (noop)", instance);
    }

    @Override
    public Class<Configuration> getConfigurationType() {
        return Configuration.class;
    }

    @Override
    public Configuration getDefaultConfiguration() {
        // List ordered lowest to highest priority.
        List<String> metadataProviders = new ArrayList<>();
//        MutableList.of(
//                ToscaMeta
//                DefaultToscaTypeProvider.class.getName(),
//                BrooklynToscaTypeProvider.class.getName());
        return new Configuration(true, "https://ai-enhanced-service-orchestrator.services.cloud.ict-serrano.eu/AISO/",
                "https://resource-orchestrator.services.cloud.ict-serrano.eu/api/v1",
                "http://85.120.206.26:30070/api/v1", false, null, null,  null, null);
    }

    @Override
    public LocationSupport getLocationSupport() {
        return new LocationSupport(true, TYPES);
    }

    @Override
    public ArtifactSupport getArtifactSupport() {
        return new ArtifactSupport();
    }

    @Override
    public Map<String, PropertyDefinition> getDeploymentPropertyDefinitions() {
        return null;
    }

    @Override
    public String getType() {
        return "Compute";
    }

}
