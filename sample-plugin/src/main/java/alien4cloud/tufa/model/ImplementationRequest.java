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

package alien4cloud.tufa.model;

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

