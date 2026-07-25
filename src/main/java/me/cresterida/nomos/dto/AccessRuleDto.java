package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class AccessRuleDto {

    @NotBlank(message = "App ID is required")
    private String appId;

    @NotBlank(message = "Proxy ID is required")
    private String proxyId;

    @NotEmpty(message = "At least one HTTP method must be allowed")
    private List<String> methods;

    public AccessRuleDto() {}

    public AccessRuleDto(String appId, String proxyId, List<String> methods) {
        this.appId = appId;
        this.proxyId = proxyId;
        this.methods = methods;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getProxyId() {
        return proxyId;
    }

    public void setProxyId(String proxyId) {
        this.proxyId = proxyId;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }
}
