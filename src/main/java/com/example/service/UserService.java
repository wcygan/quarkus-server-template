package com.example.service;

import com.example.domain.CreateUserRequest;
import com.example.domain.DuplicateUsernameException;
import com.example.domain.User;
import com.example.domain.UserNotFoundException;
import com.example.domain.UserResponse;
import com.example.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Service layer for user management operations.
 * 
 * This service implements the business logic for user operations, including:
 * - User creation with validation and duplicate checking
 * - User retrieval by ID and username
 * - Transaction boundary management
 * - Business rule enforcement
 * - Comprehensive logging and error handling
 * 
 * Design decisions:
 * - Service layer defines transaction boundaries using @Transactional
 * - Business validation performed before repository calls
 * - Repository exceptions converted to appropriate domain exceptions
 * - All operations logged for audit and debugging purposes
 * - Uses CDI @ApplicationScoped for efficient bean management
 */
@ApplicationScoped
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    @Inject
    UserRepository userRepository;

    /**
     * Creates a new user with the provided username.
     * 
     * This method performs the following operations:
     * 1. Validates the username is not already taken (business rule)
     * 2. Creates the user in the database within a transaction
     * 3. Returns the created user as a response DTO
     * 
     * @param request the user creation request containing the username
     * @return UserResponse representing the created user
     * @throws DuplicateUsernameException if the username already exists
     * @throws IllegalArgumentException if the request is invalid
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CreateUserRequest cannot be null");
        }

        String username = request.username();
        LOG.info("Creating user with username: {}", username);

        // Business validation: check for duplicate username before attempting creation
        if (userRepository.existsByUsername(username)) {
            LOG.warn("Attempt to create user with existing username: {}", username);
            throw new DuplicateUsernameException("Username already exists: " + username);
        }

        try {
            // Create user in repository (within transaction boundary)
            User user = userRepository.createUser(username);
            LOG.info("Successfully created user: {} with ID: {}", user.username(), user.id());
            
            // Convert domain object to response DTO
            return UserResponse.from(user);
            
        } catch (DuplicateUsernameException e) {
            // Re-throw business exceptions as-is
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to create user: {}", username, e);
            throw new RuntimeException("Failed to create user: " + username, e);
        }
    }

    /**
     * Retrieves a user by their unique ID.
     * 
     * @param id the unique user identifier
     * @return UserResponse representing the found user
     * @throws UserNotFoundException if no user exists with the given ID
     * @throws IllegalArgumentException if the ID is null
     */
    public UserResponse getUserById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        LOG.debug("Retrieving user by ID: {}", id);

        try {
            User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.warn("User not found with ID: {}", id);
                    return new UserNotFoundException("User not found with ID: " + id);
                });

            LOG.debug("Successfully retrieved user: {} (ID: {})", user.username(), user.id());
            return UserResponse.from(user);
            
        } catch (UserNotFoundException e) {
            // Re-throw business exceptions as-is
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to retrieve user by ID: {}", id, e);
            throw new RuntimeException("Failed to retrieve user by ID: " + id, e);
        }
    }

    /**
     * Retrieves a user by their unique username.
     * 
     * @param username the unique username
     * @return UserResponse representing the found user
     * @throws UserNotFoundException if no user exists with the given username
     * @throws IllegalArgumentException if the username is null or blank
     */
    public UserResponse getUserByUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }

        LOG.debug("Retrieving user by username: {}", username);

        try {
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    LOG.warn("User not found with username: {}", username);
                    return new UserNotFoundException("User not found with username: " + username);
                });

            LOG.debug("Successfully retrieved user: {} (ID: {})", user.username(), user.id());
            return UserResponse.from(user);
            
        } catch (UserNotFoundException e) {
            // Re-throw business exceptions as-is
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to retrieve user by username: {}", username, e);
            throw new RuntimeException("Failed to retrieve user by username: " + username, e);
        }
    }

    /**
     * Checks if a username is available (not already taken).
     * 
     * This method can be used for username validation before user creation
     * or for real-time availability checking in user interfaces.
     * 
     * @param username the username to check
     * @return true if the username is available, false if it's already taken
     * @throws IllegalArgumentException if the username is null or blank
     */
    public boolean isUsernameAvailable(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }

        LOG.debug("Checking username availability: {}", username);
        
        try {
            boolean exists = userRepository.existsByUsername(username);
            boolean available = !exists;
            
            LOG.debug("Username '{}' availability: {}", username, available ? "available" : "taken");
            return available;
            
        } catch (Exception e) {
            LOG.error("Failed to check username availability: {}", username, e);
            throw new RuntimeException("Failed to check username availability: " + username, e);
        }
    }
}