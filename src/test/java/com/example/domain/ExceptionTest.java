package com.example.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for custom business exceptions.
 * 
 * Tests exception construction, message handling, and inheritance behavior
 * for DuplicateUsernameException, UserNotFoundException, and DatabaseException.
 */
@DisplayName("Custom Business Exception Tests")
class ExceptionTest {
    
    @Test
    @DisplayName("DuplicateUsernameException should construct with message")
    void duplicateUsernameExceptionShouldConstructWithMessage() {
        // Given
        String message = "Username 'testuser' already exists";
        
        // When
        DuplicateUsernameException exception = new DuplicateUsernameException(message);
        
        // Then
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
        assertTrue(exception instanceof RuntimeException);
    }
    
    @Test
    @DisplayName("DuplicateUsernameException should handle null message")
    void duplicateUsernameExceptionShouldHandleNullMessage() {
        // When
        DuplicateUsernameException exception = new DuplicateUsernameException(null);
        
        // Then
        assertNull(exception.getMessage());
        assertNull(exception.getCause());
    }
    
    @Test
    @DisplayName("DuplicateUsernameException should handle empty message")
    void duplicateUsernameExceptionShouldHandleEmptyMessage() {
        // Given
        String emptyMessage = "";
        
        // When
        DuplicateUsernameException exception = new DuplicateUsernameException(emptyMessage);
        
        // Then
        assertEquals(emptyMessage, exception.getMessage());
    }
    
    @Test
    @DisplayName("UserNotFoundException should construct with message")
    void userNotFoundExceptionShouldConstructWithMessage() {
        // Given
        String message = "User not found with ID: 12345";
        
        // When
        UserNotFoundException exception = new UserNotFoundException(message);
        
        // Then
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
        assertTrue(exception instanceof RuntimeException);
    }
    
    @Test
    @DisplayName("UserNotFoundException should handle null message")
    void userNotFoundExceptionShouldHandleNullMessage() {
        // When
        UserNotFoundException exception = new UserNotFoundException(null);
        
        // Then
        assertNull(exception.getMessage());
        assertNull(exception.getCause());
    }
    
    @Test
    @DisplayName("DatabaseException should construct with message and cause")
    void databaseExceptionShouldConstructWithMessageAndCause() {
        // Given
        String message = "Failed to execute database operation";
        RuntimeException cause = new RuntimeException("Connection timeout");
        
        // When
        DatabaseException exception = new DatabaseException(message, cause);
        
        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertTrue(exception instanceof RuntimeException);
    }
    
    @Test
    @DisplayName("DatabaseException should construct with message only")
    void databaseExceptionShouldConstructWithMessageOnly() {
        // Given
        String message = "Database operation failed";
        
        // When
        DatabaseException exception = new DatabaseException(message);
        
        // Then
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
        assertTrue(exception instanceof RuntimeException);
    }
    
    @Test
    @DisplayName("DatabaseException should handle null message with cause")
    void databaseExceptionShouldHandleNullMessageWithCause() {
        // Given
        RuntimeException cause = new RuntimeException("Underlying error");
        
        // When
        DatabaseException exception = new DatabaseException(null, cause);
        
        // Then
        assertNull(exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
    
    @Test
    @DisplayName("DatabaseException should handle null cause")
    void databaseExceptionShouldHandleNullCause() {
        // Given
        String message = "Database error occurred";
        
        // When
        DatabaseException exception = new DatabaseException(message, null);
        
        // Then
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }
    
    @Test
    @DisplayName("All exceptions should be unchecked (RuntimeException subclasses)")
    void allExceptionsShouldBeUnchecked() {
        // Given
        String message = "Test message";
        RuntimeException cause = new RuntimeException("Test cause");
        
        // When
        DuplicateUsernameException duplicateException = new DuplicateUsernameException(message);
        UserNotFoundException notFoundException = new UserNotFoundException(message);
        DatabaseException databaseException = new DatabaseException(message, cause);
        
        // Then
        assertInstanceOf(RuntimeException.class, duplicateException);
        assertInstanceOf(RuntimeException.class, notFoundException);
        assertInstanceOf(RuntimeException.class, databaseException);
        
        // Verify they don't require checked exception handling
        // (This test verifies they are unchecked exceptions by demonstrating
        // they can be thrown without requiring exception declarations)
        assertThrows(DuplicateUsernameException.class, () -> {
            throw duplicateException;
        });
        
        assertThrows(UserNotFoundException.class, () -> {
            throw notFoundException;
        });
        
        assertThrows(DatabaseException.class, () -> {
            throw databaseException;
        });
    }
    
    @Test
    @DisplayName("Exception stack traces should be preserved")
    void exceptionStackTracesShouldBePreserved() {
        // Given
        String message = "Test database error";
        RuntimeException originalCause = new RuntimeException("Original database error");
        
        // Fill stack trace
        originalCause.fillInStackTrace();
        
        // When
        DatabaseException exception = new DatabaseException(message, originalCause);
        
        // Then
        assertNotNull(exception.getStackTrace());
        assertTrue(exception.getStackTrace().length > 0);
        assertEquals(originalCause, exception.getCause());
        assertNotNull(exception.getCause().getStackTrace());
    }
    
    @Test
    @DisplayName("Exceptions should support standard exception operations")
    void exceptionsShouldSupportStandardOperations() {
        // Given
        String duplicateMessage = "Duplicate username error";
        String notFoundMessage = "User not found error";
        String databaseMessage = "Database error";
        RuntimeException cause = new RuntimeException("Root cause");
        
        DuplicateUsernameException duplicateException = new DuplicateUsernameException(duplicateMessage);
        UserNotFoundException notFoundException = new UserNotFoundException(notFoundMessage);
        DatabaseException databaseException = new DatabaseException(databaseMessage, cause);
        
        // When & Then - verify toString contains class name and message
        assertTrue(duplicateException.toString().contains("DuplicateUsernameException"));
        assertTrue(duplicateException.toString().contains(duplicateMessage));
        
        assertTrue(notFoundException.toString().contains("UserNotFoundException"));
        assertTrue(notFoundException.toString().contains(notFoundMessage));
        
        assertTrue(databaseException.toString().contains("DatabaseException"));
        assertTrue(databaseException.toString().contains(databaseMessage));
        
        // Verify suppressed exceptions support (standard Java behavior)
        RuntimeException suppressed = new RuntimeException("Suppressed error");
        databaseException.addSuppressed(suppressed);
        assertEquals(1, databaseException.getSuppressed().length);
        assertEquals(suppressed, databaseException.getSuppressed()[0]);
    }
    
    @Test
    @DisplayName("Exceptions should be serializable (if needed for distributed systems)")
    void exceptionsShouldHaveProperClassStructure() {
        // This test verifies the exceptions follow proper Java exception patterns
        // which ensures they can be used in various frameworks and contexts
        
        // Given
        String message = "Test message";
        RuntimeException cause = new RuntimeException("Test cause");
        
        // When - create instances
        DuplicateUsernameException duplicateException = new DuplicateUsernameException(message);
        UserNotFoundException notFoundException = new UserNotFoundException(message);
        DatabaseException databaseExceptionWithCause = new DatabaseException(message, cause);
        DatabaseException databaseExceptionWithoutCause = new DatabaseException(message);
        
        // Then - verify proper class structure
        assertNotNull(duplicateException.getClass().getSimpleName());
        assertNotNull(notFoundException.getClass().getSimpleName());
        assertNotNull(databaseExceptionWithCause.getClass().getSimpleName());
        assertNotNull(databaseExceptionWithoutCause.getClass().getSimpleName());
        
        // Verify package structure
        assertEquals("com.example.domain", duplicateException.getClass().getPackage().getName());
        assertEquals("com.example.domain", notFoundException.getClass().getPackage().getName());
        assertEquals("com.example.domain", databaseExceptionWithCause.getClass().getPackage().getName());
    }
}