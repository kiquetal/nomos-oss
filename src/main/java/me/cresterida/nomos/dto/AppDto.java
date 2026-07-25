package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;

public class AppDto {

    @NotBlank(message = "App ID is required")
    private String appId;

    @NotBlank(message = "App name is required")
    private String name;

    @NotBlank(message = "Associated IDP issuer is required")
    private String idpIssuer;

    public AppDto() {}

    public AppDto(String appId, String name, String idpIssuer) {
        this.appId = appId;
        this.name = name;
        this.idpIssuer = idpIssuer;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdpIssuer() {
        return idpIssuer;
    }

    public void setIdpIssuer(String idpIssuer) {
        this.idpIssuer = idpIssuer;
    }
}
