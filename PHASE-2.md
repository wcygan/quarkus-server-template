# Phase 2: Core Implementation (Medium Priority)

> **Phase Goal**: Implement the core business logic, data access layer, and REST API endpoints for user management functionality.

## Overview

Phase 2 builds upon the foundation established in Phase 1 to create the complete user management API. This phase implements the layered architecture with domain models, repository pattern, service layer, and REST endpoints following Quarkus best practices.

**Reference**: See [PLAN.md](./PLAN.md) for overall architecture and [PHASE-1.md](./PHASE-1.md) for prerequisites.

## Phase Dependencies

- **Prerequisites**: Completed Phase 1 with working jOOQ generation and database schema
- **Previous Phase**: [PHASE-1.md](./PHASE-1.md) (Foundation)
- **Next Phase**: [PHASE-3.md](./PHASE-3.md) (Production Readiness)
- **Estimated Duration**: 3-4 days

## Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   REST Layer    │    │  Service Layer  │    │Repository Layer │    │   Database      │
│                 │    │                 │    │                 │    │                 │
│  UserResource   │───▶│   UserService   │───▶│ UserRepository  │───▶│  MySQL + jOOQ   │
│                 │    │                 │    │                 │    │                 │
│ - POST /users   │    │ - createUser()  │    │ - findById()    │    │ - users table   │
│ - GET /users/id │    │ - getUserById() │    │ - findByName()  │    │ - constraints   │
│ - GET /users?q  │    │ - getUserByName()│    │ - insert()      │    │ - indexes       │
└─────────────────┘    └─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Specialized Agent Usage for Phase 2

**Critical: Leverage specialized agents for expert implementation throughout Phase 2:**

### quarkus-specialist Agent
**Use for all Quarkus framework implementation:**
- REST endpoint implementation with RESTEasy Reactive
- CDI dependency injection setup and scoping
- Bean validation configuration and custom validators
- Exception mapper implementation and HTTP status handling
- Health check implementation with Quarkus MicroProfile Health
- Transaction boundary configuration and management

### jooq-specialist Agent
**Use for all data access layer implementation:**
- Repository pattern implementation with jOOQ DSL
- Type-safe query construction and optimization
- Complex query handling (joins, subqueries, filtering)
- Pagination and sorting implementation
- Custom data type converters and mappings
- Transaction integration with Quarkus

### mysql-database-architect Agent
**Use for database optimization and integration:**
- Query performance analysis and optimization
- Index strategy validation and refinement
- Connection pool configuration and tuning
- Database constraint validation
- Migration script optimization

**Parallel Agent Deployment Strategy:**
1. **Domain Models**: Use **quarkus-specialist** for validation and JSON serialization
2. **Repository Layer**: Deploy **jooq-specialist** for type-safe data access
3. **Service Layer**: Use **quarkus-specialist** for business logic and transaction management
4. **REST Layer**: Use **quarkus-specialist** for endpoint implementation and error handling
5. **Integration**: Coordinate all agents for end-to-end testing and optimization

## Task Breakdown

### Task 2.1: Domain Model Implementation
**🤖 Primary Agent: quarkus-specialist**

**Objective**: Create immutable domain models using Java records with proper validation and serialization.

**Deliverables**:
- `User` record for domain representation
- `CreateUserRequest` DTO with validation
- `UserResponse` DTO for API responses
- Custom validation annotations if needed

**Domain Model Design**:

```java
// User.java - Core domain entity
public record User(
    UUID id,
    String username,
    Instant createdAt
) {
    public User {
        Objects.requireNonNull(id, "User ID cannot be null");
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(createdAt, "Created timestamp cannot be null");
        
        if (username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
    }
}

// CreateUserRequest.java - Input DTO
public record CreateUserRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain alphanumeric characters, hyphens, and underscores")
    String username
) {}

// UserResponse.java - Output DTO
public record UserResponse(
    @JsonProperty("id")
    String id,
    
    @JsonProperty("username")
    String username,
    
    @JsonProperty("createdAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.id().toString(),
            user.username(),
            user.createdAt()
        );
    }
}
```

**Design Decisions**:
1. **Immutability**: Records ensure immutable domain objects
2. **Validation**: Bean Validation annotations on DTOs, not domain objects
3. **Separation**: Clear distinction between domain models and API contracts
4. **Null Safety**: Explicit null checks in record constructors
5. **JSON Serialization**: Explicit Jackson annotations for API consistency

**Success Criteria**:
- ✅ User record compiles and enforces constraints
- ✅ CreateUserRequest validates input correctly
- ✅ UserResponse serializes to expected JSON format
- ✅ Validation errors produce proper error messages

### Task 2.2: Repository Layer Implementation
**🤖 Primary Agent: jooq-specialist** (with mysql-database-architect for optimization)

**Objective**: Implement type-safe data access using jOOQ with proper error handling and transaction management.

**Deliverables**:
- `UserRepository` class with CRUD operations
- Custom exceptions for data access errors
- Proper transaction handling
- Connection pooling configuration

**Repository Implementation**:

```java
@ApplicationScoped
public class UserRepository {
    
    @Inject
    DSLContext dsl;
    
    public Optional<User> findById(UUID id) {
        return dsl.selectFrom(USERS)
            .where(USERS.ID.eq(id.toString()))
            .fetchOptional()
            .map(this::mapToUser);
    }
    
    public Optional<User> findByUsername(String username) {
        return dsl.selectFrom(USERS)
            .where(USERS.USERNAME.eq(username))
            .fetchOptional()
            .map(this::mapToUser);
    }
    
    @Transactional
    public User insert(String username) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        
        try {
            dsl.insertInto(USERS)
                .set(USERS.ID, id.toString())
                .set(USERS.USERNAME, username)
                .set(USERS.CREATED_AT, Timestamp.from(now))
                .execute();
                
            return new User(id, username, now);
        } catch (DataAccessException e) {
            if (isDuplicateKeyException(e)) {
                throw new DuplicateUsernameException("Username already exists: " + username);
            }
            throw new DatabaseException("Failed to insert user", e);
        }
    }
    
    public boolean existsByUsername(String username) {
        return dsl.fetchExists(
            dsl.selectOne()
                .from(USERS)
                .where(USERS.USERNAME.eq(username))
        );
    }
    
    private User mapToUser(UsersRecord record) {
        return new User(
            UUID.fromString(record.getId()),
            record.getUsername(),
            record.getCreatedAt().toInstant()
        );
    }
    
    private boolean isDuplicateKeyException(DataAccessException e) {
        return e.getCause() instanceof SQLIntegrityConstraintViolationException;
    }
}
```

**Custom Exceptions**:

```java
// DuplicateUsernameException.java
public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException(String message) {
        super(message);
    }
}

// DatabaseException.java
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}

// UserNotFoundException.java
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
```

**Transaction Configuration**:
```properties
# Connection pooling
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.min-size=5
quarkus.transaction-manager.default-transaction-timeout=30s
```

**Success Criteria**:
- ✅ All CRUD operations work correctly
- ✅ Duplicate username detection prevents violations
- ✅ Transactions rollback on errors
- ✅ Optional handling for not-found cases
- ✅ Proper exception mapping from database errors

### Task 2.3: Service Layer Implementation
**🤖 Primary Agent: quarkus-specialist**

**Objective**: Implement business logic layer with validation, exception handling, and transactional operations.

**Deliverables**:
- `UserService` class with business operations
- Input validation and business rule enforcement
- Global exception handling
- Logging and monitoring integration

**Service Implementation**:

```java
@ApplicationScoped
public class UserService {
    
    private static final Logger LOG = Logger.getLogger(UserService.class);
    
    @Inject
    UserRepository userRepository;
    
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        LOG.info("Creating user with username: {}", request.username());
        
        // Business validation
        if (userRepository.existsByUsername(request.username())) {
            LOG.warn("Attempt to create user with existing username: {}", request.username());
            throw new DuplicateUsernameException("Username already exists: " + request.username());
        }
        
        try {
            User user = userRepository.insert(request.username());
            LOG.info("Successfully created user: {} with ID: {}", user.username(), user.id());
            return UserResponse.from(user);
        } catch (Exception e) {
            LOG.error("Failed to create user: {}", request.username(), e);
            throw e;
        }
    }
    
    public UserResponse getUserById(UUID id) {
        LOG.debug("Retrieving user by ID: {}", id);
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + id));
            
        return UserResponse.from(user);
    }
    
    public UserResponse getUserByUsername(String username) {
        LOG.debug("Retrieving user by username: {}", username);
        
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
            
        return UserResponse.from(user);
    }
}
```

**Business Rules**:
1. **Username Uniqueness**: Enforced at service level before database
2. **Input Validation**: Bean validation on DTOs
3. **Transaction Boundaries**: Service methods define transaction scope
4. **Error Handling**: Convert repository exceptions to business exceptions
5. **Logging**: Comprehensive logging for audit and debugging

**Success Criteria**:
- ✅ CreateUser prevents duplicate usernames
- ✅ GetUser operations handle not-found cases
- ✅ Transactions work correctly
- ✅ Appropriate logging at all levels
- ✅ Business exceptions include meaningful messages

### Task 2.4: REST API Implementation
**🤖 Primary Agent: quarkus-specialist**

**Objective**: Create RESTful endpoints with proper HTTP semantics, validation, and error handling.

**Deliverables**:
- `UserResource` class with REST endpoints
- Global exception mappers
- Proper HTTP status codes
- OpenAPI documentation integration

**REST Resource Implementation**:

```java
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {
    
    @Inject
    UserService userService;
    
    @POST
    public Response createUser(@Valid CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        
        URI location = UriBuilder.fromPath("/api/users/{id}")
            .build(user.id());
            
        return Response.created(location)
            .entity(user)
            .build();
    }
    
    @GET
    @Path("/{id}")
    public UserResponse getUserById(@PathParam("id") UUID id) {
        return userService.getUserById(id);
    }
    
    @GET
    public UserResponse getUserByUsername(@QueryParam("username") String username) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("Username query parameter is required");
        }
        return userService.getUserByUsername(username);
    }
}
```

**Global Exception Handling**:

```java
@Provider
public class GlobalExceptionMapper {
    
    @ServerExceptionMapper(UserNotFoundException.class)
    public Response mapUserNotFound(UserNotFoundException e) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of(
                "error", "User not found",
                "message", e.getMessage(),
                "timestamp", Instant.now()
            ))
            .build();
    }
    
    @ServerExceptionMapper(DuplicateUsernameException.class)
    public Response mapDuplicateUsername(DuplicateUsernameException e) {
        return Response.status(Response.Status.CONFLICT)
            .entity(Map.of(
                "error", "Duplicate username",
                "message", e.getMessage(),
                "timestamp", Instant.now()
            ))
            .build();
    }
    
    @ServerExceptionMapper(ConstraintViolationException.class)
    public Response mapValidationError(ConstraintViolationException e) {
        Map<String, String> violations = e.getConstraintViolations()
            .stream()
            .collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage
            ));
            
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of(
                "error", "Validation failed",
                "violations", violations,
                "timestamp", Instant.now()
            ))
            .build();
    }
}
```

**API Specification**:

| Method | Path | Description | Request | Response | Status Codes |
|--------|------|-------------|---------|----------|--------------|
| POST | `/api/users` | Create user | CreateUserRequest | UserResponse | 201, 400, 409 |
| GET | `/api/users/{id}` | Get by ID | - | UserResponse | 200, 400, 404 |
| GET | `/api/users?username=X` | Get by username | - | UserResponse | 200, 400, 404 |

**Success Criteria**:
- ✅ POST creates user and returns 201 with Location header
- ✅ GET by ID returns user or 404
- ✅ GET by username works with query parameter
- ✅ Validation errors return 400 with details
- ✅ Duplicate username returns 409 conflict

### Task 2.5: Health Check Implementation
**🤖 Primary Agent: quarkus-specialist** (with jooq-specialist for database health checks)

**Objective**: Implement custom health checks for database connectivity and application status.

**Deliverables**:
- `DatabaseHealthCheck` for database connectivity
- Integration with Quarkus health check framework
- Proper health check responses

**Health Check Implementation**:

```java
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {
    
    @Inject
    DSLContext dsl;
    
    @Override
    public HealthCheckResponse call() {
        try {
            // Simple connectivity test
            dsl.selectOne().fetch();
            
            return HealthCheckResponse.named("database")
                .up()
                .withData("connection", "active")
                .withData("database", "mysql")
                .build();
                
        } catch (Exception e) {
            return HealthCheckResponse.named("database")
                .down()
                .withData("error", e.getMessage())
                .build();
        }
    }
}

@Liveness
@ApplicationScoped
public class ApplicationHealthCheck implements HealthCheck {
    
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("application")
            .up()
            .withData("status", "running")
            .withData("version", getClass().getPackage().getImplementationVersion())
            .build();
    }
}
```

**Health Endpoints**:
- `/q/health/live` - Liveness probe (application running)
- `/q/health/ready` - Readiness probe (ready to serve requests)
- `/q/health` - Combined health status

**Success Criteria**:
- ✅ Database health check detects connectivity issues
- ✅ Health endpoints return proper JSON responses
- ✅ Kubernetes-compatible probe responses
- ✅ Health checks execute quickly (<1 second)

## Phase Integration Testing

### End-to-End Scenarios

1. **Happy Path User Creation**:
   ```bash
   curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"username": "testuser"}'
   ```

2. **User Retrieval by ID**:
   ```bash
   curl http://localhost:8080/api/users/{uuid}
   ```

3. **User Retrieval by Username**:
   ```bash
   curl http://localhost:8080/api/users?username=testuser
   ```

4. **Error Scenarios**:
   ```bash
   # Duplicate username
   curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"username": "testuser"}'
   
   # Invalid username
   curl -X POST http://localhost:8080/api/users \
     -H "Content-Type: application/json" \
     -d '{"username": ""}'
   ```

### Performance Validation

- Response times < 100ms for CRUD operations
- Database connection pool efficiency
- Memory usage monitoring
- Health check response times

## Dependencies for Next Phase

**Phase 3 Prerequisites**:
- ✅ Working REST API with all endpoints
- ✅ Complete exception handling
- ✅ Database operations with transactions
- ✅ Health checks functional
- ✅ Basic integration testing completed

**Handoff Deliverables**:
- Functional user management API
- Complete layered architecture implementation
- Working health check endpoints
- Documentation of API endpoints and error responses

## Risk Assessment

**High Risk**:
- Transaction boundary configuration
- Exception mapping completeness

**Medium Risk**:
- UUID parameter handling in REST endpoints
- Database connection pool tuning

**Mitigation Strategies**:
- Comprehensive integration testing
- Load testing for connection pools
- Error scenario coverage
- Performance monitoring setup