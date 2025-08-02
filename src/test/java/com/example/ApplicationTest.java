package com.example;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

/**
 * Basic application test to verify the Quarkus application starts correctly
 * and the health endpoint is accessible.
 */
@QuarkusTest
public class ApplicationTest {

    @Test
    public void testHealthEndpoint() {
        given()
            .when().get("/api/health")
            .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("application", is("user-api"));
    }

    @Test
    public void testQuarkusHealthEndpoint() {
        given()
            .when().get("/q/health")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}