package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateIdpRequest(
        @NotBlank(message = "IDP name is required") String name,
        @NotBlank(message = "Issuer URL is required") String issuer
) {}
