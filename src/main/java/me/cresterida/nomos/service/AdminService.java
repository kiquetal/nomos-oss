package me.cresterida.nomos.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.cresterida.nomos.dto.CreateAccessRequest;
import me.cresterida.nomos.dto.CreateAppRequest;
import me.cresterida.nomos.dto.CreateIdpRequest;
import me.cresterida.nomos.dto.CreateProxyRequest;
import me.cresterida.nomos.dto.CreateRuleRequest;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    @Inject
    Driver driver;

    public void createIdp(CreateIdpRequest req) {
        log.info("Creating IDP: name='{}', issuer='{}'", req.name(), req.issuer());
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                    "MERGE (i:IDP {name: $name}) " +
                    "ON CREATE SET i.issuer = $issuer " +
                    "ON MATCH SET i.issuer = $issuer " +
                    "RETURN i",
                    Values.parameters("name", req.name(), "issuer", req.issuer())
            ).consume());
        }
    }

    public void createApp(CreateAppRequest req) {
        log.info("Creating App: appId='{}'", req.appId());
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                    "MERGE (a:App {appId: $appId}) " +
                    "RETURN a",
                    Values.parameters("appId", req.appId())
            ).consume());
        }
    }

    public void linkAppIdp(String appId, String idpName, String audience) {
        log.info("Linking App '{}' to IDP '{}' with audience '{}'", appId, idpName, audience);
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                    "MATCH (a:App {appId: $appId}) " +
                    "MATCH (i:IDP {name: $idpName}) " +
                    "MERGE (a)-[:USES_IDP {audience: $audience}]->(i) " +
                    "RETURN a",
                    Values.parameters(
                            "appId", appId,
                            "idpName", idpName,
                            "audience", audience
                    )
            ).consume());
        }
    }

    public void createProxy(CreateProxyRequest req) {
        log.info("Creating APIProxy: name='{}', defaultPolicy='{}'", req.name(), req.defaultPolicy());
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                    "MERGE (p:APIProxy {name: $name}) " +
                    "ON CREATE SET p.defaultPolicy = $defaultPolicy " +
                    "ON MATCH SET p.defaultPolicy = $defaultPolicy " +
                    "RETURN p",
                    Values.parameters("name", req.name(), "defaultPolicy", req.defaultPolicy())
            ).consume());
        }
    }

    public void createAccess(CreateAccessRequest req) {
        log.info("Creating ACCESS_PROXY: appId='{}', proxyName='{}', audience='{}'",
                req.appId(), req.proxyName(), req.audience());
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                    "MATCH (a:App {appId: $appId}) " +
                    "MATCH (p:APIProxy {name: $proxyName}) " +
                    "MERGE (a)-[:ACCESS_PROXY {audience: $audience}]->(p) " +
                    "RETURN a, p",
                    Values.parameters(
                            "appId", req.appId(),
                            "proxyName", req.proxyName(),
                            "audience", req.audience()
                    )
            ).consume());
        }
    }

    public String createRule(CreateRuleRequest req) {
        String ruleId = UUID.randomUUID().toString();
        log.info("Creating Rule: id='{}', proxy='{}', idp='{}', pathPattern='{}'",
                ruleId, req.proxyName(), req.idpName(), req.pathPattern());

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                // Create Rule and link to Proxy + IDP
                tx.run(
                        "MATCH (p:APIProxy {name: $proxyName}) " +
                        "MATCH (i:IDP {name: $idpName}) " +
                        "CREATE (r:Rule {id: $ruleId, pathPattern: $pathPattern, methods: $methods}) " +
                        "CREATE (p)-[:HAS_RULE]->(r) " +
                        "CREATE (r)-[:FOR_IDP]->(i) " +
                        "RETURN r",
                        Values.parameters(
                                "proxyName", req.proxyName(),
                                "idpName", req.idpName(),
                                "ruleId", ruleId,
                                "pathPattern", req.pathPattern(),
                                "methods", req.methods()
                        )
                ).consume();

                // Create Validations (and optional Enrichments)
                for (CreateRuleRequest.ValidationItem v : req.validations()) {
                    String validationId = UUID.randomUUID().toString();

                    tx.run(
                            "MATCH (r:Rule {id: $ruleId}) " +
                            "CREATE (v:Validation {id: $validationId, order: $order, level: $level, " +
                            "  paramName: $paramName, source: $source, jwtJsonPath: $jwtJsonPath, " +
                            "  validation: $validation, allowedValues: $allowedValues}) " +
                            "CREATE (r)-[:HAS_VALIDATION]->(v) " +
                            "RETURN v",
                            Values.parameters(
                                    "ruleId", ruleId,
                                    "validationId", validationId,
                                    "order", v.order(),
                                    "level", v.level(),
                                    "paramName", v.paramName(),
                                    "source", v.source(),
                                    "jwtJsonPath", v.jwtJsonPath() != null ? v.jwtJsonPath() : "",
                                    "validation", v.validation(),
                                    "allowedValues", v.allowedValues() != null ? v.allowedValues() : List.of()
                            )
                    ).consume();

                    // Create Enrichment if present
                    if (v.enrichment() != null) {
                        CreateRuleRequest.EnrichmentItem e = v.enrichment();
                        tx.run(
                                "MATCH (v:Validation {id: $validationId}) " +
                                "CREATE (e:Enrichment {conditionJsonPath: $condJsonPath, " +
                                "  conditionEquals: $condEquals, endpoint: $endpoint, " +
                                "  domainFrom: $domainFrom, responseJsonPath: $respJsonPath, " +
                                "  cacheTtlSeconds: $cacheTtl}) " +
                                "CREATE (v)-[:HAS_ENRICHMENT]->(e) " +
                                "RETURN e",
                                Values.parameters(
                                        "validationId", validationId,
                                        "condJsonPath", e.conditionJsonPath(),
                                        "condEquals", e.conditionEquals(),
                                        "endpoint", e.endpoint(),
                                        "domainFrom", e.domainFrom(),
                                        "respJsonPath", e.responseJsonPath(),
                                        "cacheTtl", e.cacheTtlSeconds()
                                )
                        ).consume();
                    }
                }

                return null;
            });
        }

        return ruleId;
    }
}
