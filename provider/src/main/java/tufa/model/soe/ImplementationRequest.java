package tufa.model.soe;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by adrian on 28.06.2017.
 */
public class ImplementationRequest {
    private String elementId;
    private String archiveVersion;
    private String id;
    private List<String> SOSMTypes;

    public ImplementationRequest(){
        SOSMTypes = new ArrayList<String>();
    }

    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    public String getArchiveVersion() {
        return archiveVersion;
    }

    public void setArchiveVersion(String archiveVersion) {
        this.archiveVersion = archiveVersion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getSOSMTypes() {
        return SOSMTypes;
    }

    public void setSOSMTypes(List<String> SOSMTypes) {
        this.SOSMTypes = SOSMTypes;
    }
}

