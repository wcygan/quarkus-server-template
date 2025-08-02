package com.example.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for user creation requests.
 * 
 * This record represents the input contract for creating new users via the REST API.
 * It includes comprehensive Bean Validation annotations to ensure data quality
 * and prevent invalid user creation attempts.
 * 
 * Validation rules:
 * - Username is required and cannot be blank
 * - Username length must be between 3 and 50 characters
 * - Username can only contain alphanumeric characters, hyphens, and underscores
 * 
 * Design decisions:
 * - Bean Validation annotations are applied to DTOs, not domain objects
 * - Strict pattern matching prevents problematic usernames
 * - Clear, user-friendly validation messages
 */
public record CreateUserRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain alphanumeric characters, hyphens, and underscores")
    String username
) {}