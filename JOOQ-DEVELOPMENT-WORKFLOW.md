# jOOQ Development Workflow Guide

This guide documents the complete jOOQ code generation pipeline and development workflow for the User Management API.

## Overview

The project uses jOOQ for type-safe database access with the following architecture:
- **Schema-first development**: Flyway migrations define database schema
- **Code generation**: jOOQ Maven plugin generates type-safe classes
- **Repository pattern**: CDI-managed repositories use jOOQ DSL
- **Test isolation**: BaseJooqDatabaseTest provides automatic cleanup

## Prerequisites

- Docker running (for TestContainers)
- MySQL 8.0+ database for development
- Maven 3.8+
- Java 17+

## 1. Database Setup

### Development Database
```bash
# Start MySQL container for development
docker run --name mysql-dev \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=userapi \
  -e MYSQL_USER=userapi \
  -e MYSQL_PASSWORD=userapi \
  -p 3306:3306 \
  -d mysql:8.0

# Verify connection
mysql -h localhost -P 3306 -u userapi -p userapi
```

### Apply Migrations
```bash
# Run Flyway migrations
mvn flyway:migrate

# Check migration status
mvn flyway:info

# Clean database (development only)
mvn flyway:clean
```

## 2. jOOQ Code Generation

### Manual Generation
```bash
# Generate jOOQ classes from current schema
mvn generate-sources

# Force regeneration (clean first)
mvn clean generate-sources
```

### Generated Artifacts
The code generation creates these files in `target/generated-sources/jooq/`:

```
com/example/generated/jooq/
├── DefaultCatalog.java          # Database catalog
├── Tables.java                  # Table references
├── tables/
│   └── Users.java              # Users table definition
└── tables/records/
    └── UsersRecord.java        # Users record class
```

### Generated Code Examples
```java
// Table reference (static import)
import static com.example.generated.jooq.Tables.USERS;

// Type-safe query
List<UsersRecord> users = dslContext
    .selectFrom(USERS)
    .where(USERS.USERNAME.like("john%"))
    .orderBy(USERS.CREATED_AT.desc())
    .fetch();

// Insert with returning
UsersRecord newUser = dslContext
    .insertInto(USERS)
    .set(USERS.ID, UUID.randomUUID().toString())
    .set(USERS.USERNAME, "newuser")
    .returning()
    .fetchOne();
```

## 3. Development Workflow

### Schema Changes
1. **Create Migration**: Add new Flyway migration file
   ```sql
   -- V2__Add_email_to_users.sql
   ALTER TABLE users ADD COLUMN email VARCHAR(255) UNIQUE;
   ```

2. **Apply Migration**: Run Flyway migrate
   ```bash
   mvn flyway:migrate
   ```

3. **Regenerate jOOQ**: Update generated classes
   ```bash
   mvn generate-sources
   ```

4. **Update Repository**: Add new operations using updated DSL
   ```java
   public Optional<UsersRecord> findByEmail(String email) {
       return Optional.ofNullable(
           dsl.selectFrom(USERS)
              .where(USERS.EMAIL.eq(email))
              .fetchOne()
       );
   }
   ```

### IDE Integration
Add generated sources to IDE:
```xml
<!-- In pom.xml build section -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>build-helper-maven-plugin</artifactId>
    <version>3.4.0</version>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>add-source</goal>
            </goals>
            <configuration>
                <sources>
                    <source>target/generated-sources/jooq</source>
                </sources>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## 4. Testing Strategy

### Base Test Class Usage
Extend `BaseJooqDatabaseTest` for database integration tests:

```java
public class UserRepositoryTest extends BaseJooqDatabaseTest {
    
    @Inject
    UserRepository userRepository;
    
    @Test
    void testCreateUser() {
        // Database is automatically clean
        UsersRecord user = userRepository.createUser("testuser");
        assertThat(user).isNotNull();
        // Cleanup happens automatically after test
    }
}
```

### Test Execution
```bash
# Run all tests (requires Docker)
mvn test -Dtest.database=true

# Run specific test class
mvn test -Dtest=UserRepositoryTest -Dtest.database=true

# Run integration tests only
mvn test -Dtest=*IntegrationTest -Dtest.database=true
```

### Test Isolation Features
- **Sequential execution**: Tests run one at a time
- **Automatic cleanup**: All tables cleaned after each test
- **Clean state**: Each test starts with empty database
- **Schema verification**: Ensures migrations are applied

## 5. Configuration

### Maven Plugin Configuration
Located in `pom.xml`:
```xml
<plugin>
    <groupId>org.jooq</groupId>
    <artifactId>jooq-codegen-maven</artifactId>
    <configuration>
        <jdbc>
            <driver>com.mysql.cj.jdbc.Driver</driver>
            <url>jdbc:mysql://localhost:3306/userapi</url>
            <user>userapi</user>
            <password>userapi</password>
        </jdbc>
        <generator>
            <database>
                <name>org.jooq.meta.mysql.MySQLDatabase</name>
                <inputSchema>userapi</inputSchema>
                <includes>.*</includes>
                <excludes>flyway_schema_history</excludes>
            </database>
            <target>
                <packageName>com.example.generated.jooq</packageName>
                <directory>target/generated-sources/jooq</directory>
            </target>
        </generator>
    </configuration>
</plugin>
```

### Quarkus Configuration
In `src/main/resources/application.properties`:
```properties
# Database configuration
quarkus.datasource.db-kind=mysql
quarkus.datasource.username=userapi
quarkus.datasource.password=userapi
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/userapi

# Flyway configuration
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration
```

## 6. CI/CD Integration

### Automated Generation
```yaml
# .github/workflows/ci.yml
- name: Setup Database
  run: |
    docker run --name mysql-ci \
      -e MYSQL_ROOT_PASSWORD=root \
      -e MYSQL_DATABASE=userapi \
      -e MYSQL_USER=userapi \
      -e MYSQL_PASSWORD=userapi \
      -p 3306:3306 -d mysql:8.0
    
    # Wait for MySQL to be ready
    sleep 30

- name: Generate jOOQ Classes
  run: |
    mvn flyway:migrate
    mvn generate-sources

- name: Run Tests
  run: mvn test -Dtest.database=true
```

## 7. Best Practices

### Repository Pattern
- Use `@ApplicationScoped` for repository classes
- Inject `DSLContext` using `@Inject`
- Handle exceptions appropriately
- Use type-safe queries exclusively

### Query Construction
```java
// ✅ Good: Type-safe, readable
List<UsersRecord> users = dsl
    .selectFrom(USERS)
    .where(USERS.USERNAME.eq(username)
        .and(USERS.CREATED_AT.gt(cutoffDate)))
    .orderBy(USERS.CREATED_AT.desc())
    .limit(10)
    .fetch();

// ❌ Avoid: Raw SQL
Result<Record> result = dsl.fetch("SELECT * FROM users WHERE username = ?", username);
```

### Error Handling
```java
try {
    return dsl.insertInto(USERS)
        .set(USERS.USERNAME, username)
        .returning()
        .fetchOne();
} catch (DataAccessException e) {
    if (isDuplicateKeyError(e)) {
        throw new DuplicateUsernameException("Username already exists");
    }
    throw e;
}
```

## 8. Troubleshooting

### Common Issues

#### 1. Generation Fails
```bash
# Check database connection
mysql -h localhost -P 3306 -u userapi -p userapi

# Verify migrations applied
mvn flyway:info

# Check plugin configuration
mvn help:effective-pom | grep -A 20 jooq-codegen-maven
```

#### 2. Classes Not Found
```bash
# Ensure generation completed
ls -la target/generated-sources/jooq/

# Add to IDE source path
# File > Project Structure > Modules > Sources > Add target/generated-sources/jooq
```

#### 3. Test Failures
```bash
# Check Docker is running
docker ps

# Verify TestContainers dependency
mvn dependency:tree | grep testcontainers

# Run with debug logging
mvn test -Dtest.database=true -Dlogging.level.com.example=DEBUG
```

### Performance Optimization
```java
// Use batch operations for multiple inserts
dsl.batch(
    dsl.insertInto(USERS).set(USERS.USERNAME, DSL.param()),
    usernames.toArray(new String[0])
).execute();

// Use exists() for existence checks
boolean exists = dsl.fetchExists(
    dsl.selectOne().from(USERS).where(USERS.USERNAME.eq(username))
);
```

## 9. Future Enhancements

- **jOOQ Pro Features**: Consider upgrading for advanced features
- **Custom Data Types**: Add converters for complex types
- **Stored Procedures**: Integrate with database procedures
- **Multi-tenancy**: Schema-per-tenant support
- **Audit Logging**: Automatic change tracking

## Quick Reference

### Essential Commands
```bash
# Full development cycle
mvn flyway:migrate && mvn generate-sources && mvn test -Dtest.database=true

# Quick test run
mvn test -Dtest=UserRepositoryTest -Dtest.database=true

# Clean and regenerate
mvn clean && mvn flyway:clean && mvn flyway:migrate && mvn generate-sources
```

### Key Files
- `pom.xml` - Maven configuration
- `src/main/resources/db/migration/` - Flyway migrations
- `target/generated-sources/jooq/` - Generated jOOQ classes
- `src/test/java/com/example/integration/BaseJooqDatabaseTest.java` - Test base class
- `src/main/java/com/example/repository/` - Repository implementations