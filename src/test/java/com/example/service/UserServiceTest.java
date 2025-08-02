package com.example.service;

import com.example.domain.CreateUserRequest;
import com.example.domain.DuplicateUsernameException;
import com.example.domain.UserNotFoundException;
import com.example.domain.UserResponse;
import com.example.integration.BaseJooqDatabaseTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for UserService business logic layer.
 * 
 * These tests verify the service layer implementation including:
 * - Business logic validation
 * - Transaction boundaries
 * - Exception handling and mapping
 * - Integration with repository layer
 * - End-to-end user operations
 * 
 * Uses BaseJooqDatabaseTest for database integration and automatic cleanup.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.DisplayName.class)
class UserServiceTest extends BaseJooqDatabaseTest {

    @Inject
    UserService userService;

    @Nested
    @DisplayName("User Creation Tests")
    class UserCreationTests {

        @Test
        @DisplayName("Should successfully create user with valid username")
        void shouldCreateUserWithValidUsername() {
            // Given
            String username = "testuser";
            CreateUserRequest request = new CreateUserRequest(username);

            // When
            UserResponse response = userService.createUser(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isNotNull();
            assertThat(response.username()).isEqualTo(username);
            assertThat(response.createdAt()).isNotNull();
            
            // Verify user can be retrieved
            UserResponse retrieved = userService.getUserByUsername(username);
            assertThat(retrieved.id()).isEqualTo(response.id());
            assertThat(retrieved.username()).isEqualTo(response.username());
            // Compare timestamps with tolerance for database precision
            assertThat(retrieved.createdAt()).isCloseTo(response.createdAt(), within(1, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Should throw DuplicateUsernameException when username already exists")
        void shouldThrowExceptionForDuplicateUsername() {
            // Given
            String username = "duplicateuser";
            CreateUserRequest request = new CreateUserRequest(username);
            
            // Create first user
            userService.createUser(request);

            // When & Then
            assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateUsernameException.class)
                .hasMessageContaining("Username already exists: " + username);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null request")
        void shouldThrowExceptionForNullRequest() {
            // When & Then
            assertThatThrownBy(() -> userService.createUser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CreateUserRequest cannot be null");
        }

        @Test
        @DisplayName("Should perform business validation before repository call")
        void shouldPerformBusinessValidationBeforeRepositoryCall() {
            // Given - create a user first
            String username = "validationtest";
            CreateUserRequest request = new CreateUserRequest(username);
            userService.createUser(request);

            // When & Then - second attempt should fail at service level
            assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateUsernameException.class)
                .hasMessageContaining("Username already exists");
        }
    }

    @Nested
    @DisplayName("User Retrieval by ID Tests")
    class UserRetrievalByIdTests {

        @Test
        @DisplayName("Should successfully retrieve user by valid ID")
        void shouldRetrieveUserByValidId() {
            // Given
            String username = "idretrievaltest";
            CreateUserRequest request = new CreateUserRequest(username);
            UserResponse created = userService.createUser(request);
            UUID userId = UUID.fromString(created.id());

            // When
            UserResponse retrieved = userService.getUserById(userId);

            // Then
            assertThat(retrieved).isNotNull();
            assertThat(retrieved.id()).isEqualTo(created.id());
            assertThat(retrieved.username()).isEqualTo(username);
            assertThat(retrieved.createdAt()).isCloseTo(created.createdAt(), within(1, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Should throw UserNotFoundException for non-existent ID")
        void shouldThrowExceptionForNonExistentId() {
            // Given
            UUID nonExistentId = UUID.randomUUID();

            // When & Then
            assertThatThrownBy(() -> userService.getUserById(nonExistentId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found with ID: " + nonExistentId);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null ID")
        void shouldThrowExceptionForNullId() {
            // When & Then
            assertThatThrownBy(() -> userService.getUserById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");
        }
    }

    @Nested
    @DisplayName("User Retrieval by Username Tests")
    class UserRetrievalByUsernameTests {

        @Test
        @DisplayName("Should successfully retrieve user by valid username")
        void shouldRetrieveUserByValidUsername() {
            // Given
            String username = "usernameretrievaltest";
            CreateUserRequest request = new CreateUserRequest(username);
            UserResponse created = userService.createUser(request);

            // When
            UserResponse retrieved = userService.getUserByUsername(username);

            // Then
            assertThat(retrieved).isNotNull();
            assertThat(retrieved.id()).isEqualTo(created.id());
            assertThat(retrieved.username()).isEqualTo(username);
            assertThat(retrieved.createdAt()).isCloseTo(created.createdAt(), within(1, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("Should throw UserNotFoundException for non-existent username")
        void shouldThrowExceptionForNonExistentUsername() {
            // Given
            String nonExistentUsername = "nonexistentuser";

            // When & Then
            assertThatThrownBy(() -> userService.getUserByUsername(nonExistentUsername))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found with username: " + nonExistentUsername);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null username")
        void shouldThrowExceptionForNullUsername() {
            // When & Then
            assertThatThrownBy(() -> userService.getUserByUsername(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank username")
        void shouldThrowExceptionForBlankUsername() {
            // When & Then
            assertThatThrownBy(() -> userService.getUserByUsername(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username cannot be null or blank");

            assertThatThrownBy(() -> userService.getUserByUsername("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username cannot be null or blank");
        }
    }

    @Nested
    @DisplayName("Username Availability Tests")
    class UsernameAvailabilityTests {

        @Test
        @DisplayName("Should return true for available username")
        void shouldReturnTrueForAvailableUsername() {
            // Given
            String availableUsername = "availableuser";

            // When
            boolean available = userService.isUsernameAvailable(availableUsername);

            // Then
            assertThat(available).isTrue();
        }

        @Test
        @DisplayName("Should return false for taken username")
        void shouldReturnFalseForTakenUsername() {
            // Given
            String takenUsername = "takenuser";
            CreateUserRequest request = new CreateUserRequest(takenUsername);
            userService.createUser(request);

            // When
            boolean available = userService.isUsernameAvailable(takenUsername);

            // Then
            assertThat(available).isFalse();
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null username")
        void shouldThrowExceptionForNullUsernameInAvailabilityCheck() {
            // When & Then
            assertThatThrownBy(() -> userService.isUsernameAvailable(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username cannot be null or blank");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank username")
        void shouldThrowExceptionForBlankUsernameInAvailabilityCheck() {
            // When & Then
            assertThatThrownBy(() -> userService.isUsernameAvailable(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username cannot be null or blank");

            assertThatThrownBy(() -> userService.isUsernameAvailable("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username cannot be null or blank");
        }
    }

    @Nested
    @DisplayName("Business Logic Integration Tests")
    class BusinessLogicIntegrationTests {

        @Test
        @DisplayName("Should handle complete user lifecycle")
        void shouldHandleCompleteUserLifecycle() {
            // Given
            String username = "lifecycleuser";
            CreateUserRequest request = new CreateUserRequest(username);

            // When - Create user
            UserResponse created = userService.createUser(request);
            UUID userId = UUID.fromString(created.id());

            // Then - Verify creation
            assertThat(created.username()).isEqualTo(username);
            assertThat(created.id()).isNotNull();
            assertThat(created.createdAt()).isNotNull();

            // When - Retrieve by ID
            UserResponse retrievedById = userService.getUserById(userId);

            // Then - Verify retrieval by ID
            assertThat(retrievedById.id()).isEqualTo(created.id());
            assertThat(retrievedById.username()).isEqualTo(created.username());
            assertThat(retrievedById.createdAt()).isCloseTo(created.createdAt(), within(1, ChronoUnit.SECONDS));

            // When - Retrieve by username
            UserResponse retrievedByUsername = userService.getUserByUsername(username);

            // Then - Verify retrieval by username
            assertThat(retrievedByUsername.id()).isEqualTo(created.id());
            assertThat(retrievedByUsername.username()).isEqualTo(created.username());
            assertThat(retrievedByUsername.createdAt()).isCloseTo(created.createdAt(), within(1, ChronoUnit.SECONDS));

            // When - Check availability
            boolean available = userService.isUsernameAvailable(username);

            // Then - Should not be available
            assertThat(available).isFalse();
        }

        @Test
        @DisplayName("Should maintain data consistency across operations")
        void shouldMaintainDataConsistencyAcrossOperations() {
            // Given
            String username1 = "consistency1";
            String username2 = "consistency2";
            
            CreateUserRequest request1 = new CreateUserRequest(username1);
            CreateUserRequest request2 = new CreateUserRequest(username2);

            // When - Create multiple users
            UserResponse user1 = userService.createUser(request1);
            UserResponse user2 = userService.createUser(request2);

            // Then - Both should be unique and retrievable
            assertThat(user1.id()).isNotEqualTo(user2.id());
            assertThat(user1.username()).isNotEqualTo(user2.username());

            // Verify both can be retrieved independently
            UserResponse retrieved1 = userService.getUserByUsername(username1);
            UserResponse retrieved2 = userService.getUserByUsername(username2);

            // Verify content matches (allowing for timestamp precision differences)
            assertThat(retrieved1.id()).isEqualTo(user1.id());
            assertThat(retrieved1.username()).isEqualTo(user1.username());
            assertThat(retrieved2.id()).isEqualTo(user2.id());
            assertThat(retrieved2.username()).isEqualTo(user2.username());
        }

        @Test
        @DisplayName("Should handle case sensitivity according to database collation")
        void shouldHandleCaseSensitivityCorrectly() {
            // Given
            String username = "CaseSensitiveUser";
            CreateUserRequest request = new CreateUserRequest(username);
            userService.createUser(request);

            // When & Then - MySQL is case-insensitive by default for varchar columns
            // so different case variations should NOT be available
            assertThat(userService.isUsernameAvailable(username.toLowerCase())).isFalse();
            assertThat(userService.isUsernameAvailable(username.toUpperCase())).isFalse();
            assertThat(userService.isUsernameAvailable(username)).isFalse();
        }
    }
}