package alien4cloud.sampleplugin.services;

import alien4cloud.tufa.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Quantity;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class ResourceManagerService {

    private static class Pair<K, V> {
        private K key;
        private V value;

        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public void setKey(K key) {
            this.key = key;
        }

        public V getValue() {
            return value;
        }

        public void setValue(V value) {
            this.value = value;
        }
    }

    @Resource
    KubernetesService kubeService;

    public BlueprintResourcedTemplate findResourcesForBlueprint(BlueprintResourceTemplate blueprint) {

        BlueprintResourcedTemplate result = new BlueprintResourcedTemplate();
        result.setBlueprintId(blueprint.getBlueprintId());
        result.setStatus("SUCCESSFUL");
        result.setResourcedServiceElements(new ArrayList<>());

        List<Node> nodes = kubeService.getNodes();

        // This is a greedy algorithm, scoring the best solution for each service
        //TODO Implement a DP solution
        for (ServiceElement se : blueprint.getServiceElements()) {
            ResourcedServiceElement rse = new ResourcedServiceElement();
            rse.setServiceElementId(se.getServiceElementId());
            rse.setCreatorId("tufa-sched-generated");
            rse.setResources(new ArrayList<>());

            Pair<Node, Implementation> selected = getBestNodeAndImplementation(nodes, se.getImplementations());

            if(selected == null){
                result.setStatus("FAILED");
                break;
            }

            String implType =  selected.getValue().getImplementationType();
            String[] implTokens = implType.split("_");


            if(implTokens[1].equals("CONTAINER")) {

                rse.setResourceType(ResourceType.KUBERNETES);
                rse.setImplementationType(implType);

                Resources res = new Resources();
                res.addRecommendation(selected.getKey().getMetadata().getName());
                ObjectMapper mapper = new ObjectMapper();
                try {
                    String kubeConfig = mapper.writeValueAsString(kubeService.getDefaultConfig());
                    res.setResourceDescriptor(kubeConfig);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
                rse.getResources().add(res);
            }
            rse.setStatus("COMPLETED");

            result.getResourcedServiceElements().add(rse);


        }

        return result;
    }

    private Pair<Node, Implementation> getBestNodeAndImplementation(List<Node> nodes, List<Implementation> serviceImpl) {
        double[][] scores = new double[nodes.size()][serviceImpl.size()];
        int i = 0;
        for (Node node : nodes) {
            int j = 0;
            for (Implementation impl : serviceImpl) {
                scores[i][j] = computeScore(node, impl);
                j++;
            }
            i++;
        }
        double best = scores[0][0];
        Node selected = nodes.get(0);
        Implementation selectedImp = serviceImpl.get(0);
        for(i = 0; i < scores.length; i++){
            for(int j = 0; j < scores[i].length; j++) {
                if (best < scores[i][j]) {
                    best = scores[i][j];
                    selected = nodes.get(i);
                    selectedImp = serviceImpl.get(j);
                }
            }
        }
        if(best != -1)
            return new Pair<>(selected, selectedImp);
        return null;
    }

    private double computeScore(Node node, Implementation serviceImpl) {
        String[] tokens = serviceImpl.getImplementationType().split("_");
        String acc = tokens[0];
        String virt = tokens[1];
        double score  = 0;
        if(virt.equals("CONTAINER")) {
            score += 30;
        }
        if(acc.equals("GPU")){
            Quantity quantity = node.getStatus().getAllocatable().get("nvidia.com/gpu");
            if(quantity != null){
                int nodeGPUs = Integer.parseInt(quantity.getAmount());
                if (nodeGPUs < serviceImpl.getAcceleratorRange().get(0))
                    return -1;
                else
                    score += 100;
            }else {
                return -1;
            }


        }else if (acc.equals("MIC")){
            //TODO seems like XeonPhis will become obsolete, but hey..
//            int nodeMICs = Integer.parseInt(node.getStatus().getAllocatable().get("intel/mic").getAmount());
//            if (serviceImpl.getAcceleratorRange().get(0) < nodeMICs)
                return -1;
        }else if (acc.equals("FPGA")){
            //TODO check how FPGA's will be integrated in SERRANO
//            int nodeMICs = Integer.parseInt(node.getStatus().getAllocatable().get("fpga").getAmount());
//            if (serviceImpl.getAcceleratorRange().get(0) < nodeMICs)
                return -1;
        }

        int cpus = Integer.parseInt(node.getStatus().getAllocatable().get("cpu").getAmount());
        if(cpus <  serviceImpl.getComputationRange().get(0)){
            return -1;
        }

        score += 10 * cpus;


        double memory = Double.parseDouble(node.getStatus().getAllocatable().get("memory").getAmount());
        memory /= 1024;
//        memory /= 1024;
        if(memory <  serviceImpl.getMemoryRange().get(0)){
            return -1;
        }

        score += memory;

        return score;
    }

}
