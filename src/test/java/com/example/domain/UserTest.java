package com.example.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the User domain record.
 * 
 * Tests the domain model's validation rules, immutability guarantees,
 * and proper construction behavior.
 */
@DisplayName("User Domain Model Tests")
class UserTest {
    
    @Test
    @DisplayName("Should create valid user with all required fields")
    void shouldCreateValidUser() {
        // Given
        UUID id = UUID.randomUUID();
        String username = "testuser";
        Instant createdAt = Instant.now();
        
        // When
        User user = new User(id, username, createdAt);
        
        // Then
        assertEquals(id, user.id());
        assertEquals(username, user.username());
        assertEquals(createdAt, user.createdAt());
    }
    
    @Test
    @DisplayName("Should reject null user ID")
    void shouldRejectNullUserId() {
        // Given
        String username = "testuser";
        Instant createdAt = Instant.now();
        
        // When & Then
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            new User(null, username, createdAt);
        });
        
        assertEquals("User ID cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject null username")
    void shouldRejectNullUsername() {
        // Given
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        
        // When & Then
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            new User(id, null, createdAt);
        });
        
        assertEquals("Username cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject null created timestamp")
    void shouldRejectNullCreatedAt() {
        // Given
        UUID id = UUID.randomUUID();
        String username = "testuser";
        
        // When & Then
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            new User(id, username, null);
        });
        
        assertEquals("Created timestamp cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject blank username")
    void shouldRejectBlankUsername() {
        // Given
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, "", createdAt);
        });
        
        assertEquals("Username cannot be blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject whitespace-only username")
    void shouldRejectWhitespaceUsername() {
        // Given
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new User(id, "   ", createdAt);
        });
        
        assertEquals("Username cannot be blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should accept username with valid characters")
    void shouldAcceptValidUsernameCharacters() {
        // Given
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String[] validUsernames = {
            "user123",
            "test_user",
            "user-name",
            "User123",
            "a",  // single character
            "a".repeat(50)  // max reasonable length
        };
        
        // When & Then
        for (String username : validUsernames) {
            assertDoesNotThrow(() -> {
                User user = new User(id, username, createdAt);
                assertEquals(username, user.username());
            }, "Username '" + username + "' should be valid");
        }
    }
    
    @Test
    @DisplayName("Should be immutable - record fields cannot be modified")
    void shouldBeImmutable() {
        // Given
        UUID id = UUID.randomUUID();
        String username = "testuser";
        Instant createdAt = Instant.now();
        User user = new User(id, username, createdAt);
        
        // When - attempt to get references to internal state
        UUID retrievedId = user.id();
        String retrievedUsername = user.username();
        Instant retrievedCreatedAt = user.createdAt();
        
        // Then - verify original values are unchanged (immutability test)
        assertEquals(id, user.id());
        assertEquals(username, user.username());
        assertEquals(createdAt, user.createdAt());
        
        // And verify we got the same instances (no defensive copying needed for immutable types)
        assertSame(id, retrievedId);
        assertSame(username, retrievedUsername);
        assertSame(createdAt, retrievedCreatedAt);
    }
    
    @Test
    @DisplayName("Should implement equals and hashCode correctly (record behavior)")
    void shouldImplementEqualsAndHashCode() {
        // Given
        UUID id = UUID.randomUUID();
        String username = "testuser";
        Instant createdAt = Instant.now();
        
        User user1 = new User(id, username, createdAt);
        User user2 = new User(id, username, createdAt);
        User user3 = new User(UUID.randomUUID(), username, createdAt);
        
        // When & Then - verify equals behavior
        assertEquals(user1, user2, "Users with same data should be equal");
        assertNotEquals(user1, user3, "Users with different IDs should not be equal");
        
        // And verify hashCode behavior
        assertEquals(user1.hashCode(), user2.hashCode(), "Equal users should have same hashCode");
        
        // And verify toString behavior (records provide this automatically)
        assertNotNull(user1.toString());
        assertTrue(user1.toString().contains(username));
        assertTrue(user1.toString().contains(id.toString()));
    }
}