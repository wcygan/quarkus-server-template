# Quarkus REST: Comprehensive Guide

## Table of Contents

1. [Core Concepts](#core-concepts)
2. [JSON Handling with Jackson](#json-handling-with-jackson)
3. [Validation and Bean Validation](#validation-and-bean-validation)
4. [Exception Handling](#exception-handling)
5. [Path Parameters & Query Parameters](#path-parameters--query-parameters)
6. [Content Negotiation](#content-negotiation)
7. [Performance Optimizations](#performance-optimizations)
8. [Testing with REST Assured](#testing-with-rest-assured)
9. [Security Integration](#security-integration)
10. [Reactive Support](#reactive-support)

## Core Concepts

### Quarkus REST vs RESTEasy Classic

Quarkus REST (formerly RESTEasy Reactive) is the modern, cloud-native REST framework that replaced RESTEasy Classic as the default in Quarkus 2.x. Key differences:

**Architecture:**
- **Quarkus REST**: Built from scratch with reactive core, supports both blocking and non-blocking workloads
- **RESTEasy Classic**: Traditional servlet-based, thread-per-request model

**Threading Model:**
- **Quarkus REST**: Uses I/O threads for non-blocking operations, worker threads for blocking operations
- **RESTEasy Classic**: Uses traditional servlet threads

**Default Media Types:**
- **Quarkus REST**: `text/plain` as default for String returns
- **RESTEasy Classic**: `text/html` as default for String returns

### Dependencies

```xml
<!-- Core REST dependency -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest</artifactId>
</dependency>

<!-- Jackson support -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-jackson</artifactId>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-validator</artifactId>
</dependency>
```

### Basic REST Resource

```java
package org.acme.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
public class GreetingResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello from Quarkus REST";
    }
}
```

## JSON Handling with Jackson

### Configuration

Jackson is the recommended JSON processor for Quarkus REST. Default configuration includes:

```properties
# Ignore unknown properties (default: true)
quarkus.jackson.fail-on-unknown-properties=false

# Write dates as timestamps (default: false, uses ISO-8601)
quarkus.jackson.write-dates-as-timestamps=false
```

### Basic JSON Endpoint

```java
package org.acme.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @GET
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    @POST
    public User createUser(User user) {
        return userService.create(user);
    }

    @GET
    @Path("/{id}")
    public User getUser(@PathParam("id") Long id) {
        return userService.findById(id);
    }
}
```

### Custom ObjectMapper

```java
package org.acme.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

@Singleton
public class CustomObjectMapperConfig implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
}
```

### Secure Field Serialization

```java
package org.acme.model;

import io.quarkus.resteasy.reactive.jackson.SecureField;

public class Person {

    @SecureField(rolesAllowed = "admin")
    private final Long id;
    
    private final String firstName;
    private final String lastName;
    
    @SecureField(rolesAllowed = "${role:admin}")
    private String email;

    public Person(Long id, String firstName, String lastName, String email) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    // Getters...
}
```

## Validation and Bean Validation

### Basic Validation

```java
package org.acme.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/registration")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class RegistrationResource {

    @POST
    public User registerUser(@Valid User user) {
        return userService.register(user);
    }

    @POST
    @Path("/quick")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public String quickRegister(
        @NotNull @FormParam("firstName") String firstName,
        @NotNull @FormParam("lastName") String lastName,
        @Email @FormParam("email") String email) {
        
        return userService.quickRegister(firstName, lastName, email);
    }
}
```

### User Entity with Validation

```java
package org.acme.model;

import jakarta.validation.constraints.*;

public class User {

    @NotNull
    @Size(min = 2, max = 50)
    private String firstName;

    @NotNull
    @Size(min = 2, max = 50)
    private String lastName;

    @Email
    @NotNull
    private String email;

    @Past
    private LocalDate birthDate;

    @Min(18)
    @Max(120)
    private Integer age;

    // Constructors, getters, setters...
}
```

### Custom Validator

```java
package org.acme.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
@Documented
public @interface StrongPassword {
    String message() default "Password must contain at least 8 characters, one uppercase, one lowercase, and one digit";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

```java
package org.acme.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        
        return hasUpper && hasLower && hasDigit;
    }
}
```

## Exception Handling

### Global Exception Mappers

```java
package org.acme.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class GlobalExceptionMappers {

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapConstraintViolation(ConstraintViolationException ex) {
        ErrorResponse error = new ErrorResponse(
            "Validation Failed",
            ex.getConstraintViolations().stream()
                .map(cv -> new ValidationError(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList()
        );
        return RestResponse.status(Response.Status.BAD_REQUEST, error);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse("Invalid Request", ex.getMessage());
        return RestResponse.status(Response.Status.BAD_REQUEST, error);
    }

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapGenericException(Exception ex) {
        ErrorResponse error = new ErrorResponse("Internal Server Error", "An unexpected error occurred");
        return RestResponse.status(Response.Status.INTERNAL_SERVER_ERROR, error);
    }
}
```

### Custom Exception with Mapper

```java
package org.acme.exception;

public class UserNotFoundException extends RuntimeException {
    private final Long userId;

    public UserNotFoundException(Long userId) {
        super("User not found with ID: " + userId);
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
```

```java
package org.acme.exception;

import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class UserExceptionMappers {

    @ServerExceptionMapper
    public RestResponse<ErrorResponse> mapUserNotFound(UserNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
            "User Not Found",
            "User with ID " + ex.getUserId() + " does not exist"
        );
        return RestResponse.status(Response.Status.NOT_FOUND, error);
    }
}
```

### Error Response Model

```java
package org.acme.model;

import java.time.LocalDateTime;
import java.util.List;

public class ErrorResponse {
    private String title;
    private String detail;
    private LocalDateTime timestamp;
    private List<ValidationError> violations;

    public ErrorResponse(String title, String detail) {
        this.title = title;
        this.detail = detail;
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(String title, List<ValidationError> violations) {
        this.title = title;
        this.violations = violations;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and setters...
}

public class ValidationError {
    private String field;
    private String message;

    public ValidationError(String field, String message) {
        this.field = field;
        this.message = message;
    }

    // Getters and setters...
}
```

## Path Parameters & Query Parameters

### Path Parameters

```java
package org.acme.rest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/books")
@Produces(MediaType.APPLICATION_JSON)
public class BookResource {

    @GET
    @Path("/{id}")
    public Book getBook(
        @PathParam("id") 
        @Min(value = 1, message = "Book ID must be positive") 
        Long id) {
        return bookService.findById(id);
    }

    @GET
    @Path("/isbn/{isbn}")
    public Book getBookByIsbn(
        @PathParam("isbn") 
        @Pattern(regexp = "\\d{13}", message = "ISBN must be 13 digits") 
        String isbn) {
        return bookService.findByIsbn(isbn);
    }

    @GET
    @Path("/category/{category}/author/{author}")
    public List<Book> getBooksByCategoryAndAuthor(
        @PathParam("category") String category,
        @PathParam("author") String author) {
        return bookService.findByCategoryAndAuthor(category, author);
    }
}
```

### Query Parameters

```java
package org.acme.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;

@Path("/api/v1/books")
@Produces(MediaType.APPLICATION_JSON)
public class BookSearchResource {

    @GET
    @Path("/search")
    public List<Book> searchBooks(
        @QueryParam("title") String title,
        @QueryParam("author") String author,
        @QueryParam("category") String category,
        @QueryParam("minPrice") @Min(0) Double minPrice,
        @QueryParam("maxPrice") @Max(10000) Double maxPrice,
        @QueryParam("page") @DefaultValue("0") @Min(0) Integer page,
        @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) Integer size,
        @QueryParam("sort") @DefaultValue("title") String sort) {
        
        BookSearchCriteria criteria = BookSearchCriteria.builder()
            .title(title)
            .author(author)
            .category(category)
            .minPrice(minPrice)
            .maxPrice(maxPrice)
            .build();
            
        return bookService.search(criteria, page, size, sort);
    }

    @GET
    @Path("/filter")
    public List<Book> filterBooks(@BeanParam BookFilterParams filters) {
        return bookService.filter(filters);
    }
}
```

### Bean Parameter Aggregation

```java
package org.acme.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

public class BookFilterParams {

    @QueryParam("category")
    private String category;

    @QueryParam("available")
    @DefaultValue("true")
    private Boolean available;

    @QueryParam("minRating")
    @Min(1) @Max(5)
    private Integer minRating;

    @QueryParam("publishedAfter")
    private String publishedAfter;

    @QueryParam("limit")
    @DefaultValue("50")
    @Min(1) @Max(200)
    private Integer limit;

    // Getters and setters...
}
```

## Content Negotiation

### Multiple Response Formats

```java
package org.acme.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/products")
public class ProductResource {

    @GET
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_PLAIN})
    public Product getProduct(@PathParam("id") Long id) {
        return productService.findById(id);
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_CSV})
    public Object getAllProducts(@HeaderParam("Accept") String acceptHeader) {
        List<Product> products = productService.findAll();
        
        if (MediaType.TEXT_CSV.equals(acceptHeader)) {
            return ProductCsvConverter.toCsv(products);
        }
        
        return products;
    }
}
```

### Custom Media Type Handling

```java
package org.acme.rest;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/reports")
public class ReportResource {

    public static final String APPLICATION_PDF = "application/pdf";
    public static final String APPLICATION_EXCEL = "application/vnd.ms-excel";

    @GET
    @Path("/{reportId}")
    @Produces({MediaType.APPLICATION_JSON, APPLICATION_PDF, APPLICATION_EXCEL})
    public Response getReport(
        @PathParam("reportId") String reportId,
        @HeaderParam("Accept") String acceptType) {
        
        Report report = reportService.findById(reportId);
        
        return switch (acceptType) {
            case APPLICATION_PDF -> Response.ok(reportService.generatePdf(report))
                .type(APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=report.pdf")
                .build();
                
            case APPLICATION_EXCEL -> Response.ok(reportService.generateExcel(report))
                .type(APPLICATION_EXCEL)
                .header("Content-Disposition", "attachment; filename=report.xlsx")
                .build();
                
            default -> Response.ok(report).type(MediaType.APPLICATION_JSON).build();
        };
    }
}
```

## Performance Optimizations

### Native Compilation Considerations

```properties
# application.properties for native builds

# Enable build-time indexing
quarkus.index-dependency.jackson.group-id=com.fasterxml.jackson.core
quarkus.index-dependency.jackson.artifact-id=jackson-databind

# Reflection configuration is handled automatically for REST endpoints
# Manual registration only needed for dynamic cases

# Thread pool configuration
quarkus.thread-pool.core-threads=4
quarkus.thread-pool.max-threads=16

# HTTP configuration
quarkus.http.io-threads=8
quarkus.http.limits.max-body-size=10M
```

### Build-time Optimization

```java
package org.acme.config;

import io.quarkus.runtime.annotations.RegisterForReflection;

// Register classes for reflection in native builds
@RegisterForReflection(targets = {
    CustomDto.class,
    ComplexResponseModel.class
})
public class ReflectionConfig {
}
```

### Startup Performance

```java
package org.acme.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class StartupService {

    // Minimize work in startup
    void onStart(@Observes StartupEvent ev) {
        // Only essential initialization here
        // Heavy operations should be lazy-loaded
    }
}
```

### Connection Pooling

```properties
# Database connection optimization
quarkus.datasource.jdbc.min-size=2
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.acquisition-timeout=60

# HTTP client pooling
quarkus.rest-client.default.connection-pool-size=10
quarkus.rest-client.default.connection-ttl=30000
```

## Testing with REST Assured

### Basic Test Setup

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit5</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <scope>test</scope>
</dependency>
```

### Integration Tests

```java
package org.acme.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.net.URL;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestHTTPEndpoint(UserResource.class)
public class UserResourceTest {

    @TestHTTPResource
    URL baseUrl;

    @Test
    void testGetAllUsers() {
        given()
            .when().get()
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("size()", greaterThan(0));
    }

    @Test
    void testCreateUser() {
        User newUser = new User("John", "Doe", "john.doe@example.com");
        
        given()
            .contentType(ContentType.JSON)
            .body(newUser)
            .when().post()
            .then()
                .statusCode(201)
                .body("firstName", equalTo("John"))
                .body("email", equalTo("john.doe@example.com"));
    }

    @Test
    void testGetUserById() {
        given()
            .pathParam("id", 1L)
            .when().get("/{id}")
            .then()
                .statusCode(200)
                .body("id", equalTo(1));
    }

    @Test
    void testValidationError() {
        User invalidUser = new User("", "", "invalid-email");
        
        given()
            .contentType(ContentType.JSON)
            .body(invalidUser)
            .when().post()
            .then()
                .statusCode(400)
                .body("title", equalTo("Validation Failed"))
                .body("violations", hasSize(greaterThan(0)));
    }
}
```

### Mocking and Test Configuration

```java
package org.acme.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
public class UserResourceMockTest {

    @InjectMock
    UserService userService;

    @Test
    void testGetUserWithMock() {
        User mockUser = new User(1L, "Mock", "User", "mock@example.com");
        Mockito.when(userService.findById(1L)).thenReturn(mockUser);

        given()
            .pathParam("id", 1L)
            .when().get("/users/{id}")
            .then()
                .statusCode(200)
                .body("firstName", equalTo("Mock"));
    }
}
```

### TestContainers Integration

```java
package org.acme.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
public class UserResourceIntegrationTest {

    @Test
    void testDatabaseIntegration() {
        // Tests run against real PostgreSQL container
        given()
            .when().get("/users")
            .then()
                .statusCode(200);
    }
}
```

## Security Integration

### Role-Based Access Control

```java
package org.acme.rest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.Context;

@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @GET
    @Path("/users")
    @RolesAllowed("admin")
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    @DELETE
    @Path("/users/{id}")
    @RolesAllowed({"admin", "super-admin"})
    public void deleteUser(@PathParam("id") Long id) {
        userService.delete(id);
    }

    @GET
    @Path("/profile")
    @RolesAllowed({"admin", "user"})
    public UserProfile getProfile(@Context SecurityContext securityContext) {
        String username = securityContext.getUserPrincipal().getName();
        return userService.getProfile(username);
    }
}
```

### JWT Configuration

```properties
# JWT Configuration
mp.jwt.verify.publickey.location=META-INF/resources/publicKey.pem
mp.jwt.verify.issuer=https://example.com
quarkus.smallrye-jwt.enabled=true

# OIDC Configuration
quarkus.oidc.auth-server-url=https://localhost:8180/auth/realms/quarkus
quarkus.oidc.client-id=backend-service
quarkus.oidc.credentials.secret=secret
```

### Security Testing

```java
package org.acme.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class AdminResourceSecurityTest {

    @Test
    @TestSecurity(user = "admin", roles = {"admin"})
    void testAdminAccess() {
        given()
            .when().get("/api/v1/admin/users")
            .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "user", roles = {"user"})
    void testUserAccessDenied() {
        given()
            .when().get("/api/v1/admin/users")
            .then()
                .statusCode(403);
    }

    @Test
    @JwtSecurity(claims = {
        @Claim(key = "upn", value = "admin@example.com"),
        @Claim(key = "groups", value = "[\"admin\"]")
    })
    void testJwtAccess() {
        given()
            .when().get("/api/v1/admin/users")
            .then()
                .statusCode(200);
    }
}
```

## Reactive Support

### Mutiny Basics

```java
package org.acme.rest;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Duration;

@Path("/api/v1/reactive")
@Produces(MediaType.APPLICATION_JSON)
public class ReactiveResource {

    @GET
    @Path("/user/{id}")
    public Uni<User> getUser(@PathParam("id") Long id) {
        return userService.findByIdAsync(id);
    }

    @GET
    @Path("/users")
    public Multi<User> getAllUsers() {
        return userService.streamAll();
    }

    @GET
    @Path("/delayed/{seconds}")
    public Uni<String> getDelayed(@PathParam("seconds") int seconds) {
        return Uni.createFrom().item("Delayed response")
            .onItem().delayIt().by(Duration.ofSeconds(seconds));
    }
}
```

### Reactive Service Layer

```java
package org.acme.service;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Row;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ReactiveUserService {

    @Inject
    PgPool client;

    public Uni<User> findByIdAsync(Long id) {
        return client
            .preparedQuery("SELECT * FROM users WHERE id = $1")
            .execute(Tuple.of(id))
            .onItem().transform(this::mapRowToUser);
    }

    public Multi<User> streamAll() {
        return client
            .query("SELECT * FROM users")
            .execute()
            .onItem().transformToMulti(rows -> Multi.createFrom().iterable(rows))
            .onItem().transform(this::mapRowToUser);
    }

    public Uni<User> createAsync(User user) {
        return client
            .preparedQuery("INSERT INTO users (name, email) VALUES ($1, $2) RETURNING id")
            .execute(Tuple.of(user.getName(), user.getEmail()))
            .onItem().transform(rows -> {
                Long id = rows.iterator().next().getLong("id");
                return new User(id, user.getName(), user.getEmail());
            });
    }

    private User mapRowToUser(Row row) {
        return new User(
            row.getLong("id"),
            row.getString("name"),
            row.getString("email")
        );
    }
}
```

### Error Handling in Reactive Flows

```java
package org.acme.rest;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/api/v1/reactive")
public class ReactiveErrorHandlingResource {

    @GET
    @Path("/user/{id}")
    public Uni<RestResponse<User>> getUserWithErrorHandling(@PathParam("id") Long id) {
        return userService.findByIdAsync(id)
            .onItem().transform(user -> RestResponse.ok(user))
            .onFailure(UserNotFoundException.class)
                .recoverWithItem(ex -> RestResponse.status(Response.Status.NOT_FOUND))
            .onFailure()
                .recoverWithItem(ex -> RestResponse.status(Response.Status.INTERNAL_SERVER_ERROR));
    }

    @GET
    @Path("/users/search")
    public Uni<List<User>> searchUsersWithFallback(@QueryParam("query") String query) {
        return userService.searchAsync(query)
            .onFailure().recoverWithItem(Collections.emptyList())
            .onItem().transform(users -> 
                users.isEmpty() ? userService.getDefaultUsers() : users
            );
    }
}
```

### Combining Reactive Operations

```java
package org.acme.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReactiveCompositionService {

    public Uni<UserWithProfile> getUserWithProfile(Long userId) {
        Uni<User> userUni = userService.findByIdAsync(userId);
        Uni<Profile> profileUni = profileService.findByUserIdAsync(userId);

        return Uni.combine().all().unis(userUni, profileUni)
            .asTuple()
            .onItem().transform(tuple -> 
                new UserWithProfile(tuple.getItem1(), tuple.getItem2())
            );
    }

    public Uni<List<User>> getUsersWithRoles(List<Long> userIds) {
        return Multi.createFrom().iterable(userIds)
            .onItem().transformToUniAndMerge(id -> 
                userService.findByIdAsync(id)
                    .onItem().call(user -> 
                        roleService.loadRolesAsync(user.getId())
                            .onItem().invoke(roles -> user.setRoles(roles))
                    )
            )
            .collect().asList();
    }
}
```

## Configuration Best Practices

### Development vs Production

```properties
# Development (application-dev.properties)
quarkus.log.level=DEBUG
quarkus.http.access-log.enabled=true
quarkus.jackson.write-dates-as-timestamps=false
quarkus.jackson.write-durations-as-timestamps=false

# Production (application-prod.properties)
quarkus.log.level=INFO
quarkus.http.access-log.enabled=false
quarkus.http.cors=false
quarkus.jackson.fail-on-unknown-properties=true
```

### Health Checks

```java
package org.acme.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import jakarta.enterprise.context.ApplicationScoped;

@Liveness
@ApplicationScoped
public class LivenessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.up("Application is running");
    }
}

@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        // Check database connectivity, external services, etc.
        return HealthCheckResponse.up("Application is ready");
    }
}
```

This comprehensive guide covers the essential aspects of building REST APIs with Quarkus REST, emphasizing performance, security, and modern development practices. The examples demonstrate idiomatic Quarkus patterns while maintaining simplicity and readability.