# Quarkus Development Guide

Comprehensive reference for building cloud-native Java applications with Quarkus REST, jOOQ, and Flyway - a modern, type-safe, and performance-focused stack.

## Table of Contents

- [Project Setup & Bootstrapping](#project-setup--bootstrapping)
- [Application Architecture](#application-architecture)
- [Development Workflow](#development-workflow)
- [Configuration Management](#configuration-management)
- [Testing Strategies](#testing-strategies)
- [Performance Optimization](#performance-optimization)
- [Essential Extensions](#essential-extensions)
- [Production Deployment](#production-deployment)
- [Best Practices](#best-practices)

## Project Setup & Bootstrapping

### Creating New Projects

**Using Quarkus CLI (Recommended):**
```bash
# Install Quarkus CLI
curl -Ls https://sh.jbang.dev | bash -s - trust add https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/
curl -Ls https://sh.jbang.dev | bash -s - app install --fresh --force quarkus@quarkusio

# Create new project
quarkus create app org.acme:my-project
cd my-project
```

**Using Maven:**
```bash
mvn io.quarkus.platform:quarkus-maven-plugin:3.x.x:create \
    -DprojectGroupId=org.acme \
    -DprojectArtifactId=my-project \
    -Dextensions="rest,jdbc-mysql,flyway"
```

### Project Structure

```
my-project/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/acme/
│   │   │       ├── GreetingResource.java
│   │   │       ├── GreetingService.java
│   │   │       └── repository/
│   │   │           └── UserRepository.java
│   │   ├── resources/
│   │   │   ├── application.properties
│   │   │   ├── db/migration/
│   │   │   │   └── V1__Create_users_table.sql
│   │   │   └── META-INF/resources/
│   │   └── docker/
│   └── test/java/
├── target/generated-sources/jooq/ (generated)
├── pom.xml
└── README.md
```

### Essential Maven Configuration

```xml
<properties>
    <quarkus.platform.version>3.x.x</quarkus.platform.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.quarkus.platform</groupId>
            <artifactId>quarkus-bom</artifactId>
            <version>${quarkus.platform.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Core Quarkus REST -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-rest</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-rest-jackson</artifactId>
    </dependency>
    
    <!-- Database -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-mysql</artifactId>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-flyway</artifactId>
    </dependency>
    
    <!-- jOOQ -->
    <dependency>
        <groupId>org.jooq</groupId>
        <artifactId>jooq</artifactId>
    </dependency>
    <dependency>
        <groupId>org.jooq</groupId>
        <artifactId>jooq-meta</artifactId>
    </dependency>
    <dependency>
        <groupId>org.jooq</groupId>
        <artifactId>jooq-codegen</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5-mockito</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mysql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.jooq</groupId>
            <artifactId>jooq-codegen-maven</artifactId>
            <version>${jooq.version}</version>
            <executions>
                <execution>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <jdbc>
                    <driver>com.mysql.cj.jdbc.Driver</driver>
                    <url>jdbc:mysql://localhost:3306/myapp_dev</url>
                    <user>dev</user>
                    <password>dev</password>
                </jdbc>
                <generator>
                    <database>
                        <name>org.jooq.meta.mysql.MySQLDatabase</name>
                        <inputSchema>myapp_dev</inputSchema>
                    </database>
                    <target>
                        <packageName>org.acme.generated.jooq</packageName>
                        <directory>target/generated-sources/jooq</directory>
                    </target>
                </generator>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Application Architecture

### Contexts and Dependency Injection (CDI)

**Service Layer Pattern:**
```java
@ApplicationScoped
public class UserService {
    
    @Inject
    UserRepository repository;
    
    @ConfigProperty(name = "app.user.max-limit")
    int maxUsers;
    
    @Transactional
    public User createUser(CreateUserRequest request) {
        if (repository.count() >= maxUsers) {
            throw new ServiceException("User limit exceeded");
        }
        return repository.insert(request.name(), request.email());
    }
    
    public List<User> findAll() {
        return repository.findAll();
    }
    
    public Optional<User> findById(Long id) {
        return repository.findById(id);
    }
    
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }
}
```

**REST Resource Pattern (Quarkus REST):**
```java
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    UserService userService;

    @GET
    public List<User> getAllUsers() {
        return userService.findAll();
    }

    @POST
    public Response createUser(@Valid CreateUserRequest request) {
        try {
            User user = userService.createUser(request);
            return Response.status(Status.CREATED)
                         .entity(user)
                         .location(URI.create("/api/users/" + user.id()))
                         .build();
        } catch (ServiceException e) {
            return Response.status(Status.BAD_REQUEST)
                         .entity(Map.of("error", e.getMessage()))
                         .build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getUser(@PathParam("id") Long id) {
        return userService.findById(id)
                .map(user -> Response.ok(user).build())
                .orElse(Response.status(Status.NOT_FOUND).build());
    }
    
    @PUT
    @Path("/{id}")
    public Response updateUser(@PathParam("id") Long id, @Valid UpdateUserRequest request) {
        try {
            User user = userService.updateUser(id, request);
            return Response.ok(user).build();
        } catch (ServiceException e) {
            return Response.status(Status.NOT_FOUND)
                         .entity(Map.of("error", e.getMessage()))
                         .build();
        }
    }
    
    @DELETE
    @Path("/{id}")
    public Response deleteUser(@PathParam("id") Long id) {
        boolean deleted = userService.deleteUser(id);
        return deleted ? Response.noContent().build() 
                      : Response.status(Status.NOT_FOUND).build();
    }
}
```

**Data Repository with jOOQ:**
```java
@ApplicationScoped
public class UserRepository {
    
    @Inject
    DSLContext dsl;
    
    public List<User> findAll() {
        return dsl.selectFrom(USERS)
                  .orderBy(USERS.CREATED_AT.desc())
                  .fetchInto(User.class);
    }
    
    public Optional<User> findById(Long id) {
        return dsl.selectFrom(USERS)
                  .where(USERS.ID.eq(id))
                  .fetchOptionalInto(User.class);
    }
    
    public Optional<User> findByEmail(String email) {
        return dsl.selectFrom(USERS)
                  .where(USERS.EMAIL.eq(email))
                  .fetchOptionalInto(User.class);
    }
    
    public List<User> findActiveUsers() {
        return dsl.selectFrom(USERS)
                  .where(USERS.ACTIVE.isTrue())
                  .orderBy(USERS.CREATED_AT.desc())
                  .fetchInto(User.class);
    }
    
    public long count() {
        return dsl.selectCount()
                  .from(USERS)
                  .fetchOneInto(Long.class);
    }
    
    public long countByDomain(String domain) {
        return dsl.selectCount()
                  .from(USERS)
                  .where(USERS.EMAIL.like("%" + domain + "%"))
                  .fetchOneInto(Long.class);
    }
    
    public User insert(String name, String email) {
        return dsl.insertInto(USERS)
                  .set(USERS.NAME, name)
                  .set(USERS.EMAIL, email)
                  .set(USERS.CREATED_AT, LocalDateTime.now())
                  .set(USERS.ACTIVE, true)
                  .returning()
                  .fetchOneInto(User.class);
    }
    
    public User update(Long id, String name, String email) {
        return dsl.update(USERS)
                  .set(USERS.NAME, name)
                  .set(USERS.EMAIL, email)
                  .where(USERS.ID.eq(id))
                  .returning()
                  .fetchOneInto(User.class);
    }
    
    public int deleteById(Long id) {
        return dsl.deleteFrom(USERS)
                  .where(USERS.ID.eq(id))
                  .execute();
    }
}
```

### Entity Design Patterns

**Record-based Entity (Java 17+):**
```java
public record User(
    Long id,
    String name,
    String email,
    LocalDateTime createdAt,
    boolean active
) {
    public static User create(String name, String email) {
        return new User(null, name, email, LocalDateTime.now(), true);
    }
    
    public User withName(String newName) {
        return new User(id, newName, email, createdAt, active);
    }
    
    public User withEmail(String newEmail) {
        return new User(id, name, newEmail, createdAt, active);
    }
    
    public User deactivate() {
        return new User(id, name, email, createdAt, false);
    }
}
```

**Request/Response DTOs:**
```java
public record CreateUserRequest(
    @NotBlank(message = "Name is required")
    String name,
    
    @Email(message = "Valid email is required")
    @NotBlank(message = "Email is required")
    String email
) {}

public record UpdateUserRequest(
    @NotBlank(message = "Name is required")
    String name,
    
    @Email(message = "Valid email is required")
    @NotBlank(message = "Email is required")
    String email
) {}

public record UserResponse(
    Long id,
    String name,
    String email,
    LocalDateTime createdAt,
    boolean active
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.id(),
            user.name(),
            user.email(),
            user.createdAt(),
            user.active()
        );
    }
}
```
```

## Development Workflow

### Live Coding and Hot Reload

**Start Development Mode:**
```bash
# Maven
./mvnw quarkus:dev

# Gradle
./gradlew quarkusDev

# Quarkus CLI
quarkus dev
```

**Key Development Features:**
- **Automatic Recompilation**: Code changes trigger immediate recompilation
- **Live Reload**: Browser automatically refreshes on changes
- **Background Compilation**: Compilation happens while you continue coding
- **Dev UI**: Access development tools at `http://localhost:8080/q/dev`

**Development Configuration:**
```properties
# Enable debugging
%dev.quarkus.log.level=DEBUG
%dev.quarkus.log.category."io.quarkus".level=INFO

# Development port
%dev.quarkus.http.port=8080

# Enable CORS for frontend development
%dev.quarkus.http.cors=true
%dev.quarkus.http.cors.origins=http://localhost:3000

# Hot reload settings
%dev.quarkus.live-reload.enabled=true
%dev.quarkus.live-reload.watched-paths=src/main/resources/templates
```

### Dev Services Integration

**Automatic Database Setup:**
```properties
# MySQL Dev Service (automatically starts container)
%dev.quarkus.datasource.db-kind=mysql
# No URL needed - Dev Services handles it

# Custom container configuration
%dev.quarkus.datasource.devservices.image-name=mysql:8.0
%dev.quarkus.datasource.devservices.port=3306
%dev.quarkus.datasource.devservices.username=dev
%dev.quarkus.datasource.devservices.password=dev
%dev.quarkus.datasource.devservices.database-name=myapp_dev

# Flyway configuration for dev
%dev.quarkus.flyway.migrate-at-start=true
%dev.quarkus.flyway.clean-at-start=true
```

## Configuration Management

### Environment-Specific Configuration

**Profile-Based Configuration:**
```properties
# Default configuration
quarkus.application.name=my-app
quarkus.http.port=8080
quarkus.log.level=INFO

# Development profile
%dev.quarkus.http.port=8181
%dev.quarkus.log.level=DEBUG
%dev.quarkus.datasource.url=jdbc:mysql://localhost:3306/myapp_dev
%dev.quarkus.flyway.migrate-at-start=true
%dev.quarkus.flyway.clean-at-start=true

# Test profile
%test.quarkus.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
%test.quarkus.flyway.migrate-at-start=true
%test.quarkus.flyway.clean-at-start=true
%test.quarkus.log.level=WARN

# Production profile
%prod.quarkus.datasource.url=${DATABASE_URL}
%prod.quarkus.flyway.migrate-at-start=true
%prod.quarkus.flyway.clean-at-start=false
%prod.quarkus.log.level=ERROR
```

### Configuration Injection Patterns

**Basic Property Injection:**
```java
@ApplicationScoped
public class EmailService {
    
    @ConfigProperty(name = "email.smtp.host")
    String smtpHost;
    
    @ConfigProperty(name = "email.smtp.port", defaultValue = "587")
    int smtpPort;
    
    @ConfigProperty(name = "email.from")
    Optional<String> fromAddress;
    
    @ConfigProperty(name = "email.enabled", defaultValue = "true")
    boolean emailEnabled;
}
```

**Configuration Mapping (Type-Safe):**
```java
@ConfigMapping(prefix = "app")
public interface AppConfig {
    String name();
    String version();
    Optional<String> description();
    
    DatabaseConfig database();
    SecurityConfig security();
    
    interface DatabaseConfig {
        int maxConnections();
        Duration connectionTimeout();
        boolean enableMetrics();
    }
    
    interface SecurityConfig {
        String jwtSecret();
        Duration tokenExpiry();
        List<String> allowedOrigins();
    }
}

// Usage
@ApplicationScoped
public class ConfigurableService {
    
    @Inject
    AppConfig config;
    
    public void doSomething() {
        String appName = config.name();
        int maxConn = config.database().maxConnections();
        String secret = config.security().jwtSecret();
    }
}
```

**Properties File Example:**
```properties
app.name=My Application
app.version=1.0.0
app.description=Sample Quarkus application

app.database.max-connections=20
app.database.connection-timeout=30s
app.database.enable-metrics=true

app.security.jwt-secret=${JWT_SECRET}
app.security.token-expiry=24h
app.security.allowed-origins=http://localhost:3000,https://myapp.com
```

## Testing Strategies

### Unit Testing with JUnit 5 and @QuarkusTest

**Basic Test Structure:**
```java
@QuarkusTest
class UserServiceTest {
    
    @Inject
    UserService userService;
    
    @Test
    @Transactional
    @Rollback
    @DisplayName("Should create user with valid request")
    void shouldCreateUserWithValidRequest() {
        // Given
        CreateUserRequest request = new CreateUserRequest("John Doe", "john@example.com");
        
        // When
        User user = userService.createUser(request);
        
        // Then
        assertThat(user.name()).isEqualTo("John Doe");
        assertThat(user.email()).isEqualTo("john@example.com");
        assertThat(user.id()).isNotNull();
        assertThat(user.active()).isTrue();
        assertThat(user.createdAt()).isNotNull();
    }
    
    @Test
    @DisplayName("Should throw exception when user limit exceeded")
    void shouldThrowExceptionWhenUserLimitExceeded() {
        // Given - assume max users is configured to 1
        CreateUserRequest firstRequest = new CreateUserRequest("John Doe", "john@example.com");
        CreateUserRequest secondRequest = new CreateUserRequest("Jane Doe", "jane@example.com");
        
        // When & Then
        assertDoesNotThrow(() -> userService.createUser(firstRequest));
        assertThrows(ServiceException.class, () -> userService.createUser(secondRequest));
    }
}
```

### Integration Testing Patterns

**REST API Testing with JUnit 5:**
```java
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class UserResourceTest {
    
    @Test
    @Order(1)
    @DisplayName("GET /api/users should return empty list initially")
    void shouldReturnEmptyListInitially() {
        given()
            .when().get("/api/users")
            .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("size()", equalTo(0));
    }
    
    @Test
    @Order(2)
    @DisplayName("POST /api/users should create user successfully")
    void shouldCreateUserSuccessfully() {
        CreateUserRequest request = new CreateUserRequest("Jane Doe", "jane@example.com");
        
        given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .body("name", equalTo("Jane Doe"))
            .body("email", equalTo("jane@example.com"))
            .body("id", notNullValue())
            .body("active", equalTo(true))
            .body("createdAt", notNullValue())
            .header("Location", matchesPattern("/api/users/\\d+"));
    }
    
    @Test
    @Order(3)
    @DisplayName("GET /api/users/{id} should return 404 for non-existent user")
    void shouldReturn404ForNonExistentUser() {
        given()
        .when()
            .get("/api/users/99999")
        .then()
            .statusCode(404);
    }
    
    @Test
    @DisplayName("POST /api/users should return 400 for invalid email")
    void shouldReturn400ForInvalidEmail() {
        CreateUserRequest request = new CreateUserRequest("John Doe", "invalid-email");
        
        given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
        .when()
            .post("/api/users")
        .then()
            .statusCode(400);
    }
}
```

### Mocking Strategies with Mockito

**Mockito Integration with @InjectMock:**
```java
@QuarkusTest
class UserServiceTest {
    
    @InjectMock
    UserRepository userRepository;
    
    @InjectMock
    EmailService emailService;
    
    @Inject
    UserService userService;
    
    @Test
    @DisplayName("Should send welcome email when user is created")
    void shouldSendWelcomeEmailWhenUserCreated() {
        // Given
        CreateUserRequest request = new CreateUserRequest("John Doe", "john@example.com");
        User mockUser = new User(1L, "John Doe", "john@example.com", LocalDateTime.now(), true);
        
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.insert(anyString(), anyString())).thenReturn(mockUser);
        
        // When
        User result = userService.createUser(request);
        
        // Then
        verify(emailService).sendWelcomeEmail("john@example.com", "John Doe");
        verify(userRepository).insert("John Doe", "john@example.com");
        assertThat(result).isEqualTo(mockUser);
    }
    
    @Test
    @DisplayName("Should throw exception when repository count exceeds limit")
    void shouldThrowExceptionWhenRepositoryCountExceedsLimit() {
        // Given
        CreateUserRequest request = new CreateUserRequest("John Doe", "john@example.com");
        when(userRepository.count()).thenReturn(100L); // Assume limit is 50
        
        // When & Then
        assertThrows(ServiceException.class, () -> userService.createUser(request));
        verifyNoInteractions(emailService);
        verify(userRepository, never()).insert(anyString(), anyString());
    }
    
    @BeforeEach
    void setUp() {
        reset(userRepository, emailService);
    }
}
```

**CDI Alternative Mocking (when needed):**
```java
@TestProfile(MockProfile.class)
@QuarkusTest
class UserServiceIntegrationTest {
    // Test implementation
}

public class MockProfile implements QuarkusTestProfile {
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(MockEmailService.class);
    }
}

@Mock
@Alternative
@Priority(1)
@ApplicationScoped
public class MockEmailService implements EmailService {
    private final List<EmailMessage> sentEmails = new ArrayList<>();
    
    @Override
    public void sendWelcomeEmail(String email, String name) {
        sentEmails.add(new EmailMessage(email, "Welcome " + name, "Welcome message"));
    }
    
    public List<EmailMessage> getSentEmails() {
        return Collections.unmodifiableList(sentEmails);
    }
}
```

### TestContainers Integration with Flyway

**Database Integration Testing:**
```java
@QuarkusTestResource(MySQLTestResource.class)
@QuarkusTest
class DatabaseIntegrationTest {
    
    @Inject
    UserRepository userRepository;
    
    @Test
    @DisplayName("Should perform database operations with real MySQL")
    void shouldPerformDatabaseOperationsWithRealMySQL() {
        // Given
        String name = "Integration Test User";
        String email = "integration@test.com";
        
        // When
        User created = userRepository.insert(name, email);
        Optional<User> found = userRepository.findById(created.id());
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo(name);
        assertThat(found.get().email()).isEqualTo(email);
        assertThat(found.get().active()).isTrue();
    }
    
    @Test
    @DisplayName("Should find users by email")
    void shouldFindUsersByEmail() {
        // Given
        String email = "findme@test.com";
        User created = userRepository.insert("Find Me", email);
        
        // When
        Optional<User> found = userRepository.findByEmail(email);
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(created.id());
    }
}

public class MySQLTestResource implements QuarkusTestResourceLifecycleManager {
    
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);
    
    @Override
    public Map<String, String> start() {
        mysql.start();
        return Map.of(
            "quarkus.datasource.url", mysql.getJdbcUrl(),
            "quarkus.datasource.username", mysql.getUsername(),
            "quarkus.datasource.password", mysql.getPassword(),
            "quarkus.flyway.migrate-at-start", "true",
            "quarkus.flyway.clean-at-start", "true"
        );
    }
    
    @Override
    public void stop() {
        // Container will be reused across test runs
    }
}
```

### Native Image Testing

**Native Integration Tests:**
```java
@QuarkusIntegrationTest
class NativeUserResourceIT extends UserResourceTest {
    // Inherits all tests from UserResourceTest
    // Runs against native executable
    
    @Test
    @DisplayName("Native executable should handle JSON serialization correctly")
    void nativeExecutableShouldHandleJsonSerialization() {
        CreateUserRequest request = new CreateUserRequest("Native User", "native@test.com");
        
        given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .body("name", equalTo("Native User"))
            .body("email", equalTo("native@test.com"));
    }
}

**Native-Specific Test Configuration:**
```properties
# Native test configuration
%test.quarkus.native.container-build=true
%test.quarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17
```

## Performance Optimization

### Native Compilation with GraalVM

**Build Native Executable:**
```bash
# Local native build
./mvnw package -Dnative

# Container-based build (recommended)
./mvnw package -Dnative -Dquarkus.native.container-build=true

# Native build with optimizations
./mvnw package -Dnative \
    -Dquarkus.native.container-build=true \
    -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17
```

**Native Configuration:**
```properties
# Native build configuration
quarkus.native.container-build=true
quarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17

# Additional native options
quarkus.native.additional-build-args=-H:+ReportExceptionStackTraces,--initialize-at-run-time=sun.security.provider.NativePRNG
quarkus.native.enable-reports=true
quarkus.native.debug.enabled=false
```

**Reflection Configuration (when needed):**
```java
@RegisterForReflection(targets = {
    MyCustomClass.class,
    ThirdPartyClass.class
})
public class ReflectionConfig {
}
```

### Memory and Startup Optimization

**JVM Tuning:**
```properties
# JVM optimization
quarkus.native.java-opts=-Xmx128m -Xms128m

# Container memory limits
quarkus.container-image.builder=jib
quarkus.jib.base-jvm-image=registry.access.redhat.com/ubi8/openjdk-17-runtime:latest
quarkus.jib.jvm-arguments=-Xmx256m,-Xms256m,-XX:+UseG1GC
```

**Build-Time Initialization:**
```java
@BuildStep
void registerForNative(BuildProducer<NativeImageSystemPropertyBuildItem> systemProps) {
    systemProps.produce(new NativeImageSystemPropertyBuildItem("java.util.logging.manager", 
        "org.jboss.logmanager.LogManager"));
}
```

## Essential Extensions

### Database and Persistence with jOOQ and Flyway

**jOOQ and Flyway Dependencies:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-mysql</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.jooq</groupId>
    <artifactId>jooq</artifactId>
</dependency>
```

**Database Configuration:**
```properties
# Database configuration
quarkus.datasource.db-kind=mysql
quarkus.datasource.url=jdbc:mysql://localhost:3306/mydb
quarkus.datasource.username=user
quarkus.datasource.password=password

# Flyway configuration
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.locations=classpath:db/migration

# jOOQ configuration
quarkus.jooq.generate-schema-source-on-compilation=true
```

**Flyway Migration Example:**
```sql
-- V1__Create_users_table.sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_active ON users(active);
```

**jOOQ DSL Context Configuration:**
```java
@ApplicationScoped
public class JooqConfiguration {
    
    @Inject
    DataSource dataSource;
    
    @Produces
    @ApplicationScoped
    public DSLContext dslContext() {
        return DSL.using(dataSource, SQLDialect.MYSQL);
    }
}
```

### Security and Authentication

**OpenID Connect:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-oidc</artifactId>
</dependency>
```

```properties
# OIDC configuration
quarkus.oidc.auth-server-url=https://your-keycloak.com/realms/your-realm
quarkus.oidc.client-id=your-client-id
quarkus.oidc.credentials.secret=your-client-secret
```

**Security Implementation:**
```java
@Path("/api/secure")
@RolesAllowed("user")
public class SecureResource {
    
    @Inject
    SecurityIdentity identity;
    
    @GET
    @RolesAllowed("admin")
    public String adminOnly() {
        return "Admin content for: " + identity.getPrincipal().getName();
    }
    
    @GET
    @Path("/user")
    public String userContent() {
        return "User content for: " + identity.getPrincipal().getName();
    }
}
```

### Messaging and Events

**Kafka Integration:**
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-kafka-client</artifactId>
</dependency>
```

**Producer:**
```java
@ApplicationScoped
public class UserEventProducer {
    
    @Inject
    @Channel("user-events")
    Emitter<UserEvent> userEventEmitter;
    
    public void publishUserCreated(User user) {
        UserEvent event = new UserEvent("USER_CREATED", user.id, user.name);
        userEventEmitter.send(event);
    }
}
```

**Consumer:**
```java
@ApplicationScoped
public class UserEventConsumer {
    
    @Incoming("user-events")
    public void handleUserEvent(UserEvent event) {
        switch (event.type()) {
            case "USER_CREATED":
                // Handle user creation
                break;
            case "USER_UPDATED":
                // Handle user update
                break;
        }
    }
}
```

### Observability and Monitoring

**Health Checks:**
```java
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {
    
    @Inject
    DataSource dataSource;
    
    @Override
    public HealthCheckResponse call() {
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5);
            return HealthCheckResponse.named("database")
                    .status(isValid)
                    .withData("connection-pool", "active")
                    .build();
        } catch (SQLException e) {
            return HealthCheckResponse.down("database");
        }
    }
}
```

**Metrics:**
```java
@ApplicationScoped
public class UserMetrics {
    
    @Inject
    MeterRegistry registry;
    
    private final Counter userCreationCounter;
    private final Timer userLookupTimer;
    
    public UserMetrics(MeterRegistry registry) {
        this.userCreationCounter = Counter.builder("users.created")
                .description("Number of users created")
                .register(registry);
        this.userLookupTimer = Timer.builder("users.lookup.duration")
                .description("User lookup duration")
                .register(registry);
    }
    
    public void incrementUserCreation() {
        userCreationCounter.increment();
    }
    
    public Timer.Sample startLookupTimer() {
        return Timer.start(registry);
    }
}
```

## Production Deployment

### Container Optimization

**Multi-Stage Dockerfile:**
```dockerfile
# Build stage
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17 AS builder
WORKDIR /project
COPY pom.xml .
COPY src src
RUN ./mvnw package -Dnative -DskipTests

# Runtime stage
FROM quay.io/quarkus/quarkus-micro-image:2.0
COPY --from=builder /project/target/*-runner /application
EXPOSE 8080
USER 1001
ENTRYPOINT ["./application"]
```

**JVM-based Container:**
```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:latest
COPY target/quarkus-app/lib/ /deployments/lib/
COPY target/quarkus-app/*.jar /deployments/
COPY target/quarkus-app/app/ /deployments/app/
COPY target/quarkus-app/quarkus/ /deployments/quarkus/
EXPOSE 8080
USER 185
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
```

### Kubernetes Deployment

**Deployment Configuration:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-quarkus-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: my-quarkus-app
  template:
    metadata:
      labels:
        app: my-quarkus-app
    spec:
      containers:
      - name: app
        image: my-quarkus-app:latest
        ports:
        - containerPort: 8080
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
```

### Configuration for Production

**Production Properties:**
```properties
# Production configuration
%prod.quarkus.log.level=WARN
%prod.quarkus.log.category."org.jooq".level=WARN
%prod.quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{1.}] %s%e%n

# Database
%prod.quarkus.datasource.url=${DATABASE_URL}
%prod.quarkus.flyway.migrate-at-start=true
%prod.quarkus.flyway.clean-at-start=false

# Security
%prod.quarkus.http.ssl.certificate.file=/etc/ssl/certs/server.crt
%prod.quarkus.http.ssl.certificate.key-file=/etc/ssl/private/server.key

# Performance
%prod.quarkus.http.io-threads=8
%prod.quarkus.http.worker-threads=64
```

## Best Practices

### Architecture Guidelines

1. **Use CDI Scopes Appropriately**
   - `@ApplicationScoped` for stateless services
   - `@RequestScoped` for request-specific state
   - `@Singleton` for expensive-to-create objects

2. **Embrace Build-Time Optimization**
   - Minimize runtime reflection
   - Use Quarkus-specific annotations
   - Leverage build-time dependency injection

3. **Design for Native Compilation**
   - Test regularly in native mode
   - Avoid dynamic class loading
   - Use explicit configuration over conventions

### Performance Guidelines

1. **Optimize Startup Time**
   - Use build-time initialization
   - Minimize I/O during startup
   - Cache expensive computations

2. **Memory Efficiency**
   - Use appropriate data structures
   - Avoid memory leaks in long-running processes
   - Monitor garbage collection patterns

3. **Container-First Design**
   - Design for horizontal scaling
   - Use health checks effectively
   - Implement graceful shutdown

### Security Best Practices

1. **Input Validation**
   - Validate all user inputs
   - Use Bean Validation annotations
   - Sanitize output appropriately

2. **Authentication & Authorization**
   - Use modern standards (OIDC, JWT)
   - Implement proper role-based access
   - Secure sensitive endpoints

3. **Configuration Security**
   - Never commit secrets to version control
   - Use environment variables for sensitive data
   - Implement proper secret rotation

### Testing Best Practices

1. **Test Pyramid**
   - Many unit tests
   - Moderate integration tests
   - Few end-to-end tests

2. **Native Testing Strategy**
   - Test critical paths in native mode
   - Use TestContainers for complex scenarios
   - Validate performance characteristics

3. **Test Data Management**
   - Use transactions with rollback in tests
   - Isolate test data between test methods
   - Use realistic test data volumes

This comprehensive guide covers the essential aspects of Quarkus development, from initial project setup through production deployment. Focus on the patterns and practices that align with your specific use case and gradually adopt more advanced features as your application evolves.