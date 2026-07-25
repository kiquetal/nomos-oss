package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;

public class ValidationRequest {

    @NotBlank(message = "Issuer (iss) is required")
    private String issuer;

    @NotBlank(message = "Audience/App ID (aud) is required")
    private String audience;

    @NotBlank(message = "Request path is required")
    private String path;

    @NotBlank(message = "HTTP method is required")
    private String method;

    public ValidationRequest() {}

    public ValidationRequest(String issuer, String audience, String path, String method) {
        this.issuer = issuer;
        this.audience = audience;
        this.path = path;
        this.method = method;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
