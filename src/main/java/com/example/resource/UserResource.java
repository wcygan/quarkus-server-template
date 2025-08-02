package com.example.resource;

import com.example.domain.CreateUserRequest;
import com.example.domain.UserResponse;
import com.example.service.UserService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.UUID;

/**
 * REST resource for user management operations.
 * 
 * This resource provides RESTful endpoints for user operations following JAX-RS standards
 * and Quarkus RESTEasy Reactive patterns. It implements the API specification defined
 * in PHASE-2.md with proper HTTP semantics, validation, and error handling.
 * 
 * API Endpoints:
 * - POST /api/users - Create a new user (returns 201 with Location header)
 * - GET /api/users/{id} - Get user by UUID (returns 200 or 404)
 * - GET /api/users?username=X - Get user by username (returns 200 or 404)
 * 
 * HTTP Status Codes:
 * - 200 OK: Successful retrieval
 * - 201 Created: Successful creation with Location header
 * - 400 Bad Request: Validation errors or malformed UUID
 * - 404 Not Found: User not found
 * - 409 Conflict: Duplicate username
 * 
 * Design decisions:
 * - Uses RESTEasy Reactive for better performance
 * - Bean Validation integration with @Valid annotation
 * - Proper Location header for POST requests following REST standards
 * - UUID path parameter handling with automatic conversion
 * - Query parameter validation for username searches
 * - Comprehensive logging for audit and debugging
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);

    @Inject
    UserService userService;

    /**
     * Creates a new user.
     * 
     * This endpoint accepts a JSON request with user details and creates a new user
     * in the system. Upon successful creation, it returns the created user data
     * along with a Location header pointing to the new user resource.
     * 
     * Request body validation:
     * - Username is required and cannot be blank
     * - Username must be 3-50 characters long
     * - Username can only contain alphanumeric characters, hyphens, and underscores
     * 
     * @param request the user creation request containing username
     * @return Response with 201 status, Location header, and created user data
     * @throws BadRequestException if validation fails (400)
     * @throws ConflictException if username already exists (409)
     */
    @POST
    public Response createUser(@Valid CreateUserRequest request) {
        LOG.info("Received user creation request for username: {}", request.username());
        
        try {
            // Create user through service layer
            UserResponse userResponse = userService.createUser(request);
            
            // Build Location header pointing to the created user resource
            URI location = UriBuilder.fromPath("/api/users/{id}")
                .build(userResponse.id());
            
            LOG.info("Successfully created user with ID: {} and username: {}", 
                userResponse.id(), userResponse.username());
            
            // Return 201 Created with Location header and user data
            return Response.created(location)
                .entity(userResponse)
                .build();
                
        } catch (Exception e) {
            LOG.error("Failed to create user with username: {}", request.username(), e);
            throw e; // Let global exception mapper handle it
        }
    }

    /**
     * Retrieves a user by their unique ID.
     * 
     * This endpoint accepts a UUID path parameter and returns the corresponding
     * user data if found. The UUID is automatically converted from string format
     * by JAX-RS parameter converters.
     * 
     * @param id the unique user identifier as UUID
     * @return UserResponse containing user data
     * @throws BadRequestException if UUID format is invalid (400)
     * @throws NotFoundException if user is not found (404)
     */
    @GET
    @Path("/{id}")
    public UserResponse getUserById(@PathParam("id") UUID id) {
        LOG.debug("Received request to get user by ID: {}", id);
        
        try {
            UserResponse userResponse = userService.getUserById(id);
            LOG.debug("Successfully retrieved user with ID: {}", id);
            return userResponse;
            
        } catch (Exception e) {
            LOG.debug("Failed to retrieve user with ID: {}", id, e);
            throw e; // Let global exception mapper handle it
        }
    }

    /**
     * Retrieves a user by their username.
     * 
     * This endpoint accepts a username as a query parameter and returns the
     * corresponding user data if found. The username parameter is required
     * and cannot be blank.
     * 
     * Usage: GET /api/users?username=johndoe
     * 
     * @param username the unique username to search for
     * @return UserResponse containing user data
     * @throws BadRequestException if username parameter is missing or blank (400)
     * @throws NotFoundException if user is not found (404)
     */
    @GET
    public UserResponse getUserByUsername(
        @QueryParam("username") 
        @NotBlank(message = "Username query parameter is required and cannot be blank")
        String username) {
        
        LOG.debug("Received request to get user by username: {}", username);
        
        // Additional validation for query parameter
        if (username == null || username.isBlank()) {
            LOG.warn("Received request with missing or blank username parameter");
            throw new BadRequestException("Username query parameter is required and cannot be blank");
        }
        
        try {
            UserResponse userResponse = userService.getUserByUsername(username);
            LOG.debug("Successfully retrieved user with username: {}", username);
            return userResponse;
            
        } catch (Exception e) {
            LOG.debug("Failed to retrieve user with username: {}", username, e);
            throw e; // Let global exception mapper handle it
        }
    }
}