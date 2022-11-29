package alien4cloud.sampleplugin.services;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import javax.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

@Service
public class KubernetesService {

    private KubernetesClient client = null;


    @PostConstruct
    public void init(){
        try {
            String kubeConfigPath = ResourceUtils.getFile("classpath:conf/kube.conf").getAbsolutePath();
            System.setProperty("kubeconfig", kubeConfigPath);
            client = new DefaultKubernetesClient();
            System.out.println("Successfully created kubernetes client");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Config getDefaultConfig(){
        return client.getConfiguration();
    }


    public KubernetesClient externalClient(Config config){

        KubernetesClient client = new DefaultKubernetesClient(config);
        return client;

    }
//
    public List<Node> getNodes(){
        List<Node> result = new ArrayList<>();
        NodeList list = client.nodes().list();
        for (Node item : list.getItems()) {
            result.add(item);
        }
        return result;
    }
}
