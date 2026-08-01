package me.cresterida.nomos.dto;

import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record LinkAppIdpRequest(
        @NotBlank(message = "Audience is required") @Schema(example = "0mtgrpsd434324324") String audience,
        @Schema(example = "Mobile BR Production") String label
) {}
