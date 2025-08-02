package com.example.repository;

import com.example.generated.jooq.tables.records.UsersRecord;
import com.example.integration.BaseJooqDatabaseTest;
import com.example.repository.UserRepository.DuplicateUsernameException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.generated.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for UserRepository using the base jOOQ database test class.
 * 
 * This test demonstrates:
 * - Extending BaseJooqDatabaseTest for automatic database setup/cleanup
 * - Testing jOOQ repository operations
 * - Type-safe database operations
 * - Comprehensive test coverage for CRUD operations
 * - Proper exception handling testing
 * 
 * Prerequisites:
 * - Docker running for TestContainers
 * - jOOQ code generation completed: mvn generate-sources
 * - Run with: mvn test -Dtest.database=true
 */
@QuarkusTest
class UserRepositoryTest extends BaseJooqDatabaseTest {

    @Inject
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Verify clean state
        assertTableIsEmpty(USERS);
        verifySchemaExists();
    }

    @Test
    void testCreateUser() {
        // Given
        String username = "testuser";
        
        // When
        UsersRecord created = userRepository.createUser(username);
        
        // Then
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull().hasSize(36); // UUID format
        assertThat(created.getUsername()).isEqualTo(username);
        assertThat(created.getCreatedAt()).isNotNull()
            .isAfter(LocalDateTime.now().minusMinutes(1))  // Created within the last minute
            .isBefore(LocalDateTime.now().plusMinutes(1)); // But not in the future (with 1min buffer)
        
        // Verify in database
        assertThat(getRowCount(USERS)).isEqualTo(1);
    }

    @Test
    void testCreateUserWithDuplicateUsername() {
        // Given
        String username = "duplicate";
        userRepository.createUser(username);
        
        // When/Then
        assertThatThrownBy(() -> userRepository.createUser(username))
            .isInstanceOf(DuplicateUsernameException.class)
            .hasMessageContaining("already exists");
        
        // Verify only one record exists
        assertThat(getRowCount(USERS)).isEqualTo(1);
    }

    @Test
    void testFindById() {
        // Given
        UsersRecord created = userRepository.createUser("findbyid");
        
        // When
        Optional<UsersRecord> found = userRepository.findById(created.getId());
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(created.getId());
        assertThat(found.get().getUsername()).isEqualTo("findbyid");
    }

    @Test
    void testFindByIdNotFound() {
        // When
        Optional<UsersRecord> found = userRepository.findById("non-existent-id");
        
        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void testFindByUsername() {
        // Given
        String username = "findbyusername";
        UsersRecord created = userRepository.createUser(username);
        
        // When
        Optional<UsersRecord> found = userRepository.findByUsername(username);
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(created.getId());
        assertThat(found.get().getUsername()).isEqualTo(username);
    }

    @Test
    void testFindByUsernameNotFound() {
        // When
        Optional<UsersRecord> found = userRepository.findByUsername("nonexistent");
        
        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void testFindAllWithPagination() {
        // Given - create test data
        for (int i = 1; i <= 5; i++) {
            userRepository.createUser("user" + i);
        }
        
        // When - get first page
        List<UsersRecord> page1 = userRepository.findAll(0, 3);
        
        // Then
        assertThat(page1).hasSize(3);
        
        // When - get second page
        List<UsersRecord> page2 = userRepository.findAll(3, 3);
        
        // Then
        assertThat(page2).hasSize(2);
        
        // Verify no overlap
        assertThat(page1).extracting(UsersRecord::getId)
            .doesNotContainAnyElementsOf(page2.stream().map(UsersRecord::getId).toList());
    }

    @Test
    void testCountUsers() {
        // Given
        assertThat(userRepository.countUsers()).isZero();
        
        // When
        userRepository.createUser("user1");
        userRepository.createUser("user2");
        userRepository.createUser("user3");
        
        // Then
        assertThat(userRepository.countUsers()).isEqualTo(3);
    }

    @Test
    void testUpdateUsername() {
        // Given
        UsersRecord user = userRepository.createUser("original");
        String newUsername = "updated";
        
        // When
        boolean updated = userRepository.updateUsername(user.getId(), newUsername);
        
        // Then
        assertThat(updated).isTrue();
        
        // Verify update
        Optional<UsersRecord> found = userRepository.findById(user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo(newUsername);
    }

    @Test
    void testUpdateUsernameNotFound() {
        // When
        boolean updated = userRepository.updateUsername("non-existent", "newname");
        
        // Then
        assertThat(updated).isFalse();
    }

    @Test
    void testUpdateUsernameDuplicate() {
        // Given
        userRepository.createUser("user1");
        UsersRecord user2 = userRepository.createUser("user2");
        
        // When/Then
        assertThatThrownBy(() -> userRepository.updateUsername(user2.getId(), "user1"))
            .isInstanceOf(DuplicateUsernameException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void testDeleteById() {
        // Given
        UsersRecord user = userRepository.createUser("todelete");
        
        // When
        boolean deleted = userRepository.deleteById(user.getId());
        
        // Then
        assertThat(deleted).isTrue();
        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(getRowCount(USERS)).isZero();
    }

    @Test
    void testDeleteByIdNotFound() {
        // When
        boolean deleted = userRepository.deleteById("non-existent");
        
        // Then
        assertThat(deleted).isFalse();
    }

    @Test
    void testExistsByUsername() {
        // Given
        String username = "exists";
        userRepository.createUser(username);
        
        // When/Then
        assertThat(userRepository.existsByUsername(username)).isTrue();
        assertThat(userRepository.existsByUsername("nonexistent")).isFalse();
    }

    @Test
    void testFindUsersCreatedBetween() {
        // Given
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        
        userRepository.createUser("inrange1");
        userRepository.createUser("inrange2");
        
        // When
        List<UsersRecord> usersInRange = userRepository.findUsersCreatedBetween(start, end);
        
        // Then
        assertThat(usersInRange).hasSize(2);
        assertThat(usersInRange).extracting(UsersRecord::getUsername)
            .containsExactlyInAnyOrder("inrange1", "inrange2"); // Order may vary due to timing
    }

    @Test
    void testTransactionalBehavior() {
        // This test demonstrates that each test method starts with a clean database
        // and any changes are isolated between tests
        
        // Given - this should be empty due to cleanup after previous tests
        assertThat(getRowCount(USERS)).isZero();
        
        // When
        userRepository.createUser("transactional");
        
        // Then
        assertThat(getRowCount(USERS)).isEqualTo(1);
        
        // After this test completes, BaseJooqDatabaseTest will clean up the table
    }

    @Test
    void testComplexJooqQuery() {
        // Demonstrate more complex jOOQ DSL usage directly in test
        
        // Given
        userRepository.createUser("alpha");
        userRepository.createUser("beta");
        userRepository.createUser("gamma");
        
        // When - direct jOOQ query to find users with names starting with specific letter
        List<UsersRecord> usersStartingWithB = dslContext
            .selectFrom(USERS)
            .where(USERS.USERNAME.like("b%"))
            .orderBy(USERS.USERNAME)
            .fetch();
        
        // Then
        assertThat(usersStartingWithB).hasSize(1);
        assertThat(usersStartingWithB.get(0).getUsername()).isEqualTo("beta");
    }
}