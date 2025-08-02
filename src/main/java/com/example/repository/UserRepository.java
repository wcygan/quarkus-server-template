package com.example.repository;

import com.example.generated.jooq.tables.records.UsersRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.generated.jooq.Tables.USERS;

/**
 * Repository for User entity operations using jOOQ type-safe queries.
 * 
 * Features:
 * - Type-safe SQL operations using jOOQ DSL
 * - UUID-based primary keys with server-side generation
 * - Proper exception handling and logging
 * - CDI integration with @ApplicationScoped
 * - Comprehensive CRUD operations
 * 
 * This class demonstrates best practices for jOOQ repository pattern:
 * - Injected DSLContext for database operations
 * - Use of generated table references (USERS)
 * - Type-safe query construction
 * - Proper error handling with custom exceptions
 */
@ApplicationScoped
public class UserRepository {

    private static final Logger LOG = LoggerFactory.getLogger(UserRepository.class);

    @Inject
    DSLContext dsl;

    /**
     * Creates a new user with server-generated UUID.
     * 
     * @param username the unique username
     * @return the created user record
     * @throws DuplicateUsernameException if username already exists
     * @throws DataAccessException if database operation fails
     */
    public UsersRecord createUser(String username) {
        LOG.debug("Creating user with username: {}", username);
        
        try {
            String userId = UUID.randomUUID().toString();
            
            UsersRecord record = dsl.insertInto(USERS)
                .set(USERS.ID, userId)
                .set(USERS.USERNAME, username)
                .set(USERS.CREATED_AT, LocalDateTime.now())
                .returning()
                .fetchOne();
            
            if (record == null) {
                throw new DataAccessException("Failed to create user - no record returned");
            }
            
            LOG.info("Created user: id={}, username={}", record.getId(), record.getUsername());
            return record;
            
        } catch (org.jooq.exception.DataAccessException e) {
            if (isDuplicateKeyError(e)) {
                LOG.warn("Attempted to create user with duplicate username: {}", username);
                throw new DuplicateUsernameException("Username '" + username + "' already exists");
            }
            LOG.error("Failed to create user: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Finds a user by their unique ID.
     * 
     * @param userId the user ID
     * @return Optional containing the user if found
     */
    public Optional<UsersRecord> findById(String userId) {
        LOG.debug("Finding user by ID: {}", userId);
        
        try {
            UsersRecord record = dsl.selectFrom(USERS)
                .where(USERS.ID.eq(userId))
                .fetchOne();
            
            return Optional.ofNullable(record);
            
        } catch (DataAccessException e) {
            LOG.error("Failed to find user by ID {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Finds a user by their unique username.
     * 
     * @param username the username
     * @return Optional containing the user if found
     */
    public Optional<UsersRecord> findByUsername(String username) {
        LOG.debug("Finding user by username: {}", username);
        
        try {
            UsersRecord record = dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOne();
            
            return Optional.ofNullable(record);
            
        } catch (DataAccessException e) {
            LOG.error("Failed to find user by username {}: {}", username, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Retrieves all users with pagination support.
     * 
     * @param offset the number of records to skip
     * @param limit the maximum number of records to return
     * @return list of user records
     */
    public List<UsersRecord> findAll(int offset, int limit) {
        LOG.debug("Finding all users with offset={}, limit={}", offset, limit);
        
        try {
            return dsl.selectFrom(USERS)
                .orderBy(USERS.CREATED_AT.desc())
                .offset(offset)
                .limit(limit)
                .fetch();
            
        } catch (DataAccessException e) {
            LOG.error("Failed to find all users: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Counts total number of users.
     * 
     * @return total user count
     */
    public int countUsers() {
        LOG.debug("Counting total users");
        
        try {
            return dsl.selectCount()
                .from(USERS)
                .fetchOne(0, int.class);
            
        } catch (DataAccessException e) {
            LOG.error("Failed to count users: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Updates a user's username.
     * 
     * @param userId the user ID
     * @param newUsername the new username
     * @return true if user was updated, false if not found
     * @throws DuplicateUsernameException if new username already exists
     */
    public boolean updateUsername(String userId, String newUsername) {
        LOG.debug("Updating username for user {}: {}", userId, newUsername);
        
        try {
            int updatedRows = dsl.update(USERS)
                .set(USERS.USERNAME, newUsername)
                .where(USERS.ID.eq(userId))
                .execute();
            
            boolean updated = updatedRows > 0;
            if (updated) {
                LOG.info("Updated username for user {}: {}", userId, newUsername);
            } else {
                LOG.warn("No user found with ID {} to update", userId);
            }
            
            return updated;
            
        } catch (org.jooq.exception.DataAccessException e) {
            if (isDuplicateKeyError(e)) {
                LOG.warn("Attempted to update user with duplicate username: {}", newUsername);
                throw new DuplicateUsernameException("Username '" + newUsername + "' already exists");
            }
            LOG.error("Failed to update user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Deletes a user by ID.
     * 
     * @param userId the user ID
     * @return true if user was deleted, false if not found
     */
    public boolean deleteById(String userId) {
        LOG.debug("Deleting user by ID: {}", userId);
        
        try {
            int deletedRows = dsl.deleteFrom(USERS)
                .where(USERS.ID.eq(userId))
                .execute();
            
            boolean deleted = deletedRows > 0;
            if (deleted) {
                LOG.info("Deleted user: {}", userId);
            } else {
                LOG.warn("No user found with ID {} to delete", userId);
            }
            
            return deleted;
            
        } catch (DataAccessException e) {
            LOG.error("Failed to delete user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Checks if a username already exists.
     * 
     * @param username the username to check
     * @return true if username exists
     */
    public boolean existsByUsername(String username) {
        LOG.debug("Checking if username exists: {}", username);
        
        try {
            return dsl.fetchExists(
                dsl.selectOne()
                   .from(USERS)
                   .where(USERS.USERNAME.eq(username))
            );
            
        } catch (DataAccessException e) {
            LOG.error("Failed to check username existence {}: {}", username, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Demonstrates complex jOOQ query with multiple conditions.
     * Finds users created within a date range.
     * 
     * @param startDate start of date range
     * @param endDate end of date range
     * @return list of users created in the date range
     */
    public List<UsersRecord> findUsersCreatedBetween(LocalDateTime startDate, LocalDateTime endDate) {
        LOG.debug("Finding users created between {} and {}", startDate, endDate);
        
        try {
            return dsl.selectFrom(USERS)
                .where(USERS.CREATED_AT.between(startDate, endDate))
                .orderBy(USERS.CREATED_AT.asc())
                .fetch();
            
        } catch (DataAccessException e) {
            LOG.error("Failed to find users by date range: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Helper method to check if a database error is due to duplicate key constraint.
     * MySQL-specific error code checking.
     */
    private boolean isDuplicateKeyError(org.jooq.exception.DataAccessException e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("duplicate entry") || 
               message.contains("unique constraint") ||
               e.sqlState() != null && e.sqlState().equals("23000");
    }

    /**
     * Custom exception for duplicate username attempts.
     */
    public static class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException(String message) {
            super(message);
        }
    }
}