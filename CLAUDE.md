# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **fully functional template repository** for building cloud-native User Management APIs using Quarkus. **Phase 1 (Foundation) is complete** with a working Maven project, database schema, jOOQ integration, and comprehensive test suite. The project demonstrates enterprise-grade architecture patterns and best practices.

**Current Status**: ✅ **Phase 1 Complete** - Ready for Phase 2 (Core Implementation)

## Key Architecture Decisions

**Technology Stack:**
- **Quarkus 3.x** with REST (RESTEasy Reactive) for sub-second startup times
- **jOOQ** for type-safe database access (NOT JPA/Hibernate)
- **Flyway** for database schema migrations
- **MySQL** as the primary database
- **Java Records** for immutable domain models
- **UUID-based primary keys** with server-side generation

**Layered Architecture:**
```
REST Layer (UserResource) 
    ↓
Service Layer (UserService) 
    ↓  
Repository Layer (UserRepository with jOOQ)
    ↓
Database (MySQL + Flyway migrations)
```

## Essential Commands

### Development Workflow
```bash
# Start local development environment
docker-compose up -d
./mvnw quarkus:dev

# Generate jOOQ classes from database schema
./mvnw generate-sources

# Compile project with jOOQ generation
./mvnw clean compile
```

### Testing Commands
```bash
# Run basic unit tests (no database required)
./mvnw test

# Run integration tests with TestContainers (requires Docker)
./mvnw test -Dtest.database=true

# Run specific test classes
./mvnw test -Dtest.database=true -Dtest="UserRepositoryTest"
./mvnw test -Dtest.database=true -Dtest="DatabaseIntegrationTest,SimpleMySQLIntegrationTest"

# Run all tests (unit + integration)
./mvnw verify -Dtest.database=true
```

### Build Commands
```bash
# Standard JVM build
./mvnw clean package

# Native compilation (requires GraalVM)
./mvnw clean package -Pnative

# Build Docker images (when implemented)
# docker build --target runtime-jvm -t user-api:jvm .
# docker build --target runtime-native -t user-api:native .
```

### Database Management
```bash
# Start/stop MySQL database
docker-compose up -d mysql
docker-compose down

# Run Flyway migrations manually (if needed)
./mvnw flyway:migrate

# Clean database (development only)
./mvnw flyway:clean
```

## Implementation Phases

The project follows a **three-phase implementation approach**:

1. ✅ **PHASE-1.md**: Foundation (Maven setup, database schema, jOOQ generation) - **COMPLETE**
2. 🚧 **PHASE-2.md**: Core Implementation (domain models, services, REST endpoints) - **NEXT**
3. 📋 **PHASE-3.md**: Production Readiness (testing, containers, K8s deployment) - **PLANNED**

Each phase has detailed task breakdowns, success criteria, and dependencies clearly documented.

### Phase 1 Achievements ✅
- Complete Maven project with Quarkus 3.15.1
- Working jOOQ code generation pipeline
- MySQL database schema with Flyway migrations
- Comprehensive integration test suite (23 tests)
- Production-ready configuration profiles
- Docker-based development environment

## Critical Design Patterns

**Domain Model Strategy:**
- Java Records for immutable entities (`User`)
- Separate DTOs for API contracts (`CreateUserRequest`, `UserResponse`)
- Bean Validation on DTOs, business validation in services

**Database Strategy:**
- Schema-first development using Flyway migrations
- jOOQ code generation from migrated schema
- Repository pattern with CDI injection
- Transactional boundaries at service layer

**Error Handling:**
- Custom business exceptions (`DuplicateUsernameException`, `UserNotFoundException`)
- Global exception mappers for consistent API responses
- Proper HTTP status codes (201, 404, 409, etc.)

## Development Workflow

1. **Database First**: Create Flyway migration → Run migration → Generate jOOQ classes
2. **Layer by Layer**: Implement Repository → Service → REST Resource
3. **Test Driven**: Write tests before implementation
4. **Type Safety**: Leverage jOOQ compilation checks and Bean Validation

## Configuration Profiles

- **Dev**: Local MySQL via Docker Compose, hot reload enabled
- **Test**: TestContainers MySQL for integration tests, H2 for unit tests
- **Prod**: External MySQL, optimized connection pooling (to be implemented)

## Health Check Endpoints

- `/q/health` - Combined health status
- `/q/health/live` - Liveness probe (application running)
- `/q/health/ready` - Readiness probe (ready to serve requests)

## Project Structure & Key Files

### Core Application Files
```
src/main/java/com/example/
├── Application.java                    # Main application entry point
├── config/JooqConfiguration.java      # jOOQ CDI configuration  
├── repository/UserRepository.java     # jOOQ-based repository implementation
└── resource/HealthResource.java       # Basic health endpoint

src/main/resources/
├── application.properties             # Multi-environment configuration
└── db/migration/
    └── V1__Create_users_table.sql     # Database schema migration
```

### Test Infrastructure
```
src/test/java/com/example/
├── ApplicationTest.java                         # Basic application tests
├── integration/
│   ├── BaseJooqDatabaseTest.java               # Base class for jOOQ tests
│   ├── DatabaseIntegrationTest.java            # Database integration tests
│   ├── MySQLTestResource.java                  # TestContainers resource
│   └── SimpleMySQLIntegrationTest.java         # Basic MySQL tests
└── repository/
    └── UserRepositoryTest.java                 # Repository layer tests (17 tests)
```

### Development Files
```
docker-compose.yml                      # Local MySQL development environment
pom.xml                                 # Maven configuration with jOOQ plugin
target/generated-sources/jooq/          # Generated jOOQ classes (excluded from git)
```

### Documentation Files
- **PLAN.md**: Master implementation plan with task dependencies
- **QUARKUS.md**: Comprehensive Quarkus development guide  
- **quarkus-recommended-tech-stack.md**: Technology choices and rationales
- **PHASE-*.md**: Detailed phase-by-phase implementation guides

## Specialized Agent Usage

This project has specialized agents that should be used frequently for relevant tasks:

### quarkus-specialist Agent
**Use for all Quarkus-related tasks:**
- Project setup and configuration
- REST endpoint implementation with RESTEasy Reactive
- CDI and dependency injection patterns
- Configuration profiles and MicroProfile Config
- Native compilation troubleshooting
- Health checks and observability setup
- Testing with QuarkusTest framework
- Performance optimization and tuning

**Examples:**
- "Configure Quarkus application.properties for multiple environments"
- "Implement REST endpoints following Quarkus best practices"
- "Debug native compilation reflection errors"
- "Set up health checks and metrics"

### jooq-specialist Agent
**Use for all jOOQ database operations:**
- Repository layer implementation
- Type-safe query construction with jOOQ DSL
- Complex JOIN operations and subqueries
- Pagination and filtering patterns
- Code generation configuration
- Transaction management
- Performance optimization
- Converting raw SQL to jOOQ DSL

**Examples:**
- "Create UserRepository with jOOQ for CRUD operations"
- "Implement complex search queries with dynamic filtering"
- "Configure jOOQ code generation from Flyway migrations"
- "Optimize jOOQ queries for better performance"

### mysql-database-architect Agent
**Use for all database design and optimization:**
- Schema design and table relationships
- Index optimization and query performance
- Flyway migration scripts
- Database configuration for different environments
- Connection pooling setup
- Query performance analysis
- Data modeling best practices

**Examples:**
- "Design MySQL schema for user management with proper indexes"
- "Analyze slow query performance and optimize"
- "Create Flyway migration for new features"
- "Configure MySQL for Quarkus with proper connection pooling"

**Agent Selection Guidelines:**
- Use **quarkus-specialist** for framework-level concerns
- Use **jooq-specialist** for repository and query implementation  
- Use **mysql-database-architect** for schema design and database optimization
- Combine agents when tasks span multiple domains (e.g., Quarkus + jOOQ integration)

### Working Code Examples

**jOOQ Repository Pattern** (see `src/main/java/com/example/repository/UserRepository.java`):
```java
@ApplicationScoped
public class UserRepository {
    @Inject DSLContext dslContext;
    
    public UsersRecord createUser(String username) {
        // Type-safe jOOQ operations with generated classes
        return dslContext.insertInto(USERS)
            .set(USERS.ID, UUID.randomUUID().toString())
            .set(USERS.USERNAME, username)
            .returning()
            .fetchOne();
    }
}
```

**Integration Test Pattern** (see `src/test/java/com/example/integration/BaseJooqDatabaseTest.java`):
```java
@QuarkusTest
@QuarkusTestResource(MySQLTestResource.class)
@EnabledIfSystemProperty(named = "test.database", matches = "true") 
public abstract class BaseJooqDatabaseTest {
    @Inject protected DSLContext dslContext;
    // Automatic table cleanup and isolation
}
```

**TestContainers Setup** (see `src/test/java/com/example/integration/MySQLTestResource.java`):
```java
public class MySQLTestResource implements QuarkusTestResourceLifecycleManager {
    // Manages MySQL container lifecycle for tests
    // Provides isolated database environment per test class
}
```

## Success Criteria

### Phase 1 ✅ (Complete)
- ✅ Sub-second JVM startup times (achieved)
- ✅ Type-safe database queries with jOOQ (working)
- ✅ Comprehensive test coverage (23 integration tests)
- ✅ Health monitoring endpoints (implemented)
- ✅ Duplicate username prevention (tested)
- ✅ Production-ready database architecture

### Phase 2 🚧 (Next Goals)
- REST API endpoints with proper HTTP status codes
- Service layer with business logic and validation
- DTO models with Bean Validation
- Global exception handling
- API documentation with OpenAPI

### Phase 3 📋 (Future Goals)  
- Native compilation compatibility
- Container images (JVM + Native)
- Kubernetes deployment manifests
- Performance benchmarking
- Production monitoring and observability

## Common Development Tasks

### Adding New Database Tables
1. Create Flyway migration: `src/main/resources/db/migration/V2__Add_new_table.sql`
2. Run migration: `./mvnw flyway:migrate`
3. Generate jOOQ classes: `./mvnw generate-sources`
4. Implement repository with type-safe queries
5. Add integration tests extending `BaseJooqDatabaseTest`

### Running Specific Tests
```bash
# Test specific functionality
./mvnw test -Dtest.database=true -Dtest="UserRepositoryTest#testCreateUser"

# Test database integration
./mvnw test -Dtest.database=true -Dtest="DatabaseIntegrationTest"

# Test repository layer
./mvnw test -Dtest.database=true -Dtest="UserRepositoryTest"
```

### Debugging Database Issues
1. Check Docker container: `docker-compose ps`
2. View logs: `docker-compose logs mysql`
3. Connect to database: `docker exec -it quarkus-mysql-dev mysql -u userapi -puserapi userapi`
4. Verify schema: `SHOW TABLES; DESCRIBE users;`

## Best Practices Learned

1. **Database First**: Always create Flyway migrations before implementing code
2. **Test Isolation**: Use `BaseJooqDatabaseTest` for automatic cleanup
3. **Type Safety**: Leverage jOOQ generated classes for compile-time safety
4. **Container Management**: Use TestContainers for reliable integration testing
5. **Profile Separation**: Keep test and development configurations separate