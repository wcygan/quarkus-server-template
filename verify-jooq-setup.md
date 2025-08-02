# jOOQ Setup Verification Guide

This guide helps verify that the jOOQ code generation pipeline is properly configured and working.

## Prerequisites Verification

### 1. Check Docker is Running
```bash
docker --version
docker ps
```

### 2. Verify Maven Configuration
```bash
mvn --version
```

### 3. Check Database Configuration
```bash
# Verify properties are set correctly
grep -A 5 "db\." pom.xml
```

## jOOQ Pipeline Verification

### Step 1: Start Development Database
```bash
# Start MySQL container
docker run --name mysql-jooq-test \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=userapi \
  -e MYSQL_USER=userapi \
  -e MYSQL_PASSWORD=userapi \
  -p 3306:3306 \
  -d mysql:8.0

# Wait for MySQL to start (30 seconds)
sleep 30

# Verify connection
docker exec mysql-jooq-test mysql -u userapi -p'userapi' -e "SELECT 1"
```

### Step 2: Apply Flyway Migrations
```bash
# Run migrations
mvn flyway:migrate

# Verify migrations applied
mvn flyway:info

# Check table was created
docker exec mysql-jooq-test mysql -u userapi -p'userapi' userapi -e "SHOW TABLES"
docker exec mysql-jooq-test mysql -u userapi -p'userapi' userapi -e "DESCRIBE users"
```

Expected output:
```
+----------+----------+------+-----+-------------------+-------+
| Field    | Type     | Null | Key | Default           | Extra |
+----------+----------+------+-----+-------------------+-------+
| id       | char(36) | NO   | PRI | NULL              |       |
| username | varchar(50) | NO | UNI | NULL              |       |
| created_at | timestamp | YES |     | CURRENT_TIMESTAMP |       |
+----------+----------+------+-----+-------------------+-------+
```

### Step 3: Generate jOOQ Classes
```bash
# Generate jOOQ classes
mvn generate-sources

# Verify generated files exist
ls -la target/generated-sources/jooq/com/example/generated/jooq/
ls -la target/generated-sources/jooq/com/example/generated/jooq/tables/
ls -la target/generated-sources/jooq/com/example/generated/jooq/tables/records/
```

Expected files:
```
target/generated-sources/jooq/com/example/generated/jooq/
├── DefaultCatalog.java
├── Indexes.java
├── Keys.java
├── Tables.java
├── tables/
│   └── Users.java
└── tables/records/
    └── UsersRecord.java
```

### Step 4: Verify Compilation
```bash
# Compile the project (includes generated sources)
mvn compile

# Check for compilation errors
echo $?  # Should be 0
```

### Step 5: Test Database Integration
```bash
# Run the repository test
mvn test -Dtest=UserRepositoryTest -Dtest.database=true

# Run all integration tests
mvn test -Dtest=*IntegrationTest -Dtest.database=true
```

### Step 6: Verify CDI Integration
```bash
# Start Quarkus in dev mode (optional verification)
mvn quarkus:dev

# In another terminal, test health endpoints
curl http://localhost:8080/q/health
curl http://localhost:8080/q/health/ready
```

## Success Criteria Checklist

- [ ] MySQL container starts successfully
- [ ] Flyway migrations apply without errors
- [ ] `users` table exists with correct schema
- [ ] jOOQ code generation creates all expected files
- [ ] Generated classes compile without errors
- [ ] `Tables.USERS` reference is available
- [ ] `UsersRecord` class is generated with proper fields
- [ ] DSLContext can be injected in CDI beans
- [ ] Repository tests pass with database operations
- [ ] Type-safe queries work correctly

## Troubleshooting

### jOOQ Generation Fails
```bash
# Check database connection
docker exec mysql-jooq-test mysql -u userapi -p'userapi' userapi -e "SELECT 1"

# Verify plugin configuration
mvn help:effective-pom | grep -A 30 jooq-codegen-maven

# Run with debug output
mvn generate-sources -X -Djooq.codegen.skip=false
```

### Compilation Errors
```bash
# Clean and regenerate
mvn clean
mvn flyway:migrate
mvn generate-sources
mvn compile

# Check IDE source paths include target/generated-sources/jooq
```

### Test Failures
```bash
# Verify TestContainers works
docker run --rm hello-world

# Check test configuration
cat src/test/java/com/example/integration/MySQLTestProfile.java

# Run with debug logging
mvn test -Dtest=UserRepositoryTest -Dtest.database=true -Dlogging.level.root=DEBUG
```

## Cleanup
```bash
# Stop and remove test database
docker stop mysql-jooq-test
docker rm mysql-jooq-test

# Clean Maven artifacts
mvn clean
```

## Configuration Summary

The jOOQ setup includes:

1. **Maven Plugin Configuration**: `pom.xml`
   - jOOQ code generation plugin with MySQL driver
   - Automatic execution in `generate-sources` phase
   - Target package: `com.example.generated.jooq`

2. **Generated Code Location**: `target/generated-sources/jooq/`
   - Excluded from Git (in `.gitignore`)
   - Added to IDE classpath automatically

3. **CDI Integration**: `JooqConfiguration.java`
   - DSLContext producer for dependency injection
   - Integration with Quarkus Agroal DataSource

4. **Test Framework**: `BaseJooqDatabaseTest.java`
   - TestContainers MySQL setup
   - Automatic table cleanup after each test
   - Sequential test execution for isolation

This configuration ensures type-safe database operations with compile-time verification and seamless integration with Quarkus CDI.