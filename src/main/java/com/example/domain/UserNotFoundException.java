package com.example.domain;

/**
 * Business exception thrown when a requested user cannot be found.
 * 
 * This exception represents a business scenario where a user lookup operation
 * fails to find the requested user by ID or username. It extends RuntimeException
 * to avoid forcing callers to handle checked exceptions for common lookup failures.
 * 
 * Usage scenarios:
 * - User lookup by non-existent ID
 * - User lookup by non-existent username
 * - User profile access for deleted users
 * - API requests with invalid user references
 * 
 * HTTP mapping: This exception should be mapped to HTTP 404 Not Found status.
 */
public class UserNotFoundException extends RuntimeException {
    
    /**
     * Constructs a new UserNotFoundException with the specified detail message.
     * 
     * @param message the detail message explaining which user was not found
     */
    public UserNotFoundException(String message) {
        super(message);
    }
}