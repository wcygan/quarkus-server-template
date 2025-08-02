package com.example.health;

import com.example.integration.BaseJooqDatabaseTest;
import com.example.integration.MySQLTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DatabaseHealthCheck covering both success and failure scenarios.
 * 
 * Success scenarios use real database connectivity through TestContainers.
 * Failure scenarios use mocked DSLContext to simulate database errors.
 */
@QuarkusTest
@QuarkusTestResource(MySQLTestResource.class)
@EnabledIfSystemProperty(named = "test.database", matches = "true")
class DatabaseHealthCheckTest extends BaseJooqDatabaseTest {

    @Inject
    @Readiness
    Instance<DatabaseHealthCheck> databaseHealthCheckInstance;
    
    private DatabaseHealthCheck getHealthCheck() {
        return databaseHealthCheckInstance.get();
    }

    @Test
    void testDatabaseHealthCheckSuccess() {
        // When
        HealthCheckResponse response = getHealthCheck().call();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("database");
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        
        // Verify response data
        assertThat(response.getData()).isPresent();
        Map<String, Object> data = response.getData().get();
        assertThat(data.get("connection")).isEqualTo("active");
        assertThat(data.get("database")).isEqualTo("mysql");
        assertThat(data.get("dialect")).isEqualTo("MySQL");
        
        // Verify timing data is present and reasonable
        Object duration = data.get("check_duration_ms");
        assertThat(duration).isNotNull();
        assertThat((Long) duration).isGreaterThanOrEqualTo(0L);
        assertThat((Long) duration).isLessThan(5000L); // Should complete in under 5 seconds
    }

    @Test
    void testDatabaseHealthCheckWithRealQuery() {
        // Given - health check with real database
        
        // When
        HealthCheckResponse response = getHealthCheck().call();

        // Then
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        
        // Verify we can also perform a real query after health check
        Integer result = dslContext.selectOne().fetchOne(0, Integer.class);
        assertThat(result).isEqualTo(1);
    }

    @Test 
    void testDatabaseHealthCheckFastExecution() {
        // Given
        long startTime = System.currentTimeMillis();
        
        // When
        HealthCheckResponse response = getHealthCheck().call();
        
        // Then
        long duration = System.currentTimeMillis() - startTime;
        assertThat(duration).isLessThan(1000L); // Should complete in under 1 second
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
    }

    @Test
    void testDatabaseHealthCheckWithMultipleCalls() {
        // Given - Test multiple consecutive calls to verify consistency
        
        // When - Call health check multiple times
        HealthCheckResponse response1 = getHealthCheck().call();
        HealthCheckResponse response2 = getHealthCheck().call();
        HealthCheckResponse response3 = getHealthCheck().call();

        // Then - All should return UP status consistently
        assertThat(response1.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response2.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response3.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        
        // All should have consistent database information
        Map<String, Object> data1 = response1.getData().get();
        Map<String, Object> data2 = response2.getData().get();
        Map<String, Object> data3 = response3.getData().get();
        
        assertThat(data1.get("database")).isEqualTo(data2.get("database")).isEqualTo(data3.get("database"));
        assertThat(data1.get("dialect")).isEqualTo(data2.get("dialect")).isEqualTo(data3.get("dialect"));
    }

    @Test
    void testDatabaseHealthCheckErrorHandling() {
        // Given - Create a health check with null DSLContext to test error handling
        DatabaseHealthCheck healthCheckWithNull = new DatabaseHealthCheck();
        healthCheckWithNull.dslContext = null;

        // When
        HealthCheckResponse response = healthCheckWithNull.call();

        // Then
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getData()).isPresent();
        Map<String, Object> data = response.getData().get();
        assertThat(data.get("connection")).isEqualTo("failed");
        assertThat(data.get("error_type")).isEqualTo("NullPointerException");
        assertThat(data.get("check_duration_ms")).isNotNull();
    }

    @Test
    void testDatabaseHealthCheckResponseStructure() {
        // When
        HealthCheckResponse response = getHealthCheck().call();

        // Then - Verify all expected fields are present
        assertThat(response.getName()).isNotBlank();
        assertThat(response.getStatus()).isIn(HealthCheckResponse.Status.UP, HealthCheckResponse.Status.DOWN);
        assertThat(response.getData()).isNotNull();
        
        // Check required data fields
        if (response.getStatus() == HealthCheckResponse.Status.UP) {
            Map<String, Object> data = response.getData().get();
            assertThat(data).containsKeys("connection", "database", "dialect", "check_duration_ms");
        } else {
            Map<String, Object> data = response.getData().get();
            assertThat(data).containsKeys("connection", "error", "error_type", "check_duration_ms");
        }
    }
}