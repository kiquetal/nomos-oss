package me.cresterida.nomos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

public record CreateRuleRequest(
        @NotBlank(message = "Proxy name is required") @Schema(example = "account-service") String proxyName,
        @NotBlank(message = "IDP name is required") @Schema(example = "auth0") String idpName,
        @NotBlank(message = "Path pattern is required") @Schema(example = "/{country}/accounts/{msisdn}/balance") String pathPattern,
        @NotEmpty(message = "At least one HTTP method is required") @Schema(example = "[\"GET\"]") List<String> methods,
        @NotEmpty(message = "At least one validation is required") @Valid List<ValidationItem> validations
) {

    public record ValidationItem(
            @Positive(message = "Order must be positive") int order,
            @Positive(message = "Level must be positive") int level,
            @NotBlank(message = "Param name is required") String paramName,
            @NotBlank(message = "Source is required (path or query)")
            @Pattern(regexp = "path|query", message = "Source must be 'path' or 'query'")
            String source,
            String jwtJsonPath,
            @NotBlank(message = "Validation type is required")
            @Pattern(regexp = "equals|contains|in", message = "Validation must be 'equals', 'contains', or 'in'")
            String validation,
            List<String> allowedValues,
            @Valid EnrichmentItem enrichment
    ) {}

    public record EnrichmentItem(
            @NotBlank(message = "Condition JsonPath is required") String conditionJsonPath,
            @NotNull(message = "Condition equals value is required") Boolean conditionEquals,
            @NotBlank(message = "Endpoint is required") String endpoint,
            @NotBlank(message = "Domain from is required") String domainFrom,
            @NotBlank(message = "Response JsonPath is required") String responseJsonPath,
            @Positive(message = "Cache TTL must be positive") int cacheTtlSeconds
    ) {}
}
