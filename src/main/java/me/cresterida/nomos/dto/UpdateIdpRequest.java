package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record UpdateIdpRequest(
        @NotBlank(message = "Issuer URL is required")
        @Schema(example = "https://new-issuer.example.com")
        String issuer
) {}
