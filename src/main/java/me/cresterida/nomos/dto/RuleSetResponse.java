package me.cresterida.nomos.dto;

import java.util.List;

public class RuleSetResponse {

    private String proxy;
    private String appId;
    private String idp;
    private String defaultPolicy;
    private List<RuleDto> rules;

    public RuleSetResponse() {}

    public RuleSetResponse(String proxy, String appId, String idp, String defaultPolicy, List<RuleDto> rules) {
        this.proxy = proxy;
        this.appId = appId;
        this.idp = idp;
        this.defaultPolicy = defaultPolicy;
        this.rules = rules;
    }

    public String getProxy() { return proxy; }
    public void setProxy(String proxy) { this.proxy = proxy; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getIdp() { return idp; }
    public void setIdp(String idp) { this.idp = idp; }

    public String getDefaultPolicy() { return defaultPolicy; }
    public void setDefaultPolicy(String defaultPolicy) { this.defaultPolicy = defaultPolicy; }

    public List<RuleDto> getRules() { return rules; }
    public void setRules(List<RuleDto> rules) { this.rules = rules; }

    // ─── Nested DTOs ───

    public static class RuleDto {
        private String id;
        private String pathPattern;
        private List<String> methods;
        private List<ValidationDto> validations;

        public RuleDto() {}

        public RuleDto(String id, String pathPattern, List<String> methods, List<ValidationDto> validations) {
            this.id = id;
            this.pathPattern = pathPattern;
            this.methods = methods;
            this.validations = validations;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getPathPattern() { return pathPattern; }
        public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }

        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> methods) { this.methods = methods; }

        public List<ValidationDto> getValidations() { return validations; }
        public void setValidations(List<ValidationDto> validations) { this.validations = validations; }
    }

    public static class ValidationDto {
        private int order;
        private int level;
        private String paramName;
        private String source;
        private String jwtJsonPath;
        private String validation;
        private List<String> allowedValues;
        private EnrichmentDto enrichment;

        public ValidationDto() {}

        public ValidationDto(int order, int level, String paramName, String source, String jwtJsonPath, String validation, List<String> allowedValues, EnrichmentDto enrichment) {
            this.order = order;
            this.level = level;
            this.paramName = paramName;
            this.source = source;
            this.jwtJsonPath = jwtJsonPath;
            this.validation = validation;
            this.allowedValues = allowedValues;
            this.enrichment = enrichment;
        }

        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }

        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }

        public String getParamName() { return paramName; }
        public void setParamName(String paramName) { this.paramName = paramName; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getJwtJsonPath() { return jwtJsonPath; }
        public void setJwtJsonPath(String jwtJsonPath) { this.jwtJsonPath = jwtJsonPath; }

        public String getValidation() { return validation; }
        public void setValidation(String validation) { this.validation = validation; }

        public List<String> getAllowedValues() { return allowedValues; }
        public void setAllowedValues(List<String> allowedValues) { this.allowedValues = allowedValues; }

        public EnrichmentDto getEnrichment() { return enrichment; }
        public void setEnrichment(EnrichmentDto enrichment) { this.enrichment = enrichment; }
    }

    public static class EnrichmentDto {
        private ConditionDto condition;
        private String endpoint;
        private String domainFrom;
        private String responseJsonPath;
        private int cacheTtlSeconds;

        public EnrichmentDto() {}

        public EnrichmentDto(ConditionDto condition, String endpoint, String domainFrom, String responseJsonPath, int cacheTtlSeconds) {
            this.condition = condition;
            this.endpoint = endpoint;
            this.domainFrom = domainFrom;
            this.responseJsonPath = responseJsonPath;
            this.cacheTtlSeconds = cacheTtlSeconds;
        }

        public ConditionDto getCondition() { return condition; }
        public void setCondition(ConditionDto condition) { this.condition = condition; }

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public String getDomainFrom() { return domainFrom; }
        public void setDomainFrom(String domainFrom) { this.domainFrom = domainFrom; }

        public String getResponseJsonPath() { return responseJsonPath; }
        public void setResponseJsonPath(String responseJsonPath) { this.responseJsonPath = responseJsonPath; }

        public int getCacheTtlSeconds() { return cacheTtlSeconds; }
        public void setCacheTtlSeconds(int cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }
    }

    public static class ConditionDto {
        private String jwtJsonPath;
        private boolean equals;

        public ConditionDto() {}

        public ConditionDto(String jwtJsonPath, boolean equals) {
            this.jwtJsonPath = jwtJsonPath;
            this.equals = equals;
        }

        public String getJwtJsonPath() { return jwtJsonPath; }
        public void setJwtJsonPath(String jwtJsonPath) { this.jwtJsonPath = jwtJsonPath; }

        public boolean isEquals() { return equals; }
        public void setEquals(boolean equals) { this.equals = equals; }
    }
}
