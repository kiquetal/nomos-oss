package me.cresterida.nomos.config;

import io.smallrye.health.api.Wellness;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.neo4j.driver.Driver;

@Readiness
@ApplicationScoped
public class Neo4jHealthCheck implements HealthCheck {

    @Inject
    Driver driver;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("Neo4j connection");
        try {
            driver.verifyConnectivity();
            builder.up().withData("status", "connected");
        } catch (Exception e) {
            builder.down().withData("error", e.getMessage());
        }
        return builder.build();
    }
}
