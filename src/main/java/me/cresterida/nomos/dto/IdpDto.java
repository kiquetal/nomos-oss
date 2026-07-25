package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;

public class IdpDto {

    @NotBlank(message = "IDP name is required")
    private String name;

    @NotBlank(message = "IDP issuer URL is required")
    private String issuer;

    private String jwksUri;

    public IdpDto() {}

    public IdpDto(String name, String issuer, String jwksUri) {
        this.name = name;
        this.issuer = issuer;
        this.jwksUri = jwksUri;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }
}
