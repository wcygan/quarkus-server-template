package com.example.health;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Application health check for liveness probe.
 * 
 * This health check verifies that the application is running and responsive.
 * It's used as a liveness probe in Kubernetes deployments to determine if
 * the application should be restarted.
 * 
 * Unlike readiness checks, liveness checks should only fail when the application
 * is in an unrecoverable state. This implementation always returns UP unless
 * the JVM itself is in a critical state.
 * 
 * The check provides metadata about the application including:
 * - Application name and version
 * - Current timestamp
 * - Runtime information
 */
@Liveness
@ApplicationScoped
public class ApplicationHealthCheck implements HealthCheck {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationHealthCheck.class);

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "user-api")
    String applicationName;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "unknown")
    String applicationVersion;

    @Override
    public HealthCheckResponse call() {
        String checkName = "application";
        
        try {
            LOG.debug("Executing application liveness check");
            
            // Get current timestamp for health check response
            String currentTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            
            // Get JVM information
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            LOG.debug("Application liveness check completed successfully");
            
            return HealthCheckResponse.named(checkName)
                .up()
                .withData("status", "running")
                .withData("application", applicationName)
                .withData("version", applicationVersion)
                .withData("timestamp", currentTime)
                .withData("uptime_ms", getUptimeMillis())
                .withData("memory_used_mb", usedMemory / (1024 * 1024))
                .withData("memory_total_mb", totalMemory / (1024 * 1024))
                .build();
                
        } catch (Exception e) {
            // This should rarely happen as liveness checks should be very simple
            // Log the error but still return UP unless it's a critical JVM issue
            LOG.warn("Application health check encountered an error: {}", e.getMessage());
            
            return HealthCheckResponse.named(checkName)
                .up()
                .withData("status", "running_with_warnings")
                .withData("application", applicationName)
                .withData("version", applicationVersion)
                .withData("warning", e.getMessage())
                .build();
        }
    }

    /**
     * Calculate approximate application uptime in milliseconds.
     * Uses ManagementFactory if available, otherwise returns a placeholder.
     */
    private long getUptimeMillis() {
        try {
            return java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        } catch (Exception e) {
            LOG.debug("Could not retrieve uptime: {}", e.getMessage());
            return -1; // Indicates uptime unavailable
        }
    }
}