package com.example.domain;

/**
 * Business exception thrown when attempting to create a user with a username that already exists.
 * 
 * This exception represents a business rule violation where username uniqueness is required.
 * It extends RuntimeException to avoid forcing callers to handle checked exceptions for
 * business logic violations.
 * 
 * Usage scenarios:
 * - User registration with existing username
 * - Username change to an already taken name
 * - Bulk user import with duplicate usernames
 * 
 * HTTP mapping: This exception should be mapped to HTTP 409 Conflict status.
 */
public class DuplicateUsernameException extends RuntimeException {
    
    /**
     * Constructs a new DuplicateUsernameException with the specified detail message.
     * 
     * @param message the detail message explaining the duplicate username violation
     */
    public DuplicateUsernameException(String message) {
        super(message);
    }
}