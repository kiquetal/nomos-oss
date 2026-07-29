package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAppRequest(
        @NotBlank(message = "App ID is required") String appId
) {}
