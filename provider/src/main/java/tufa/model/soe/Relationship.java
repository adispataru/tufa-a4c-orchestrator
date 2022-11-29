package tufa.model.soe;

/**
 * Created by adrian on 13.09.2017.
 */
public class Relationship {
    private String nodeName;
    private String relationshipName;
    private String relationshipType;
    private String relationshipVersion;
    private String requirementName;
    private String requirementType;
    private String target;
    private String targetedCapabilityName;

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getRelationshipName() {
        return relationshipName;
    }

    public void setRelationshipName(String relationshipName) {
        this.relationshipName = relationshipName;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public String getRelationshipVersion() {
        return relationshipVersion;
    }

    public void setRelationshipVersion(String relationshipVersion) {
        this.relationshipVersion = relationshipVersion;
    }

    public String getRequirementName() {
        return requirementName;
    }

    public void setRequirementName(String requirementName) {
        this.requirementName = requirementName;
    }

    public String getRequirementType() {
        return requirementType;
    }

    public void setRequirementType(String requirementType) {
        this.requirementType = requirementType;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getTargetedCapabilityName() {
        return targetedCapabilityName;
    }

    public void setTargetedCapabilityName(String targetedCapabilityName) {
        this.targetedCapabilityName = targetedCapabilityName;
    }
}
