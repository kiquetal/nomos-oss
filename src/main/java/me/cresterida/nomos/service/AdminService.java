package me.cresterida.nomos.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.cresterida.nomos.dto.CreateAccessRequest;
import me.cresterida.nomos.dto.CreateAppRequest;
import me.cresterida.nomos.dto.CreateIdpRequest;
import me.cresterida.nomos.dto.CreateProxyRequest;
import me.cresterida.nomos.dto.CreateRuleRequest;
import me.cresterida.nomos.dto.CreateRulesRequest;
import me.cresterida.nomos.exception.NomosException;

import java.util.ArrayList;
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

    public void linkAppIdp(String appId, String idpName, String audience, String label) {
        log.info("Linking App '{}' to IDP '{}' with audience '{}', label='{}'", appId, idpName, audience, label);
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                Result appCheck = tx.run("MATCH (a:App {appId: $appId}) RETURN a", Values.parameters("appId", appId));
                if (!appCheck.hasNext()) {
                    throw new NomosException("APP_NOT_FOUND", "App '" + appId + "' does not exist");
                }
                Result idpCheck = tx.run("MATCH (i:IDP {name: $name}) RETURN i", Values.parameters("name", idpName));
                if (!idpCheck.hasNext()) {
                    throw new NomosException("IDP_NOT_FOUND", "IDP '" + idpName + "' does not exist");
                }
                tx.run(
                    "MATCH (a:App {appId: $appId}) " +
                    "MATCH (i:IDP {name: $idpName}) " +
                    "MERGE (a)-[r:USES_IDP {audience: $audience}]->(i) " +
                    "SET r.label = $label " +
                    "RETURN a",
                    Values.parameters(
                            "appId", appId,
                            "idpName", idpName,
                            "audience", audience,
                            "label", label != null ? label : ""
                    )
                ).consume();
                return null;
            });
        }
    }

    public void updateIdpIssuer(String idpName, String issuer) {
        log.info("Updating IDP '{}' issuer to '{}'", idpName, issuer);
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                    "MATCH (i:IDP {name: $idpName}) " +
                    "SET i.issuer = $issuer " +
                    "RETURN i",
                    Values.parameters("idpName", idpName, "issuer", issuer)
            ).consume());
        }
    }

    public void updateAudienceLabel(String appId, String idpName, String audience, String label) {
        log.info("Updating label for audience '{}' on App '{}' IDP '{}' to '{}'", audience, appId, idpName, label);
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                    "MATCH (a:App {appId: $appId})-[r:USES_IDP {audience: $aud}]->(i:IDP {name: $idpName}) " +
                    "SET r.label = $label " +
                    "RETURN r",
                    Values.parameters("appId", appId, "aud", audience, "idpName", idpName, "label", label)
            ).consume());
        }
    }

    public void deleteApp(String appId) {
        log.info("Deleting App: appId='{}'", appId);
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                    "MATCH (a:App {appId: $appId}) " +
                    "DETACH DELETE a",
                    Values.parameters("appId", appId)
            ).consume());
        }
    }

    public void deleteAudience(String appId, String idpName, String audience) {
        log.info("Deleting audience '{}' for App '{}' on IDP '{}'", audience, appId, idpName);
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                // Delete USES_IDP relationship
                tx.run(
                        "MATCH (a:App {appId: $appId})-[r:USES_IDP {audience: $aud}]->(i:IDP {name: $idpName}) " +
                        "DELETE r",
                        Values.parameters("appId", appId, "aud", audience, "idpName", idpName)
                ).consume();
                // Delete ACCESS_PROXY relationships with this audience AND idp
                tx.run(
                        "MATCH (a:App {appId: $appId})-[acc:ACCESS_PROXY {audience: $aud, idp: $idp}]->() " +
                        "DELETE acc",
                        Values.parameters("appId", appId, "aud", audience, "idp", idpName)
                ).consume();
                return null;
            });
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
        log.info("Creating ACCESS_PROXY: appId='{}', proxyName='{}', audience='{}', idp='{}'",
                req.appId(), req.proxyName(), req.audience(), req.idpName());
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                Result appCheck = tx.run("MATCH (a:App {appId: $appId}) RETURN a", Values.parameters("appId", req.appId()));
                if (!appCheck.hasNext()) {
                    throw new NomosException("APP_NOT_FOUND", "App '" + req.appId() + "' does not exist");
                }
                Result proxyCheck = tx.run("MATCH (p:APIProxy {name: $name}) RETURN p", Values.parameters("name", req.proxyName()));
                if (!proxyCheck.hasNext()) {
                    throw new NomosException("PROXY_NOT_FOUND", "Proxy '" + req.proxyName() + "' does not exist");
                }
                Result idpCheck = tx.run("MATCH (i:IDP {name: $name}) RETURN i", Values.parameters("name", req.idpName()));
                if (!idpCheck.hasNext()) {
                    throw new NomosException("IDP_NOT_FOUND", "IDP '" + req.idpName() + "' does not exist");
                }
                Result audCheck = tx.run(
                        "MATCH (a:App {appId: $appId})-[:USES_IDP {audience: $aud}]->(i:IDP {name: $idp}) RETURN a",
                        Values.parameters("appId", req.appId(), "aud", req.audience(), "idp", req.idpName()));
                if (!audCheck.hasNext()) {
                    throw new NomosException("AUDIENCE_NOT_REGISTERED",
                            "Audience '" + req.audience() + "' is not registered for app '" + req.appId() + "' on IDP '" + req.idpName() + "'");
                }
                tx.run(
                    "MATCH (a:App {appId: $appId}) " +
                    "MATCH (p:APIProxy {name: $proxyName}) " +
                    "MERGE (a)-[:ACCESS_PROXY {audience: $audience, idp: $idp}]->(p) " +
                    "RETURN a, p",
                    Values.parameters(
                            "appId", req.appId(),
                            "proxyName", req.proxyName(),
                            "audience", req.audience(),
                            "idp", req.idpName()
                    )
                ).consume();
                return null;
            });
        }
    }

    public String createRule(CreateRuleRequest req) {
        String ruleId = UUID.randomUUID().toString();
        log.info("Creating Rule: id='{}', proxy='{}', idp='{}', pathPattern='{}'",
                ruleId, req.proxyName(), req.idpName(), req.pathPattern());

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                Result proxyCheck = tx.run("MATCH (p:APIProxy {name: $name}) RETURN p", Values.parameters("name", req.proxyName()));
                if (!proxyCheck.hasNext()) {
                    throw new NomosException("PROXY_NOT_FOUND", "Proxy '" + req.proxyName() + "' does not exist");
                }
                Result idpCheck = tx.run("MATCH (i:IDP {name: $name}) RETURN i", Values.parameters("name", req.idpName()));
                if (!idpCheck.hasNext()) {
                    throw new NomosException("IDP_NOT_FOUND", "IDP '" + req.idpName() + "' does not exist");
                }

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

    public List<String> createRules(CreateRulesRequest req) {
        log.info("Creating {} rules for proxy='{}', idp='{}'", req.rules().size(), req.proxyName(), req.idpName());
        List<String> ruleIds = new ArrayList<>();

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                Result proxyCheck = tx.run("MATCH (p:APIProxy {name: $name}) RETURN p", Values.parameters("name", req.proxyName()));
                if (!proxyCheck.hasNext()) {
                    throw new NomosException("PROXY_NOT_FOUND", "Proxy '" + req.proxyName() + "' does not exist");
                }
                Result idpCheck = tx.run("MATCH (i:IDP {name: $name}) RETURN i", Values.parameters("name", req.idpName()));
                if (!idpCheck.hasNext()) {
                    throw new NomosException("IDP_NOT_FOUND", "IDP '" + req.idpName() + "' does not exist");
                }

                for (CreateRulesRequest.RuleItem rule : req.rules()) {
                    String ruleId = UUID.randomUUID().toString();
                    ruleIds.add(ruleId);

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
                                    "pathPattern", rule.pathPattern(),
                                    "methods", rule.methods()
                            )
                    ).consume();

                    // Create Validations (and optional Enrichments)
                    for (CreateRuleRequest.ValidationItem v : rule.validations()) {
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
                }
                return null;
            });
        }

        return ruleIds;
    }
}
