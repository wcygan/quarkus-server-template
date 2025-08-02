package com.example.integration;

import io.quarkus.test.junit.QuarkusTestProfile;
import org.testcontainers.containers.MySQLContainer;

import java.util.Map;

/**
 * Test profile that configures TestContainers MySQL for integration tests.
 * This profile enables database testing with a real MySQL instance.
 */
public class MySQLTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // Start MySQL TestContainer
        MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        
        mysql.start();
        
        return Map.of(
            "quarkus.datasource.jdbc", "true",
            "quarkus.datasource.db-kind", "mysql",
            "quarkus.datasource.username", mysql.getUsername(),
            "quarkus.datasource.password", mysql.getPassword(),
            "quarkus.datasource.jdbc.url", mysql.getJdbcUrl(),
            "quarkus.flyway.migrate-at-start", "true",
            "quarkus.flyway.clean-at-start", "true"
        );
    }

    @Override
    public String getConfigProfile() {
        return "test-mysql";
    }
}