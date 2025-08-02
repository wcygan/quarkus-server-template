package com.example.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Basic health check resource to verify the application is running.
 * This supplements the SmallRye Health checks configured in the application.
 */
@Path("/api/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @GET
    public Response health() {
        return Response.ok(Map.of(
            "status", "UP",
            "application", "user-api",
            "version", "1.0.0-SNAPSHOT"
        )).build();
    }
}