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
@FormProperties({ "serrano", "aiURL", "roURL", "telemetryURL", "kube", "kubeURL", "cacertData", "kubeToken", "kubeNamespace"})
public class Configuration {

    @FormLabel("SERRANO Infrastructure")
    @FormPropertyDefinition(type = "boolean")
    private Boolean serrano;

    @FormLabel("AI-SO URL")
//    @FormPropertyConstraint(pattern = "https\\:.+(?:\\d+)")
//    @NotNull
    private String aiURL;

    @FormLabel("Resource Orc. URL")
    private String roURL;

    @FormLabel("Telemetry URL")
    private String telemetryURL;

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
