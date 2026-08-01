package me.cresterida.nomos.dto;

import java.util.List;

public record ProxyAccessExpandedResponse(
        String proxy,
        String defaultPolicy,
        List<RuleSummary> rules
) {
    public record RuleSummary(
            String pathPattern,
            List<String> methods
    ) {}
}
