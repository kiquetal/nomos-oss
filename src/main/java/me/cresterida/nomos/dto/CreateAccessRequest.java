package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record CreateAccessRequest(
        @NotBlank(message = "App ID is required")
        @Schema(example = "mobile-app-br")
        String appId,

        @NotBlank(message = "Proxy name is required")
        @Schema(example = "account-service")
        String proxyName,

        @NotBlank(message = "Audience is required")
        @Schema(example = "mobile-br-auth0-client")
        String audience
) {}
