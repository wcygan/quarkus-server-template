package com.example.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CreateUserRequest DTO.
 * 
 * Tests Bean Validation annotations, constraint violation messages,
 * and valid input scenarios.
 */
@DisplayName("CreateUserRequest DTO Validation Tests")
class CreateUserRequestTest {
    
    private Validator validator;
    
    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }
    
    @Test
    @DisplayName("Should create valid request with proper username")
    void shouldCreateValidRequest() {
        // Given
        String validUsername = "testuser";
        
        // When
        CreateUserRequest request = new CreateUserRequest(validUsername);
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        
        // Then
        assertTrue(violations.isEmpty(), "Valid username should not have violations");
        assertEquals(validUsername, request.username());
    }
    
    @Test
    @DisplayName("Should reject null username")
    void shouldRejectNullUsername() {
        // Given
        CreateUserRequest request = new CreateUserRequest(null);
        
        // When
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        
        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CreateUserRequest> violation = violations.iterator().next();
        assertEquals("Username is required", violation.getMessage());
        assertEquals("username", violation.getPropertyPath().toString());
    }
    
    @Test
    @DisplayName("Should reject blank username")
    void shouldRejectBlankUsername() {
        // Given
        CreateUserRequest request = new CreateUserRequest("");
        
        // When
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        
        // Then
        assertFalse(violations.isEmpty(), "Should have validation violations");
        // Blank username triggers @NotBlank, @Size, and @Pattern violations
        assertTrue(violations.size() >= 1, "Should have at least one violation");
        
        // Check that @NotBlank violation is present
        boolean hasNotBlankViolation = violations.stream()
            .anyMatch(v -> v.getMessage().equals("Username is required"));
        assertTrue(hasNotBlankViolation, "Should have @NotBlank violation");
    }
    
    @Test
    @DisplayName("Should reject whitespace-only username")
    void shouldRejectWhitespaceUsername() {
        // Given
        CreateUserRequest request = new CreateUserRequest("   ");
        
        // When
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        
        // Then
        assertFalse(violations.isEmpty(), "Should have validation violations");
        // Whitespace username triggers @NotBlank and @Pattern violations
        assertTrue(violations.size() >= 1, "Should have at least one violation");
        
        // Check that @NotBlank violation is present
        boolean hasNotBlankViolation = violations.stream()
            .anyMatch(v -> v.getMessage().equals("Username is required"));
        assertTrue(hasNotBlankViolation, "Should have @NotBlank violation");
    }
    
    @Test
    @DisplayName("Should reject username too short")
    void shouldRejectUsernameTooShort() {
        // Given
        CreateUserRequest request = new CreateUserRequest("ab");  // 2 characters, min is 3
        
        // When
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        
        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CreateUserRequest> violation = violations.iterator().next();
        assertEquals("Username must be between 3 and 50 characters", violation.getMessage());
    }
    
    @Test
    @DisplayName("Should reject username too long")
    void shouldRejectUsernameTooLong() {
        // Given
        String longUsername = "a".repeat(51);  // 51 characters, max is 50
        CreateUserRequest request = new CreateUserRequest(longUsername);
        
        // When
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        
        // Then
        assertEquals(1, violations.size());
        ConstraintViolation<CreateUserRequest> violation = violations.iterator().next();
        assertEquals("Username must be between 3 and 50 characters", violation.getMessage());
    }
    
    @Test
    @DisplayName("Should accept username at minimum length boundary")
    void shouldAcceptMinimumLengthUsername() {
        // Given
        CreateUserRequest request = new CreateUserRequest("abc");  // exactly 3 characters
        
        // When
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        
        // Then
        assertTrue(violations.isEmpty(), "3-character username should be valid");
    }
    
    @Test
    @DisplayName("Should accept username at maximum length boundary")
    void shouldAcceptMaximumLengthUsername() {
        // Given
        String maxUsername = "a".repeat(50);  // exactly 50 characters
        CreateUserRequest request = new CreateUserRequest(maxUsername);
        
        // When
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        
        // Then
        assertTrue(violations.isEmpty(), "50-character username should be valid");
    }
    
    @Test
    @DisplayName("Should reject username with invalid characters")
    void shouldRejectInvalidCharacters() {
        // Given
        String[] invalidUsernames = {
            "user@domain",      // @ symbol
            "user.name",        // dot
            "user name",        // space
            "user!",           // exclamation
            "user#123",        // hash
            "user$",           // dollar sign
            "user%",           // percent
            "user&co",         // ampersand
            "user*",           // asterisk
            "user+plus",       // plus
            "user=equal",      // equals
            "user?question",   // question mark
            "user[bracket]",   // brackets
            "user{brace}",     // braces
            "user|pipe",       // pipe
            "user\\backslash", // backslash
            "user/slash",      // forward slash
            "user:colon",      // colon
            "user;semicolon",  // semicolon
            "user\"quote",     // double quote
            "user'quote",      // single quote
            "user<less>",      // angle brackets
            "user,comma"       // comma
        };
        
        // When & Then
        for (String invalidUsername : invalidUsernames) {
            CreateUserRequest request = new CreateUserRequest(invalidUsername);
            Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
            
            assertEquals(1, violations.size(), 
                "Username '" + invalidUsername + "' should be invalid");
            ConstraintViolation<CreateUserRequest> violation = violations.iterator().next();
            assertEquals("Username can only contain alphanumeric characters, hyphens, and underscores", 
                violation.getMessage());
        }
    }
    
    @Test
    @DisplayName("Should accept username with valid characters")
    void shouldAcceptValidCharacters() {
        // Given
        String[] validUsernames = {
            "user123",         // alphanumeric
            "test_user",       // underscore
            "user-name",       // hyphen
            "User123",         // mixed case
            "USER_NAME",       // uppercase with underscore
            "user-123_test",   // combination of allowed characters
            "123user",         // starting with numbers
            "a_b_c",          // multiple underscores
            "a-b-c",          // multiple hyphens
            "_user",          // starting with underscore
            "-user",          // starting with hyphen
            "user_",          // ending with underscore
            "user-"           // ending with hyphen
        };
        
        // When & Then
        for (String validUsername : validUsernames) {
            CreateUserRequest request = new CreateUserRequest(validUsername);
            Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
            
            assertTrue(violations.isEmpty(), 
                "Username '" + validUsername + "' should be valid but got violations: " + violations);
        }
    }
    
    @Test
    @DisplayName("Should handle multiple validation errors")
    void shouldHandleMultipleValidationErrors() {
        // Given - username that's both too short and has invalid characters
        CreateUserRequest request = new CreateUserRequest("a@");  // 2 chars + invalid char
        
        // When
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);
        
        // Then
        assertEquals(2, violations.size(), "Should have both length and pattern violations");
        
        boolean hasLengthViolation = violations.stream()
            .anyMatch(v -> v.getMessage().contains("between 3 and 50 characters"));
        boolean hasPatternViolation = violations.stream()
            .anyMatch(v -> v.getMessage().contains("alphanumeric characters"));
            
        assertTrue(hasLengthViolation, "Should have length violation");
        assertTrue(hasPatternViolation, "Should have pattern violation");
    }
}