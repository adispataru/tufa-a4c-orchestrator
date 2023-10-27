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
@FormProperties({ "AIUrl", "dataBrokerUrl", "storageServiceUrl", "user", "password", "location", "kube", "kubeURL", "cacertData", "kubeUsername", "kubeToken", "kubeNamespace"})
public class Configuration {

    @FormLabel("AI-Enhanced Service Orchestrator URL")
    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String AIUrl;

    @FormLabel("Serrano Data Broker URL")
    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String dataBrokerUrl;

    @FormLabel("Serrano Storage Service URL")
    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String storageServiceUrl;

    @FormLabel("Serrano SDK User")
    private String user;

    @FormLabel("Serrano SDK Password")
    @FormPropertyDefinition(type = "string", isPassword = true)
    private String password;

    @FormLabel("Default Location")
    private String location;

    @FormLabel("Kubernetes configuration file (optional)")
    @FormPropertyDefinition(type = "boolean")
    private Boolean kube;

    @FormLabel("URL")
    private String kubeURL;

    @FormLabel("CA cert data")
    private String cacertData;

    @FormLabel("Username")
    private String kubeUsername;

    @FormLabel("Token")
    private String kubeToken;

    @FormLabel("Namespace")
    private String kubeNamespace;



//    @FormLabel("Tosca Metadata Providers")
//    private List<String> providers;
}
