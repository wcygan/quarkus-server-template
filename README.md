# User Management API - Quarkus Template

A cloud-native User Management API built with Quarkus, jOOQ, and MySQL.

## Quick Start

```bash
# Start development mode with hot reload
./mvnw quarkus:dev

# Run tests
./mvnw test

# Run integration tests with database (requires Docker)
./mvnw test -Dtest.database=true

# Build application
./mvnw clean package
```

## Project Status

This is currently a **template project** in the planning phase. Implementation follows the phases defined in `PHASE-*.md` files.

## Tech Stack

- **Quarkus 3.15.1** - Cloud-native Java framework
- **jOOQ** - Type-safe database access
- **MySQL** - Primary database
- **Flyway** - Database migrations
- **TestContainers** - Integration testing

## Health Endpoints

- `/api/health` - Application health
- `/q/health` - Quarkus health checks
- `/q/health/live` - Liveness probe
- `/q/health/ready` - Readiness probe

## Development

See `QUARKUS.md` for detailed development guidelines and `PLAN.md` for implementation roadmap.