package tufa;

import alien4cloud.deployment.matching.services.nodes.MatchingConfigurations;
import alien4cloud.deployment.matching.services.nodes.MatchingConfigurationsParser;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.deployment.matching.MatchingConfiguration;
import alien4cloud.model.orchestrators.locations.LocationResourceTemplate;
import alien4cloud.orchestrators.locations.services.LocationResourceGeneratorService;
import alien4cloud.orchestrators.plugin.ILocationConfiguratorPlugin;
import alien4cloud.orchestrators.plugin.ILocationResourceAccessor;
import alien4cloud.orchestrators.plugin.model.PluginArchive;
import alien4cloud.plugin.model.ManagedPlugin;
import alien4cloud.topology.TopologyServiceCore;
import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.utils.AlienConstants;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.alien4cloud.tosca.catalog.ArchiveParser;
import org.alien4cloud.tosca.model.CSARDependency;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.tosca.utils.NodeTemplateUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static alien4cloud.tosca.topology.TemplateBuilder.buildNodeTemplate;

@Slf4j
@Component
public class TUFALocationConfigurer implements ILocationConfiguratorPlugin {

    private ArchiveParser archiveParser;
    private MatchingConfigurationsParser matchingConfigurationsParser;
    private ManagedPlugin selfContext;
    private TopologyServiceCore topologyService;

    private List<PluginArchive> archives;

    @Autowired
    public TUFALocationConfigurer(ArchiveParser archiveParser, MatchingConfigurationsParser matchingConfigurationsParser, ManagedPlugin selfContext, TopologyServiceCore topologyService) {
        this.archiveParser = archiveParser;
        this.matchingConfigurationsParser = matchingConfigurationsParser;
        this.selfContext = selfContext;
        this.topologyService = topologyService;
        this.archives = parseArchives();
    }

    private List<PluginArchive> parseArchives() {
        List<PluginArchive> archives = Lists.newArrayList();
//        addToAchive(archives, "brooklyn/types");
        return archives;
    }

    private void addToAchive(List<PluginArchive> archives, String path) {
        Path archivePath = selfContext.getPluginPath().resolve(path);
        // Parse the archives
        try {
            ParsingResult<ArchiveRoot> result = archiveParser.parseDir(archivePath, AlienConstants.GLOBAL_WORKSPACE_ID);
            PluginArchive pluginArchive = new PluginArchive(result.getResult(), archivePath);
            archives.add(pluginArchive);
        } catch(ParsingException e) {
            log.error("Failed to parse archive, plugin won't work as expected", e);
        }
    }

    @Override
    public List<PluginArchive> pluginArchives() {
        return archives;
    }

    @Override
    public List<String> getResourcesTypes() {
        return Collections.singletonList("tufa.nodes.DataBroker");
    }

    @Override
    public Map<String, MatchingConfiguration> getMatchingConfigurations() {
        Path matchingConfigPath = selfContext.getPluginPath().resolve("serrano/serrano-matching-config.yaml");
        MatchingConfigurations matchingConfigurations;
        try {
            matchingConfigurations = matchingConfigurationsParser.parseFile(matchingConfigPath).getResult();
        } catch(ParsingException e) {
            return Maps.newHashMap();
        }
        return matchingConfigurations.getMatchingConfigurations();
    }

    @Override
    public List<LocationResourceTemplate> instances(ILocationResourceAccessor resourceAccessor) {
//        String elementType = "serrano.nodes.DataBroker";

//        Set<CSARDependency> dependencies = resourceAccessor.getDependencies();
//        computeContext.setGeneratedNamePrefix(null);
//
//        try {
//            NodeType nodeType = resourceAccessor.getIndexedToscaElement(elementType);
//            computeContext.getNodeTypes().add(nodeType);
//        } catch (NotFoundException e) {
//            log.warn("No compute found with the element id: " + elementType, e);
//        }

        LocationResourceGeneratorService.ComputeContext computeContext = new LocationResourceGeneratorService.ComputeContext();
        List<LocationResourceTemplate> generated = Lists.newArrayList();

        for (NodeType indexedNodeType : computeContext.getNodeTypes()) {
            String name = StringUtils.isNotBlank(computeContext.getGeneratedNamePrefix()) ? computeContext.getGeneratedNamePrefix()
                    : "SERRANO_DEFAULT_BROKER_NAME";
            NodeTemplate node =  buildNodeTemplate(indexedNodeType);

            LocationResourceTemplate resource = new LocationResourceTemplate();
            resource.setService(false);
            resource.setTemplate(node);
            resource.setName(name);

            generated.add(resource);
        }
        return generated;
    }
}
