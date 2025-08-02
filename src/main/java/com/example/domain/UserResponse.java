package com.example.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Data Transfer Object for user API responses.
 * 
 * This record represents the output contract for user data returned by the REST API.
 * It includes explicit Jackson annotations for consistent JSON serialization
 * and provides a factory method for easy conversion from domain objects.
 * 
 * JSON format decisions:
 * - ID is serialized as string (UUID as string is more portable)
 * - Timestamps use ISO-8601 format with 'Z' timezone indicator
 * - Explicit property names for API contract stability
 * 
 * Design decisions:
 * - Separate from domain model to allow independent evolution
 * - Factory method pattern for clean domain-to-DTO conversion
 * - Explicit JSON annotations prevent accidental API changes
 */
public record UserResponse(
    @JsonProperty("id")
    String id,
    
    @JsonProperty("username")
    String username,
    
    @JsonProperty("createdAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    Instant createdAt
) {
    /**
     * Factory method to create UserResponse from domain User object.
     * 
     * @param user the domain user object
     * @return UserResponse DTO with converted data
     */
    public static UserResponse from(User user) {
        return new UserResponse(
            user.id().toString(),
            user.username(),
            user.createdAt()
        );
    }
}