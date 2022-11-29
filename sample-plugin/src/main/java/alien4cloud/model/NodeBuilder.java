/*
 * Copyright 2017 Institute e-Austria Timisoara.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package alien4cloud.model;


import java.util.ArrayList;
import java.util.List;

/**
 * Created by adrian on 13.09.2017.
 */
public class NodeBuilder {
    private static final String version = ":1.0.0-SNAPSHOT";
    public static List<AdditionalOperation> basicContainer(String serviceName, int number){

        List<AdditionalOperation> result = new ArrayList<AdditionalOperation>();
        AdditionalOperation addNodeOp = new AdditionalOperation();
        addNodeOp.put("indexedNodeTypeId", "tufa.nodes.Compute.Container" + version);
        addNodeOp.put("nodeName", "DockerHost_"+number);
        addNodeOp.put("type", "org.alien4cloud.tosca.editor.operations.nodetemplate.AddNodeOperation");
        result.add(addNodeOp);


        AdditionalOperation rel = new AdditionalOperation();
        rel.put("nodeName", serviceName);
        rel.put("relationshipName", "hostedOn" + addNodeOp.get("nodeName") + "Host");
        rel.put("relationshipType", "tosca.relationships.HostedOn");
        rel.put("relationshipVersion","1.0.0-ALIEN20");
        rel.put("requirementName", "host");
        rel.put("requirementType", "tosca.capabilities.Container.Docker");
        rel.put("target", addNodeOp.get("nodeName"));
        rel.put("targetedCapabilityName", "host");
        rel.put("type", "org.alien4cloud.tosca.editor.operations.relationshiptemplate.AddRelationshipOperation");

        result.add(rel);
        return result;
    }

    public static List<AdditionalOperation> basicResource(String serviceName, int number){

        List<AdditionalOperation> result = new ArrayList<AdditionalOperation>();
        AdditionalOperation addNodeOp = new AdditionalOperation();
        addNodeOp.put("indexedNodeTypeId", "tufa.nodes.Compute" + version);
        addNodeOp.put("nodeName", "Compute_"+number);
        addNodeOp.put("type", "org.alien4cloud.tosca.editor.operations.nodetemplate.AddNodeOperation");
        result.add(addNodeOp);


        AdditionalOperation rel = new AdditionalOperation();
        rel.put("nodeName", serviceName);
        rel.put("relationshipName", "hostedOn" + addNodeOp.get("nodeName") + "Host");
        rel.put("relationshipType", "tosca.relationships.HostedOn");
        rel.put("relationshipVersion","1.0.0-ALIEN20");
        rel.put("requirementName", "host");
        rel.put("requirementType", "tosca.capabilities.Container");
        rel.put("target", addNodeOp.get("nodeName"));
        rel.put("targetedCapabilityName", "host");
        rel.put("type", "org.alien4cloud.tosca.editor.operations.relationshiptemplate.AddRelationshipOperation");

        result.add(rel);

        return result;
    }

    public static List<AdditionalOperation> basicMIC(String hostName, String serviceName, int number){
        List<AdditionalOperation> result = new ArrayList<AdditionalOperation>();
        AdditionalOperation addNodeOp = new AdditionalOperation();
        addNodeOp.put("indexedNodeTypeId", "tufa.nodes.MIC" + version);
        addNodeOp.put("nodeName", "MIC_"+number);
        addNodeOp.put("type", "org.alien4cloud.tosca.editor.operations.nodetemplate.AddNodeOperation");
        result.add(addNodeOp);


        AdditionalOperation rel = new AdditionalOperation();
        rel.put("nodeName", addNodeOp.get("nodeName"));
        rel.put("relationshipName", "mountAccelerator" + hostName + "Host");
        rel.put("relationshipType", "tufa.rel.Accelerator.AttachesTo");
        rel.put("relationshipVersion","1.0.0-SNAPSHOT");
        rel.put("requirementName", "attachment");
        rel.put("requirementType", "tufa.cap.AccAttachment");
        rel.put("target", hostName);
        rel.put("targetedCapabilityName", "attach");
        rel.put("type", "org.alien4cloud.tosca.editor.operations.relationshiptemplate.AddRelationshipOperation");
        result.add(rel);

        AdditionalOperation rel2 = new AdditionalOperation();
        rel2.put("nodeName", serviceName);
        rel2.put("relationshipName", "acceleratedBy" + addNodeOp.get("nodeName") + "Accelerator");
        rel2.put("relationshipType", "tufa.rel.AcceleratedByMIC");
        rel2.put("relationshipVersion","1.0.0-SNAPSHOT");
        rel2.put("requirementName", "accelerator");
        rel2.put("requirementType", "tufa.cap.MIC");
        rel2.put("target", addNodeOp.get("nodeName"));
        rel2.put("targetedCapabilityName", "accelerator");
        rel2.put("type", "org.alien4cloud.tosca.editor.operations.relationshiptemplate.AddRelationshipOperation");
        result.add(rel2);


//        addAccelerator(serviceName, result, rel2);

        return result;

    }

    private static void addAccelerator(String serviceName, List<AdditionalOperation> result, AdditionalOperation rel2) {
//        if(!"".equals(mountingPoint)) {
//            AdditionalOperation updateRel2 = new AdditionalOperation();
//            updateRel2.put("nodeName", serviceName);
//            updateRel2.put("propertyName", "host_path");
//            updateRel2.put("propertyValue", mountingPoint);
//
//            updateRel2.put("relationshipName", rel2.get("relationshipName"));
//            updateRel2.put("type", "org.alien4cloud.tosca.editor.operations.relationshiptemplate.UpdateRelationshipPropertyValueOperation");
//            result.add(updateRel2);
//        }
    }

    public static List<AdditionalOperation> basicGPU(String hostName, int number, String serviceName){

        List<AdditionalOperation> result = new ArrayList<AdditionalOperation>();
        AdditionalOperation addNodeOp = new AdditionalOperation();
        addNodeOp.put("indexedNodeTypeId", "tufa.nodes.GPU" + version);
        addNodeOp.put("nodeName", "GPU_"+number);
        addNodeOp.put("type", "org.alien4cloud.tosca.editor.operations.nodetemplate.AddNodeOperation");
        result.add(addNodeOp);


        AdditionalOperation rel = new AdditionalOperation();
        rel.put("nodeName", addNodeOp.get("nodeName"));
        rel.put("relationshipName", "mountAccelerator" + hostName + "Host");
        rel.put("relationshipType", "tufa.rel.Accelerator.AttachesTo");
        rel.put("relationshipVersion","1.0.0-SNAPSHOT");
        rel.put("requirementName", "attachment");
        rel.put("requirementType", "tufa.cap.AccAttachment");
        rel.put("target", hostName);
        rel.put("targetedCapabilityName", "attach");
        rel.put("type", "org.alien4cloud.tosca.editor.operations.relationshiptemplate.AddRelationshipOperation");
        result.add(rel);

        AdditionalOperation rel2 = new AdditionalOperation();
        rel2.put("nodeName", serviceName);
        rel2.put("relationshipName", "acceleratedBy" + addNodeOp.get("nodeName") + "Accelerator");
        rel2.put("relationshipType", "tufa.rel.AcceleratedByGPU");
        rel2.put("relationshipVersion","1.0.0-SNAPSHOT");
        rel2.put("requirementName", "accelerator");
        rel2.put("requirementType", "tufa.cap.AcceleratedByGPU");
        rel2.put("target", addNodeOp.get("nodeName"));
        rel2.put("targetedCapabilityName", "accelerator");
        rel2.put("type", "org.alien4cloud.tosca.editor.operations.relationshiptemplate.AddRelationshipOperation");
        result.add(rel2);

        addAccelerator(serviceName, result, rel2);


        return result;

    }

    public static List<AdditionalOperation> basicDFE(String hostName, int number, String serviceName){
        List<AdditionalOperation> result = new ArrayList<AdditionalOperation>();
        AdditionalOperation addNodeOp = new AdditionalOperation();
        addNodeOp.put("indexedNodeTypeId", "cloudlightning.nodes.DFE" + version);
        addNodeOp.put("nodeName", "DFE_"+number);
        addNodeOp.put("type", "org.alien4cloud.tosca.editor.operations.nodetemplate.AddNodeOperation");
        result.add(addNodeOp);


        AdditionalOperation rel = new AdditionalOperation();
        rel.put("nodeName", addNodeOp.get("nodeName"));
        rel.put("relationshipName", "mountAccelerator" + hostName + "Host");
        rel.put("relationshipType", "tufa.rel.Accelerator.AttachesTo");
        rel.put("relationshipVersion","1.0.0-SNAPSHOT");
        rel.put("requirementName", "attachment");
        rel.put("requirementType", "tufa.cap.AccAttachment");
        rel.put("target", hostName);
        rel.put("targetedCapabilityName", "attach");
        rel.put("type", "org.alien4cloud.tosca.editor.operations.relationshiptemplate.AddRelationshipOperation");
        result.add(rel);

        AdditionalOperation rel2 = new AdditionalOperation();
        rel2.put("nodeName", serviceName);
        rel2.put("relationshipName", "acceleratedBy" + addNodeOp.get("nodeName") + "Accelerator");
        rel2.put("relationshipType", "tufa.rel.AcceleratedByDFE");
        rel2.put("relationshipVersion","1.0.0-SNAPSHOT");
        rel2.put("requirementName", "accelerator");
        rel2.put("requirementType", "tufa.cap.AcceleratedByDFE");
        rel2.put("target", addNodeOp.get("nodeName"));
        rel2.put("targetedCapabilityName", "accelerator");
        rel2.put("type", "org.alien4cloud.tosca.editor.operations.relationshiptemplate.AddRelationshipOperation");
        result.add(rel2);

        return result;

    }
}
