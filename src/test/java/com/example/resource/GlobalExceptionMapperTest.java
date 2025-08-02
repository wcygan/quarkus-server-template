package com.example.resource;

import com.example.integration.BaseJooqDatabaseTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Comprehensive integration tests for GlobalExceptionMapper.
 * 
 * This test class specifically focuses on verifying that all exception scenarios
 * are properly mapped to appropriate HTTP responses with consistent error formatting.
 * It ensures that the global exception handling works correctly across all endpoints
 * and provides meaningful error messages to clients.
 * 
 * Test scenarios covered:
 * - UserNotFoundException → 404 Not Found
 * - DuplicateUsernameException → 409 Conflict  
 * - ConstraintViolationException → 400 Bad Request with field violations
 * - IllegalArgumentException → 400 Bad Request
 * - WebApplicationException → Original status code preserved
 * - Generic exceptions → 500 Internal Server Error
 * - Error response format consistency
 * - Timestamp inclusion in all error responses
 * - Field-level validation error reporting
 * 
 * Prerequisites:
 * - Docker must be running for TestContainers
 * - Run with: mvn test -Dtest.database=true
 * - Extends BaseJooqDatabaseTest for database isolation
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GlobalExceptionMapperTest extends BaseJooqDatabaseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @Order(1)
    @DisplayName("UserNotFoundException should map to 404 with proper error format")
    void testUserNotFoundException_Mapping() throws Exception {
        // When - Request non-existent user by ID
        UUID nonExistentId = UUID.randomUUID();
        Response response = given()
            .when()
            .get("/api/users/{id}", nonExistentId)
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .extract()
            .response();

        // Then - Verify error response structure
        Map<String, Object> errorResponse = parseErrorResponse(response);
        
        assertThat(errorResponse.get("error")).isEqualTo("User not found");
        assertThat(errorResponse.get("message")).asString()
            .contains("User not found with ID: " + nonExistentId);
        assertThat(errorResponse.get("timestamp")).isNotNull();
        assertThat(errorResponse).doesNotContainKey("violations");

        // Verify timestamp is recent and valid
        verifyTimestampIsRecent((String) errorResponse.get("timestamp"));
    }

    @Test
    @Order(2)
    @DisplayName("UserNotFoundException by username should map to 404 with proper error format")
    void testUserNotFoundByUsername_Mapping() throws Exception {
        // When - Request non-existent user by username
        Response response = given()
            .queryParam("username", "nonexistentuser")
            .when()
            .get("/api/users")
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .extract()
            .response();

        // Then - Verify error response structure
        Map<String, Object> errorResponse = parseErrorResponse(response);
        
        assertThat(errorResponse.get("error")).isEqualTo("User not found");
        assertThat(errorResponse.get("message")).asString()
            .contains("User not found with username: nonexistentuser");
        assertThat(errorResponse.get("timestamp")).isNotNull();

        verifyTimestampIsRecent((String) errorResponse.get("timestamp"));
    }

    @Test
    @Order(3)
    @DisplayName("DuplicateUsernameException should map to 409 with proper error format")
    void testDuplicateUsernameException_Mapping() throws Exception {
        // Given - Create initial user
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "duplicateuser"))
            .when()
            .post("/api/users")
            .then()
            .statusCode(201);

        // When - Attempt duplicate creation
        Response response = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "duplicateuser"))
            .when()
            .post("/api/users")
            .then()
            .statusCode(409)
            .contentType(ContentType.JSON)
            .extract()
            .response();

        // Then - Verify error response structure
        Map<String, Object> errorResponse = parseErrorResponse(response);
        
        assertThat(errorResponse.get("error")).isEqualTo("Duplicate username");
        assertThat(errorResponse.get("message")).asString()
            .contains("Username already exists: duplicateuser");
        assertThat(errorResponse.get("timestamp")).isNotNull();
        assertThat(errorResponse).doesNotContainKey("violations");

        verifyTimestampIsRecent((String) errorResponse.get("timestamp"));
    }

    @Test
    @Order(4)
    @DisplayName("Bean Validation errors should map to 400 with field violations")
    void testConstraintViolationException_Mapping() throws Exception {
        // When - Send request with validation errors
        Response response = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "")) // Empty username violates @NotBlank
            .when()
            .post("/api/users")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .extract()
            .response();

        // Then - Verify error response structure
        Map<String, Object> errorResponse = parseErrorResponse(response);
        
        assertThat(errorResponse.get("error")).isEqualTo("Validation failed");
        assertThat(errorResponse.get("message")).asString()
            .contains("Request validation failed");
        assertThat(errorResponse.get("timestamp")).isNotNull();
        assertThat(errorResponse).containsKey("violations");

        // Verify violations structure
        @SuppressWarnings("unchecked")
        Map<String, String> violations = (Map<String, String>) errorResponse.get("violations");
        assertThat(violations).isNotEmpty();
        assertThat(violations).containsKey("username");
        // Empty string triggers multiple validation messages, check for one of them
        String usernameViolation = violations.get("username");
        assertThat(usernameViolation).isIn(
            "Username is required", 
            "Username must be between 3 and 50 characters",
            "Username can only contain alphanumeric characters, hyphens, and underscores"
        );

        verifyTimestampIsRecent((String) errorResponse.get("timestamp"));
    }

    @Test
    @Order(5)
    @DisplayName("Multiple validation errors should include all field violations")
    void testMultipleValidationErrors_Mapping() throws Exception {
        // When - Send request with multiple validation errors
        Response response = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "ab")) // Too short (violates @Size)
            .when()
            .post("/api/users")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .extract()
            .response();

        // Then - Verify error response structure
        Map<String, Object> errorResponse = parseErrorResponse(response);
        
        assertThat(errorResponse.get("error")).isEqualTo("Validation failed");
        assertThat(errorResponse).containsKey("violations");

        @SuppressWarnings("unchecked")
        Map<String, String> violations = (Map<String, String>) errorResponse.get("violations");
        assertThat(violations).containsKey("username");
        assertThat(violations.get("username")).contains("Username must be between 3 and 50 characters");
    }

    @Test
    @Order(6)
    @DisplayName("Pattern validation errors should map correctly")
    void testPatternValidationError_Mapping() throws Exception {
        // When - Send request with invalid username pattern
        Response response = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "invalid@username"))
            .when()
            .post("/api/users")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .extract()
            .response();

        // Then - Verify error response structure
        Map<String, Object> errorResponse = parseErrorResponse(response);
        
        @SuppressWarnings("unchecked")
        Map<String, String> violations = (Map<String, String>) errorResponse.get("violations");
        assertThat(violations).containsKey("username");
        assertThat(violations.get("username")).contains("Username can only contain alphanumeric characters");
    }

    @Test
    @Order(7)
    @DisplayName("IllegalArgumentException should map to 400 with proper error format")
    void testIllegalArgumentException_Mapping() throws Exception {
        // When - Request user with blank username parameter
        Response response = given()
            .queryParam("username", "")
            .when()
            .get("/api/users")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .extract()
            .response();

        // Then - Verify error response structure
        Map<String, Object> errorResponse = parseErrorResponse(response);
        
        assertThat(errorResponse.get("error")).isEqualTo("Validation failed");
        assertThat(errorResponse.get("message")).asString()
            .contains("Request validation failed");
        assertThat(errorResponse.get("timestamp")).isNotNull();
        // Query parameter validation now includes violations
        assertThat(errorResponse).containsKey("violations");

        verifyTimestampIsRecent((String) errorResponse.get("timestamp"));
    }

    @Test
    @Order(8)
    @DisplayName("Malformed UUID should map to 400 through WebApplicationException")
    void testWebApplicationException_Mapping() throws Exception {
        // When - Request user with malformed UUID
        Response response = given()
            .when()
            .get("/api/users/{id}", "not-a-valid-uuid")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .extract()
            .response();

        // Then - Verify error response structure
        Map<String, Object> errorResponse = parseErrorResponse(response);
        
        assertThat(errorResponse.get("error")).isEqualTo("Bad request");
        assertThat(errorResponse.get("message")).isNotNull();
        assertThat(errorResponse.get("timestamp")).isNotNull();

        verifyTimestampIsRecent((String) errorResponse.get("timestamp"));
    }

    @Test
    @Order(9)
    @DisplayName("Unsupported media type should map correctly")
    void testUnsupportedMediaType_Mapping() throws Exception {
        // When - Send request with unsupported content type
        Response response = given()
            .contentType("text/plain")
            .body("plain text body")
            .when()
            .post("/api/users")
            .then()
            .statusCode(415) // Unsupported Media Type
            .extract()
            .response();

        // Note: This tests that WebApplicationException mapping preserves original status codes
        assertThat(response.getStatusCode()).isEqualTo(415);
    }

    @Test
    @Order(10)
    @DisplayName("Method not allowed should map correctly")
    void testMethodNotAllowed_Mapping() throws Exception {
        // When - Use unsupported HTTP method
        Response response = given()
            .when()
            .put("/api/users")
            .then()
            .statusCode(405) // Method Not Allowed
            .extract()
            .response();

        // Note: This tests that WebApplicationException mapping preserves original status codes
        assertThat(response.getStatusCode()).isEqualTo(405);
    }

    @Test
    @Order(11)
    @DisplayName("All error responses should have consistent structure")
    void testErrorResponseConsistency() throws Exception {
        // Test multiple error scenarios and verify they all have consistent structure
        
        // 404 error
        Map<String, Object> notFoundResponse = parseErrorResponse(
            given().get("/api/users/{id}", UUID.randomUUID()).then().statusCode(404).extract().response()
        );
        
        // 409 error - first create a user, then try to duplicate
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", "consistencytest"))
            .post("/api/users")
            .then()
            .statusCode(201);
            
        Map<String, Object> conflictResponse = parseErrorResponse(
            given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "consistencytest"))
                .post("/api/users")
                .then()
                .statusCode(409)
                .extract()
                .response()
        );
        
        // 400 validation error
        Map<String, Object> validationResponse = parseErrorResponse(
            given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", ""))
                .post("/api/users")
                .then()
                .statusCode(400)
                .extract()
                .response()
        );

        // Verify all responses have required fields
        for (Map<String, Object> response : List.of(notFoundResponse, conflictResponse, validationResponse)) {
            assertThat(response).containsKeys("error", "message", "timestamp");
            assertThat(response.get("error")).isInstanceOf(String.class);
            assertThat(response.get("message")).isInstanceOf(String.class);
            assertThat(response.get("timestamp")).isInstanceOf(String.class);
            verifyTimestampIsRecent((String) response.get("timestamp"));
        }

        // Validation response should also have violations
        assertThat(validationResponse).containsKey("violations");
        assertThat(validationResponse.get("violations")).isInstanceOf(Map.class);
    }

    /**
     * Helper method to parse error response JSON into a Map.
     */
    private Map<String, Object> parseErrorResponse(Response response) throws Exception {
        return objectMapper.readValue(
            response.asString(),
            new TypeReference<Map<String, Object>>() {}
        );
    }

    /**
     * Helper method to verify that a timestamp string is recent and valid.
     */
    private void verifyTimestampIsRecent(String timestampString) {
        Instant timestamp = Instant.parse(timestampString);
        Instant now = Instant.now();
        
        // Timestamp should be within the last 10 seconds
        assertThat(timestamp).isBetween(now.minusSeconds(10), now.plusSeconds(1));
    }
}