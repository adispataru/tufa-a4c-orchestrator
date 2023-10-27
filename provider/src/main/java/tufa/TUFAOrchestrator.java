package tufa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import alien4cloud.model.runtime.Execution;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import alien4cloud.model.orchestrators.locations.Location;
import alien4cloud.orchestrators.plugin.ILocationAutoConfigurer;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.IOrchestratorPlugin;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.model.PaaSDeploymentContext;
import lombok.extern.slf4j.Slf4j;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
public class TUFAOrchestrator extends TUFAProvider implements IOrchestratorPlugin<Configuration>, ILocationAutoConfigurer {

    @Inject
    private TUFALocationConfigurerFactory tufaLocationConfigurerFactory;

    @Override
    public ILocationConfiguratorPlugin getConfigurator(String locationType) {
        return tufaLocationConfigurerFactory.newInstance(locationType);
    }

    @Override
    public List<PluginArchive> pluginArchives() {

        return Collections.emptyList();
    }

    @Override
    public List<Location> getLocations() {
//        List<LocationSummary> locations = getNewBrooklynApi().getLocationApi().list();
        List<Location> newLocations = Lists.newArrayList();
        Location l = new Location();
        l.setName("SERRANO");
        l.setInfrastructureType("SERRANO");
        newLocations.add(l);
        Location l2 = new Location();
        l2.setName("Kubernetes");
        l2.setInfrastructureType("Kubernetes");
        newLocations.add(l2);
        return newLocations;
    }


    @Override
    public void setConfiguration(String s, Configuration configuration) throws PluginConfigurationException {
        super.configuration = configuration;
        super.serranoMappingService.setConfiguration(super.configuration);
    }



    @Override
    public Set<String> init(Map<String, String> activeDeployments) {
        useLocalContextClassLoader();
        try {
            log.info("INIT: " + activeDeployments);


            if(configuration.getKube()) {
                // Create a Kubernetes client configuration from the custom configuration file
//                Config config = Config.fromKubeconfig(kubeConfig);
                Config config = new ConfigBuilder().withCaCertData(configuration.getCacertData())
                        .withMasterUrl(configuration.getKubeURL())
                        .withUsername(configuration.getKubeUsername())
                        .withOauthToken(configuration.getKubeToken())
                        .withNamespace(configuration.getKubeNamespace()).build();

                // Initialize the Kubernetes client
                try {
                    kubeClient = new DefaultKubernetesClient(config);
                    // Now you can use the 'client' object to interact with your Kubernetes cluster
                    // For example, you can list pods, create resources, etc.
                    kubeClient.pods().list().getItems().forEach(pod -> {
                        System.out.println("Pod Name: " + pod.getMetadata().getName());
                    });
                    log.info("Successfully connected to Kubernetes cluster");
                } catch (Exception e) {
                    log.info("Could not initialize Kubernetes client, with provided credentials");
                }
            }

//            catalogMapper.addBaseTypes();

//            List<ToscaTypeProvider> metadataProviders = new LinkedList<>();
//            for (String providerClass : configuration.getProviders()) {
//                try {
//                    Object provider = beanFactory.getBean(Class.forName(providerClass));
//                    if(provider instanceof RequiresBrooklynApi) {
//                        ((RequiresBrooklynApi) provider).setBrooklynApi(getNewBrooklynApi());
//                    }
//                    // Alien UI has higher priority items at the end of the list.
//                    // Reverse the order here.
//                    metadataProviders.add(0, ToscaTypeProvider.class.cast(provider));
//                } catch (ClassNotFoundException e) {
//                    log.warn("Could not load metadata provider " + providerClass, e);
//                }
//            }

//            catalogMapper.mapBrooklynEntities(getNewBrooklynApi(), new ToscaMetadataProvider(metadataProviders));

        } finally {
            revertContextClassLoader();
        }
        return activeDeployments.keySet();
    }

    @Override
    public void update(PaaSTopologyDeploymentContext paaSTopologyDeploymentContext, IPaaSCallback<?> iPaaSCallback) {

    }

    @Override
    public void purge(PaaSDeploymentContext deploymentContext, IPaaSCallback<?> callback) {
        //TODO: Implement method
    }

    @Override
    public void resume(PaaSDeploymentContext deploymentContext, Execution execution, IPaaSCallback<?> callback) {
        //TODO: Implement method
    }

    @Override
    public void resetStep(PaaSDeploymentContext deploymentContext, Execution execution, String stepName, boolean done, IPaaSCallback<?> callback) {
        //TODO: Implement method
    }

    @Override
    public void launchWorkflow(PaaSDeploymentContext paaSDeploymentContext, String s, Map<String, Object> map, IPaaSCallback<String> iPaaSCallback) {
        //TODO: Implement method
    }

    @Override
    public void cancelTask(PaaSDeploymentContext deploymentContext, String taskId, IPaaSCallback<String> callback) {
        //TODO: Implement method
    }
}
