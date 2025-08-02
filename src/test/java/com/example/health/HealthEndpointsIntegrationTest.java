package com.example.health;

import com.example.integration.MySQLTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for health check endpoints.
 * 
 * Verifies that health check endpoints return proper responses and are
 * accessible through HTTP. Tests both individual probe endpoints and
 * the combined health endpoint.
 */
@QuarkusTest
@QuarkusTestResource(MySQLTestResource.class)
@EnabledIfSystemProperty(named = "test.database", matches = "true")
class HealthEndpointsIntegrationTest {

    @Test
    void testCombinedHealthEndpoint() {
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("status", equalTo("UP"))
            .body("checks", not(empty()))
            .body("checks.name", hasItems("application", "database"))
            .body("checks.status", everyItem(equalTo("UP")));
    }

    @Test
    void testLivenessProbeEndpoint() {
        given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("status", equalTo("UP"))
            .body("checks", not(empty()))
            .body("checks.name", hasItem("application"))
            .body("checks[0].status", equalTo("UP"))
            .body("checks[0].data.status", equalTo("running"))
            .body("checks[0].data.application", notNullValue())
            .body("checks[0].data.version", notNullValue())
            .body("checks[0].data.timestamp", notNullValue());
    }

    @Test
    void testReadinessProbeEndpoint() {
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("status", equalTo("UP"))
            .body("checks", not(empty()))
            .body("checks.name", hasItem("database"))
            .body("checks.find { it.name == 'database' }.status", equalTo("UP"))
            .body("checks.find { it.name == 'database' }.data.connection", equalTo("active"))
            .body("checks.find { it.name == 'database' }.data.database", equalTo("mysql"))
            .body("checks.find { it.name == 'database' }.data.dialect", equalTo("MySQL"));
    }

    @Test
    void testHealthEndpointResponseTimes() {
        // Test liveness endpoint response time
        long start = System.currentTimeMillis();
        given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200);
        long livenessTime = System.currentTimeMillis() - start;
        
        // Test readiness endpoint response time
        start = System.currentTimeMillis();
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200);
        long readinessTime = System.currentTimeMillis() - start;
        
        // Both should be fast (under 1 second)
        assertThat(livenessTime).isLessThan(1000L);
        assertThat(readinessTime).isLessThan(1000L);
    }

    @Test
    void testHealthEndpointDataStructure() {
        Response response = given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .extract().response();

        // Parse and verify the structure
        assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
        assertThat(response.jsonPath().getList("checks")).isNotEmpty();
        
        // Verify our custom health checks are present (may include additional built-in checks)
        assertThat(response.jsonPath().getList("checks.name"))
            .contains("application", "database");
    }

    @Test
    void testLivenessProbeMetadata() {
        given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200)
            .body("checks[0].data.memory_used_mb", greaterThan(0))
            .body("checks[0].data.memory_total_mb", greaterThan(0))
            .body("checks[0].data.uptime_ms", anyOf(greaterThan(0), equalTo(-1)))
            .body("checks[0].data.timestamp", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z"));
    }

    @Test
    void testReadinessProbeMetadata() {
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("checks.find { it.name == 'database' }.data.check_duration_ms", greaterThanOrEqualTo(0))
            .body("checks.find { it.name == 'database' }.data.check_duration_ms", lessThan(5000)); // Should be under 5 seconds
    }

    @Test
    void testHealthEndpointAccessibility() {
        // Verify health endpoints are accessible without authentication
        // (important for Kubernetes probes)
        
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200);
            
        given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200);
            
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200);
    }

    @Test
    void testHealthEndpointHeaders() {
        // Verify correct content type and other headers
        given()
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .contentType("application/json; charset=UTF-8")
            .header("cache-control", anyOf(equalTo("no-cache"), equalTo("no-store")));
    }

    @Test
    void testKubernetesCompatibility() {
        // Test that health endpoints return responses compatible with Kubernetes
        // health probe expectations
        
        // Liveness probe should return 200 for UP
        given()
            .when().get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
            
        // Readiness probe should return 200 for UP
        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
}