package com.example.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

/**
 * Integration tests that require a database connection.
 * These tests use TestContainers to spin up a MySQL instance.
 * 
 * This test is disabled by default since it requires Docker.
 * To run: mvn test -Dtest.database=true
 */
@QuarkusTest
@TestProfile(MySQLTestProfile.class)
@EnabledIfSystemProperty(named = "test.database", matches = "true")
public class DatabaseIntegrationTest {

    @Test
    public void testDatabaseHealthCheck() {
        // This test will verify database connectivity when implemented
        given()
            .when().get("/q/health/ready")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}