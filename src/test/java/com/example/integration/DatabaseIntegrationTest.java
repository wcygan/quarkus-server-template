package com.example.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that require a database connection.
 * These tests use TestContainers to spin up a MySQL instance.
 * 
 * This test is disabled by default since it requires Docker.
 * To run: mvn test -Dtest.database=true
 */
@QuarkusTest
@QuarkusTestResource(MySQLTestResource.class)
@EnabledIfSystemProperty(named = "test.database", matches = "true")
public class DatabaseIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseIntegrationTest.class);

    @Inject
    DataSource dataSource;

    @Inject 
    DSLContext dslContext;

    @Test
    public void testDatabaseHealthCheck() {
        // Test the health endpoint which should verify database connectivity
        given()
            .when().get("/q/health/ready")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    public void testDatabaseConnectivity() {
        LOG.info("Testing database connectivity via DataSource");
        
        assertThat(dataSource).isNotNull();
        
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1 as test_value")) {
            
            assertThat(resultSet.next()).isTrue();
            int value = resultSet.getInt("test_value");
            assertThat(value).isEqualTo(1);
            
            LOG.info("Database connectivity verified via JDBC: SELECT 1 returned {}", value);
        } catch (Exception e) {
            throw new RuntimeException("Database connectivity test failed", e);
        }
    }

    @Test
    public void testjOOQIntegration() {
        LOG.info("Testing jOOQ integration");
        
        assertThat(dslContext).isNotNull();
        
        // Test basic jOOQ query
        Integer result = dslContext.selectOne().fetchOne(0, Integer.class);
        assertThat(result).isEqualTo(1);
        
        LOG.info("jOOQ integration verified: SELECT 1 returned {}", result);
    }

    @Test
    public void testFlywayMigrations() {
        LOG.info("Testing that Flyway migrations have executed correctly");
        
        // Test that the users table exists and has the correct structure
        try (Connection connection = dataSource.getConnection()) {
            
            // Check that users table exists
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SHOW TABLES LIKE 'users'")) {
                
                assertThat(resultSet.next()).isTrue();
                String tableName = resultSet.getString(1);
                assertThat(tableName).isEqualTo("users");
                
                LOG.info("Verified that 'users' table exists");
            }
            
            // Check table structure
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("DESCRIBE users")) {
                
                boolean hasId = false, hasUsername = false, hasCreatedAt = false;
                
                while (resultSet.next()) {
                    String columnName = resultSet.getString("Field");
                    String columnType = resultSet.getString("Type");
                    String isNullable = resultSet.getString("Null");
                    String key = resultSet.getString("Key");
                    
                    switch (columnName) {
                        case "id":
                            hasId = true;
                            assertThat(columnType).isEqualTo("char(36)");
                            assertThat(isNullable).isEqualTo("NO");
                            assertThat(key).isEqualTo("PRI");
                            break;
                        case "username":
                            hasUsername = true;
                            assertThat(columnType).isEqualTo("varchar(50)");
                            assertThat(isNullable).isEqualTo("NO");
                            assertThat(key).isEqualTo("UNI");
                            break;
                        case "created_at":
                            hasCreatedAt = true;
                            assertThat(columnType).isEqualTo("timestamp");
                            break;
                    }
                }
                
                assertThat(hasId).isTrue();
                assertThat(hasUsername).isTrue();
                assertThat(hasCreatedAt).isTrue();
                
                LOG.info("Verified users table structure is correct");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Flyway migration verification failed", e);
        }
    }
}