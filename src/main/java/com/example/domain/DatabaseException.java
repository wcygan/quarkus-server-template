package com.example.domain;

/**
 * Technical exception thrown when database operations fail due to infrastructure issues.
 * 
 * This exception represents technical failures in the data access layer that are not
 * related to business rule violations. It wraps underlying database exceptions to provide
 * a consistent exception hierarchy for the application layer.
 * 
 * Usage scenarios:
 * - Database connection failures
 * - SQL execution errors
 * - Transaction rollback failures
 * - Database constraint violations (non-business related)
 * - Timeout exceptions
 * 
 * HTTP mapping: This exception should be mapped to HTTP 500 Internal Server Error status.
 * 
 * Design decisions:
 * - Extends RuntimeException to avoid checked exception handling overhead
 * - Includes original cause for debugging and error reporting
 * - Separates technical failures from business rule violations
 */
public class DatabaseException extends RuntimeException {
    
    /**
     * Constructs a new DatabaseException with the specified detail message and cause.
     * 
     * @param message the detail message explaining the database operation failure
     * @param cause the underlying exception that caused this database failure
     */
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Constructs a new DatabaseException with the specified detail message.
     * 
     * @param message the detail message explaining the database operation failure
     */
    public DatabaseException(String message) {
        super(message);
    }
}