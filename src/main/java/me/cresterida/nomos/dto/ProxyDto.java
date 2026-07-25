package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;

public class ProxyDto {

    @NotBlank(message = "Proxy ID is required")
    private String proxyId;

    @NotBlank(message = "Proxy name is required")
    private String name;

    @NotBlank(message = "Path pattern is required (e.g. /outbox-api/)")
    private String pathPattern;

    public ProxyDto() {}

    public ProxyDto(String proxyId, String name, String pathPattern) {
        this.proxyId = proxyId;
        this.name = name;
        this.pathPattern = pathPattern;
    }

    public String getProxyId() {
        return proxyId;
    }

    public void setProxyId(String proxyId) {
        this.proxyId = proxyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }
}
