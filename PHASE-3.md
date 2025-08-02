# Phase 3: Production Readiness (Low Priority)

> **Phase Goal**: Transform the core application into a production-ready system with comprehensive testing, deployment configurations, and operational monitoring.

## Overview

Phase 3 focuses on production readiness by implementing comprehensive testing strategies, environment-specific configurations, containerization, and operational monitoring. This phase ensures the application can be deployed and maintained in production environments with confidence.

**Reference**: See [PLAN.md](./PLAN.md) for overall architecture and previous phases for implementation context.

## Phase Dependencies

- **Prerequisites**: Completed Phase 2 with functional REST API and database operations
- **Previous Phases**: [PHASE-1.md](./PHASE-1.md) (Foundation), [PHASE-2.md](./PHASE-2.md) (Core Implementation)
- **Estimated Duration**: 4-5 days

## Production Readiness Checklist

- [ ] **Testing**: Unit, integration, and native compilation tests
- [ ] **Configuration**: Environment-specific settings and secrets management
- [ ] **Containerization**: Optimized Docker images for JVM and native modes
- [ ] **Observability**: Metrics, logging, and distributed tracing
- [ ] **Deployment**: Kubernetes manifests and CI/CD pipeline configuration
- [ ] **Documentation**: API documentation and operational runbooks

## Task Breakdown

### Task 3.1: Comprehensive Testing Strategy

**Objective**: Implement multi-tier testing strategy covering unit tests, integration tests, and native compilation validation.

**Deliverables**:
- Unit tests with high coverage (>90%)
- Integration tests with TestContainers
- Native compilation tests
- Performance and load testing setup

**Testing Architecture**:
```
├── src/test/java/
│   ├── unit/                    # Fast, isolated unit tests
│   │   ├── UserServiceTest.java
│   │   ├── UserRepositoryTest.java
│   │   └── UserResourceTest.java
│   ├── integration/             # Full-stack integration tests
│   │   ├── UserApiIntegrationTest.java
│   │   ├── DatabaseIntegrationTest.java
│   │   └── HealthCheckIntegrationTest.java
│   └── native/                  # Native compilation tests
│       └── NativeUserResourceIT.java
```

**Unit Test Implementation**:

```java
@QuarkusTest
class UserServiceTest {
    
    @InjectMock
    UserRepository userRepository;
    
    @Inject
    UserService userService;
    
    @Test
    void shouldCreateUserSuccessfully() {
        // Given
        String username = "testuser";
        User expectedUser = new User(UUID.randomUUID(), username, Instant.now());
        CreateUserRequest request = new CreateUserRequest(username);
        
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.insert(username)).thenReturn(expectedUser);
        
        // When
        UserResponse response = userService.createUser(request);
        
        // Then
        assertThat(response.username()).isEqualTo(username);
        assertThat(response.id()).isNotNull();
        verify(userRepository).existsByUsername(username);
        verify(userRepository).insert(username);
    }
    
    @Test
    void shouldThrowExceptionWhenUsernameExists() {
        // Given
        String username = "existinguser";
        CreateUserRequest request = new CreateUserRequest(username);
        
        when(userRepository.existsByUsername(username)).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> userService.createUser(request))
            .isInstanceOf(DuplicateUsernameException.class)
            .hasMessageContaining("Username already exists");
    }
}
```

**Integration Test with TestContainers**:

```java
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class UserApiIntegrationTest {
    
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String datasourceUrl;
    
    @Test
    @Order(1)
    void shouldCreateUserSuccessfully() {
        CreateUserRequest request = new CreateUserRequest("integrationtest");
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .header("Location", containsString("/api/users/"))
            .body("username", equalTo("integrationtest"))
            .body("id", notNullValue())
            .body("createdAt", notNullValue());
    }
    
    @Test
    @Order(2)
    void shouldPreventDuplicateUsernames() {
        CreateUserRequest request = new CreateUserRequest("integrationtest");
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/users")
        .then()
            .statusCode(409)
            .body("error", equalTo("Duplicate username"));
    }
    
    @Test
    @Order(3)
    void shouldRetrieveUserById() {
        // First create a user to get the ID
        CreateUserRequest request = new CreateUserRequest("retrievetest");
        String userId = given()
            .contentType(ContentType.JSON)
            .body(request)
        .when()
            .post("/api/users")
        .then()
            .statusCode(201)
            .extract()
            .path("id");
            
        // Then retrieve by ID
        given()
        .when()
            .get("/api/users/{id}", userId)
        .then()
            .statusCode(200)
            .body("id", equalTo(userId))
            .body("username", equalTo("retrievetest"));
    }
}
```

**Native Compilation Tests**:

```java
@QuarkusIntegrationTest
class NativeUserResourceIT extends UserApiIntegrationTest {
    // Inherits all tests but runs against native executable
}
```

**Test Configuration**:
```properties
# application-test.properties
%test.quarkus.datasource.db-kind=h2
%test.quarkus.datasource.jdbc.url=jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1
%test.quarkus.flyway.migrate-at-start=true
%test.quarkus.flyway.clean-at-start=true

# TestContainers specific
%test.quarkus.test.container.image-pull-policy=missing
%test.quarkus.log.category."org.testcontainers".level=INFO
```

**Success Criteria**:
- ✅ >90% code coverage across all layers
- ✅ All integration tests pass with TestContainers
- ✅ Native compilation tests succeed
- ✅ Tests run in <30 seconds total
- ✅ Parallel test execution works correctly

### Task 3.2: Environment Configuration Management

**Objective**: Configure environment-specific settings with proper secrets management and externalized configuration.

**Deliverables**:
- Multi-environment configuration profiles
- Secrets management strategy
- Configuration validation
- Environment-specific optimization

**Configuration Structure**:
```
├── src/main/resources/
│   ├── application.properties          # Base configuration
│   ├── application-dev.properties      # Development overrides
│   ├── application-test.properties     # Test configuration
│   └── application-prod.properties     # Production settings
```

**Production Configuration**:

```properties
# application-prod.properties

# Database Configuration
%prod.quarkus.datasource.db-kind=mysql
%prod.quarkus.datasource.username=${DB_USERNAME}
%prod.quarkus.datasource.password=${DB_PASSWORD}
%prod.quarkus.datasource.jdbc.url=${DATABASE_URL}

# Connection Pool Optimization
%prod.quarkus.datasource.jdbc.max-size=20
%prod.quarkus.datasource.jdbc.min-size=5
%prod.quarkus.datasource.jdbc.acquisition-timeout=PT30S
%prod.quarkus.datasource.jdbc.leak-detection-interval=PT2M

# Flyway Production Settings
%prod.quarkus.flyway.migrate-at-start=false
%prod.quarkus.flyway.clean-at-start=false
%prod.quarkus.flyway.validate-on-migrate=true

# Security
%prod.quarkus.http.host=0.0.0.0
%prod.quarkus.http.port=8080
%prod.quarkus.http.cors=false
%prod.quarkus.http.access-log.enabled=true

# Logging
%prod.quarkus.log.level=INFO
%prod.quarkus.log.category."com.example".level=INFO
%prod.quarkus.log.console.json=true
%prod.quarkus.log.category."io.quarkus.security".level=DEBUG

# Health Checks
%prod.quarkus.smallrye-health.root-path=/q/health
%prod.quarkus.smallrye-health.liveness-path=/q/health/live
%prod.quarkus.smallrye-health.readiness-path=/q/health/ready

# Metrics and Monitoring
%prod.quarkus.micrometer.enabled=true
%prod.quarkus.micrometer.binder.http-server.enabled=true
%prod.quarkus.micrometer.binder.jvm.enabled=true
%prod.quarkus.micrometer.export.prometheus.enabled=true
```

**Configuration Validation**:

```java
@ConfigMapping(prefix = "app")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface ApplicationConfig {
    
    /**
     * Database configuration section
     */
    DatabaseConfig database();
    
    /**
     * Security configuration section
     */
    SecurityConfig security();
    
    interface DatabaseConfig {
        @WithDefault("10")
        int maxConnections();
        
        @WithDefault("PT30S")
        Duration connectionTimeout();
        
        Optional<String> encryptionKey();
    }
    
    interface SecurityConfig {
        @WithDefault("false")
        boolean corsEnabled();
        
        @WithDefault("8080")
        int port();
        
        Optional<String> jwtSecret();
    }
}
```

**Secrets Management**:

```yaml
# kubernetes/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: quarkus-app-secrets
type: Opaque
data:
  db-username: <base64-encoded-username>
  db-password: <base64-encoded-password>
  jwt-secret: <base64-encoded-jwt-secret>
```

**Success Criteria**:
- ✅ Configuration works across dev/test/prod environments
- ✅ Secrets are externalized and secure
- ✅ Configuration validation prevents startup with invalid settings
- ✅ Environment variables override properties correctly

### Task 3.3: Container Optimization

**Objective**: Create optimized Docker images for both JVM and native compilation modes with security and performance best practices.

**Deliverables**:
- Multi-stage Dockerfile for JVM and native modes
- Security-hardened container images
- Container optimization for startup time and memory usage
- Docker Compose for local development

**Multi-Stage Dockerfile**:

```dockerfile
# Multi-stage Dockerfile supporting both JVM and Native modes

### Build Stage ###
FROM registry.redhat.io/ubi8/openjdk-17:1.14 AS build-jvm
USER root
COPY --chown=185 . /tmp/src/
WORKDIR /tmp/src
RUN ./mvnw clean package -DskipTests

### Native Build Stage ###
FROM quay.io/quarkus/ubi-quarkus-mandrel:22.3-java17 AS build-native
USER root
COPY --chown=1001 . /tmp/src/
WORKDIR /tmp/src
RUN ./mvnw clean package -Pnative -DskipTests

### JVM Runtime ###
FROM registry.redhat.io/ubi8/openjdk-17-runtime:1.14 AS runtime-jvm
ENV LANGUAGE='en_US:en'

# Copy application JAR
COPY --from=build-jvm --chown=185 /tmp/src/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build-jvm --chown=185 /tmp/src/target/quarkus-app/*.jar /deployments/
COPY --from=build-jvm --chown=185 /tmp/src/target/quarkus-app/app/ /deployments/app/
COPY --from=build-jvm --chown=185 /tmp/src/target/quarkus-app/quarkus/ /deployments/quarkus/

# Security and optimization
USER 185
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/q/health/ready || exit 1

ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]

### Native Runtime ###
FROM registry.redhat.io/ubi8/ubi-minimal:8.7 AS runtime-native

WORKDIR /work/
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work

# Copy native executable
COPY --from=build-native --chown=1001:root /tmp/src/target/*-runner /work/application

# Security settings
USER 1001
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=1s --retries=3 \
  CMD curl -f http://localhost:8080/q/health/ready || exit 1

ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

**Build Scripts**:

```bash
#!/bin/bash
# build-images.sh

# Build JVM image
docker build --target runtime-jvm -t quarkus-user-api:jvm .

# Build Native image
docker build --target runtime-native -t quarkus-user-api:native .

# Multi-platform build (optional)
docker buildx build --platform linux/amd64,linux/arm64 \
  --target runtime-jvm -t quarkus-user-api:jvm-multiarch .
```

**Container Security**:

```dockerfile
# Security scanning with Trivy
FROM aquasec/trivy:latest AS security-scan
COPY --from=runtime-jvm / /scan-target
RUN trivy filesystem --exit-code 1 --severity HIGH,CRITICAL /scan-target
```

**Docker Compose for Development**:

```yaml
# docker-compose.yml
version: '3.8'

services:
  app:
    build:
      context: .
      target: runtime-jvm
    ports:
      - "8080:8080"
    environment:
      - QUARKUS_PROFILE=dev
      - QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://mysql:3306/userdb
      - QUARKUS_DATASOURCE_USERNAME=user
      - QUARKUS_DATASOURCE_PASSWORD=password
    depends_on:
      mysql:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/q/health/ready"]
      interval: 30s
      timeout: 10s
      retries: 3

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
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  mysql_data:
```

**Success Criteria**:
- ✅ JVM image <300MB, Native image <150MB
- ✅ Native image starts in <100ms
- ✅ Security scan passes with no high/critical vulnerabilities
- ✅ Health checks work correctly in containers
- ✅ Multi-platform support (AMD64/ARM64)

### Task 3.4: Observability Implementation

**Objective**: Implement comprehensive observability with metrics, structured logging, and distributed tracing.

**Deliverables**:
- Prometheus metrics integration
- Structured JSON logging
- Distributed tracing with OpenTelemetry
- Custom business metrics

**Metrics Configuration**:

```properties
# Prometheus metrics
quarkus.micrometer.enabled=true
quarkus.micrometer.binder.http-server.enabled=true
quarkus.micrometer.binder.jvm.enabled=true
quarkus.micrometer.binder.system.enabled=true
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.export.prometheus.path=/q/metrics
```

**Custom Metrics Implementation**:

```java
@ApplicationScoped
public class UserMetrics {
    
    @Inject
    MeterRegistry meterRegistry;
    
    private final Counter usersCreated;
    private final Timer userCreationTime;
    private final Gauge activeUsers;
    
    public UserMetrics(MeterRegistry meterRegistry) {
        this.usersCreated = Counter.builder("users.created.total")
            .description("Total number of users created")
            .register(meterRegistry);
            
        this.userCreationTime = Timer.builder("users.creation.duration")
            .description("Time taken to create a user")
            .register(meterRegistry);
            
        this.activeUsers = Gauge.builder("users.active.current")
            .description("Current number of active users")
            .register(meterRegistry, this, UserMetrics::getActiveUserCount);
    }
    
    public void recordUserCreated() {
        usersCreated.increment();
    }
    
    public Timer.Sample startUserCreationTimer() {
        return Timer.start(meterRegistry);
    }
    
    private double getActiveUserCount() {
        // Implementation to count active users
        return 0; // Placeholder
    }
}
```

**Structured Logging Configuration**:

```properties
# JSON logging for production
%prod.quarkus.log.console.json=true
%prod.quarkus.log.console.json.pretty-print=false
%prod.quarkus.log.console.json.additional-field."service.name".value=user-api
%prod.quarkus.log.console.json.additional-field."service.version".value=${quarkus.application.version}

# Log levels
%prod.quarkus.log.level=INFO
%prod.quarkus.log.category."com.example".level=INFO
%prod.quarkus.log.category."org.jooq".level=WARN
```

**Distributed Tracing**:

```properties
# OpenTelemetry configuration
quarkus.otel.enabled=true
quarkus.otel.service.name=user-api
quarkus.otel.traces.exporter=jaeger
quarkus.otel.exporter.jaeger.endpoint=http://jaeger:14268/api/traces
```

**Custom Tracing**:

```java
@ApplicationScoped
public class UserService {
    
    @WithSpan("user.create")
    public UserResponse createUser(@SpanAttribute("username") String username) {
        Span.current().addEvent("Starting user creation");
        
        try {
            UserResponse response = userService.createUser(request);
            Span.current().addEvent("User created successfully");
            return response;
        } catch (Exception e) {
            Span.current().recordException(e);
            throw e;
        }
    }
}
```

**Success Criteria**:
- ✅ Prometheus metrics endpoint accessible at `/q/metrics`
- ✅ Custom business metrics track user operations
- ✅ JSON logs contain trace IDs and structured data
- ✅ Distributed traces show end-to-end request flow
- ✅ Performance metrics capture 95th percentile response times

### Task 3.5: Kubernetes Deployment

**Objective**: Create production-ready Kubernetes manifests with proper resource management, scaling, and monitoring.

**Deliverables**:
- Kubernetes deployment manifests
- Service and ingress configuration
- ConfigMaps and Secrets management
- Horizontal Pod Autoscaler setup

**Deployment Manifest**:

```yaml
# kubernetes/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-api
  labels:
    app: user-api
    version: v1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: user-api
  template:
    metadata:
      labels:
        app: user-api
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/q/metrics"
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1001
        fsGroup: 1001
      containers:
      - name: user-api
        image: quarkus-user-api:native
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: QUARKUS_PROFILE
          value: "prod"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: database-url
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: db-username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: db-password
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        startupProbe:
          httpGet:
            path: /q/health/ready
            port: 8080
          initialDelaySeconds: 1
          periodSeconds: 1
          timeoutSeconds: 3
          failureThreshold: 30
```

**Service Configuration**:

```yaml
# kubernetes/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: user-api-service
  labels:
    app: user-api
spec:
  selector:
    app: user-api
  ports:
  - port: 80
    targetPort: 8080
    name: http
  type: ClusterIP
```

**Horizontal Pod Autoscaler**:

```yaml
# kubernetes/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: user-api-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: user-api
  minReplicas: 3
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
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 25
        periodSeconds: 60
```

**Ingress Configuration**:

```yaml
# kubernetes/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: user-api-ingress
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/rate-limit: "100"
    nginx.ingress.kubernetes.io/rate-limit-window: "1m"
spec:
  tls:
  - hosts:
    - api.example.com
    secretName: user-api-tls
  rules:
  - host: api.example.com
    http:
      paths:
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: user-api-service
            port:
              number: 80
```

**Success Criteria**:
- ✅ Deployment rolls out successfully
- ✅ Health checks pass in Kubernetes
- ✅ Horizontal Pod Autoscaler responds to load
- ✅ Ingress routes traffic correctly
- ✅ Secrets and ConfigMaps work properly

### Task 3.6: CI/CD Pipeline

**Objective**: Implement automated CI/CD pipeline with testing, security scanning, and deployment automation.

**Deliverables**:
- GitHub Actions workflow
- Security scanning integration
- Automated testing pipeline
- Deployment automation

**GitHub Actions Workflow**:

```yaml
# .github/workflows/ci-cd.yml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: root
          MYSQL_DATABASE: testdb
          MYSQL_USER: test
          MYSQL_PASSWORD: test
        ports:
          - 3306:3306
        options: --health-cmd="mysqladmin ping" --health-interval=10s --health-timeout=5s --health-retries=3

    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Cache Maven dependencies
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        
    - name: Run tests
      run: ./mvnw clean verify
      
    - name: Generate test report
      uses: dorny/test-reporter@v1
      if: success() || failure()
      with:
        name: Maven Tests
        path: target/surefire-reports/*.xml
        reporter: java-junit
        
    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v3

  security-scan:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Run Trivy vulnerability scanner
      uses: aquasecurity/trivy-action@master
      with:
        scan-type: 'fs'
        scan-ref: '.'
        format: 'sarif'
        output: 'trivy-results.sarif'
        
    - name: Upload Trivy scan results to GitHub Security tab
      uses: github/codeql-action/upload-sarif@v2
      with:
        sarif_file: 'trivy-results.sarif'

  build-jvm:
    needs: [test, security-scan]
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Build JVM image
      run: |
        ./mvnw clean package -DskipTests
        docker build --target runtime-jvm -t ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:jvm-${{ github.sha }} .
        
    - name: Log in to Container Registry
      uses: docker/login-action@v2
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
        
    - name: Push JVM image
      run: docker push ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:jvm-${{ github.sha }}

  build-native:
    needs: [test, security-scan]
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up GraalVM
      uses: graalvm/setup-graalvm@v1
      with:
        version: 'latest'
        java-version: '17'
        components: 'native-image'
        
    - name: Build native image
      run: |
        ./mvnw clean package -Pnative -DskipTests
        docker build --target runtime-native -t ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:native-${{ github.sha }} .
        
    - name: Log in to Container Registry
      uses: docker/login-action@v2
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
        
    - name: Push native image
      run: docker push ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:native-${{ github.sha }}

  deploy-staging:
    needs: [build-jvm, build-native]
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/develop'
    environment: staging
    steps:
    - uses: actions/checkout@v3
    
    - name: Deploy to staging
      run: |
        # Update Kubernetes manifests with new image
        sed -i 's|image: quarkus-user-api:native|image: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:native-${{ github.sha }}|' kubernetes/deployment.yaml
        
        # Deploy to Kubernetes (requires kubectl configuration)
        kubectl apply -f kubernetes/
        kubectl rollout status deployment/user-api -n staging
```

**Success Criteria**:
- ✅ All tests pass in CI pipeline
- ✅ Security scanning identifies vulnerabilities
- ✅ Both JVM and native images build successfully
- ✅ Automated deployment to staging works
- ✅ Rollback strategy is tested and documented

## Phase Completion Validation

### Production Readiness Checklist

- [ ] **Testing Coverage**: >90% code coverage with comprehensive test suite
- [ ] **Security**: Vulnerability scanning passes, secrets properly managed
- [ ] **Performance**: Load testing validates performance requirements
- [ ] **Monitoring**: Full observability stack operational
- [ ] **Deployment**: Automated CI/CD pipeline functional
- [ ] **Documentation**: Complete API docs and operational runbooks

### Performance Benchmarks

- Native image startup: <100ms
- JVM image startup: <2 seconds
- API response time: <50ms (95th percentile)
- Memory usage: <256MB under load
- CPU usage: <50% under normal load

### Final Handoff

**Production-Ready Deliverables**:
- Complete tested application with >90% coverage
- Optimized container images (JVM + Native)
- Kubernetes deployment manifests
- CI/CD pipeline with security scanning
- Comprehensive monitoring and observability
- Production configuration and secrets management
- Operational documentation and runbooks

This completes the production readiness phase, delivering a fully operational, monitored, and maintainable Quarkus application ready for production deployment.