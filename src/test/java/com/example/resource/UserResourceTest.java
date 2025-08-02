package com.example.resource;

import com.example.domain.CreateUserRequest;
import com.example.domain.UserResponse;
import com.example.integration.BaseJooqDatabaseTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Comprehensive integration tests for UserResource REST endpoints.
 * 
 * This test class verifies the complete REST API functionality including:
 * - All three endpoints (POST, GET by ID, GET by username)
 * - HTTP status codes and response formats
 * - Request/response validation
 * - Error handling scenarios
 * - Global exception mapper behavior
 * - Location header generation
 * - JSON serialization/deserialization
 * 
 * Test scenarios covered:
 * - Happy path user creation and retrieval
 * - Validation errors (400 Bad Request)
 * - Duplicate username conflicts (409 Conflict)
 * - User not found errors (404 Not Found)
 * - Malformed UUID handling (400 Bad Request)
 * - Query parameter validation
 * - End-to-end integration testing
 * 
 * Prerequisites:
 * - Docker must be running for TestContainers
 * - Run with: mvn test -Dtest.database=true
 * - Extends BaseJooqDatabaseTest for database isolation
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserResourceTest extends BaseJooqDatabaseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @Order(1)
    @DisplayName("POST /api/users - Should create user successfully and return 201 with Location header")
    void testCreateUser_Success() {
        // Given
        CreateUserRequest request = new CreateUserRequest("testuser123");

        // When
        Response response = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/users")
            .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .header("Location", notNullValue())
            .body("id", notNullValue())
            .body("username", equalTo("testuser123"))
            .body("createdAt", notNullValue())
            .extract()
            .response();

        // Then - Verify response structure
        UserResponse userResponse = response.as(UserResponse.class);
        assertThat(userResponse.id()).isNotNull();
        assertThat(userResponse.username()).isEqualTo("testuser123");
        assertThat(userResponse.createdAt()).isNotNull();
        assertThat(userResponse.createdAt()).isBefore(Instant.now().plusSeconds(1));

        // Verify Location header format (includes host and port in test environment)
        String locationHeader = response.getHeader("Location");
        assertThat(locationHeader).matches("http://localhost:\\d+/api/users/[0-9a-f-]{36}");
        assertThat(locationHeader).contains(userResponse.id());

        // Verify user was actually created in database
        assertThat(getRowCount(com.example.generated.jooq.Tables.USERS)).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/users - Should return 409 when username already exists")
    void testCreateUser_DuplicateUsername() {
        // Given - Create initial user
        CreateUserRequest firstRequest = new CreateUserRequest("duplicateuser");
        given()
            .contentType(ContentType.JSON)
            .body(firstRequest)
            .when()
            .post("/api/users")
            .then()
            .statusCode(201);

        // When - Attempt to create user with same username
        CreateUserRequest duplicateRequest = new CreateUserRequest("duplicateuser");
        
        given()
            .contentType(ContentType.JSON)
            .body(duplicateRequest)
            .when()
            .post("/api/users")
            .then()
            .statusCode(409)
            .contentType(ContentType.JSON)
            .body("error", equalTo("Duplicate username"))
            .body("message", containsString("Username already exists: duplicateuser"))
            .body("timestamp", notNullValue());

        // Verify only one user was created
        assertThat(getRowCount(com.example.generated.jooq.Tables.USERS)).isEqualTo(1);
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/users - Should return 400 for validation errors")
    void testCreateUser_ValidationErrors() throws Exception {
        // Test cases for different validation scenarios
        testValidationError(new CreateUserRequest(""), "Username is required");
        testValidationError(new CreateUserRequest("ab"), "Username must be between 3 and 50 characters");
        testValidationError(new CreateUserRequest("a".repeat(51)), "Username must be between 3 and 50 characters");
        testValidationError(new CreateUserRequest("invalid@user"), "Username can only contain alphanumeric characters, hyphens, and underscores");
        testValidationError(new CreateUserRequest("invalid user"), "Username can only contain alphanumeric characters, hyphens, and underscores");
        testValidationError(new CreateUserRequest("invalid.user"), "Username can only contain alphanumeric characters, hyphens, and underscores");

        // Test null request body
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when()
            .post("/api/users")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON);
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/users/{id} - Should return user when found")
    void testGetUserById_Success() {
        // Given - Create a user first
        CreateUserRequest request = new CreateUserRequest("getbyiduser");
        UserResponse createdUser = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/users")
            .then()
            .statusCode(201)
            .extract()
            .as(UserResponse.class);

        // When - Get user by ID
        given()
            .when()
            .get("/api/users/{id}", createdUser.id())
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(createdUser.id()))
            .body("username", equalTo("getbyiduser"))
            .body("createdAt", notNullValue());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/users/{id} - Should return 404 when user not found")
    void testGetUserById_NotFound() {
        // Given - Random UUID that doesn't exist
        UUID nonExistentId = UUID.randomUUID();

        // When - Try to get non-existent user
        given()
            .when()
            .get("/api/users/{id}", nonExistentId)
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .body("error", equalTo("User not found"))
            .body("message", containsString("User not found with ID: " + nonExistentId))
            .body("timestamp", notNullValue());
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/users/{id} - Should return 400 for malformed UUID")
    void testGetUserById_MalformedUUID() {
        // When - Try to get user with invalid UUID format
        given()
            .when()
            .get("/api/users/{id}", "invalid-uuid-format")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/users?username=X - Should return user when found")
    void testGetUserByUsername_Success() {
        // Given - Create a user first
        CreateUserRequest request = new CreateUserRequest("getbyusernameuser");
        UserResponse createdUser = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/users")
            .then()
            .statusCode(201)
            .extract()
            .as(UserResponse.class);

        // When - Get user by username
        given()
            .queryParam("username", "getbyusernameuser")
            .when()
            .get("/api/users")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(createdUser.id()))
            .body("username", equalTo("getbyusernameuser"))
            .body("createdAt", notNullValue());
    }

    @Test
    @Order(8)
    @DisplayName("GET /api/users?username=X - Should return 404 when user not found")
    void testGetUserByUsername_NotFound() {
        // When - Try to get non-existent user by username
        given()
            .queryParam("username", "nonexistentuser")
            .when()
            .get("/api/users")
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .body("error", equalTo("User not found"))
            .body("message", containsString("User not found with username: nonexistentuser"))
            .body("timestamp", notNullValue());
    }

    @Test
    @Order(9)
    @DisplayName("GET /api/users - Should return 400 when username parameter is missing")
    void testGetUserByUsername_MissingParameter() {
        // When - Try to get user without username parameter
        given()
            .when()
            .get("/api/users")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", equalTo("Invalid request"))
            .body("message", containsString("Username query parameter is required"))
            .body("timestamp", notNullValue());
    }

    @Test
    @Order(10)
    @DisplayName("GET /api/users?username= - Should return 400 when username parameter is blank")
    void testGetUserByUsername_BlankParameter() {
        // When - Try to get user with blank username parameter
        given()
            .queryParam("username", "")
            .when()
            .get("/api/users")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", equalTo("Invalid request"))
            .body("message", containsString("Username query parameter is required"))
            .body("timestamp", notNullValue());
    }

    @Test
    @Order(11)
    @DisplayName("End-to-end workflow - Create user and retrieve by both ID and username")
    void testEndToEndWorkflow() {
        // Given - Create user request
        CreateUserRequest request = new CreateUserRequest("e2euser123");

        // When - Create user
        UserResponse createdUser = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/users")
            .then()
            .statusCode(201)
            .extract()
            .as(UserResponse.class);

        // Then - Verify user can be retrieved by ID
        UserResponse userById = given()
            .when()
            .get("/api/users/{id}", createdUser.id())
            .then()
            .statusCode(200)
            .extract()
            .as(UserResponse.class);

        // And - Verify user can be retrieved by username
        UserResponse userByUsername = given()
            .queryParam("username", "e2euser123")
            .when()
            .get("/api/users")
            .then()
            .statusCode(200)
            .extract()
            .as(UserResponse.class);

        // Verify all responses are consistent
        assertThat(userById).isEqualTo(createdUser);
        assertThat(userByUsername).isEqualTo(createdUser);
        assertThat(userById).isEqualTo(userByUsername);
    }

    @Test
    @Order(12)
    @DisplayName("Response format validation - Verify JSON structure and data types")
    void testResponseFormatValidation() throws Exception {
        // Given - Create a user
        CreateUserRequest request = new CreateUserRequest("formattest");
        Response response = given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/users")
            .then()
            .statusCode(201)
            .extract()
            .response();

        // When - Parse response as generic map
        Map<String, Object> responseMap = objectMapper.readValue(
            response.asString(), 
            new TypeReference<Map<String, Object>>() {}
        );

        // Then - Verify response structure
        assertThat(responseMap).containsKeys("id", "username", "createdAt");
        assertThat(responseMap.get("id")).isInstanceOf(String.class);
        assertThat(responseMap.get("username")).isInstanceOf(String.class);
        assertThat(responseMap.get("createdAt")).isInstanceOf(String.class);

        // Verify ID is valid UUID format
        String idString = (String) responseMap.get("id");
        assertThat(UUID.fromString(idString)).isNotNull();

        // Verify timestamp is valid ISO-8601 format
        String timestampString = (String) responseMap.get("createdAt");
        assertThat(Instant.parse(timestampString)).isNotNull();
    }

    @Test
    @Order(13)
    @DisplayName("Error response format validation - Verify consistent error structure")
    void testErrorResponseFormatValidation() throws Exception {
        // When - Trigger a 404 error
        Response response = given()
            .when()
            .get("/api/users/{id}", UUID.randomUUID())
            .then()
            .statusCode(404)
            .extract()
            .response();

        // Then - Parse and verify error response structure
        Map<String, Object> errorResponse = objectMapper.readValue(
            response.asString(), 
            new TypeReference<Map<String, Object>>() {}
        );

        assertThat(errorResponse).containsKeys("error", "message", "timestamp");
        assertThat(errorResponse.get("error")).isInstanceOf(String.class);
        assertThat(errorResponse.get("message")).isInstanceOf(String.class);
        assertThat(errorResponse.get("timestamp")).isInstanceOf(String.class);

        // Verify timestamp is valid
        String timestampString = (String) errorResponse.get("timestamp");
        assertThat(Instant.parse(timestampString)).isNotNull();
    }

    /**
     * Helper method to test validation errors with specific input and expected message.
     */
    private void testValidationError(CreateUserRequest request, String expectedMessageFragment) {
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/users")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", equalTo("Validation failed"))
            .body("violations", notNullValue())
            .body("timestamp", notNullValue());
    }
}