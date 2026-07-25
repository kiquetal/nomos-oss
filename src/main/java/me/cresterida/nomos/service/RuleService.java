package me.cresterida.nomos.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.cresterida.nomos.dto.*;

import java.util.List;

@ApplicationScoped
public class RuleService {

    private static final Logger log = LoggerFactory.getLogger(RuleService.class);

    @Inject
    Driver driver;

    public void createIdp(IdpDto idp) {
        log.info("Creating IDP: {} with Issuer: {}", idp.getName(), idp.getIssuer());
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                "MERGE (i:Idp {issuer: $issuer}) " +
                "ON CREATE SET i.name = $name, i.jwksUri = $jwksUri " +
                "ON MATCH SET i.name = $name, i.jwksUri = $jwksUri " +
                "RETURN i",
                Values.parameters(
                    "issuer", idp.getIssuer(),
                    "name", idp.getName(),
                    "jwksUri", idp.getJwksUri() != null ? idp.getJwksUri() : ""
                )
            ).consume());
        }
    }

    public void createApp(AppDto app) {
        log.info("Creating App: {} with AppId: {} on IDP Issuer: {}", app.getName(), app.getAppId(), app.getIdpIssuer());
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                // Ensure IDP exists, create App, and link App to IDP via AUTHENTICATED_BY relationship
                return tx.run(
                    "MATCH (i:Idp {issuer: $idpIssuer}) " +
                    "MERGE (a:App {appId: $appId}) " +
                    "ON CREATE SET a.name = $name " +
                    "ON MATCH SET a.name = $name " +
                    "MERGE (a)-[:AUTHENTICATED_BY]->(i) " +
                    "RETURN a",
                    Values.parameters(
                        "idpIssuer", app.getIdpIssuer(),
                        "appId", app.getAppId(),
                        "name", app.getName()
                    )
                ).consume();
            });
        }
    }

    public void createProxy(ProxyDto proxy) {
        log.info("Creating Proxy: {} with Path Pattern: {}", proxy.getProxyId(), proxy.getPathPattern());
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                "MERGE (p:Proxy {proxyId: $proxyId}) " +
                "ON CREATE SET p.name = $name, p.pathPattern = $pathPattern " +
                "ON MATCH SET p.name = $name, p.pathPattern = $pathPattern " +
                "RETURN p",
                Values.parameters(
                    "proxyId", proxy.getProxyId(),
                    "name", proxy.getName(),
                    "pathPattern", proxy.getPathPattern()
                )
            ).consume());
        }
    }

    public void createAccessRule(AccessRuleDto rule) {
        log.info("Creating Access Rule for App: {} -> Proxy: {} with Methods: {}", rule.getAppId(), rule.getProxyId(), rule.getMethods());
        try (Session session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                "MATCH (a:App {appId: $appId}) " +
                "MATCH (p:Proxy {proxyId: $proxyId}) " +
                "MERGE (a)-[r:CAN_ACCESS]->(p) " +
                "SET r.methods = $methods " +
                "RETURN r",
                Values.parameters(
                    "appId", rule.getAppId(),
                    "proxyId", rule.getProxyId(),
                    "methods", rule.getMethods()
                )
            ).consume());
        }
    }

    public ValidationResponse validateRequest(ValidationRequest req) {
        log.debug("Validating access request: Issuer: {}, Audience/AppId: {}, Path: {}, Method: {}",
                req.getIssuer(), req.getAudience(), req.getPath(), req.getMethod());

        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                // Find path starting with proxy.pathPattern
                Result result = tx.run(
                    "MATCH (idp:Idp {issuer: $issuer})<-[:AUTHENTICATED_BY]-(app:App {appId: $appId})-[r:CAN_ACCESS]->(p:Proxy) " +
                    "RETURN p.pathPattern AS pathPattern, r.methods AS methods, app.name AS appName",
                    Values.parameters(
                        "issuer", req.getIssuer(),
                        "appId", req.getAudience()
                    )
                );

                while (result.hasNext()) {
                    Record record = result.next();
                    String pathPattern = record.get("pathPattern").asString();
                    List<Object> methodsObj = record.get("methods").asList();
                    String appName = record.get("appName").asString();

                    // Perform routing & wildcard checks (e.g. /outbox-api/*)
                    if (isPathMatched(req.getPath(), pathPattern)) {
                        if (methodsObj.stream().anyMatch(m -> m.toString().equalsIgnoreCase(req.getMethod()))) {
                            log.info("Access GRANTED: App '{}' ({}) is authorized to {} {}", appName, req.getAudience(), req.getMethod(), req.getPath());
                            return new ValidationResponse(true, "Authorized by Nomos central rules.", req.getAudience());
                        }
                    }
                }

                log.warn("Access DENIED: No rules permit App ID '{}' on IDP '{}' to perform {} on {}",
                        req.getAudience(), req.getIssuer(), req.getMethod(), req.getPath());
                return new ValidationResponse(false, "No matching allowed rule found for this request context.", req.getAudience());
            });
        } catch (Exception e) {
            log.error("Error occurred while validating Nomos rule", e);
            return new ValidationResponse(false, "Internal Rules Engine Error: " + e.getMessage(), req.getAudience());
        }
    }

    private boolean isPathMatched(String actualPath, String pathPattern) {
        if (actualPath == null || pathPattern == null) {
            return false;
        }
        // Normalize slashes
        String actual = actualPath.trim();
        String pattern = pathPattern.trim();

        // Direct match
        if (actual.equalsIgnoreCase(pattern)) {
            return true;
        }

        // Wildcard match (e.g. /outbox-api/* matches /outbox-api/something)
        if (pattern.endsWith("*")) {
            String basePattern = pattern.substring(0, pattern.length() - 1);
            return actual.toLowerCase().startsWith(basePattern.toLowerCase());
        }

        // Prefix match
        return actual.toLowerCase().startsWith(pattern.toLowerCase());
    }
}
