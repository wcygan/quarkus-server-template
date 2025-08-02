package com.example.resource;

import com.example.domain.DuplicateUsernameException;
import com.example.domain.UserNotFoundException;
import io.quarkus.logging.Log;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception mapper for handling all application exceptions.
 * 
 * This class provides centralized exception handling for the REST API, converting
 * business exceptions and validation errors into proper HTTP responses with
 * consistent error format. It follows the error response specification from
 * PHASE-2.md with timestamps and detailed error information.
 * 
 * Supported exception mappings:
 * - UserNotFoundException → 404 Not Found
 * - DuplicateUsernameException → 409 Conflict
 * - ConstraintViolationException → 400 Bad Request with validation details
 * - IllegalArgumentException → 400 Bad Request
 * - WebApplicationException → Original status code with enhanced error format
 * - Generic Exception → 500 Internal Server Error
 * 
 * Error Response Format:
 * {
 *   "error": "Error type description",
 *   "message": "Detailed error message",
 *   "timestamp": "2024-01-01T12:00:00Z",
 *   "violations": { ... } // Only for validation errors
 * }
 * 
 * Design decisions:
 * - Uses Quarkus RESTEasy Reactive @ServerExceptionMapper for performance
 * - Consistent error response format across all error types
 * - Includes timestamps for debugging and audit trails
 * - Detailed validation error reporting with field-level violations
 * - Comprehensive logging for debugging while avoiding information disclosure
 * - Handles both business exceptions and infrastructure exceptions
 */
public class GlobalExceptionMapper {

    /**
     * Maps UserNotFoundException to HTTP 404 Not Found.
     * 
     * This occurs when a user is requested by ID or username but doesn't exist
     * in the system. Returns a clear error message without exposing internal details.
     * 
     * @param exception the user not found exception
     * @return Response with 404 status and error details
     */
    @ServerExceptionMapper
    public Response mapUserNotFound(UserNotFoundException exception) {
        Log.debug("User not found: " + exception.getMessage());
        
        Map<String, Object> errorResponse = createErrorResponse(
            "User not found",
            exception.getMessage()
        );
        
        return Response.status(Response.Status.NOT_FOUND)
            .entity(errorResponse)
            .build();
    }

    /**
     * Maps DuplicateUsernameException to HTTP 409 Conflict.
     * 
     * This occurs when attempting to create a user with a username that already
     * exists in the system. Returns a conflict status indicating the resource
     * state conflict.
     * 
     * @param exception the duplicate username exception
     * @return Response with 409 status and error details
     */
    @ServerExceptionMapper
    public Response mapDuplicateUsername(DuplicateUsernameException exception) {
        Log.info("Duplicate username attempt: " + exception.getMessage());
        
        Map<String, Object> errorResponse = createErrorResponse(
            "Duplicate username",
            exception.getMessage()
        );
        
        return Response.status(Response.Status.CONFLICT)
            .entity(errorResponse)
            .build();
    }

    /**
     * Maps Bean Validation ConstraintViolationException to HTTP 400 Bad Request.
     * 
     * This occurs when input validation fails on request DTOs. Returns detailed
     * validation errors with field-level violation information to help clients
     * understand and fix validation issues.
     * 
     * @param exception the constraint violation exception
     * @return Response with 400 status and detailed validation errors
     */
    @ServerExceptionMapper
    public Response mapValidationError(ConstraintViolationException exception) {
        Log.debug("Validation error: " + exception.getMessage());
        
        // Extract field-level validation violations
        Map<String, String> violations = exception.getConstraintViolations()
            .stream()
            .collect(Collectors.toMap(
                violation -> getFieldName(violation),
                ConstraintViolation::getMessage,
                (existing, replacement) -> existing // Handle duplicate keys
            ));
        
        Map<String, Object> errorResponse = createErrorResponse(
            "Validation failed",
            "Request validation failed. Please check the provided data."
        );
        errorResponse.put("violations", violations);
        
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(errorResponse)
            .build();
    }

    /**
     * Maps IllegalArgumentException to HTTP 400 Bad Request.
     * 
     * This occurs when method arguments are invalid or when business logic
     * detects invalid input parameters. Returns a bad request status with
     * the specific error message.
     * 
     * @param exception the illegal argument exception
     * @return Response with 400 status and error details
     */
    @ServerExceptionMapper
    public Response mapIllegalArgument(IllegalArgumentException exception) {
        Log.debug("Illegal argument: " + exception.getMessage());
        
        Map<String, Object> errorResponse = createErrorResponse(
            "Invalid request",
            exception.getMessage()
        );
        
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(errorResponse)
            .build();
    }

    /**
     * Maps WebApplicationException to maintain original HTTP status.
     * 
     * This handles JAX-RS exceptions that already have appropriate HTTP status
     * codes (like BadRequestException, NotFoundException, etc.) while enhancing
     * the error response format for consistency.
     * 
     * @param exception the web application exception
     * @return Response with original status and enhanced error format
     */
    @ServerExceptionMapper
    public Response mapWebApplicationException(WebApplicationException exception) {
        Log.debug("Web application exception: " + exception.getMessage());
        
        String errorType = getErrorTypeFromStatus(exception.getResponse().getStatus());
        String message = exception.getMessage() != null ? 
            exception.getMessage() : 
            "Request could not be processed";
        
        Map<String, Object> errorResponse = createErrorResponse(errorType, message);
        
        return Response.status(exception.getResponse().getStatus())
            .entity(errorResponse)
            .build();
    }

    /**
     * Maps all other exceptions to HTTP 500 Internal Server Error.
     * 
     * This is the catch-all handler for unexpected exceptions that don't have
     * specific mappings. It logs the full exception for debugging while returning
     * a generic error message to avoid information disclosure.
     * 
     * @param exception the generic exception
     * @return Response with 500 status and generic error message
     */
    @ServerExceptionMapper
    public Response mapGenericException(Exception exception) {
        Log.error("Unexpected error occurred", exception);
        
        Map<String, Object> errorResponse = createErrorResponse(
            "Internal server error",
            "An unexpected error occurred. Please try again later."
        );
        
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(errorResponse)
            .build();
    }

    /**
     * Creates a consistent error response structure.
     * 
     * All error responses follow the same format with error type, message,
     * and timestamp for consistency across the API.
     * 
     * @param error the error type description
     * @param message the detailed error message
     * @return Map containing the structured error response
     */
    private Map<String, Object> createErrorResponse(String error, String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        errorResponse.put("timestamp", Instant.now());
        return errorResponse;
    }

    /**
     * Extracts the field name from a constraint violation.
     * 
     * This handles both simple field names and complex property paths,
     * returning the last segment of the property path for cleaner error messages.
     * 
     * @param violation the constraint violation
     * @return the field name or property path segment
     */
    private String getFieldName(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        
        // Handle simple field names and method parameter names
        if (propertyPath.contains(".")) {
            String[] parts = propertyPath.split("\\.");
            return parts[parts.length - 1];
        }
        
        return propertyPath.isEmpty() ? "value" : propertyPath;
    }

    /**
     * Determines the error type description from HTTP status code.
     * 
     * @param status the HTTP status code
     * @return user-friendly error type description
     */
    private String getErrorTypeFromStatus(int status) {
        return switch (status) {
            case 400 -> "Bad request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not found";
            case 405 -> "Method not allowed";
            case 409 -> "Conflict";
            case 422 -> "Unprocessable entity";
            case 500 -> "Internal server error";
            case 502 -> "Bad gateway";
            case 503 -> "Service unavailable";
            default -> "Request failed";
        };
    }
}