package me.cresterida.nomos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

public record CreateRulesRequest(
        @NotBlank(message = "Proxy name is required") @Schema(example = "account-service") String proxyName,
        @NotBlank(message = "IDP name is required") @Schema(example = "auth0") String idpName,
        @NotEmpty(message = "At least one rule is required") @Valid List<RuleItem> rules
) {

    public record RuleItem(
            @NotBlank(message = "Path pattern is required") @Schema(example = "/{country}/accounts/{msisdn}/balance") String pathPattern,
            @NotEmpty(message = "At least one HTTP method is required") @Schema(example = "[\"GET\"]") List<String> methods,
            @NotEmpty(message = "At least one validation is required") @Valid List<CreateRuleRequest.ValidationItem> validations
    ) {}
}
