# Quarkus Docker Containerization Guide

Comprehensive guide for Docker containerization strategies for Quarkus applications, focusing on production-ready patterns and development workflow optimization.

## Table of Contents

- [Container Image Building](#container-image-building)
- [Native vs JVM Images](#native-vs-jvm-images)
- [Base Images](#base-images)
- [Build Optimization](#build-optimization)
- [Configuration Management](#configuration-management)
- [Health Checks](#health-checks)
- [Development Workflow](#development-workflow)
- [Production Deployment](#production-deployment)
- [Monitoring & Logging](#monitoring--logging)
- [Kubernetes Integration](#kubernetes-integration)

## Container Image Building

### Quarkus Container Image Extensions

Quarkus provides multiple container image extensions for building Docker images:

#### Docker Extension
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-container-image-docker</artifactId>
</dependency>
```

#### Jib Extension (Recommended for Speed)
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-container-image-jib</artifactId>
</dependency>
```

#### Buildpacks Extension
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-container-image-buildpacks</artifactId>
</dependency>
```

### Building Container Images

#### Building JVM Images
```bash
# Build JVM container image
./mvnw clean package -Dquarkus.container-image.build=true

# Build and push to registry
./mvnw clean package -Dquarkus.container-image.build=true -Dquarkus.container-image.push=true
```

#### Building Native Images
```bash
# Build native container image
./mvnw clean package -Dnative -Dquarkus.native.container-build=true -Dquarkus.container-image.build=true

# Build native with specific platform (ARM64)
./mvnw package -DskipTests -Dnative -Dquarkus.native.container-build=true -Dquarkus.jib.platforms=linux/arm64
```

### Multi-Stage Dockerfile Patterns

#### JVM Multi-Stage Dockerfile
```dockerfile
# Build stage
FROM registry.access.redhat.com/ubi8/ubi-minimal:8.9 AS builder
WORKDIR /build
COPY pom.xml .
COPY src src
USER root
RUN microdnf install -y java-17-openjdk-devel maven
RUN mvn clean package -DskipTests

# Runtime stage
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19
ENV LANGUAGE='en_US:en'

# Copy application artifacts
COPY --from=builder --chown=185 /build/target/quarkus-app/lib/ /deployments/lib/
COPY --from=builder --chown=185 /build/target/quarkus-app/*.jar /deployments/
COPY --from=builder --chown=185 /build/target/quarkus-app/app/ /deployments/app/
COPY --from=builder --chown=185 /build/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
```

#### Native Multi-Stage Dockerfile
```dockerfile
# Build stage
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17 AS builder
WORKDIR /project
COPY pom.xml .
COPY src src
RUN ./mvnw package -Dnative -DskipTests

# Runtime stage
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
COPY --from=builder /project/target/*-runner /work/application

EXPOSE 8080
USER 1001
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

## Native vs JVM Images

### Performance Comparison

| Metric | Native Image | JVM Image | Native Advantage |
|--------|-------------|-----------|------------------|
| Startup Time | ~100ms | ~2-10s | 10-100x faster |
| Memory Usage | ~83MB | ~183MB | ~55% reduction |
| Image Size | ~139MB | ~250MB+ | ~45% smaller |
| Cold Start | Instant | Several seconds | Significant improvement |
| Runtime Throughput | Lower | Higher | JVM optimizes over time |

### Trade-offs Analysis

#### Native Images - Best For:
- **Serverless Functions**: Sub-second cold starts
- **Microservices**: Fast scaling and high density
- **CI/CD Pipelines**: Quick startup for testing
- **Edge Computing**: Minimal resource footprint
- **Container Density**: More instances per host

**Native Benefits:**
- 83.15MB vs 183MB memory usage (100MB savings per instance)
- 10x less CPU usage during startup
- 20x less memory usage compared to traditional frameworks
- Theoretical 545% throughput improvement with multiple instances

#### JVM Images - Best For:
- **High-Throughput Applications**: Better runtime optimization
- **Long-Running Services**: JIT compilation benefits
- **Complex Applications**: Better debugging and profiling
- **Development**: Faster build times

**JVM Benefits:**
- Superior runtime performance for sustained workloads
- Better tooling ecosystem (profilers, debuggers)
- Faster build times (no native compilation overhead)
- More predictable performance characteristics

### Build Commands

#### Native Build with Container
```bash
# Standard native build
./mvnw clean package -Dnative -Dquarkus.native.container-build=true

# With specific builder image
./mvnw clean package -Dnative \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17

# Native with optimizations
./mvnw package -Dnative \
  -Dquarkus.native.container-build=true \
  -Dquarkus.native.additional-build-args=-R:+DumpHeapAndExit
```

## Base Images

### Recommended Base Images for 2025

#### UBI (Universal Base Images) - Production Recommended
```dockerfile
# JVM Runtime
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19

# Native Runtime
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0
```

**UBI Benefits:**
- Red Hat enterprise support
- Regular security updates
- Compliance with enterprise requirements
- Built-in non-root user (UID 1001)

#### Distroless Images - Maximum Security
```dockerfile
# Native with distroless
FROM quay.io/quarkus/quarkus-distroless-image:2.0
```

**Distroless Benefits:**
- No package managers, shells, or unnecessary utilities
- Minimal attack surface
- Built-in non-root user (`nonroot`)
- Smallest possible runtime footprint

#### Alpine Linux - Minimal Size
```dockerfile
# Custom Alpine-based (requires manual setup)
FROM alpine:3.19
RUN apk add --no-cache openjdk17-jre-headless
```

**Alpine Benefits:**
- ~3MB base image size
- Fast image pulls
- Security-focused minimal distribution
- Good for development and testing

### Security Hardening Patterns

#### Non-Root User Configuration
```dockerfile
# UBI-based security pattern
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0
WORKDIR /work
RUN chown 1001 /work && chmod "g+rwX" /work && chown 1001:root /work
COPY --chown=1001:root --chmod=0755 target/*-runner /work/application
USER 1001
EXPOSE 8080
ENTRYPOINT ["./application"]
```

#### Distroless Security Pattern
```dockerfile
FROM quay.io/quarkus/quarkus-distroless-image:2.0
COPY --chown=nonroot:nonroot target/*-runner /application
USER nonroot
EXPOSE 8080
ENTRYPOINT ["/application"]
```

## Build Optimization

### Layer Caching Strategies

#### Optimized Layer Ordering
```dockerfile
# 1. Base dependencies (changes rarely)
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19

# 2. System packages (changes rarely)
RUN microdnf update -y && microdnf clean all

# 3. Application dependencies (changes occasionally)
COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/

# 4. Application metadata (changes occasionally)
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/

# 5. Application code (changes frequently)
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
```

### Multi-Stage Build Optimization

#### Dependency Caching Pattern
```dockerfile
# Dependency cache stage
FROM maven:3.9-openjdk-17-slim AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build stage
FROM deps AS builder
COPY src src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19
COPY --from=builder /app/target/quarkus-app/ /deployments/
USER 185
```

### .dockerignore Optimization
```dockerignore
# Build artifacts
target/
*.log
.mvn/wrapper/maven-wrapper.jar

# IDE files
.idea/
.vscode/
*.iml

# Version control
.git/
.gitignore

# OS files
.DS_Store
Thumbs.db

# Documentation
README.md
docs/
```

### Jib Configuration for Optimization
```properties
# Base images
quarkus.jib.base-jvm-image=registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19
quarkus.jib.base-native-image=quay.io/quarkus/quarkus-micro-image:2.0

# JVM optimization
quarkus.jib.jvm-arguments=-Xmx256m,-Xms256m,-XX:+UseG1GC

# Custom entrypoint
quarkus.jib.jvm-entrypoint=java,-Dcustom.param=custom_value,-jar,quarkus-run.jar

# Multi-platform builds
quarkus.jib.platforms=linux/amd64,linux/arm64
```

## Configuration Management

### Environment Variables

#### Application Properties Mapping
```properties
# application.properties
app.database.url=jdbc:postgresql://localhost:5432/mydb
app.database.username=user
app.database.pool-size=20
```

#### Environment Variable Override
```bash
# Property: app.database.url
# Environment Variable: APP_DATABASE_URL
export APP_DATABASE_URL=jdbc:postgresql://prod-db:5432/proddb

# Property: quarkus.datasource.username  
# Environment Variable: QUARKUS_DATASOURCE_USERNAME
export QUARKUS_DATASOURCE_USERNAME=produser
```

### Docker Secrets Integration

#### Using Docker Secrets
```dockerfile
# Dockerfile with secrets support
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19

# Create non-root user
RUN useradd -r -u 1001 -g root appuser

# Copy application
COPY --chown=1001 target/quarkus-app/ /deployments/

# Create script to handle secrets
COPY --chown=1001 entrypoint.sh /deployments/
RUN chmod +x /deployments/entrypoint.sh

USER 1001
ENTRYPOINT ["/deployments/entrypoint.sh"]
```

#### Secret Handling Script
```bash
#!/bin/bash
# entrypoint.sh

# Load secrets from files if they exist
if [ -f /run/secrets/db_password ]; then
    export QUARKUS_DATASOURCE_PASSWORD=$(cat /run/secrets/db_password)
fi

if [ -f /run/secrets/jwt_secret ]; then
    export JWT_SECRET=$(cat /run/secrets/jwt_secret)
fi

# Start the application
exec java $JAVA_OPTS -jar /deployments/quarkus-run.jar
```

#### Docker Compose with Secrets
```yaml
version: '3.8'

services:
  app:
    image: my-quarkus-app:latest
    secrets:
      - db_password
      - jwt_secret
    environment:
      - QUARKUS_DATASOURCE_URL=jdbc:postgresql://db:5432/mydb
      - QUARKUS_DATASOURCE_USERNAME=user
    depends_on:
      - db

  db:
    image: postgres:15
    environment:
      - POSTGRES_DB=mydb
      - POSTGRES_USER=user
      - POSTGRES_PASSWORD_FILE=/run/secrets/db_password
    secrets:
      - db_password

secrets:
  db_password:
    file: ./secrets/db_password.txt
  jwt_secret:
    file: ./secrets/jwt_secret.txt
```

### Configuration Mapping
```java
@ConfigMapping(prefix = "app")
public interface AppConfig {
    DatabaseConfig database();
    SecurityConfig security();
    
    interface DatabaseConfig {
        String url();
        String username();
        @ConfigProperty(name = "pool-size", defaultValue = "10")
        int poolSize();
    }
    
    interface SecurityConfig {
        @ConfigProperty(name = "jwt-secret")
        String jwtSecret();
        Duration tokenExpiry();
    }
}
```

## Health Checks

### SmallRye Health Integration

#### Maven Dependency
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-health</artifactId>
</dependency>
```

#### Default Health Endpoints
- `/q/health` - Overall health status
- `/q/health/live` - Liveness probe (application is alive)
- `/q/health/ready` - Readiness probe (application is ready for traffic)
- `/q/health/started` - Startup probe (application has started)

### Custom Health Checks

#### Database Health Check
```java
@ApplicationScoped
@Liveness
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
                    .withData("max-connections", "20")
                    .build();
        } catch (SQLException e) {
            return HealthCheckResponse.named("database")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
```

#### External Service Health Check
```java
@ApplicationScoped
@Readiness
public class ExternalApiHealthCheck implements HealthCheck {
    
    @RestClient
    ExternalApiClient apiClient;
    
    @Override
    public HealthCheckResponse call() {
        try {
            apiClient.healthCheck();
            return HealthCheckResponse.named("external-api")
                    .up()
                    .withData("status", "connected")
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("external-api")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
```

#### Startup Health Check
```java
@ApplicationScoped
@Startup
public class ApplicationStartupCheck implements HealthCheck {
    
    private volatile boolean started = false;
    
    @EventObserver
    void onStart(@Observes StartupEvent event) {
        // Perform startup initialization
        started = true;
    }
    
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("startup")
                .status(started)
                .withData("startup-time", System.currentTimeMillis())
                .build();
    }
}
```

### Docker Health Check Integration

#### Dockerfile Health Check
```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19

COPY target/quarkus-app/ /deployments/

# Add curl for health checks
USER root
RUN microdnf install -y curl && microdnf clean all
USER 185

# Configure health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/q/health/live || exit 1

EXPOSE 8080
CMD ["java", "-jar", "/deployments/quarkus-run.jar"]
```

#### Health Check Configuration
```properties
# Management interface (recommended for production)
quarkus.management.enabled=true
quarkus.management.port=9090

# Health check configuration
quarkus.smallrye-health.root-path=/health
quarkus.smallrye-health.liveness-path=/live
quarkus.smallrye-health.readiness-path=/ready

# Enable all health check types
quarkus.smallrye-health.check.startup.enabled=true
```

## Development Workflow

### Docker Compose for Local Development

#### Complete Development Stack
```yaml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: src/main/docker/Dockerfile.jvm
    ports:
      - "8080:8080"
      - "5005:5005" # Debug port
    environment:
      - QUARKUS_DATASOURCE_URL=jdbc:postgresql://db:5432/quarkus_dev
      - QUARKUS_DATASOURCE_USERNAME=quarkus
      - QUARKUS_DATASOURCE_PASSWORD=quarkus
      - QUARKUS_LOG_LEVEL=DEBUG
      - JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    depends_on:
      db:
        condition: service_healthy
    volumes:
      - ./target:/deployments/target:ro
    networks:
      - quarkus-dev

  db:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=quarkus_dev
      - POSTGRES_USER=quarkus
      - POSTGRES_PASSWORD=quarkus
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./db/init:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U quarkus"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - quarkus-dev

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    networks:
      - quarkus-dev

volumes:
  postgres_data:
  redis_data:

networks:
  quarkus-dev:
    driver: bridge
```

### Hot Reload with Containers

#### Dev Mode Dockerfile
```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19

# Install development tools
USER root
RUN microdnf install -y findutils && microdnf clean all

# Copy source and build artifacts
WORKDIR /app
COPY . .

# Set up for live reload
ENV QUARKUS_LAUNCH_DEVMODE=true
ENV QUARKUS_LIVE_RELOAD_PASSWORD=changeit
ENV QUARKUS_PACKAGE_TYPE=mutable-jar

USER 185
EXPOSE 8080 5005

CMD ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", \
     "-jar", "target/quarkus-app/quarkus-run.jar"]
```

#### Docker Compose Watch (2025 Feature)
```yaml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile.dev
    ports:
      - "8080:8080"
    develop:
      watch:
        - action: rebuild
          path: src/
        - action: sync
          path: target/classes/
          target: /app/target/classes/
```

### Dev Services Integration

#### Automatic Database Setup
```properties
# Dev Services configuration (no URL needed - auto-provided)
%dev.quarkus.datasource.db-kind=postgresql

# Customize Dev Services container
%dev.quarkus.datasource.devservices.image-name=postgres:15-alpine
%dev.quarkus.datasource.devservices.port=5432
%dev.quarkus.datasource.devservices.username=dev
%dev.quarkus.datasource.devservices.password=dev
%dev.quarkus.datasource.devservices.database-name=quarkus_dev

# Container reuse for faster restarts
%dev.quarkus.datasource.devservices.reuse=true

# Flyway integration
%dev.quarkus.flyway.migrate-at-start=true
%dev.quarkus.flyway.clean-at-start=true
```

#### Testcontainers Configuration
```properties
# Enable container reuse
testcontainers.reuse.enable=true

# Custom testcontainers configuration
%test.quarkus.datasource.devservices.image-name=postgres:15-alpine
%test.quarkus.datasource.devservices.reuse=true
```

### Development Commands

#### Quick Development Setup
```bash
# Start dev mode with automatic reload
./mvnw quarkus:dev

# Run with Docker Compose
docker-compose up --build

# Build and test with containers
./mvnw clean package -Dquarkus.container-image.build=true

# Native development build
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

## Production Deployment

### Security Hardening

#### Secure Base Configuration
```dockerfile
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0

# Create application directory with proper permissions
WORKDIR /work
RUN chown 1001:root /work && chmod 755 /work

# Copy application with proper ownership
COPY --chown=1001:root --chmod=755 target/*-runner /work/application

# Remove unnecessary packages and clean cache
USER root
RUN microdnf remove -y shadow-utils && \
    microdnf clean all && \
    rm -rf /var/cache/yum

# Switch to non-root user
USER 1001

# Expose only necessary port
EXPOSE 8080

# Use exec form to ensure proper signal handling
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

#### Security Scan Integration
```yaml
# docker-compose.security.yml
version: '3.8'

services:
  security-scan:
    image: aquasec/trivy:latest
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - ./reports:/reports
    command: 
      - image
      - --format
      - json
      - --output
      - /reports/security-report.json
      - my-quarkus-app:latest
```

### Resource Limits and Constraints

#### Production Dockerfile with Limits
```dockerfile
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=70.0 \
               -XX:+UseG1GC \
               -XX:+UnlockExperimentalVMOptions \
               -XX:+UseCGroupMemoryLimitForHeap \
               -Djava.security.egd=file:/dev/./urandom"

COPY target/quarkus-app/ /deployments/

USER 185
EXPOSE 8080

CMD ["java", "-jar", "/deployments/quarkus-run.jar"]
```

#### Resource-Constrained Configuration
```properties
# Production memory configuration
%prod.quarkus.http.io-threads=8
%prod.quarkus.http.worker-threads=64

# Database connection pooling
%prod.quarkus.datasource.jdbc.max-size=20
%prod.quarkus.datasource.jdbc.min-size=5

# Logging optimization
%prod.quarkus.log.level=WARN
%prod.quarkus.log.category."io.quarkus".level=INFO
%prod.quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss,SSS} %-5p [%c{1.}] %s%e%n
```

### Container Registry and Deployment

#### Multi-Stage Production Build
```dockerfile
# Build stage
FROM registry.access.redhat.com/ubi8/ubi-minimal:8.9 AS builder

WORKDIR /build
COPY pom.xml .
COPY src src

USER root
RUN microdnf install -y java-17-openjdk-devel maven && \
    mvn clean package -DskipTests -B && \
    microdnf clean all

# Production stage
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19

LABEL maintainer="your-team@company.com" \
      version="1.0.0" \
      description="Quarkus application"

# Security updates
USER root
RUN microdnf update -y && microdnf clean all

# Copy application artifacts
COPY --from=builder --chown=185 /build/target/quarkus-app/lib/ /deployments/lib/
COPY --from=builder --chown=185 /build/target/quarkus-app/*.jar /deployments/
COPY --from=builder --chown=185 /build/target/quarkus-app/app/ /deployments/app/
COPY --from=builder --chown=185 /build/target/quarkus-app/quarkus/ /deployments/quarkus/

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/q/health/live || exit 1

USER 185
EXPOSE 8080

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 \
               -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

CMD ["java", "-jar", "/deployments/quarkus-run.jar"]
```

#### Registry Push Configuration
```properties
# Container registry configuration
quarkus.container-image.registry=your-registry.com
quarkus.container-image.group=your-organization
quarkus.container-image.name=quarkus-app
quarkus.container-image.tag=latest

# Automatic push on build
quarkus.container-image.push=true

# Multi-platform support
quarkus.jib.platforms=linux/amd64,linux/arm64
```

## Monitoring & Logging

### OpenTelemetry Integration

#### Maven Dependencies
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>
```

#### Observability Configuration
```properties
# OpenTelemetry configuration
quarkus.otel.exporter.otlp.endpoint=http://jaeger:4317
quarkus.otel.exporter.otlp.headers=authorization=Bearer ${OTEL_TOKEN}

# Enable all telemetry signals
quarkus.otel.metrics.enabled=true
quarkus.otel.logs.enabled=true

# Service information
quarkus.otel.service.name=quarkus-app
quarkus.otel.service.version=${project.version}

# Prometheus metrics
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.export.prometheus.path=/q/metrics
```

### Logging Strategy

#### Structured Logging Configuration
```properties
# JSON logging for production
%prod.quarkus.log.console.json=true
%prod.quarkus.log.console.json.pretty-print=false
%prod.quarkus.log.console.json.record-delimiter=\n

# Log levels
%prod.quarkus.log.level=INFO
%prod.quarkus.log.category."org.hibernate".level=WARN
%prod.quarkus.log.category."io.netty".level=WARN

# Async logging for performance
%prod.quarkus.log.console.async=true
%prod.quarkus.log.console.async.queue-length=1024
```

#### Custom Log Configuration
```java
@ApplicationScoped
public class LoggingConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingConfiguration.class);
    
    @EventObserver
    void onStart(@Observes StartupEvent event) {
        logger.info("Application started with structured logging enabled");
    }
    
    @Produces
    @ApplicationScoped
    public Logger createLogger(InjectionPoint injectionPoint) {
        return LoggerFactory.getLogger(injectionPoint.getMember().getDeclaringClass());
    }
}
```

### Docker Logging Drivers

#### Production Logging Stack
```yaml
version: '3.8'

services:
  app:
    image: my-quarkus-app:latest
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
        labels: "service=quarkus-app"
    environment:
      - QUARKUS_LOG_CONSOLE_JSON=true
    networks:
      - monitoring

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    networks:
      - monitoring

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana_data:/var/lib/grafana
    networks:
      - monitoring

  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "16686:16686"
      - "14268:14268"
    networks:
      - monitoring

volumes:
  prometheus_data:
  grafana_data:

networks:
  monitoring:
    driver: bridge
```

#### Prometheus Configuration
```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'quarkus-app'
    static_configs:
      - targets: ['app:8080']
    metrics_path: '/q/metrics'
    scrape_interval: 5s
```

### Custom Metrics

#### Application Metrics
```java
@ApplicationScoped
public class ApplicationMetrics {
    
    @Inject
    MeterRegistry registry;
    
    private final Counter userCreationCounter;
    private final Timer userLookupTimer;
    private final Gauge activeUsersGauge;
    
    public ApplicationMetrics(MeterRegistry registry) {
        this.userCreationCounter = Counter.builder("users.created")
                .description("Number of users created")
                .register(registry);
                
        this.userLookupTimer = Timer.builder("users.lookup.duration")
                .description("User lookup duration")
                .register(registry);
                
        this.activeUsersGauge = Gauge.builder("users.active")
                .description("Number of active users")
                .register(registry, this, ApplicationMetrics::getActiveUserCount);
    }
    
    public void incrementUserCreation() {
        userCreationCounter.increment();
    }
    
    public Timer.Sample startLookupTimer() {
        return Timer.start(registry);
    }
    
    private double getActiveUserCount() {
        // Implementation to get active user count
        return 0.0;
    }
}
```

## Kubernetes Integration

### Deployment Manifests

#### Quarkus Kubernetes Extension
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-kubernetes</artifactId>
</dependency>
```

#### Generated Kubernetes Resources
```properties
# Kubernetes configuration
quarkus.kubernetes.deployment-target=kubernetes

# Service configuration
quarkus.kubernetes.service-type=LoadBalancer
quarkus.kubernetes.ports."http".container-port=8080
quarkus.kubernetes.ports."management".container-port=9090

# Resource limits
quarkus.kubernetes.resources.requests.memory=256Mi
quarkus.kubernetes.resources.requests.cpu=100m
quarkus.kubernetes.resources.limits.memory=512Mi
quarkus.kubernetes.resources.limits.cpu=500m

# Environment variables
quarkus.kubernetes.env.mapping.DATABASE_URL.from-secret=database-secret
quarkus.kubernetes.env.mapping.DATABASE_URL.with-key=url

# Image configuration
quarkus.kubernetes.image-pull-policy=Always
quarkus.container-image.registry=your-registry.com
quarkus.container-image.group=your-org
quarkus.container-image.name=quarkus-app
```

#### Complete Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: quarkus-app
  labels:
    app: quarkus-app
    version: v1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: quarkus-app
  template:
    metadata:
      labels:
        app: quarkus-app
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/q/metrics"
    spec:
      containers:
      - name: quarkus-app
        image: your-registry.com/your-org/quarkus-app:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        - containerPort: 9090
          name: management
          protocol: TCP
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: database-secret
              key: url
        - name: DATABASE_USERNAME
          valueFrom:
            secretKeyRef:
              name: database-secret
              key: username
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: database-secret
              key: password
        - name: JAVA_OPTS
          value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0"
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
            scheme: HTTP
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
            scheme: HTTP
          initialDelaySeconds: 5
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        startupProbe:
          httpGet:
            path: /q/health/started
            port: 8080
            scheme: HTTP
          initialDelaySeconds: 10
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 30
        securityContext:
          runAsNonRoot: true
          runAsUser: 1001
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          capabilities:
            drop:
            - ALL
        volumeMounts:
        - name: tmp
          mountPath: /tmp
      volumes:
      - name: tmp
        emptyDir: {}
      securityContext:
        fsGroup: 1001
      serviceAccountName: quarkus-app

---
apiVersion: v1
kind: Service
metadata:
  name: quarkus-app-service
  labels:
    app: quarkus-app
spec:
  selector:
    app: quarkus-app
  ports:
  - name: http
    port: 80
    targetPort: 8080
    protocol: TCP
  - name: management
    port: 9090
    targetPort: 9090
    protocol: TCP
  type: ClusterIP

---
apiVersion: v1
kind: Secret
metadata:
  name: database-secret
type: Opaque
data:
  url: amRiYzpwb3N0Z3Jlc3FsOi8vcG9zdGdyZXM6NTQzMi9xdWFya3Vz  # base64 encoded
  username: cXVhcmt1cw==  # base64 encoded
  password: cXVhcmt1cw==  # base64 encoded
```

### Scaling and Service Discovery

#### Horizontal Pod Autoscaler
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: quarkus-app-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: quarkus-app
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 15
      - type: Pods
        value: 4
        periodSeconds: 15
      selectPolicy: Max
```

#### Service Discovery Configuration
```properties
# Kubernetes service discovery
quarkus.kubernetes-client.namespace=${KUBERNETES_NAMESPACE:default}
quarkus.kubernetes.service-account=quarkus-app

# Service mesh integration (Istio)
quarkus.kubernetes.annotations."sidecar.istio.io/inject"=true
quarkus.kubernetes.annotations."traffic.sidecar.istio.io/includeInboundPorts"=8080

# Ingress configuration
quarkus.kubernetes.ingress.expose=true
quarkus.kubernetes.ingress.host=api.yourdomain.com
quarkus.kubernetes.ingress.annotations."kubernetes.io/ingress.class"=nginx
quarkus.kubernetes.ingress.annotations."cert-manager.io/cluster-issuer"=letsencrypt-prod
```

### Build and Deploy Commands

#### Complete CI/CD Pipeline
```bash
#!/bin/bash
# build-and-deploy.sh

set -e

# Build application
echo "Building Quarkus application..."
./mvnw clean package -DskipTests

# Build and push container image
echo "Building container image..."
./mvnw package -DskipTests \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.tag=${BUILD_NUMBER:-latest}

# Generate Kubernetes manifests
echo "Generating Kubernetes manifests..."
./mvnw package -DskipTests \
  -Dquarkus.kubernetes.deploy=false

# Apply to Kubernetes
echo "Deploying to Kubernetes..."
kubectl apply -f target/kubernetes/

# Wait for rollout
echo "Waiting for deployment rollout..."
kubectl rollout status deployment/quarkus-app --timeout=300s

# Verify health
echo "Verifying application health..."
kubectl wait --for=condition=ready pod -l app=quarkus-app --timeout=300s

echo "Deployment completed successfully!"
```

This comprehensive guide provides production-ready Docker containerization strategies for Quarkus applications, covering all aspects from development workflow to Kubernetes deployment. Each section includes practical examples and best practices for 2025 cloud-native development.