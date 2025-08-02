package com.example.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Test resource that manages MySQL TestContainer lifecycle for integration tests.
 * This class is used by @QuarkusTestResource annotation to provide a MySQL database
 * for tests that need real database connectivity.
 */
public class MySQLTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(MySQLTestResource.class);
    private MySQLContainer<?> mysql;

    @Override
    public Map<String, String> start() {
        LOG.info("Starting MySQL TestContainer...");
        
        // Create and start MySQL container
        mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("userapi")  // Match the jOOQ generated schema name
                .withUsername("userapi")      // Match production setup  
                .withPassword("userapi")      // Simple password for tests
                .withCommand("--character-set-server=utf8mb4",
                           "--collation-server=utf8mb4_unicode_ci",
                           "--skip-character-set-client-handshake");

        mysql.start();
        
        LOG.info("MySQL TestContainer started. JDBC URL: {}", mysql.getJdbcUrl());

        // Return configuration properties for Quarkus using HashMap (more than 10 entries)
        Map<String, String> config = new HashMap<>();
        
        // Core datasource configuration
        config.put("quarkus.datasource.jdbc", "true");
        config.put("quarkus.datasource.db-kind", "mysql");
        config.put("quarkus.datasource.jdbc.url", mysql.getJdbcUrl());
        config.put("quarkus.datasource.username", mysql.getUsername());
        config.put("quarkus.datasource.password", mysql.getPassword());
        
        // Connection pool settings for tests
        config.put("quarkus.datasource.jdbc.min-size", "1");
        config.put("quarkus.datasource.jdbc.max-size", "5");
        config.put("quarkus.datasource.jdbc.acquisition-timeout", "5S");
        
        // Flyway configuration
        config.put("quarkus.flyway.migrate-at-start", "true");
        config.put("quarkus.flyway.clean-at-start", "true");
        config.put("quarkus.flyway.baseline-on-migrate", "true");
        config.put("quarkus.flyway.locations", "classpath:db/migration");
        
        LOG.info("Returning test configuration with {} properties", config.size());
        config.forEach((key, value) -> LOG.debug("Config: {} = {}", key, 
            key.contains("password") ? "***" : value));
        
        return config;
    }

    @Override
    public void stop() {
        LOG.info("Stopping MySQL TestContainer...");
        if (mysql != null) {
            mysql.stop();
            LOG.info("MySQL TestContainer stopped");
        }
    }
}