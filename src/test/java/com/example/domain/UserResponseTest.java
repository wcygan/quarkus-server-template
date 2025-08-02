package com.example.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the UserResponse DTO.
 * 
 * Tests JSON serialization behavior, factory method conversion,
 * and proper formatting of timestamp and UUID fields.
 */
@DisplayName("UserResponse DTO Serialization Tests")
class UserResponseTest {
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @Test
    @DisplayName("Should create UserResponse from User domain object")
    void shouldCreateFromUser() {
        // Given
        UUID id = UUID.randomUUID();
        String username = "testuser";
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        User user = new User(id, username, createdAt);
        
        // When
        UserResponse response = UserResponse.from(user);
        
        // Then
        assertEquals(id.toString(), response.id());
        assertEquals(username, response.username());
        assertEquals(createdAt, response.createdAt());
    }
    
    @Test
    @DisplayName("Should serialize to JSON with correct field names and formats")
    void shouldSerializeToJsonCorrectly() throws JsonProcessingException {
        // Given
        String id = UUID.randomUUID().toString();
        String username = "testuser";
        Instant createdAt = Instant.parse("2023-12-01T10:30:45Z");
        UserResponse response = new UserResponse(id, username, createdAt);
        
        // When
        String json = objectMapper.writeValueAsString(response);
        
        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"id\":\"" + id + "\""));
        assertTrue(json.contains("\"username\":\"" + username + "\""));
        assertTrue(json.contains("\"createdAt\":\"2023-12-01T10:30:45Z\""));
    }
    
    @Test
    @DisplayName("Should deserialize from JSON with correct field mapping")
    void shouldDeserializeFromJsonCorrectly() throws JsonProcessingException {
        // Given
        String id = UUID.randomUUID().toString();
        String username = "testuser";
        String json = """
            {
                "id": "%s",
                "username": "%s",
                "createdAt": "2023-12-01T10:30:45Z"
            }
            """.formatted(id, username);
        
        // When
        UserResponse response = objectMapper.readValue(json, UserResponse.class);
        
        // Then
        assertEquals(id, response.id());
        assertEquals(username, response.username());
        assertEquals(Instant.parse("2023-12-01T10:30:45Z"), response.createdAt());
    }
    
    @Test
    @DisplayName("Should handle timestamp serialization with consistent format")
    void shouldHandleTimestampSerializationConsistently() throws JsonProcessingException {
        // Given - test various timestamp formats
        Instant[] testTimestamps = {
            Instant.parse("2023-01-01T00:00:00Z"),           // start of year
            Instant.parse("2023-12-31T23:59:59Z"),           // end of year
            Instant.parse("2023-07-15T12:30:45Z"),           // middle of year
            Instant.parse("2023-02-28T00:00:00Z"),           // non-leap year
            Instant.parse("2024-02-29T00:00:00Z")            // leap year
        };
        
        String id = UUID.randomUUID().toString();
        String username = "testuser";
        
        // When & Then
        for (Instant timestamp : testTimestamps) {
            UserResponse response = new UserResponse(id, username, timestamp);
            String json = objectMapper.writeValueAsString(response);
            
            // Verify timestamp is in ISO format with Z timezone
            assertTrue(json.contains("\"createdAt\":\"" + timestamp.toString() + "\""),
                "Timestamp " + timestamp + " should serialize in ISO format with Z timezone");
            
            // Verify round-trip serialization
            UserResponse deserialized = objectMapper.readValue(json, UserResponse.class);
            assertEquals(timestamp, deserialized.createdAt(),
                "Timestamp should survive round-trip serialization");
        }
    }
    
    @Test
    @DisplayName("Should handle UUID conversion from domain object correctly")
    void shouldHandleUuidConversionCorrectly() {
        // Given
        UUID[] testUuids = {
            UUID.randomUUID(),
            UUID.fromString("00000000-0000-0000-0000-000000000000"),  // nil UUID
            UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),  // max UUID
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000")   // specific UUID
        };
        
        String username = "testuser";
        Instant createdAt = Instant.now();
        
        // When & Then
        for (UUID uuid : testUuids) {
            User user = new User(uuid, username, createdAt);
            UserResponse response = UserResponse.from(user);
            
            assertEquals(uuid.toString(), response.id(),
                "UUID " + uuid + " should be converted to string correctly");
        }
    }
    
    @Test
    @DisplayName("Should maintain field order in JSON output")
    void shouldMaintainFieldOrderInJson() throws JsonProcessingException {
        // Given
        String id = UUID.randomUUID().toString();
        String username = "testuser";
        Instant createdAt = Instant.parse("2023-12-01T10:30:45Z");
        UserResponse response = new UserResponse(id, username, createdAt);
        
        // When
        String json = objectMapper.writeValueAsString(response);
        
        // Then - verify field order matches record declaration order
        int idIndex = json.indexOf("\"id\":");
        int usernameIndex = json.indexOf("\"username\":");
        int createdAtIndex = json.indexOf("\"createdAt\":");
        
        assertTrue(idIndex < usernameIndex, "id should come before username");
        assertTrue(usernameIndex < createdAtIndex, "username should come before createdAt");
    }
    
    @Test
    @DisplayName("Should handle special characters in username correctly")
    void shouldHandleSpecialCharactersInUsername() throws JsonProcessingException {
        // Given
        String id = UUID.randomUUID().toString();
        String[] specialUsernames = {
            "user_with_underscores",
            "user-with-hyphens",
            "user123",
            "USER_UPPERCASE",
            "123numeric_start",
            "_underscore_start",
            "-hyphen_start"
        };
        Instant createdAt = Instant.now();
        
        // When & Then
        for (String username : specialUsernames) {
            UserResponse response = new UserResponse(id, username, createdAt);
            String json = objectMapper.writeValueAsString(response);
            
            // Verify proper JSON escaping
            assertTrue(json.contains("\"username\":\"" + username + "\""),
                "Username '" + username + "' should be properly serialized in JSON");
            
            // Verify round-trip
            UserResponse deserialized = objectMapper.readValue(json, UserResponse.class);
            assertEquals(username, deserialized.username(),
                "Username '" + username + "' should survive round-trip serialization");
        }
    }
    
    @Test
    @DisplayName("Should implement equals and hashCode correctly (record behavior)")
    void shouldImplementEqualsAndHashCode() {
        // Given
        String id = UUID.randomUUID().toString();
        String username = "testuser";
        Instant createdAt = Instant.now();
        
        UserResponse response1 = new UserResponse(id, username, createdAt);
        UserResponse response2 = new UserResponse(id, username, createdAt);
        UserResponse response3 = new UserResponse(UUID.randomUUID().toString(), username, createdAt);
        
        // When & Then
        assertEquals(response1, response2, "Responses with same data should be equal");
        assertNotEquals(response1, response3, "Responses with different IDs should not be equal");
        assertEquals(response1.hashCode(), response2.hashCode(), "Equal responses should have same hashCode");
        
        // Verify toString includes relevant information
        String toString = response1.toString();
        assertTrue(toString.contains(username));
        assertTrue(toString.contains(id));
    }
    
    @Test
    @DisplayName("Should handle factory method with edge case User objects")
    void shouldHandleFactoryMethodEdgeCases() {
        // Given - User with edge case values
        UUID nilUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        String minUsername = "abc";  // minimum valid length
        String maxUsername = "a".repeat(50);  // maximum valid length
        Instant epochStart = Instant.EPOCH;
        Instant farFuture = Instant.parse("2999-12-31T23:59:59Z");
        
        User[] edgeCaseUsers = {
            new User(nilUuid, minUsername, epochStart),
            new User(UUID.randomUUID(), maxUsername, farFuture),
            new User(UUID.randomUUID(), "test_user", Instant.now())
        };
        
        // When & Then
        for (User user : edgeCaseUsers) {
            assertDoesNotThrow(() -> {
                UserResponse response = UserResponse.from(user);
                assertEquals(user.id().toString(), response.id());
                assertEquals(user.username(), response.username());
                assertEquals(user.createdAt(), response.createdAt());
            }, "Factory method should handle edge case user: " + user);
        }
    }
}