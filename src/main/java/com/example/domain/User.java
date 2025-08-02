package com.example.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Core domain entity representing a user in the system.
 * 
 * This record represents the immutable domain model for users, enforcing
 * business rules and constraints at the domain level. It uses Java records
 * for immutability and includes validation in the compact constructor.
 * 
 * Design decisions:
 * - Uses UUID for globally unique identifiers
 * - Enforces non-null constraints for all fields
 * - Validates username is not blank
 * - Uses Instant for precise timestamp handling
 */
public record User(
    UUID id,
    String username,
    Instant createdAt
) {
    /**
     * Compact constructor with validation.
     * Ensures all business rules are enforced at creation time.
     */
    public User {
        Objects.requireNonNull(id, "User ID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        
        if (username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
    }
}