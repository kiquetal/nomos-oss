package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkAppIdpRequest(
        @NotBlank(message = "Audience is required") String audience
) {}
