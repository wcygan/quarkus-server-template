# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **template repository** for building cloud-native User Management APIs using Quarkus. The project is currently in the **planning and documentation phase** - no implementation exists yet. All files are comprehensive planning documents that define architecture, implementation phases, and best practices.

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

### Project Setup (When Implementation Begins)
```bash
# Create Quarkus project
quarkus create app com.example:user-api --extension=rest,rest-jackson,jdbc-mysql,flyway,smallrye-health

# Development mode with hot reload
mvn quarkus:dev

# Generate jOOQ classes from database
mvn generate-sources

# Run Flyway migrations
mvn flyway:migrate
```

### Build Commands
```bash
# Standard JVM build
mvn clean package

# Native compilation 
mvn clean package -Pnative

# Build Docker images
docker build --target runtime-jvm -t user-api:jvm .
docker build --target runtime-native -t user-api:native .
```

### Testing Commands
```bash
# Run all tests
mvn clean verify

# Unit tests only
mvn test

# Integration tests with TestContainers
mvn verify -Dtest=*IntegrationTest

# Native compilation tests
mvn verify -Pnative
```

## Implementation Phases

The project follows a **three-phase implementation approach**:

1. **PHASE-1.md**: Foundation (Maven setup, database schema, jOOQ generation)
2. **PHASE-2.md**: Core Implementation (domain models, services, REST endpoints)  
3. **PHASE-3.md**: Production Readiness (testing, containers, K8s deployment)

Each phase has detailed task breakdowns, success criteria, and dependencies clearly documented.

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

- **Dev**: MySQL Dev Services, aggressive migration settings
- **Test**: H2 in-memory with MySQL compatibility mode
- **Prod**: External MySQL, conservative migration settings

## Health Check Endpoints

- `/q/health` - Combined health status
- `/q/health/live` - Liveness probe (application running)
- `/q/health/ready` - Readiness probe (ready to serve requests)

## Key Documentation Files

- **PLAN.md**: Master implementation plan with task dependencies
- **QUARKUS.md**: Comprehensive Quarkus development guide
- **quarkus-recommended-tech-stack.md**: Technology choices and rationales
- **PHASE-*.md**: Detailed phase-by-phase implementation guides

## Success Criteria for Implementation

- Sub-second native startup times
- Type-safe database queries with jOOQ
- >90% test coverage across all layers  
- Comprehensive health monitoring
- Duplicate username prevention
- Native compilation compatibility