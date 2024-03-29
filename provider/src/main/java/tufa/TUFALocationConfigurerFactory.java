package tufa;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.orchestrators.plugin.model.PluginArchive;

@Component
public class TUFALocationConfigurerFactory {
    @Inject
    private ApplicationContext applicationContext;

    public ILocationConfiguratorPlugin newInstance(String locationType) {

        if (Arrays.asList(TUFAOrchestratorFactory.TYPES).contains(locationType)) {
            return applicationContext. getBean(TUFALocationConfigurer.class);
        }
        return new ILocationConfiguratorPlugin() {
            @Override
            public List<PluginArchive> pluginArchives() {
                return Collections.emptyList();
            }

            @Override
            public List<String> getResourcesTypes() {
                return Collections.emptyList();
            }

            @Override
            public Map<String, MatchingConfiguration> getMatchingConfigurations() {
                return Collections.emptyMap();
            }

            @Override
            public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
                return Collections.emptyList();
            }
        };
    }
}
