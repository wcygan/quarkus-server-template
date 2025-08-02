# Phase 1: Foundation (High Priority)

> **Phase Goal**: Establish the foundational infrastructure for the Quarkus User Management API, including project setup, database schema, and code generation pipeline.

## Overview

Phase 1 focuses on creating the essential building blocks that all subsequent development will depend on. This phase establishes the Maven configuration, database schema through Flyway migrations, and jOOQ code generation pipeline that provides type-safe database access.

**Reference**: See [PLAN.md](./PLAN.md) for overall project structure and dependencies.

## Phase Dependencies

- **Prerequisites**: Java 17+, Maven 3.8+, Docker (for database)
- **Next Phase**: [PHASE-2.md](./PHASE-2.md) (Core Implementation)
- **Estimated Duration**: 1-2 days

## Specialized Agent Usage for Phase 1

**Critical: Use these specialized agents throughout Phase 1 for optimal efficiency and expertise:**

### quarkus-specialist Agent
**Use for all Quarkus framework setup tasks:**
- Maven project initialization with proper Quarkus BOM and extensions
- Quarkus-specific configuration in application.properties
- Development mode setup and troubleshooting
- Extension configuration (REST, Jackson, MySQL, Flyway, Health)
- Dev Services configuration for local development

### mysql-database-architect Agent  
**Use for all database architecture tasks:**
- Database schema design with proper constraints and indexing
- MySQL-specific configuration and optimization
- Flyway migration script creation and validation
- Connection pooling configuration
- Performance considerations for table design

### jooq-specialist Agent
**Use for all jOOQ integration tasks:**
- jOOQ Maven plugin configuration
- Code generation pipeline setup
- Database-to-Java type mapping configuration
- Integration with Maven build lifecycle
- Generated class validation and testing

**Agent Coordination Strategy:**
1. Start with **quarkus-specialist** for project foundation
2. Use **mysql-database-architect** for schema design
3. Deploy **jooq-specialist** for code generation setup
4. Return to **quarkus-specialist** for final integration testing

## Task Breakdown

### Task 1.1: Maven Project Initialization
**🤖 Primary Agent: quarkus-specialist**

**Objective**: Set up Quarkus project with all required extensions and proper Maven configuration.

**Deliverables**:
- `pom.xml` with Quarkus BOM and required extensions
- Maven wrapper configuration
- Basic project structure following Quarkus conventions

**Required Quarkus Extensions**:
```xml
<!-- Core REST and JSON -->
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

<!-- Health Checks -->
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-smallrye-health</artifactId>
</dependency>

<!-- jOOQ Dependencies -->
<dependency>
  <groupId>org.jooq</groupId>
  <artifactId>jooq</artifactId>
</dependency>
```

**Maven Plugin Configuration**:
- jOOQ Maven plugin with MySQL generator
- Database connection configuration for code generation
- Source generation output directory setup

**Success Criteria**:
- ✅ `mvn compile` executes without errors
- ✅ Quarkus dev mode starts successfully: `mvn quarkus:dev`
- ✅ Health check endpoint accessible at `/q/health`

### Task 1.2: Database Schema Design
**🤖 Primary Agent: mysql-database-architect**

**Objective**: Create the foundational database schema using Flyway migrations with proper constraints and indexing.

**Deliverables**:
- `V1__Create_users_table.sql` migration file
- Proper UUID support configuration for MySQL
- Database constraints and indexing strategy

**Schema Specification**:
```sql
-- Migration: V1__Create_users_table.sql
CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes for performance
    INDEX idx_username (username),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci;
```

**Design Decisions**:
1. **UUID Storage**: CHAR(36) for human-readable UUIDs vs BINARY(16) for performance
2. **Username Constraints**: 50 character limit, unique index, case-sensitive collation
3. **Indexing Strategy**: Separate indexes for common query patterns
4. **Engine Choice**: InnoDB for ACID compliance and foreign key support
5. **Character Set**: UTF8MB4 for full Unicode support including emojis

**Flyway Configuration**:
```properties
# application.properties
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration
quarkus.flyway.baseline-on-migrate=true
```

**Success Criteria**:
- ✅ Migration executes successfully against MySQL 8.0+
- ✅ Table structure matches specification exactly
- ✅ Unique constraint prevents duplicate usernames
- ✅ UUID values can be inserted and queried correctly

### Task 1.3: jOOQ Code Generation Pipeline
**🤖 Primary Agent: jooq-specialist**

**Objective**: Establish automated jOOQ code generation from the database schema to ensure type-safe database access.

**Deliverables**:
- jOOQ Maven plugin configuration
- Generated jOOQ classes in `target/generated-sources/jooq`
- Integration with Maven build lifecycle

**jOOQ Plugin Configuration**:
```xml
<plugin>
  <groupId>org.jooq</groupId>
  <artifactId>jooq-codegen-maven</artifactId>
  <executions>
    <execution>
      <id>generate-mysql</id>
      <phase>generate-sources</phase>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <jdbc>
      <driver>com.mysql.cj.jdbc.Driver</driver>
      <url>jdbc:mysql://localhost:3306/userdb</url>
      <user>user</user>
      <password>password</password>
    </jdbc>
    <generator>
      <database>
        <name>org.jooq.meta.mysql.MySQLDatabase</name>
        <includes>.*</includes>
        <excludes></excludes>
        <inputSchema>userdb</inputSchema>
      </database>
      <target>
        <packageName>com.example.generated.jooq</packageName>
        <directory>target/generated-sources/jooq</directory>
      </target>
    </generator>
  </configuration>
</plugin>
```

**Code Generation Strategy**:
1. **Development Workflow**: Manual generation during development
2. **CI/CD Integration**: Automated generation in build pipeline
3. **Version Control**: Generated code excluded from Git (in .gitignore)
4. **IDE Integration**: Configure IDE to recognize generated sources

**Generated Artifacts**:
- `Tables.java` - Table definitions and references
- `DefaultCatalog.java` - Database catalog representation
- `Users.java` - Type-safe Users table representation
- Record classes for each table

**Success Criteria**:
- ✅ `mvn generate-sources` creates jOOQ classes successfully
- ✅ Generated classes compile without errors
- ✅ DSLContext can be injected in CDI beans
- ✅ Type-safe queries can be written against Users table

### Task 1.4: Development Environment Setup
**🤖 Primary Agent: quarkus-specialist** (with mysql-database-architect for database config)

**Objective**: Configure local development environment with database services and proper tooling.

**Deliverables**:
- Docker Compose configuration for MySQL
- Development-specific Quarkus configuration
- IDE setup recommendations

**Docker Compose Configuration**:
```yaml
# docker-compose.yml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: userdb
      MYSQL_USER: user
      MYSQL_PASSWORD: password
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

volumes:
  mysql_data:
```

**Development Configuration**:
```properties
# application-dev.properties
%dev.quarkus.datasource.db-kind=mysql
%dev.quarkus.datasource.username=user
%dev.quarkus.datasource.password=password
%dev.quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/userdb

%dev.quarkus.flyway.migrate-at-start=true
%dev.quarkus.flyway.clean-at-start=false

# Development-friendly settings
%dev.quarkus.log.level=DEBUG
%dev.quarkus.log.category."com.example".level=TRACE
```

**IDE Integration**:
- IntelliJ IDEA: jOOQ plugin for code completion
- VS Code: Java extensions and Quarkus tools
- Eclipse: Quarkus tools and Maven integration

**Success Criteria**:
- ✅ `docker-compose up` starts MySQL successfully
- ✅ Quarkus connects to database in dev mode
- ✅ Flyway migrations execute automatically
- ✅ jOOQ code generation works with running database

## Phase Validation

### Acceptance Tests

1. **Project Structure Validation**:
   ```bash
   mvn clean compile
   mvn quarkus:dev
   curl http://localhost:8080/q/health
   ```

2. **Database Connectivity**:
   ```bash
   docker-compose up -d mysql
   mvn flyway:migrate
   mvn generate-sources
   ```

3. **Code Generation Verification**:
   - Verify generated jOOQ classes exist in target directory
   - Confirm DSLContext can be injected
   - Test basic query compilation

### Common Issues & Solutions

1. **MySQL Connection Issues**:
   - Verify Docker container is running
   - Check port availability (3306)
   - Validate credentials in configuration

2. **jOOQ Generation Failures**:
   - Ensure database schema exists before generation
   - Verify Maven plugin configuration
   - Check MySQL driver version compatibility

3. **Quarkus Dev Mode Issues**:
   - Clear target directory and rebuild
   - Verify Java version (17+)
   - Check for port conflicts (8080)

## Dependencies for Next Phase

**Phase 2 Prerequisites**:
- ✅ Working jOOQ code generation pipeline
- ✅ Database schema with Users table
- ✅ Quarkus project compiling and running
- ✅ Development environment configured

**Handoff Deliverables**:
- Functional Maven project with all dependencies
- Generated jOOQ classes for Users table
- Working database connection and migrations
- Documentation of any environment-specific configurations

## Risk Assessment

**High Risk**:
- jOOQ plugin configuration complexity
- MySQL version compatibility issues

**Medium Risk**:
- Docker environment setup on different platforms
- IDE-specific jOOQ integration challenges

**Mitigation Strategies**:
- Provide detailed troubleshooting guide
- Test on multiple platforms (Windows, macOS, Linux)
- Document IDE-specific setup procedures
- Create alternative H2 configuration for development