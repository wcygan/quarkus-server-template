# Development Environment Guide

This guide covers the complete development environment setup and workflow for the Quarkus User Management API.

## Quick Start

### Prerequisites
- Java 17 or later
- Maven 3.8+
- Docker and Docker Compose
- Git

### One-Command Setup
```bash
# Complete environment setup
./scripts/dev-setup.sh

# Start development server
./scripts/dev-start.sh
```

## Manual Setup

### 1. Start MySQL Database
```bash
# Start MySQL in Docker
docker-compose up -d mysql

# Verify MySQL is running
docker-compose ps
```

### 2. Run Database Migrations
```bash
# Apply Flyway migrations
./mvnw flyway:migrate

# Check migration status
./mvnw flyway:info
```

### 3. Generate jOOQ Classes
```bash
# Generate type-safe database classes
./mvnw generate-sources
```

### 4. Start Quarkus Development Mode
```bash
# Start with hot reload
./mvnw quarkus:dev
```

## Development URLs

| Service | URL | Description |
|---------|-----|-------------|
| Application | http://localhost:8080 | Main API endpoints |
| Health Check | http://localhost:8080/q/health | Application health status |
| Dev UI | http://localhost:8080/q/dev | Quarkus development interface |
| MySQL | localhost:3306 | Database connection |

## Development Commands

### Database Operations
```bash
# Reset database (clean + migrate)
./mvnw flyway:clean flyway:migrate

# Check migration status
./mvnw flyway:info

# Validate migrations
./mvnw flyway:validate
```

### jOOQ Code Generation
```bash
# Regenerate jOOQ classes after schema changes
./mvnw generate-sources

# Clean and regenerate
./mvnw clean generate-sources
```

### Testing
```bash
# Run all tests
./mvnw test

# Run integration tests only
./mvnw verify -Dtest=*IntegrationTest

# Run with TestContainers
./mvnw test -Dquarkus.test.profile=test
```

### Build and Package
```bash
# JVM build
./mvnw clean package

# Native build (requires GraalVM)
./mvnw clean package -Pnative

# Build Docker images
docker build --target runtime-jvm -t user-api:jvm .
```

## IDE Setup

### IntelliJ IDEA
1. Import as Maven project
2. Enable annotation processing
3. Install Quarkus plugin
4. Configure code style (Java 17, 4 spaces)

### VS Code
1. Install Java Extension Pack
2. Install Quarkus Extension
3. Configure workspace settings for Maven

### Eclipse
1. Import existing Maven project
2. Install Quarkus Tools plugin
3. Enable Maven nature

## Development Workflow

### 1. Feature Development
```bash
# 1. Ensure database is running
docker-compose up -d mysql

# 2. Create/modify Flyway migration
# Edit: src/main/resources/db/migration/V{version}__{description}.sql

# 3. Apply migration
./mvnw flyway:migrate

# 4. Regenerate jOOQ classes
./mvnw generate-sources

# 5. Implement feature (TDD approach)
# - Write tests first
# - Implement code
# - Verify with Quarkus dev mode

# 6. Test
./mvnw test
```

### 2. Database Schema Changes
```bash
# 1. Create new migration file
# Example: V2__Add_email_to_users.sql

# 2. Apply migration
./mvnw flyway:migrate

# 3. Regenerate jOOQ classes
./mvnw generate-sources

# 4. Update code to use new schema
```

### 3. Hot Reload Development
- Start `./mvnw quarkus:dev`
- Make code changes
- Save files
- Changes automatically reload
- Test in browser/Postman

## Environment Variables

### Development (.env file)
```bash
# Database
DB_URL=jdbc:mysql://localhost:3306/userapi
DB_USERNAME=userapi
DB_PASSWORD=userapi

# Quarkus
QUARKUS_PROFILE=dev
QUARKUS_LOG_LEVEL=DEBUG
```

### Production
```bash
# Override in production environment
DB_URL=jdbc:mysql://prod-host:3306/userapi
DB_USERNAME=${PROD_DB_USER}
DB_PASSWORD=${PROD_DB_PASSWORD}
QUARKUS_PROFILE=prod
```

## Common Development Tasks

### Adding New Dependencies
```bash
# Add Quarkus extension
./mvnw quarkus:add-extension -Dextensions="extension-name"

# Example: Add OpenAPI support
./mvnw quarkus:add-extension -Dextensions="quarkus-smallrye-openapi"
```

### Database Connection Issues
```bash
# Check MySQL status
docker-compose logs mysql

# Test connection
docker-compose exec mysql mysql -u userapi -p userapi -e "SELECT 1"

# Reset database
docker-compose down -v
docker-compose up -d mysql
./mvnw flyway:migrate
```

### jOOQ Generation Issues
```bash
# Ensure database is running and migrated
docker-compose ps mysql
./mvnw flyway:info

# Clean and regenerate
./mvnw clean
./mvnw generate-sources

# Check generated classes
ls -la target/generated-sources/jooq/
```

## Performance Tips

### Development Mode
- Use `./mvnw quarkus:dev` for fastest reload
- Keep MySQL container running between sessions
- Use incremental compilation
- Monitor memory usage with Dev UI

### Build Optimization
- Use multi-stage Docker builds
- Enable native compilation for production
- Profile with JVM tools during development

## Troubleshooting

### Common Issues

#### MySQL Connection Refused
```bash
# Check if MySQL is running
docker-compose ps mysql

# Check logs
docker-compose logs mysql

# Restart if needed
docker-compose restart mysql
```

#### jOOQ Classes Not Found
```bash
# Regenerate sources
./mvnw clean generate-sources

# Check if classes exist
find target/generated-sources -name "*.java" | head -5
```

#### Flyway Migration Errors
```bash
# Check migration status
./mvnw flyway:info

# Repair if needed (development only)
./mvnw flyway:repair

# Start fresh (development only)
docker-compose down -v
docker-compose up -d mysql
./mvnw flyway:migrate
```

#### Port Already in Use
```bash
# Find process using port 8080
lsof -i :8080

# Kill process if needed
kill -9 <PID>

# Or use different port
./mvnw quarkus:dev -Dquarkus.http.port=8081
```

### Getting Help
- Check Quarkus Dev UI: http://localhost:8080/q/dev
- Review application logs
- Check container logs: `docker-compose logs`
- Validate configuration: `./mvnw quarkus:info`

## Next Steps
After successful setup, proceed to [PHASE-2.md](PHASE-2.md) for core implementation.