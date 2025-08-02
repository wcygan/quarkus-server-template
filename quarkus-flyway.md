# Quarkus Flyway MySQL Integration Guide

## Table of Contents

1. [Setup & Configuration](#setup--configuration)
2. [Migration Scripts](#migration-scripts)
3. [Environment Management](#environment-management)
4. [Dev Services Integration](#dev-services-integration)
5. [Database Support](#database-support)
6. [Migration Strategies](#migration-strategies)
7. [Testing](#testing)
8. [Production Deployment](#production-deployment)
9. [Configuration Properties](#configuration-properties)
10. [Troubleshooting](#troubleshooting)

## Setup & Configuration

### Maven Dependencies

For a complete Quarkus Flyway MySQL setup, include these dependencies in your `pom.xml`:

```xml
<dependencies>
    <!-- Core Quarkus Flyway extension -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-flyway</artifactId>
    </dependency>

    <!-- MySQL JDBC driver -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-mysql</artifactId>
    </dependency>

    <!-- MySQL-specific Flyway support -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-mysql</artifactId>
    </dependency>

    <!-- Conditional extension (automatically added) -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-flyway-mysql</artifactId>
    </dependency>

    <!-- For testing -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-junit5</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- TestContainers for integration testing -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mysql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Basic Configuration

Configure your datasource and Flyway settings in `application.properties`:

```properties
# Database configuration
quarkus.datasource.db-kind=mysql
quarkus.datasource.username=myuser
quarkus.datasource.password=mypassword
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/mydatabase

# Flyway configuration
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.locations=db/migration
quarkus.flyway.schemas=mydatabase
quarkus.flyway.table=flyway_schema_history

# Connection resilience
quarkus.flyway.connect-retries=10
quarkus.flyway.connect-retries-interval=30s
```

### Extension Installation

You can add the Flyway MySQL extension using:

```bash
# Quarkus CLI
quarkus ext add io.quarkus:quarkus-flyway-mysql

# Maven
./mvnw quarkus:add-extension -Dextensions="io.quarkus:quarkus-flyway-mysql"

# Gradle
./gradlew addExtension --extensions="io.quarkus:quarkus-flyway-mysql"
```

## Migration Scripts

### Naming Conventions

Flyway follows strict naming conventions for migration scripts:

- **Versioned migrations**: `V{version}__{description}.sql`
- **Repeatable migrations**: `R__{description}.sql`
- **Baseline migrations**: `B{version}__{description}.sql`

Examples:
```
V1__Create_users_table.sql
V1.1__Add_email_column.sql
V2__Create_orders_table.sql
V2.1__Add_order_indexes.sql
R__Update_user_permissions.sql
```

### MySQL-Specific Migration Examples

#### V1__Create_users_table.sql
```sql
-- Create users table with MySQL-specific features
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    
    -- MySQL-specific indexes
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci
  COMMENT='User accounts table';
```

#### V2__Create_orders_table.sql
```sql
-- Orders table with foreign key constraints
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(20) NOT NULL UNIQUE,
    total_amount DECIMAL(10,2) NOT NULL,
    status ENUM('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED') DEFAULT 'PENDING',
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    shipping_address JSON,
    
    -- Foreign key constraint
    CONSTRAINT fk_orders_user_id 
        FOREIGN KEY (user_id) REFERENCES users(id) 
        ON DELETE CASCADE ON UPDATE CASCADE,
    
    -- Indexes
    INDEX idx_user_id (user_id),
    INDEX idx_order_number (order_number),
    INDEX idx_status (status),
    INDEX idx_order_date (order_date)
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci;
```

#### V3__Create_stored_procedures.sql
```sql
-- MySQL stored procedure example
DELIMITER //

CREATE PROCEDURE GetUserOrderSummary(IN userId BIGINT)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT 
        u.username,
        u.email,
        COUNT(o.id) as total_orders,
        COALESCE(SUM(o.total_amount), 0) as total_spent,
        MAX(o.order_date) as last_order_date
    FROM users u
    LEFT JOIN orders o ON u.id = o.user_id
    WHERE u.id = userId
    AND u.is_active = TRUE
    GROUP BY u.id, u.username, u.email;

    COMMIT;
END //

DELIMITER ;
```

#### V4__Add_audit_triggers.sql
```sql
-- Audit table for tracking changes
CREATE TABLE user_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    action ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    old_values JSON,
    new_values JSON,
    changed_by VARCHAR(50),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_action (action),
    INDEX idx_changed_at (changed_at)
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci;

-- Trigger for UPDATE operations
DELIMITER //

CREATE TRIGGER users_audit_update
    AFTER UPDATE ON users
    FOR EACH ROW
BEGIN
    INSERT INTO user_audit (
        user_id, 
        action, 
        old_values, 
        new_values, 
        changed_by
    ) VALUES (
        NEW.id,
        'UPDATE',
        JSON_OBJECT(
            'username', OLD.username,
            'email', OLD.email,
            'first_name', OLD.first_name,
            'last_name', OLD.last_name,
            'is_active', OLD.is_active
        ),
        JSON_OBJECT(
            'username', NEW.username,
            'email', NEW.email,
            'first_name', NEW.first_name,
            'last_name', NEW.last_name,
            'is_active', NEW.is_active
        ),
        USER()
    );
END //

DELIMITER ;
```

#### R__Update_user_stats_view.sql (Repeatable Migration)
```sql
-- Repeatable migration for view updates
CREATE OR REPLACE VIEW user_statistics AS
SELECT 
    u.id,
    u.username,
    u.email,
    COUNT(o.id) as total_orders,
    COALESCE(SUM(o.total_amount), 0) as lifetime_value,
    AVG(o.total_amount) as average_order_value,
    MAX(o.order_date) as last_order_date,
    DATEDIFF(NOW(), MAX(o.order_date)) as days_since_last_order
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.is_active = TRUE
GROUP BY u.id, u.username, u.email;
```

### Best Practices for Migration Scripts

1. **Always use transactions** for complex migrations
2. **Include rollback comments** for reference
3. **Use descriptive naming** for constraints and indexes
4. **Test migrations** on sample data
5. **Keep migrations atomic** - one logical change per migration
6. **Use MySQL-specific features** when beneficial (JSON columns, spatial data types)

## Environment Management

### Profile-Specific Configuration

Configure different environments using Quarkus profiles:

#### application.properties (Default/Common)
```properties
# Common Flyway settings
quarkus.flyway.locations=db/migration
quarkus.flyway.table=flyway_schema_history
quarkus.flyway.baseline-version=1
quarkus.flyway.baseline-description=Initial version
```

#### application-dev.properties
```properties
# Development environment
quarkus.datasource.db-kind=mysql
quarkus.datasource.username=dev_user
quarkus.datasource.password=dev_password
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/app_dev

# Aggressive development settings
%dev.quarkus.flyway.migrate-at-start=true
%dev.quarkus.flyway.clean-at-start=true
%dev.quarkus.flyway.baseline-on-migrate=true

# Connection settings for local development
%dev.quarkus.flyway.connect-retries=3
%dev.quarkus.flyway.connect-retries-interval=5s
```

#### application-test.properties
```properties
# Test environment with H2 for unit tests
%test.quarkus.datasource.db-kind=h2
%test.quarkus.datasource.username=sa
%test.quarkus.datasource.password=
%test.quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1

# Test-specific Flyway settings
%test.quarkus.flyway.migrate-at-start=true
%test.quarkus.flyway.clean-at-start=true
%test.quarkus.flyway.locations=db/migration,db/test-migration
```

#### application-prod.properties
```properties
# Production environment
%prod.quarkus.datasource.db-kind=mysql
%prod.quarkus.datasource.username=${DB_USERNAME}
%prod.quarkus.datasource.password=${DB_PASSWORD}
%prod.quarkus.datasource.jdbc.url=${DB_URL}

# Conservative production settings
%prod.quarkus.flyway.migrate-at-start=false
%prod.quarkus.flyway.clean-at-start=false
%prod.quarkus.flyway.clean-disabled=true
%prod.quarkus.flyway.baseline-on-migrate=false

# Production connection resilience
%prod.quarkus.flyway.connect-retries=10
%prod.quarkus.flyway.connect-retries-interval=30s
```

### Multiple Datasources

Configure Flyway for multiple databases:

```properties
# Primary datasource
quarkus.datasource.db-kind=mysql
quarkus.datasource.username=primary_user
quarkus.datasource.password=primary_password
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/primary_db

# Secondary datasource
quarkus.datasource.analytics.db-kind=mysql
quarkus.datasource.analytics.username=analytics_user
quarkus.datasource.analytics.password=analytics_password
quarkus.datasource.analytics.jdbc.url=jdbc:mysql://localhost:3306/analytics_db

# Flyway configuration for primary datasource
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=db/migration/primary

# Flyway configuration for analytics datasource
quarkus.flyway.analytics.migrate-at-start=true
quarkus.flyway.analytics.locations=db/migration/analytics
```

## Dev Services Integration

### Automatic Database Provisioning

Quarkus Dev Services automatically provisions databases when no configuration is provided:

```properties
# Dev Services configuration (development profile)
%dev.quarkus.datasource.db-kind=mysql

# Optional: Specify MySQL version
%dev.quarkus.datasource.devservices.image-name=mysql:8.0

# Dev Services will automatically:
# - Start MySQL container
# - Create database
# - Apply Flyway migrations
# - Provide connection details
```

### Custom Dev Services Configuration

```properties
# Custom Dev Services settings
%dev.quarkus.datasource.devservices.enabled=true
%dev.quarkus.datasource.devservices.image-name=mysql:8.0
%dev.quarkus.datasource.devservices.port=3306
%dev.quarkus.datasource.devservices.username=devuser
%dev.quarkus.datasource.devservices.password=devpass
%dev.quarkus.datasource.devservices.database-name=devdb

# Custom initialization
%dev.quarkus.datasource.devservices.init-script-path=init-dev.sql
```

### Development Workflow

1. **Start Development**: Run `./mvnw quarkus:dev`
2. **Automatic Setup**: Dev Services starts MySQL container
3. **Migration Execution**: Flyway runs migrations automatically
4. **Hot Reload**: Changes to migrations trigger re-execution

## Database Support

### MySQL-Specific Features

#### Configuration for MySQL Dialect
```properties
# MySQL connection parameters
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC

# MySQL-specific Flyway settings
quarkus.flyway.sql-migration-prefix=V
quarkus.flyway.sql-migration-separator=__
quarkus.flyway.sql-migration-suffixes=.sql

# Character set and collation
quarkus.flyway.init-sql=SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci
```

#### MySQL Performance Optimization

```properties
# Connection pool settings
quarkus.datasource.jdbc.min-size=5
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.acquisition-timeout=5s

# MySQL-specific JDBC parameters
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/mydb?\
    useSSL=false&\
    allowPublicKeyRetrieval=true&\
    serverTimezone=UTC&\
    useUnicode=true&\
    characterEncoding=UTF-8&\
    useLegacyDatetimeCode=false&\
    rewriteBatchedStatements=true&\
    useCompression=true&\
    cachePrepStmts=true&\
    prepStmtCacheSize=250&\
    prepStmtCacheSqlLimit=2048
```

#### MySQL-Specific SQL Syntax Support

Flyway supports MySQL-specific syntax including:

- **Stored Procedures**: Using `DELIMITER` statements
- **Triggers**: MySQL trigger syntax
- **Comment Directives**: `/*!40101 ... */` style comments
- **JSON Columns**: Native JSON support in MySQL 5.7+
- **Generated Columns**: Virtual and stored computed columns

Example with MySQL features:
```sql
-- MySQL comment directive
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;

-- JSON column usage
CREATE TABLE products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    specifications JSON,
    -- Generated column from JSON
    category VARCHAR(50) GENERATED ALWAYS AS (JSON_UNQUOTE(JSON_EXTRACT(specifications, '$.category'))) STORED,
    
    INDEX idx_category (category)
);

-- Spatial data types
CREATE TABLE locations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255),
    coordinates POINT NOT NULL,
    SPATIAL INDEX idx_coordinates (coordinates)
);
```

## Migration Strategies

### Schema Evolution Patterns

#### 1. Additive Changes (Safe)
```sql
-- V5__Add_phone_column.sql
ALTER TABLE users 
ADD COLUMN phone VARCHAR(20) NULL 
AFTER email;

-- Add index separately for better performance
CREATE INDEX idx_users_phone ON users(phone);
```

#### 2. Destructive Changes (Requires Planning)
```sql
-- V6__Remove_deprecated_columns.sql
-- Step 1: Check that columns are not used
SELECT COUNT(*) FROM users WHERE deprecated_field IS NOT NULL;

-- Step 2: Remove the column
ALTER TABLE users DROP COLUMN deprecated_field;
```

#### 3. Data Migration Pattern
```sql
-- V7__Migrate_user_preferences.sql
-- Create new structure
CREATE TABLE user_preferences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    preference_key VARCHAR(50) NOT NULL,
    preference_value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_user_pref (user_id, preference_key),
    CONSTRAINT fk_user_pref_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Migrate existing data
INSERT INTO user_preferences (user_id, preference_key, preference_value)
SELECT id, 'theme', theme_preference 
FROM users 
WHERE theme_preference IS NOT NULL;

INSERT INTO user_preferences (user_id, preference_key, preference_value)
SELECT id, 'language', language_preference 
FROM users 
WHERE language_preference IS NOT NULL;

-- Remove old columns (in separate migration)
```

#### 4. Zero-Downtime Schema Changes
```sql
-- V8__Add_new_email_column.sql (Step 1)
-- Add new column
ALTER TABLE users ADD COLUMN new_email VARCHAR(255) NULL;

-- V9__Populate_new_email.sql (Step 2)
-- Populate new column
UPDATE users SET new_email = email WHERE new_email IS NULL;

-- V10__Switch_to_new_email.sql (Step 3)
-- Make new column non-null and add constraints
ALTER TABLE users MODIFY new_email VARCHAR(255) NOT NULL;
ALTER TABLE users ADD UNIQUE KEY uk_users_new_email (new_email);

-- V11__Remove_old_email.sql (Step 4)
-- Drop old column and rename new one
ALTER TABLE users DROP COLUMN email;
ALTER TABLE users CHANGE new_email email VARCHAR(255) NOT NULL;
```

### Rollback Strategies

While Flyway doesn't support automatic rollbacks for versioned migrations, you can implement manual rollback procedures:

#### U1__Rollback_users_table.sql (Undo migration)
```sql
-- Manual rollback for V1__Create_users_table.sql
DROP TABLE IF EXISTS users;
```

#### Rollback with Data Preservation
```sql
-- V12__Rollback_with_backup.sql
-- Create backup before changes
CREATE TABLE users_backup AS SELECT * FROM users;

-- Apply changes...
ALTER TABLE users ADD COLUMN temp_field VARCHAR(255);

-- If rollback needed:
-- DROP TABLE users;
-- RENAME TABLE users_backup TO users;
```

## Testing

### Unit Testing with H2

Configure H2 for fast unit tests:

```properties
# application-test.properties
%test.quarkus.datasource.db-kind=h2
%test.quarkus.datasource.username=sa
%test.quarkus.datasource.password=
%test.quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL

%test.quarkus.flyway.migrate-at-start=true
%test.quarkus.flyway.clean-at-start=true
```

### Integration Testing with TestContainers

#### Test Configuration
```java
@QuarkusTest
@TestProfile(MySQLTestProfile.class)
public class DatabaseMigrationTest {

    @Inject
    Flyway flyway;

    @Test
    @DisplayName("Should apply all migrations successfully")
    public void testMigrations() {
        var info = flyway.info();
        var applied = Arrays.stream(info.all())
                .filter(migration -> migration.getState() == MigrationState.SUCCESS)
                .count();
        
        assertTrue(applied > 0, "No migrations were applied");
    }

    @Test
    @DisplayName("Should create users table with correct structure")
    @TestTransaction
    public void testUsersTableStructure() {
        // Test table structure after migrations
        try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
            var meta = connection.getMetaData();
            var tables = meta.getTables(null, null, "users", null);
            assertTrue(tables.next(), "Users table should exist");
            
            var columns = meta.getColumns(null, null, "users", null);
            Set<String> columnNames = new HashSet<>();
            while (columns.next()) {
                columnNames.add(columns.getString("COLUMN_NAME"));
            }
            
            assertTrue(columnNames.contains("id"));
            assertTrue(columnNames.contains("username"));
            assertTrue(columnNames.contains("email"));
        }
    }
}
```

#### TestContainers MySQL Profile
```java
public class MySQLTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.datasource.devservices.enabled", "false",
            "quarkus.datasource.username", "test",
            "quarkus.datasource.password", "test",
            "quarkus.datasource.jdbc.url", getJdbcUrl(),
            "quarkus.flyway.migrate-at-start", "true",
            "quarkus.flyway.clean-at-start", "true"
        );
    }

    private String getJdbcUrl() {
        return "jdbc:tc:mysql:8.0:///testdb?TC_TMPFS=/testtmpfs:rw";
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(
            new TestResourceEntry(MySQLTestResource.class)
        );
    }
}

public class MySQLTestResource implements QuarkusTestResourceLifecycleManager {
    
    private MySQLContainer<?> mysql;

    @Override
    public Map<String, String> start() {
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withTmpFs(Map.of("/testtmpfs", "rw"));
        
        mysql.start();

        return Map.of(
            "quarkus.datasource.jdbc.url", mysql.getJdbcUrl(),
            "quarkus.datasource.username", mysql.getUsername(),
            "quarkus.datasource.password", mysql.getPassword()
        );
    }

    @Override
    public void stop() {
        if (mysql != null) {
            mysql.stop();
        }
    }
}
```

### Migration Validation Tests

```java
@QuarkusTest
public class MigrationValidationTest {

    @Test
    @DisplayName("Should validate migration naming conventions")
    public void testMigrationNaming() {
        var config = Flyway.configure()
            .dataSource("jdbc:h2:mem:validation;MODE=MySQL", "sa", "")
            .locations("db/migration")
            .validateMigrationNaming(true);
        
        var flyway = config.load();
        
        assertDoesNotThrow(() -> flyway.validate());
    }

    @Test
    @DisplayName("Should detect migration conflicts")
    public void testMigrationConflicts() {
        var flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:conflicts;MODE=MySQL", "sa", "")
            .locations("db/migration")
            .load();

        var info = flyway.info();
        var pending = Arrays.stream(info.all())
            .filter(m -> m.getState() == MigrationState.PENDING)
            .collect(Collectors.toList());

        // Verify no version conflicts
        var versions = pending.stream()
            .map(MigrationInfo::getVersion)
            .collect(Collectors.toSet());
        
        assertEquals(pending.size(), versions.size(), 
            "Migration version conflicts detected");
    }
}
```

### Performance Testing

```java
@QuarkusTest
public class MigrationPerformanceTest {

    @Test
    @DisplayName("Should complete migrations within acceptable time")
    @Timeout(30)
    public void testMigrationPerformance() {
        var start = System.currentTimeMillis();
        
        var flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:perf;MODE=MySQL", "sa", "")
            .locations("db/migration")
            .load();
        
        flyway.migrate();
        
        var duration = System.currentTimeMillis() - start;
        assertTrue(duration < 10000, 
            "Migrations took too long: " + duration + "ms");
    }
}
```

## Production Deployment

### Pre-Deployment Validation

#### 1. Dry Run Validation
```bash
# Use Flyway CLI for production validation
./flyway info -url=jdbc:mysql://prod-db:3306/myapp -user=flyway_user -password=****

# Validate migrations without applying
./flyway validate -url=jdbc:mysql://prod-db:3306/myapp -user=flyway_user -password=****
```

#### 2. Migration Impact Analysis
```sql
-- Check current schema state
SELECT 
    table_name,
    table_rows,
    data_length,
    index_length,
    (data_length + index_length) as total_size
FROM information_schema.tables 
WHERE table_schema = 'myapp'
ORDER BY total_size DESC;

-- Check for blocking operations
SHOW PROCESSLIST;
SHOW ENGINE INNODB STATUS;
```

### Deployment Strategies

#### 1. Application-Controlled Migration
```properties
# Production configuration
%prod.quarkus.flyway.migrate-at-start=true
%prod.quarkus.flyway.baseline-on-migrate=false
%prod.quarkus.flyway.clean-disabled=true
%prod.quarkus.flyway.out-of-order=false
%prod.quarkus.flyway.validate-on-migrate=true
```

#### 2. Separate Migration Process
```bash
#!/bin/bash
# deploy-migrations.sh

set -e

DB_URL="${DB_URL:-jdbc:mysql://localhost:3306/myapp}"
DB_USER="${DB_USER:-flyway}"
DB_PASSWORD="${DB_PASSWORD}"

# Validate before migration
echo "Validating migrations..."
./flyway validate \
    -url="$DB_URL" \
    -user="$DB_USER" \
    -password="$DB_PASSWORD"

# Create backup
echo "Creating backup..."
mysqldump -h $(echo $DB_URL | cut -d'/' -f3 | cut -d':' -f1) \
    -P $(echo $DB_URL | cut -d':' -f4 | cut -d'/' -f1) \
    -u "$DB_USER" -p"$DB_PASSWORD" \
    $(echo $DB_URL | cut -d'/' -f4) > backup_$(date +%Y%m%d_%H%M%S).sql

# Apply migrations
echo "Applying migrations..."
./flyway migrate \
    -url="$DB_URL" \
    -user="$DB_USER" \
    -password="$DB_PASSWORD"

echo "Migration completed successfully"
```

#### 3. Kubernetes Init Container
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
spec:
  template:
    spec:
      initContainers:
      - name: flyway-migration
        image: flyway/flyway:latest
        command:
        - flyway
        - migrate
        - -url=jdbc:mysql://mysql-service:3306/myapp
        - -user=$(DB_USER)
        - -password=$(DB_PASSWORD)
        env:
        - name: DB_USER
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        volumeMounts:
        - name: migrations
          mountPath: /flyway/sql
      containers:
      - name: app
        image: myapp:latest
        # ... rest of configuration
      volumes:
      - name: migrations
        configMap:
          name: migration-scripts
```

### Monitoring and Error Handling

#### 1. Migration Status Monitoring
```java
@ApplicationScoped
public class MigrationHealthCheck implements HealthCheck {

    @Inject
    Flyway flyway;

    @Override
    public HealthCheckResponse call() {
        try {
            var info = flyway.info();
            var failed = Arrays.stream(info.all())
                .anyMatch(m -> m.getState() == MigrationState.FAILED);
            
            if (failed) {
                return HealthCheckResponse.down("flyway")
                    .withData("status", "Migration failed")
                    .build();
            }
            
            var pending = Arrays.stream(info.all())
                .filter(m -> m.getState() == MigrationState.PENDING)
                .count();
            
            return HealthCheckResponse.up("flyway")
                .withData("pendingMigrations", pending)
                .withData("currentVersion", info.current().getVersion().toString())
                .build();
                
        } catch (Exception e) {
            return HealthCheckResponse.down("flyway")
                .withData("error", e.getMessage())
                .build();
        }
    }
}
```

#### 2. Custom Migration Callbacks
```java
@ApplicationScoped
public class ProductionMigrationCallback implements Callback {

    private static final Logger LOG = LoggerFactory.getLogger(ProductionMigrationCallback.class);

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE || 
               event == Event.AFTER_MIGRATE ||
               event == Event.AFTER_MIGRATE_ERROR;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false;
    }

    @Override
    public void handle(Event event, Context context) {
        switch (event) {
            case BEFORE_MIGRATE:
                LOG.info("Starting migration process...");
                // Send notification to monitoring system
                break;
            case AFTER_MIGRATE:
                LOG.info("Migration completed successfully");
                // Update deployment status
                break;
            case AFTER_MIGRATE_ERROR:
                LOG.error("Migration failed: {}", context.getException().getMessage());
                // Alert operations team
                break;
        }
    }
}
```

#### 3. Rollback Procedures
```bash
#!/bin/bash
# rollback-migration.sh

set -e

BACKUP_FILE="$1"
DB_URL="${DB_URL}"
DB_USER="${DB_USER}"
DB_PASSWORD="${DB_PASSWORD}"

if [ -z "$BACKUP_FILE" ]; then
    echo "Usage: $0 <backup_file>"
    exit 1
fi

echo "Rolling back to backup: $BACKUP_FILE"

# Stop application
kubectl scale deployment myapp --replicas=0

# Restore database
mysql -h $(echo $DB_URL | cut -d'/' -f3 | cut -d':' -f1) \
    -P $(echo $DB_URL | cut -d':' -f4 | cut -d'/' -f1) \
    -u "$DB_USER" -p"$DB_PASSWORD" \
    $(echo $DB_URL | cut -d'/' -f4) < "$BACKUP_FILE"

# Restart application
kubectl scale deployment myapp --replicas=3

echo "Rollback completed"
```

## Configuration Properties

### Complete Reference

#### Core Configuration
```properties
# Basic Flyway settings
quarkus.flyway.enabled=true
quarkus.flyway.active=true
quarkus.flyway.migrate-at-start=false
quarkus.flyway.clean-at-start=false
quarkus.flyway.baseline-at-start=false

# Migration locations and naming
quarkus.flyway.locations=db/migration
quarkus.flyway.sql-migration-prefix=V
quarkus.flyway.sql-migration-separator=__
quarkus.flyway.sql-migration-suffixes=.sql
quarkus.flyway.repeatable-sql-migration-prefix=R

# Schema and table configuration
quarkus.flyway.schemas=myschema
quarkus.flyway.default-schema=myschema
quarkus.flyway.table=flyway_schema_history
quarkus.flyway.create-schemas=true

# Baseline configuration
quarkus.flyway.baseline-version=1
quarkus.flyway.baseline-description=Initial version
quarkus.flyway.baseline-on-migrate=false

# Connection settings
quarkus.flyway.connect-retries=0
quarkus.flyway.connect-retries-interval=120s
quarkus.flyway.jdbc-url=
quarkus.flyway.username=
quarkus.flyway.password=
```

#### Advanced Configuration
```properties
# Validation and error handling
quarkus.flyway.validate-on-migrate=true
quarkus.flyway.validate-migration-naming=false
quarkus.flyway.ignore-migration-patterns=

# Migration execution control
quarkus.flyway.out-of-order=false
quarkus.flyway.group=false
quarkus.flyway.mixed=false
quarkus.flyway.clean-disabled=false

# Placeholder configuration
quarkus.flyway.placeholder-prefix=${
quarkus.flyway.placeholder-suffix=}
quarkus.flyway.placeholder-replacement=true
quarkus.flyway.placeholders.myplaceholder=value

# Initialization SQL
quarkus.flyway.init-sql=SET sql_mode='TRADITIONAL'

# Callbacks
quarkus.flyway.callbacks=com.example.MyCallback

# Named datasource configuration
quarkus.flyway.analytics.migrate-at-start=true
quarkus.flyway.analytics.locations=db/migration/analytics
```

#### Environment-Specific Overrides
```properties
# Development
%dev.quarkus.flyway.migrate-at-start=true
%dev.quarkus.flyway.clean-at-start=true
%dev.quarkus.flyway.baseline-on-migrate=true

# Testing
%test.quarkus.flyway.migrate-at-start=true
%test.quarkus.flyway.clean-at-start=true
%test.quarkus.flyway.locations=db/migration,db/test-migration

# Production
%prod.quarkus.flyway.migrate-at-start=false
%prod.quarkus.flyway.clean-at-start=false
%prod.quarkus.flyway.clean-disabled=true
%prod.quarkus.flyway.validate-on-migrate=true
%prod.quarkus.flyway.out-of-order=false
```

## Troubleshooting

### Common Issues and Solutions

#### 1. Migration Checksum Mismatch

**Problem**: Migration file was modified after being applied
```
Migration checksum mismatch for migration version 1.1
```

**Solution**:
```bash
# Option 1: Repair the schema history
./flyway repair -url=jdbc:mysql://localhost:3306/mydb -user=user -password=pass

# Option 2: Ignore checksums (not recommended for production)
quarkus.flyway.validate-on-migrate=false
```

#### 2. Failed Migration

**Problem**: Migration fails mid-execution
```
Migration V2__add_column.sql failed
```

**Investigation**:
```sql
-- Check Flyway history table
SELECT * FROM flyway_schema_history 
WHERE success = 0 
ORDER BY installed_on DESC;

-- Check MySQL error log
SHOW ENGINE INNODB STATUS;
```

**Solution**:
```bash
# 1. Fix the migration script
# 2. Manually clean up partial changes
# 3. Mark migration as resolved
./flyway repair
```

#### 3. Connection Issues

**Problem**: Cannot connect to MySQL database
```
Unable to obtain connection from database
```

**Solutions**:
```properties
# Increase connection timeout
quarkus.flyway.connect-retries=10
quarkus.flyway.connect-retries-interval=30s

# Check JDBC URL parameters
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/mydb?\
    connectTimeout=60000&\
    socketTimeout=60000&\
    autoReconnect=true
```

#### 4. Out of Order Migrations

**Problem**: Need to insert migration between existing versions
```
Migration version 1.5 detected but 2.0 already applied
```

**Solution**:
```properties
# Allow out-of-order migrations (use carefully)
quarkus.flyway.out-of-order=true
```

#### 5. Large Migration Performance

**Problem**: Migration takes too long or locks tables

**Solutions**:
```sql
-- Use smaller batch sizes
UPDATE users SET status = 'ACTIVE' WHERE status IS NULL LIMIT 1000;

-- Add progress logging
SELECT CONCAT('Processed ', ROW_COUNT(), ' rows') AS progress;

-- Use pt-online-schema-change for large tables
pt-online-schema-change --alter "ADD COLUMN new_field VARCHAR(255)" \
    --host=localhost --user=root --ask-pass D=mydb,t=large_table --execute
```

#### 6. Character Encoding Issues

**Problem**: UTF-8 characters not displaying correctly

**Solution**:
```properties
# Set proper character encoding
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/mydb?\
    useUnicode=true&\
    characterEncoding=UTF-8&\
    useSSL=false

# Initialize with proper charset
quarkus.flyway.init-sql=SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci
```

#### 7. TestContainers MySQL Issues

**Problem**: TestContainers MySQL fails to start

**Solutions**:
```java
// Use specific MySQL version
@Container
static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
    .withDatabaseName("testdb")
    .withUsername("test")
    .withPassword("test")
    .withCommand("--default-authentication-plugin=mysql_native_password");

// For Apple Silicon Macs
@Container
static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
    .withDatabaseName("testdb")
    .withCreateContainerCmdModifier(cmd -> 
        cmd.withPlatform(Platform.LINUX_AMD64));
```

#### 8. Dev Services MySQL Configuration

**Problem**: Dev Services MySQL container configuration issues

**Solution**:
```properties
# Customize Dev Services MySQL
%dev.quarkus.datasource.devservices.image-name=mysql:8.0
%dev.quarkus.datasource.devservices.container-env.MYSQL_ROOT_PASSWORD=root
%dev.quarkus.datasource.devservices.container-env.MYSQL_DATABASE=myapp
%dev.quarkus.datasource.devservices.command=--default-authentication-plugin=mysql_native_password
```

### Debugging Tools

#### 1. Flyway Info Command
```bash
# Check migration status
./flyway info -url=jdbc:mysql://localhost:3306/mydb -user=user -password=pass

# Sample output:
# Version    | Description       | Type | State   | Installed On
# 1          | Create user table | SQL  | Success | 2024-01-15 10:30:00
# 1.1        | Add email column  | SQL  | Success | 2024-01-15 10:35:00
# 2          | Create orders     | SQL  | Pending |
```

#### 2. MySQL Migration Validation
```sql
-- Check table structure
DESCRIBE users;
SHOW CREATE TABLE users;

-- Check constraints
SELECT * FROM information_schema.TABLE_CONSTRAINTS 
WHERE TABLE_SCHEMA = 'mydb';

-- Check indexes
SHOW INDEX FROM users;

-- Verify data integrity
SELECT COUNT(*) FROM users WHERE email IS NULL;
```

#### 3. Performance Analysis
```sql
-- Check slow queries during migration
SELECT * FROM mysql.slow_log 
WHERE start_time > DATE_SUB(NOW(), INTERVAL 1 HOUR);

-- Monitor table locks
SHOW OPEN TABLES WHERE In_use > 0;

-- Check InnoDB status
SHOW ENGINE INNODB STATUS;
```

### Best Practices Summary

1. **Version Control**: Always version control migration scripts
2. **Testing**: Test migrations on production-like data
3. **Backup**: Always backup before production migrations
4. **Monitoring**: Monitor migration performance and status
5. **Rollback Plan**: Have a rollback strategy ready
6. **Incremental**: Keep migrations small and incremental
7. **Documentation**: Document complex migrations
8. **Security**: Use dedicated migration user with minimal privileges

This comprehensive guide provides enterprise-ready patterns for integrating Flyway with Quarkus and MySQL, covering development through production deployment scenarios.