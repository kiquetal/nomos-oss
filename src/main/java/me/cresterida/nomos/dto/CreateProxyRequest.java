package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateProxyRequest(
        @NotBlank(message = "Proxy name is required") String name,
        @NotBlank(message = "Default policy is required")
        @Pattern(regexp = "allow|deny", message = "Default policy must be 'allow' or 'deny'")
        String defaultPolicy
) {}
