package tufa;

import javax.validation.constraints.NotNull;

import alien4cloud.ui.form.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FormProperties({ "aiURL", "kube", "kubeURL", "cacertData", "kubeToken", "kubeNamespace"})
public class Configuration {

    @FormLabel("AI-Enhanced Service Orchestrator URL")
    @FormPropertyConstraint(pattern = "https\\:.+(?:\\d+)")
    @NotNull
    private String aiURL;

    @FormLabel("Kubernetes configuration (optional)")
    @FormPropertyDefinition(type = "boolean")
    private Boolean kube;

    @FormLabel("URL")
    private String kubeURL;

    @FormLabel("CA cert data")
    private String cacertData;

    @FormLabel("Token")
    private String kubeToken;

    @FormLabel("Namespace (optional)")
    private String kubeNamespace;

}
