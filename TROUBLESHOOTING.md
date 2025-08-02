# Troubleshooting Guide

This guide covers common issues and solutions for the Quarkus User Management API development environment.

## Quick Diagnosis

### Environment Check
```bash
# Run comprehensive environment verification
./scripts/verify-environment.sh
```

### Service Status
```bash
# Check all services
docker-compose ps

# Check application health
curl http://localhost:8080/q/health
```

## Common Issues

### 1. MySQL Connection Issues

#### Symptoms
- `Connection refused` errors
- `Unknown database 'userapi'` errors
- Timeout during connection

#### Solutions
```bash
# Check if MySQL container is running
docker-compose ps mysql

# Check MySQL logs
docker-compose logs mysql

# Restart MySQL
docker-compose restart mysql

# Complete reset (development only)
docker-compose down -v
docker-compose up -d mysql
```

#### Verification
```bash
# Test connection directly
docker-compose exec mysql mysql -u userapi -puserapi -e "SELECT 1" userapi
```

### 2. Port Conflicts

#### Symptoms
- `Port already in use` errors
- `Address already in use` errors

#### Solutions
```bash
# Find process using port 3306 (MySQL)
lsof -i :3306

# Find process using port 8080 (Quarkus)
lsof -i :8080

# Kill process if needed
kill -9 <PID>

# Or use different ports in application.properties
%dev.quarkus.http.port=8081
```

### 3. jOOQ Code Generation Failures

#### Symptoms
- `Cannot find symbol` errors for jOOQ classes
- `Package com.example.generated.jooq does not exist`
- Empty `target/generated-sources/jooq` directory

#### Solutions
```bash
# Ensure database is running and migrated
docker-compose ps mysql
./mvnw flyway:info

# Clean and regenerate
./mvnw clean
./mvnw generate-sources

# Check if classes were generated
find target/generated-sources/jooq -name "*.java" | head -5
```

#### Verification
```bash
# Should show generated classes
ls -la target/generated-sources/jooq/com/example/generated/jooq/
```

### 4. Flyway Migration Issues

#### Symptoms
- `Migration checksum mismatch` errors
- `Schema validation failed` errors
- `Baseline required` errors

#### Solutions
```bash
# Check migration status
./mvnw flyway:info

# Development environment - repair and migrate
./mvnw flyway:repair
./mvnw flyway:migrate

# Nuclear option - start fresh (development only)
docker-compose down -v
docker-compose up -d mysql
./mvnw flyway:migrate
```

### 5. Quarkus Development Mode Issues

#### Symptoms
- Hot reload not working
- `ClassNotFoundException` errors
- Slow startup times

#### Solutions
```bash
# Clean and restart
./mvnw clean
./mvnw quarkus:dev

# Check for conflicting processes
ps aux | grep java

# Clear Maven cache
rm -rf ~/.m2/repository/com/example/user-api
```

### 6. Maven Dependency Issues

#### Symptoms
- `Could not resolve dependencies` errors
- `Plugin not found` errors
- Build failures

#### Solutions
```bash
# Update Maven wrapper
./mvnw wrapper:wrapper

# Force dependency refresh
./mvnw dependency:resolve -U

# Clear local repository (nuclear option)
rm -rf ~/.m2/repository
./mvnw dependency:resolve
```

### 7. Docker Issues

#### Symptoms
- `Cannot connect to Docker daemon` errors
- Container startup failures
- Volume mount issues

#### Solutions
```bash
# Check Docker status
docker info

# Restart Docker service (Linux)
sudo systemctl restart docker

# Restart Docker Desktop (macOS/Windows)
# Use Docker Desktop GUI

# Clean Docker resources
docker system prune -a
```

### 8. Native Compilation Issues

#### Symptoms
- `UnsatisfiedLinkError` in native mode
- Reflection configuration errors
- Missing resources in native image

#### Solutions
```bash
# Add reflection configuration
# Edit: src/main/resources/META-INF/native-image/reflect-config.json

# Test native compilation
./mvnw clean package -Pnative

# Use JVM mode for development
./mvnw quarkus:dev
```

## Performance Issues

### Slow Startup

#### Causes
- Missing jOOQ classes requiring regeneration
- Network issues with Docker
- Insufficient system resources

#### Solutions
```bash
# Pre-generate jOOQ classes
./mvnw generate-sources

# Increase Docker memory allocation
# Docker Desktop → Settings → Resources → Memory

# Use development profile
./mvnw quarkus:dev -Dquarkus.profile=dev
```

### High Memory Usage

#### Solutions
```bash
# Monitor memory usage
docker stats

# Limit container memory
# Edit docker-compose.yml:
# services:
#   mysql:
#     mem_limit: 512m

# Use JVM memory tuning
export MAVEN_OPTS="-Xmx1024m"
./mvnw quarkus:dev
```

## Development Workflow Issues

### IDE Integration Problems

#### IntelliJ IDEA
```bash
# Refresh Maven project
# View → Tool Windows → Maven → Reload

# Enable annotation processing
# Settings → Build → Compiler → Annotation Processors → Enable

# Invalidate caches and restart
# File → Invalidate Caches and Restart
```

#### VS Code
```bash
# Reload Java projects
# Command Palette (Cmd+Shift+P) → "Java: Reload Projects"

# Install recommended extensions
# View → Extensions → @recommended
```

### Test Execution Issues

#### TestContainers Problems
```bash
# Ensure Docker is running
docker info

# Clean test containers
docker container prune -f

# Run specific test
./mvnw test -Dtest=DatabaseIntegrationTest
```

#### Test Database Issues
```bash
# Use test profile
./mvnw test -Dquarkus.test.profile=test

# Check test configuration
cat src/test/resources/application.properties
```

## Environment Reset

### Complete Reset (Nuclear Option)
```bash
# Stop all services
docker-compose down -v

# Clean Maven build
./mvnw clean

# Remove generated sources
rm -rf target/

# Clean Docker
docker system prune -a

# Start fresh
./scripts/dev-setup.sh
```

### Partial Reset
```bash
# Reset database only
docker-compose down -v mysql
docker-compose up -d mysql
./mvnw flyway:migrate

# Reset jOOQ only
rm -rf target/generated-sources/
./mvnw generate-sources
```

## Getting Help

### Log Analysis
```bash
# Application logs
./mvnw quarkus:dev

# Database logs
docker-compose logs -f mysql

# Container inspection
docker-compose exec mysql bash
```

### Configuration Validation
```bash
# Check Quarkus configuration
./mvnw quarkus:info

# Validate Docker Compose
docker-compose config

# Check port availability
netstat -tulpn | grep :8080
```

### Health Checks
```bash
# Application health
curl http://localhost:8080/q/health

# Database health
curl http://localhost:8080/q/health/ready

# Development UI
open http://localhost:8080/q/dev
```

## Prevention Tips

1. **Always verify environment** before starting development
2. **Keep Docker resources adequate** (memory, disk)
3. **Use scripts for common tasks** (setup, start, reset)
4. **Monitor logs regularly** during development
5. **Keep dependencies updated** periodically
6. **Use version control** for configuration changes
7. **Document environment-specific issues** and solutions

## Contact and Support

- Check [DEVELOPMENT.md](DEVELOPMENT.md) for workflow guidance
- Review [QUARKUS.md](QUARKUS.md) for framework-specific help
- Consult official documentation:
  - [Quarkus Guides](https://quarkus.io/guides/)
  - [jOOQ Documentation](https://www.jooq.org/doc/)
  - [Flyway Documentation](https://flywaydb.org/documentation/)