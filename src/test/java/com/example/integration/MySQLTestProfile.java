package com.example.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test profile that configures TestContainers MySQL for integration tests.
 * This profile enables database testing with a real MySQL instance using TestContainers.
 * 
 * Key features:
 * - Starts MySQL 8.0 container with proper Quarkus integration
 * - Configures Flyway migrations to run at startup
 * - Provides isolated database environment per test class
 * - Manages container lifecycle automatically
 */
public class MySQLTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // Test resources will handle the container configuration
        // Additional overrides for test environment
        return Map.of(
            // Connection pool settings for tests
            "quarkus.datasource.jdbc.min-size", "1",
            "quarkus.datasource.jdbc.max-size", "5",
            "quarkus.datasource.jdbc.acquisition-timeout", "5S",
            
            // Flyway configuration for tests
            "quarkus.flyway.baseline-on-migrate", "true",
            "quarkus.flyway.locations", "classpath:db/migration",
            
            // Logging configuration for debugging
            "quarkus.log.category.\"com.example\".level", "DEBUG",
            "quarkus.log.category.\"org.jooq\".level", "DEBUG",
            "quarkus.log.category.\"org.flywaydb\".level", "INFO",
            "quarkus.log.category.\"com.mysql.cj\".level", "WARN"
        );
    }

    @Override
    public String getConfigProfile() {
        return "test-mysql";
    }

    @Override
    public List<TestResourceEntry> testResources() {
        // Return the TestContainer as a test resource for proper lifecycle management
        return Collections.singletonList(
            new TestResourceEntry(MySQLTestResourceLifecycleManager.class)
        );
    }

    /**
     * Test resource lifecycle manager for MySQL TestContainer.
     * This ensures proper startup and shutdown of the container.
     */
    public static class MySQLTestResourceLifecycleManager implements QuarkusTestResourceLifecycleManager {
        
        private MySQLContainer<?> container;
        
        @Override
        public Map<String, String> start() {
            container = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpass")
                    .withCommand("--character-set-server=utf8mb4",
                               "--collation-server=utf8mb4_unicode_ci",
                               "--skip-character-set-client-handshake");
            
            container.start();
            
            // Return the configuration that will be applied to the application
            return Map.of(
                "quarkus.datasource.jdbc", "true",
                "quarkus.datasource.db-kind", "mysql",
                "quarkus.datasource.jdbc.url", container.getJdbcUrl(),
                "quarkus.datasource.username", container.getUsername(),
                "quarkus.datasource.password", container.getPassword(),
                "quarkus.flyway.migrate-at-start", "true",
                "quarkus.flyway.clean-at-start", "true"
            );
        }

        @Override
        public void stop() {
            if (container != null) {
                container.stop();
            }
        }
    }
}