package com.example.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Database health check for readiness probe.
 * 
 * This health check verifies that the application can connect to the database
 * and execute queries. It's used as a readiness probe in Kubernetes deployments
 * to ensure the application is ready to serve requests.
 * 
 * The check executes a simple SELECT 1 query using jOOQ to verify:
 * - Database connection is available
 * - jOOQ DSLContext is properly configured
 * - Database is responsive
 * 
 * Fast execution (<1 second) is guaranteed by the underlying connection pool timeouts.
 */
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseHealthCheck.class);

    @Inject
    DSLContext dslContext;

    @Override
    public HealthCheckResponse call() {
        String checkName = "database";
        Instant startTime = Instant.now();
        
        try {
            LOG.debug("Executing database health check");
            
            // Simple connectivity test using jOOQ
            // This verifies both connection availability and jOOQ configuration
            dslContext.selectOne().fetch();
            
            Duration duration = Duration.between(startTime, Instant.now());
            LOG.debug("Database health check completed successfully in {}ms", duration.toMillis());
            
            return HealthCheckResponse.named(checkName)
                .up()
                .withData("connection", "active")
                .withData("database", "mysql")
                .withData("dialect", "MySQL")
                .withData("check_duration_ms", duration.toMillis())
                .build();
                
        } catch (Exception e) {
            Duration duration = Duration.between(startTime, Instant.now());
            LOG.error("Database health check failed after {}ms: {}", duration.toMillis(), e.getMessage());
            
            return HealthCheckResponse.named(checkName)
                .down()
                .withData("connection", "failed")
                .withData("error", e.getMessage())
                .withData("error_type", e.getClass().getSimpleName())
                .withData("check_duration_ms", duration.toMillis())
                .build();
        }
    }
}