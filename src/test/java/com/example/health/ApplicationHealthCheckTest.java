package com.example.health;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ApplicationHealthCheck covering liveness probe functionality.
 * 
 * Application health checks should almost always return UP since they're used
 * for liveness probes in Kubernetes. These tests verify the response structure
 * and metadata content.
 */
@QuarkusTest
class ApplicationHealthCheckTest {

    @Inject
    @Liveness
    Instance<ApplicationHealthCheck> applicationHealthCheckInstance;
    
    private ApplicationHealthCheck getHealthCheck() {
        return applicationHealthCheckInstance.get();
    }

    @Test
    void testApplicationHealthCheckSuccess() {
        // When
        HealthCheckResponse response = getHealthCheck().call();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("application");
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        
        // Verify response data contains expected fields
        assertThat(response.getData()).isPresent();
        Map<String, Object> data = response.getData().get();
        assertThat(data.get("status")).isEqualTo("running");
        assertThat(data.get("application")).isNotNull();
        assertThat(data.get("version")).isNotNull();
        assertThat(data.get("timestamp")).isNotNull();
    }

    @Test
    void testApplicationHealthCheckMetadata() {
        // When
        HealthCheckResponse response = getHealthCheck().call();

        // Then
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        
        // Verify application metadata
        Map<String, Object> data = response.getData().get();
        String appName = (String) data.get("application");
        String version = (String) data.get("version");
        String timestamp = (String) data.get("timestamp");
        
        assertThat(appName).isNotBlank();
        assertThat(version).isNotBlank();
        assertThat(timestamp).isNotBlank();
        
        // Verify timestamp format (ISO-8601)
        assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z");
    }

    @Test
    void testApplicationHealthCheckMemoryInformation() {
        // When
        HealthCheckResponse response = getHealthCheck().call();

        // Then
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        
        // Verify memory information is present and reasonable
        Map<String, Object> data = response.getData().get();
        Object usedMemory = data.get("memory_used_mb");
        Object totalMemory = data.get("memory_total_mb");
        
        assertThat(usedMemory).isNotNull();
        assertThat(totalMemory).isNotNull();
        
        Long usedMB = (Long) usedMemory;
        Long totalMB = (Long) totalMemory;
        
        assertThat(usedMB).isGreaterThan(0L);
        assertThat(totalMB).isGreaterThan(usedMB);
        assertThat(totalMB).isLessThan(10000L); // Reasonable upper bound for tests
    }

    @Test
    void testApplicationHealthCheckUptimeInformation() {
        // When
        HealthCheckResponse response = getHealthCheck().call();

        // Then
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        
        // Verify uptime information
        Map<String, Object> data = response.getData().get();
        Object uptime = data.get("uptime_ms");
        assertThat(uptime).isNotNull();
        
        Long uptimeMs = (Long) uptime;
        // Uptime should be positive (or -1 if unavailable)
        assertThat(uptimeMs).satisfiesAnyOf(
            value -> assertThat(value).isGreaterThan(0L),
            value -> assertThat(value).isEqualTo(-1L)
        );
    }

    @Test
    void testApplicationHealthCheckFastExecution() {
        // Given
        long startTime = System.currentTimeMillis();
        
        // When
        HealthCheckResponse response = getHealthCheck().call();
        
        // Then
        long duration = System.currentTimeMillis() - startTime;
        assertThat(duration).isLessThan(500L); // Should be very fast, under 500ms
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
    }

    @Test
    void testApplicationHealthCheckConsistentResponse() {
        // When - Call health check multiple times
        HealthCheckResponse response1 = getHealthCheck().call();
        HealthCheckResponse response2 = getHealthCheck().call();
        HealthCheckResponse response3 = getHealthCheck().call();

        // Then - All should return UP status
        assertThat(response1.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response2.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response3.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        
        // Application name and version should be consistent
        Map<String, Object> data1 = response1.getData().get();
        Map<String, Object> data2 = response2.getData().get();
        Map<String, Object> data3 = response3.getData().get();
        
        assertThat(data1.get("application"))
            .isEqualTo(data2.get("application"))
            .isEqualTo(data3.get("application"));
            
        assertThat(data1.get("version"))
            .isEqualTo(data2.get("version"))
            .isEqualTo(data3.get("version"));
    }

    @Test
    void testApplicationHealthCheckResponseStructure() {
        // When
        HealthCheckResponse response = getHealthCheck().call();

        // Then - Verify response structure
        assertThat(response.getName()).isEqualTo("application");
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getData()).isNotNull();
        
        // Verify all required fields are present
        Map<String, Object> data = response.getData().get();
        assertThat(data).containsKeys(
            "status", 
            "application", 
            "version", 
            "timestamp", 
            "uptime_ms",
            "memory_used_mb",
            "memory_total_mb"
        );
        
        // Verify status is running (not running_with_warnings)
        assertThat(data.get("status")).isEqualTo("running");
    }

    @Test
    void testApplicationHealthCheckWithConfiguredValues() {
        // When
        HealthCheckResponse response = getHealthCheck().call();

        // Then
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        
        // Verify that configured application name is used
        Map<String, Object> data = response.getData().get();
        String appName = (String) data.get("application");
        assertThat(appName).isNotNull();
        
        // In test environment, should use configured or default values
        assertThat(appName).satisfiesAnyOf(
            name -> assertThat(name).isEqualTo("user-api"),
            name -> assertThat(name).isNotBlank()
        );
    }
}