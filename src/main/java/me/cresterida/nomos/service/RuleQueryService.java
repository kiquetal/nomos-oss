package me.cresterida.nomos.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.cresterida.nomos.dto.RuleSetResponse;
import me.cresterida.nomos.dto.AppAudienceResponse;
import me.cresterida.nomos.dto.ProxyAccessResponse;
import me.cresterida.nomos.dto.RuleSetResponse.ConditionDto;
import me.cresterida.nomos.dto.RuleSetResponse.EnrichmentDto;
import me.cresterida.nomos.dto.RuleSetResponse.RuleDto;
import me.cresterida.nomos.dto.RuleSetResponse.ValidationDto;
import me.cresterida.nomos.exception.NomosException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RuleQueryService {

    private static final Logger log = LoggerFactory.getLogger(RuleQueryService.class);

    @Inject
    Driver driver;

    public List<String> getAppsByAudienceAndIssuer(String audience, String issuer) {
        log.info("Fetching apps for audience='{}', issuer='{}'", audience, issuer);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(
                        "MATCH (a:App)-[:USES_IDP {audience: $aud}]->(i:IDP {issuer: $issuer}) " +
                        "RETURN a.appId AS appId",
                        Values.parameters("aud", audience, "issuer", issuer)
                );
                List<String> apps = new ArrayList<>();
                while (result.hasNext()) {
                    apps.add(result.next().get("appId").asString());
                }
                return apps;
            });
        }
    }

    public List<AppAudienceResponse> getAudiencesByApp(String appId) {
        log.info("Fetching audiences for appId='{}'", appId);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(
                        "MATCH (a:App {appId: $appId})-[r:USES_IDP]->(i:IDP) " +
                        "RETURN r.audience AS audience, i.name AS idp",
                        Values.parameters("appId", appId)
                );
                List<AppAudienceResponse> audiences = new ArrayList<>();
                while (result.hasNext()) {
                    Record rec = result.next();
                    audiences.add(new AppAudienceResponse(
                            rec.get("audience").asString(),
                            rec.get("idp").asString()
                    ));
                }
                return audiences;
            });
        }
    }

    public List<Map<String, String>> getAppsByProxy(String proxyName) {
        log.info("Fetching apps for proxy='{}'", proxyName);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(
                        "MATCH (a:App)-[r:ACCESS_PROXY]->(p:APIProxy {name: $proxyName}) " +
                        "RETURN a.appId AS appId, r.audience AS audience",
                        Values.parameters("proxyName", proxyName)
                );
                List<Map<String, String>> apps = new ArrayList<>();
                while (result.hasNext()) {
                    Record rec = result.next();
                    apps.add(Map.of(
                            "appId", rec.get("appId").asString(),
                            "audience", rec.get("audience").asString()
                    ));
                }
                return apps;
            });
        }
    }

    public List<ProxyAccessResponse> getProxiesByAppAndAudience(String appId, String audience) {
        log.info("Fetching proxies for appId='{}', audience='{}'", appId, audience);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(
                        "MATCH (a:App {appId: $appId})-[:ACCESS_PROXY {audience: $aud}]->(p:APIProxy) " +
                        "RETURN p.name AS proxy, p.defaultPolicy AS defaultPolicy",
                        Values.parameters("appId", appId, "aud", audience)
                );
                List<ProxyAccessResponse> proxies = new ArrayList<>();
                while (result.hasNext()) {
                    Record rec = result.next();
                    proxies.add(new ProxyAccessResponse(
                            rec.get("proxy").asString(),
                            rec.get("defaultPolicy").asString()
                    ));
                }
                return proxies;
            });
        }
    }

    public List<String> getAudiencesByIdp(String idpName) {
        log.info("Fetching all audiences for IDP: {}", idpName);
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run(
                        "MATCH (a:App)-[r:USES_IDP]->(i:IDP {name: $idpName}) " +
                        "RETURN r.audience AS audience",
                        Values.parameters("idpName", idpName)
                );
                List<String> audiences = new ArrayList<>();
                while (result.hasNext()) {
                    audiences.add(result.next().get("audience").asString());
                }
                return audiences;
            });
        }
    }

    public RuleSetResponse getRules(String proxy, String aud, String issuer) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {

                // ─── Step 1+2: Resolve aud + issuer → App with access to this proxy ───
                // Multiple apps may share the same audience+IDP.
                // We don't care which app — we care if ANY app with this audience can reach the proxy.
                Result access = tx.run(
                        "MATCH (a:App)-[:USES_IDP {audience: $aud}]->(i:IDP {issuer: $issuer}) " +
                        "MATCH (a)-[:ACCESS_PROXY {audience: $aud}]->(p:APIProxy {name: $proxy}) " +
                        "RETURN a.appId AS appId, i.name AS idp, p.defaultPolicy AS defaultPolicy " +
                        "LIMIT 1",
                        Values.parameters("aud", aud, "issuer", issuer, "proxy", proxy)
                );

                if (!access.hasNext()) {
                    // Distinguish between unknown audience vs proxy not allowed
                    Result check = tx.run(
                            "MATCH (a:App)-[:USES_IDP {audience: $aud}]->(i:IDP {issuer: $issuer}) " +
                            "RETURN a.appId AS appId LIMIT 1",
                            Values.parameters("aud", aud, "issuer", issuer)
                    );
                    if (!check.hasNext()) {
                        log.warn("Unknown audience: '{}' with issuer '{}'", aud, issuer);
                        throw new NomosException("UNKNOWN_AUDIENCE",
                                "Audience '" + aud + "' is not registered");
                    }
                    log.warn("Audience '{}' does not have access to proxy '{}'", aud, proxy);
                    throw new NomosException("PROXY_NOT_ALLOWED",
                            "Audience '" + aud + "' does not have access to proxy '" + proxy + "'");
                }

                Record rec = access.next();
                String appId = rec.get("appId").asString();
                String idp = rec.get("idp").asString();
                String defaultPolicy = rec.get("defaultPolicy").asString();

                // ─── Step 3: Get rules for this proxy + IDP (may be empty) ───
                Result step3 = tx.run(
                        "MATCH (a:App {appId: $appId})-[:ACCESS_PROXY {audience: $aud}]->(p:APIProxy {name: $proxy}) " +
                        "OPTIONAL MATCH (p)-[:HAS_RULE]->(r:Rule)-[:FOR_IDP]->(i:IDP {name: $idp}) " +
                        "OPTIONAL MATCH (r)-[:HAS_VALIDATION]->(v:Validation) " +
                        "OPTIONAL MATCH (v)-[:HAS_ENRICHMENT]->(e:Enrichment) " +
                        "RETURN r.id AS ruleId, r.pathPattern AS pathPattern, r.methods AS methods, " +
                        "       v.order AS vOrder, v.level AS vLevel, v.paramName AS paramName, " +
                        "       v.source AS vSource, v.jwtJsonPath AS jwtJsonPath, v.validation AS validation, " +
                        "       v.allowedValues AS allowedValues, " +
                        "       e.conditionJsonPath AS condJsonPath, e.conditionEquals AS condEquals, " +
                        "       e.endpoint AS enrichEndpoint, e.domainFrom AS domainFrom, " +
                        "       e.responseJsonPath AS respJsonPath, e.cacheTtlSeconds AS cacheTtl " +
                        "ORDER BY r.id, v.order",
                        Values.parameters("appId", appId, "aud", aud, "proxy", proxy, "idp", idp)
                );

                List<RuleDto> rules = buildRules(step3);

                log.info("Resolved rules for aud='{}', proxy='{}': appId='{}', idp='{}', defaultPolicy='{}', rules={}",
                        aud, proxy, appId, idp, defaultPolicy, rules.size());

                return new RuleSetResponse(proxy, appId, idp, defaultPolicy, rules);
            });
        }
    }

    private List<RuleDto> buildRules(Result result) {
        // Group by ruleId, preserving insertion order
        Map<String, RuleDto> ruleMap = new LinkedHashMap<>();

        while (result.hasNext()) {
            Record rec = result.next();

            // OPTIONAL MATCH may return nulls when no rules exist
            if (rec.get("ruleId").isNull()) {
                continue;
            }

            String ruleId = rec.get("ruleId").asString();
            String pathPattern = rec.get("pathPattern").asString();
            List<String> methods = rec.get("methods").isNull() ?
                    List.of() : rec.get("methods").asList(v -> v.asString());

            RuleDto rule = ruleMap.computeIfAbsent(ruleId,
                    id -> new RuleDto(id, pathPattern, methods, new ArrayList<>()));

            // Validation may be null if rule has no validations
            if (rec.get("paramName").isNull()) {
                continue;
            }

            // Build enrichment (optional)
            EnrichmentDto enrichment = null;
            if (!rec.get("enrichEndpoint").isNull()) {
                ConditionDto condition = new ConditionDto(
                        rec.get("condJsonPath").asString(),
                        rec.get("condEquals").asBoolean()
                );
                enrichment = new EnrichmentDto(
                        condition,
                        rec.get("enrichEndpoint").asString(),
                        rec.get("domainFrom").asString(),
                        rec.get("respJsonPath").asString(),
                        rec.get("cacheTtl").asInt()
                );
            }

            ValidationDto validation = new ValidationDto(
                    rec.get("vOrder").asInt(),
                    rec.get("vLevel").asInt(),
                    rec.get("paramName").asString(),
                    rec.get("vSource").isNull() ? "path" : rec.get("vSource").asString(),
                    rec.get("jwtJsonPath").isNull() || rec.get("jwtJsonPath").asString().isEmpty() ? null : rec.get("jwtJsonPath").asString(),
                    rec.get("validation").asString(),
                    rec.get("allowedValues").isNull() || rec.get("allowedValues").asList().isEmpty() ? null : rec.get("allowedValues").asList(v -> v.asString()),
                    enrichment
            );

            rule.getValidations().add(validation);
        }

        return new ArrayList<>(ruleMap.values());
    }
}
