package com.example;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Main application entry point for the User Management API.
 * 
 * This class provides the main method for starting the Quarkus application
 * and implements QuarkusApplication for custom startup logic if needed.
 */
@QuarkusMain
@ApplicationScoped
public class Application implements QuarkusApplication {

    public static void main(String... args) {
        Quarkus.run(Application.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        // Custom application startup logic can be added here
        Quarkus.waitForExit();
        return 0;
    }
}