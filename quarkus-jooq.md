# jOOQ Integration with Quarkus: Complete Guide

A comprehensive guide for integrating jOOQ (Java Object Oriented Querying) with Quarkus for type-safe database access and enterprise patterns.

## Table of Contents

1. [Setup & Configuration](#setup--configuration)
2. [Code Generation](#code-generation)
3. [DSLContext Configuration](#dslcontext-configuration)
4. [Query Patterns](#query-patterns)
5. [Transaction Management](#transaction-management)
6. [Performance Optimization](#performance-optimization)
7. [Testing Strategies](#testing-strategies)
8. [Error Handling](#error-handling)
9. [MySQL Specifics](#mysql-specifics)
10. [Best Practices](#best-practices)

## Setup & Configuration

### Maven Dependencies

Add the Quarkus BOM and jOOQ extension to your `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-bom</artifactId>
            <version>${quarkus.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Quarkus jOOQ Extension -->
    <dependency>
        <groupId>io.quarkiverse.jooq</groupId>
        <artifactId>quarkus-jooq</artifactId>
        <version>2.0.0</version>
    </dependency>
    
    <!-- MySQL Driver -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-jdbc-mysql</artifactId>
    </dependency>
    
    <!-- Flyway for migrations -->
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-flyway</artifactId>
    </dependency>
</dependencies>
```

### Basic Configuration

Configure your datasource in `application.properties`:

```properties
# Database Configuration
quarkus.datasource.db-kind=mysql
quarkus.datasource.username=your_username
quarkus.datasource.password=your_password
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/your_database

# jOOQ Configuration
quarkus.jooq.dialect=MYSQL

# Transaction Configuration
quarkus.datasource.jdbc.transactions=enabled
quarkus.datasource.jdbc.transaction-isolation-level=read-committed

# Development Configuration
quarkus.datasource.jdbc.acquisition-timeout=30s
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.min-size=5
```

### Multiple Datasources

For multiple datasources, configure named datasources:

```properties
# Default datasource
quarkus.datasource.db-kind=mysql
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/primary_db

# Secondary datasource
quarkus.datasource.secondary.db-kind=mysql
quarkus.datasource.secondary.jdbc.url=jdbc:mysql://localhost:3306/secondary_db
quarkus.jooq.secondary.dialect=MYSQL
```

## Code Generation

### jOOQ Maven Plugin Configuration

Add the jOOQ code generation plugin to your `pom.xml`:

```xml
<plugin>
    <groupId>org.jooq</groupId>
    <artifactId>jooq-codegen-maven</artifactId>
    <version>3.20.5</version>
    <executions>
        <execution>
            <id>jooq-codegen</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <jdbc>
            <driver>com.mysql.cj.jdbc.Driver</driver>
            <url>jdbc:mysql://localhost:3306/your_database</url>
            <user>your_username</user>
            <password>your_password</password>
        </jdbc>
        <generator>
            <database>
                <name>org.jooq.meta.mysql.MySQLDatabase</name>
                <includes>.*</includes>
                <excludes></excludes>
                <inputSchema>your_database</inputSchema>
            </database>
            <target>
                <packageName>com.example.jooq.generated</packageName>
                <directory>target/generated-sources/jooq</directory>
            </target>
            <generate>
                <pojos>true</pojos>
                <daos>true</daos>
                <validationAnnotations>true</validationAnnotations>
                <springAnnotations>false</springAnnotations>
                <javaTimeTypes>true</javaTimeTypes>
            </generate>
        </generator>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.33</version>
        </dependency>
    </dependencies>
</plugin>
```

### Build-time vs Runtime Generation

**Build-time Generation (Recommended):**
- Generates code during Maven build
- Better performance and startup time
- Type safety at compile time

```bash
# Generate jOOQ code
mvn jooq-codegen:generate

# Build with code generation
mvn clean compile
```

**Runtime Generation:**
For dynamic environments, but not recommended for production:

```java
@ApplicationScoped
public class RuntimeCodeGenerator {
    
    @PostConstruct
    void generateCode() {
        Configuration configuration = new DefaultConfiguration()
            .set(connectionProvider)
            .set(SQLDialect.MYSQL);
        
        // Runtime generation logic
    }
}
```

### Using TestContainers for Code Generation

Configure TestContainers for reliable code generation:

```xml
<plugin>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-jooq-codegen-maven-plugin</artifactId>
    <version>1.19.3</version>
    <executions>
        <execution>
            <id>generate-jooq-sources</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <phase>generate-sources</phase>
        </execution>
    </executions>
    <configuration>
        <database>
            <type>MYSQL</type>
            <containerImage>mysql:8.0</containerImage>
            <username>test</username>
            <password>test</password>
            <databaseName>testdb</databaseName>
        </database>
        <flyway>
            <locations>
                <location>filesystem:src/main/resources/db/migration</location>
            </locations>
        </flyway>
        <jooq>
            <generator>
                <database>
                    <includes>.*</includes>
                    <inputSchema>testdb</inputSchema>
                </database>
                <target>
                    <packageName>com.example.jooq.generated</packageName>
                    <directory>target/generated-sources/jooq</directory>
                </target>
            </generator>
        </jooq>
    </configuration>
</plugin>
```

## DSLContext Configuration

### Basic CDI Integration

Inject the default DSLContext:

```java
@ApplicationScoped
public class UserRepository {
    
    @Inject
    DSLContext dsl;
    
    public List<User> findAll() {
        return dsl.selectFrom(USER)
                  .fetchInto(User.class);
    }
}
```

### Custom DSLContext Configuration

Create custom configuration for advanced scenarios:

```java
@ApplicationScoped
public class CustomJooqConfiguration implements JooqCustomContext {
    
    @Override
    public void apply(Configuration.Builder configBuilder) {
        configBuilder
            .set(new CustomExecuteListener())
            .set(new CustomRecordMapperProvider())
            .settings(new Settings()
                .withRenderNameCase(RenderNameCase.LOWER)
                .withRenderKeywordCase(RenderKeywordCase.UPPER));
    }
}
```

### Multiple DSLContext Injection

For multiple datasources:

```java
@ApplicationScoped
public class MultiDataSourceService {
    
    @Inject
    DSLContext defaultDsl;
    
    @Inject
    @DataSource("secondary")
    DSLContext secondaryDsl;
    
    public void transferData() {
        // Use defaultDsl for primary operations
        // Use secondaryDsl for secondary operations
    }
}
```

### Transaction-aware DSLContext

Create transaction-scoped DSLContext:

```java
public class TransactionDSLContextProducer {
    
    @Inject
    DataSource dataSource;
    
    @Produces
    @TransactionScoped
    public DSLContext createTransactionScopedDSL() {
        return DSL.using(dataSource, SQLDialect.MYSQL);
    }
}
```

## Query Patterns

### Basic CRUD Operations

```java
@ApplicationScoped
public class UserRepository {
    
    @Inject
    DSLContext dsl;
    
    public List<User> findAll() {
        return dsl.selectFrom(USER)
                  .orderBy(USER.CREATED_AT.desc())
                  .fetchInto(User.class);
    }
    
    public Optional<User> findById(Long id) {
        return dsl.selectFrom(USER)
                  .where(USER.ID.eq(id))
                  .fetchOptionalInto(User.class);
    }
    
    public User create(User user) {
        UserRecord record = dsl.newRecord(USER, user);
        record.store();
        return record.into(User.class);
    }
    
    public User update(User user) {
        return dsl.update(USER)
                  .set(USER.NAME, user.getName())
                  .set(USER.EMAIL, user.getEmail())
                  .set(USER.UPDATED_AT, LocalDateTime.now())
                  .where(USER.ID.eq(user.getId()))
                  .returning()
                  .fetchOneInto(User.class);
    }
    
    public boolean delete(Long id) {
        return dsl.deleteFrom(USER)
                  .where(USER.ID.eq(id))
                  .execute() > 0;
    }
}
```

### Complex Joins and Aggregations

```java
@ApplicationScoped
public class OrderRepository {
    
    @Inject
    DSLContext dsl;
    
    public List<OrderSummary> getOrderSummaryByCustomer() {
        return dsl.select(
                    CUSTOMER.NAME,
                    count(ORDER.ID).as("total_orders"),
                    sum(ORDER_ITEM.QUANTITY.mul(ORDER_ITEM.PRICE)).as("total_amount")
                )
                .from(CUSTOMER)
                .join(ORDER).on(CUSTOMER.ID.eq(ORDER.CUSTOMER_ID))
                .join(ORDER_ITEM).on(ORDER.ID.eq(ORDER_ITEM.ORDER_ID))
                .where(ORDER.CREATED_AT.greaterThan(LocalDateTime.now().minusMonths(6)))
                .groupBy(CUSTOMER.ID, CUSTOMER.NAME)
                .having(count(ORDER.ID).greaterThan(5))
                .orderBy(sum(ORDER_ITEM.QUANTITY.mul(ORDER_ITEM.PRICE)).desc())
                .fetchInto(OrderSummary.class);
    }
}
```

### Nested Collections with MULTISET

```java
public class FilmRepository {
    
    @Inject
    DSLContext dsl;
    
    public List<Film> findFilmsWithActorsAndCategories() {
        return dsl.select(
                    FILM.TITLE,
                    multiset(
                        select(FILM.actor().FIRST_NAME, FILM.actor().LAST_NAME)
                        .from(FILM.actor())
                    ).as("actors").convertFrom(r -> r.map(mapping(Actor::new))),
                    multiset(
                        select(FILM.category().NAME)
                        .from(FILM.category())
                    ).as("categories").convertFrom(r -> r.map(Record1::value1))
                )
                .from(FILM)
                .orderBy(FILM.TITLE)
                .fetchInto(Film.class);
    }
}
```

### Window Functions and CTEs

```java
public class AnalyticsRepository {
    
    @Inject
    DSLContext dsl;
    
    public List<SalesReport> getSalesAnalytics() {
        var monthlySales = name("monthly_sales").as(
            select(
                extract(DatePart.YEAR, ORDER.CREATED_AT).as("year"),
                extract(DatePart.MONTH, ORDER.CREATED_AT).as("month"),
                sum(ORDER_ITEM.QUANTITY.mul(ORDER_ITEM.PRICE)).as("total_sales")
            )
            .from(ORDER)
            .join(ORDER_ITEM).on(ORDER.ID.eq(ORDER_ITEM.ORDER_ID))
            .groupBy(
                extract(DatePart.YEAR, ORDER.CREATED_AT),
                extract(DatePart.MONTH, ORDER.CREATED_AT)
            )
        );
        
        return dsl.with(monthlySales)
                  .select(
                      monthlySales.field("year"),
                      monthlySales.field("month"),
                      monthlySales.field("total_sales"),
                      lag(monthlySales.field("total_sales"), 1)
                          .over(orderBy(monthlySales.field("year"), monthlySales.field("month")))
                          .as("previous_month_sales")
                  )
                  .from(monthlySales)
                  .fetchInto(SalesReport.class);
    }
}
```

### Type-safe Dynamic Queries

```java
@ApplicationScoped
public class DynamicQueryRepository {
    
    @Inject
    DSLContext dsl;
    
    public List<User> findUsers(UserSearchCriteria criteria) {
        var query = dsl.selectFrom(USER);
        
        List<Condition> conditions = new ArrayList<>();
        
        if (criteria.getName() != null) {
            conditions.add(USER.NAME.containsIgnoreCase(criteria.getName()));
        }
        
        if (criteria.getEmail() != null) {
            conditions.add(USER.EMAIL.eq(criteria.getEmail()));
        }
        
        if (criteria.getCreatedAfter() != null) {
            conditions.add(USER.CREATED_AT.greaterThan(criteria.getCreatedAfter()));
        }
        
        if (criteria.getIsActive() != null) {
            conditions.add(USER.IS_ACTIVE.eq(criteria.getIsActive()));
        }
        
        if (!conditions.isEmpty()) {
            query = query.where(DSL.and(conditions));
        }
        
        return query.orderBy(USER.CREATED_AT.desc())
                   .limit(criteria.getLimit())
                   .offset(criteria.getOffset())
                   .fetchInto(User.class);
    }
}
```

## Transaction Management

### Declarative Transactions with @Transactional

```java
@ApplicationScoped
public class UserService {
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    EmailService emailService;
    
    @Transactional
    public User createUser(CreateUserRequest request) {
        // Validate unique email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already exists");
        }
        
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setCreatedAt(LocalDateTime.now());
        
        User savedUser = userRepository.create(user);
        
        // Send welcome email (part of transaction)
        emailService.sendWelcomeEmail(savedUser);
        
        return savedUser;
    }
    
    @Transactional(rollbackOn = Exception.class)
    public void updateUserProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        
        user.setName(request.getName());
        user.setUpdatedAt(LocalDateTime.now());
        
        userRepository.update(user);
        
        // Additional operations that should be part of the transaction
        auditService.logProfileUpdate(userId, request);
    }
}
```

### Programmatic Transaction Management

```java
@ApplicationScoped
public class TransferService {
    
    @Inject
    DSLContext dsl;
    
    @Inject
    TransactionManager transactionManager;
    
    public void transferFunds(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        transactionManager.begin();
        try {
            // Debit from account
            int debitResult = dsl.update(ACCOUNT)
                .set(ACCOUNT.BALANCE, ACCOUNT.BALANCE.minus(amount))
                .where(ACCOUNT.ID.eq(fromAccountId))
                .and(ACCOUNT.BALANCE.greaterOrEqual(amount))
                .execute();
            
            if (debitResult == 0) {
                throw new InsufficientFundsException("Insufficient balance");
            }
            
            // Credit to account
            dsl.update(ACCOUNT)
               .set(ACCOUNT.BALANCE, ACCOUNT.BALANCE.plus(amount))
               .where(ACCOUNT.ID.eq(toAccountId))
               .execute();
            
            // Record transaction
            dsl.insertInto(TRANSACTION)
               .set(TRANSACTION.FROM_ACCOUNT_ID, fromAccountId)
               .set(TRANSACTION.TO_ACCOUNT_ID, toAccountId)
               .set(TRANSACTION.AMOUNT, amount)
               .set(TRANSACTION.CREATED_AT, LocalDateTime.now())
               .execute();
            
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
            throw new TransferException("Transfer failed", e);
        }
    }
}
```

### Transaction Scoped Beans

```java
@TransactionScoped
public class TransactionAuditService {
    
    private List<AuditEvent> events = new ArrayList<>();
    
    public void logEvent(String event, Object data) {
        events.add(new AuditEvent(event, data, LocalDateTime.now()));
    }
    
    @PreDestroy
    void onTransactionEnd() {
        // Flush audit events when transaction completes
        events.forEach(this::persistAuditEvent);
    }
    
    private void persistAuditEvent(AuditEvent event) {
        // Persist audit event
    }
}
```

## Performance Optimization

### Lazy Fetching and Streaming

```java
@ApplicationScoped
public class LargeDataRepository {
    
    @Inject
    DSLContext dsl;
    
    public Stream<User> streamAllUsers() {
        return dsl.selectFrom(USER)
                  .orderBy(USER.ID)
                  .fetchLazy()
                  .stream();
    }
    
    public void processLargeDataset() {
        try (Stream<User> userStream = streamAllUsers()) {
            userStream
                .filter(user -> user.getLastLoginAt() != null)
                .filter(user -> user.getLastLoginAt().isBefore(LocalDateTime.now().minusMonths(6)))
                .forEach(this::archiveUser);
        }
    }
}
```

### Batch Operations

```java
@ApplicationScoped
public class BatchRepository {
    
    @Inject
    DSLContext dsl;
    
    public void batchInsertUsers(List<User> users) {
        BatchBindStep batch = dsl.batch(
            dsl.insertInto(USER)
               .columns(USER.NAME, USER.EMAIL, USER.CREATED_AT)
               .values((String) null, null, null)
        );
        
        for (User user : users) {
            batch.bind(user.getName(), user.getEmail(), user.getCreatedAt());
        }
        
        batch.execute();
    }
    
    public void batchUpdateUsers(List<User> users) {
        List<Query> queries = users.stream()
            .map(user -> dsl.update(USER)
                           .set(USER.NAME, user.getName())
                           .set(USER.EMAIL, user.getEmail())
                           .set(USER.UPDATED_AT, LocalDateTime.now())
                           .where(USER.ID.eq(user.getId())))
            .collect(Collectors.toList());
        
        dsl.batch(queries).execute();
    }
}
```

### Connection Pooling Optimization

```properties
# Optimal connection pool settings
quarkus.datasource.jdbc.initial-size=5
quarkus.datasource.jdbc.min-size=5
quarkus.datasource.jdbc.max-size=20
quarkus.datasource.jdbc.acquisition-timeout=30s
quarkus.datasource.jdbc.leak-detection-interval=10m
quarkus.datasource.jdbc.idle-removal-interval=5m
quarkus.datasource.jdbc.max-lifetime=30m

# Transaction settings
quarkus.datasource.jdbc.transaction-requirement=strict
quarkus.datasource.jdbc.transaction-isolation-level=read-committed
```

### Query Performance Monitoring

```java
@ApplicationScoped
public class PerformanceAwareExecuteListener implements ExecuteListener {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceAwareExecuteListener.class);
    
    @Override
    public void start(ExecuteContext ctx) {
        ctx.data("start-time", System.currentTimeMillis());
    }
    
    @Override
    public void end(ExecuteContext ctx) {
        long startTime = (Long) ctx.data("start-time");
        long duration = System.currentTimeMillis() - startTime;
        
        if (duration > 1000) { // Log slow queries
            logger.warn("Slow query detected: {}ms - {}", duration, ctx.sql());
        }
        
        // Metrics collection
        Metrics.timer("jooq.query.duration")
               .record(Duration.ofMillis(duration));
    }
}
```

### Native Compilation Optimizations

```java
@RegisterForReflection(targets = {
    UserRecord.class,
    OrderRecord.class,
    // Add all generated record classes
})
public class JooqReflectionConfig {
}
```

## Testing Strategies

### Unit Testing with Mocking

```java
@QuarkusTest
class UserServiceTest {
    
    @InjectMock
    DSLContext dsl;
    
    @Inject
    UserService userService;
    
    @Test
    void shouldCreateUserSuccessfully() {
        // Given
        CreateUserRequest request = new CreateUserRequest("John Doe", "john@example.com");
        UserRecord mockRecord = mock(UserRecord.class);
        
        when(dsl.selectFrom(USER)).thenReturn(mock(SelectJoinStep.class));
        when(dsl.newRecord(eq(USER), any(User.class))).thenReturn(mockRecord);
        when(mockRecord.into(User.class)).thenReturn(createExpectedUser());
        
        // When
        User result = userService.createUser(request);
        
        // Then
        assertThat(result.getName()).isEqualTo("John Doe");
        assertThat(result.getEmail()).isEqualTo("john@example.com");
        verify(mockRecord).store();
    }
}
```

### Integration Testing with TestContainers

```java
@QuarkusTest
@TestProfile(MySQLTestProfile.class)
class UserRepositoryIntegrationTest {
    
    @Inject
    UserRepository userRepository;
    
    @Test
    @Transactional
    void shouldFindUsersByNamePattern() {
        // Given
        createTestUser("John Doe", "john@example.com");
        createTestUser("Jane Doe", "jane@example.com");
        createTestUser("Bob Smith", "bob@example.com");
        
        // When
        List<User> users = userRepository.findByNamePattern("Doe");
        
        // Then
        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getName)
                         .containsExactly("John Doe", "Jane Doe");
    }
}

public class MySQLTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "quarkus.datasource.devservices.enabled", "true",
            "quarkus.datasource.devservices.image-name", "mysql:8.0",
            "quarkus.datasource.devservices.username", "test",
            "quarkus.datasource.devservices.password", "test",
            "quarkus.flyway.migrate-at-start", "true"
        );
    }
}
```

### Repository Testing with Test Data Builder

```java
@QuarkusTest
class OrderRepositoryTest {
    
    @Inject
    OrderRepository orderRepository;
    
    @Inject
    TestDataBuilder testDataBuilder;
    
    @Test
    @Transactional
    void shouldCalculateOrderTotalsCorrectly() {
        // Given
        Customer customer = testDataBuilder.createCustomer("John Doe");
        Product product1 = testDataBuilder.createProduct("Product 1", new BigDecimal("10.00"));
        Product product2 = testDataBuilder.createProduct("Product 2", new BigDecimal("20.00"));
        
        Order order = testDataBuilder.createOrder(customer)
            .withItem(product1, 2) // 2 * 10.00 = 20.00
            .withItem(product2, 1) // 1 * 20.00 = 20.00
            .build();              // Total: 40.00
        
        // When
        List<OrderSummary> summaries = orderRepository.getOrderSummaryByCustomer();
        
        // Then
        assertThat(summaries).hasSize(1);
        OrderSummary summary = summaries.get(0);
        assertThat(summary.getCustomerName()).isEqualTo("John Doe");
        assertThat(summary.getTotalOrders()).isEqualTo(1);
        assertThat(summary.getTotalAmount()).isEqualTo(new BigDecimal("40.00"));
    }
}
```

### Test Data Management

```java
@ApplicationScoped
public class TestDataBuilder {
    
    @Inject
    DSLContext dsl;
    
    public Customer createCustomer(String name) {
        return dsl.insertInto(CUSTOMER)
                  .set(CUSTOMER.NAME, name)
                  .set(CUSTOMER.EMAIL, name.toLowerCase().replace(" ", ".") + "@example.com")
                  .set(CUSTOMER.CREATED_AT, LocalDateTime.now())
                  .returning()
                  .fetchOneInto(Customer.class);
    }
    
    public Product createProduct(String name, BigDecimal price) {
        return dsl.insertInto(PRODUCT)
                  .set(PRODUCT.NAME, name)
                  .set(PRODUCT.PRICE, price)
                  .set(PRODUCT.CREATED_AT, LocalDateTime.now())
                  .returning()
                  .fetchOneInto(Product.class);
    }
    
    public OrderBuilder createOrder(Customer customer) {
        return new OrderBuilder(dsl, customer);
    }
    
    public static class OrderBuilder {
        private final DSLContext dsl;
        private final Customer customer;
        private final List<OrderItemData> items = new ArrayList<>();
        
        public OrderBuilder(DSLContext dsl, Customer customer) {
            this.dsl = dsl;
            this.customer = customer;
        }
        
        public OrderBuilder withItem(Product product, int quantity) {
            items.add(new OrderItemData(product, quantity));
            return this;
        }
        
        public Order build() {
            Order order = dsl.insertInto(ORDER)
                            .set(ORDER.CUSTOMER_ID, customer.getId())
                            .set(ORDER.CREATED_AT, LocalDateTime.now())
                            .returning()
                            .fetchOneInto(Order.class);
            
            for (OrderItemData item : items) {
                dsl.insertInto(ORDER_ITEM)
                   .set(ORDER_ITEM.ORDER_ID, order.getId())
                   .set(ORDER_ITEM.PRODUCT_ID, item.product().getId())
                   .set(ORDER_ITEM.QUANTITY, item.quantity())
                   .set(ORDER_ITEM.PRICE, item.product().getPrice())
                   .execute();
            }
            
            return order;
        }
    }
    
    private record OrderItemData(Product product, int quantity) {}
}
```

## Error Handling

### Custom Exception Mapping

```java
@ApplicationScoped
public class DatabaseExceptionMapper implements ExecuteListener {
    
    @Override
    public void exception(ExecuteContext ctx) {
        SQLException sqlException = ctx.sqlException();
        String sqlState = sqlException.getSQLState();
        
        switch (sqlState) {
            case "23000": // Integrity constraint violation
                if (sqlException.getMessage().contains("Duplicate entry")) {
                    ctx.exception(new DuplicateResourceException(
                        "Resource already exists", sqlException));
                } else {
                    ctx.exception(new ConstraintViolationException(
                        "Database constraint violated", sqlException));
                }
                break;
                
            case "23503": // Foreign key constraint violation
                ctx.exception(new ForeignKeyViolationException(
                    "Referenced resource not found", sqlException));
                break;
                
            case "08S01": // Communication link failure
                ctx.exception(new DatabaseConnectionException(
                    "Database connection lost", sqlException));
                break;
                
            default:
                // Let jOOQ handle other exceptions
                break;
        }
    }
}
```

### Business Exception Handling

```java
@ApplicationScoped
public class UserService {
    
    @Inject
    UserRepository userRepository;
    
    @Transactional
    public User createUser(CreateUserRequest request) {
        try {
            validateUserRequest(request);
            return userRepository.create(mapToUser(request));
        } catch (DuplicateResourceException e) {
            throw new BusinessException("User with this email already exists", 
                                      ErrorCode.USER_ALREADY_EXISTS, e);
        } catch (ConstraintViolationException e) {
            throw new BusinessException("Invalid user data", 
                                      ErrorCode.INVALID_DATA, e);
        } catch (DatabaseConnectionException e) {
            throw new ServiceUnavailableException("Database temporarily unavailable", e);
        }
    }
    
    private void validateUserRequest(CreateUserRequest request) {
        if (request.getEmail() == null || !isValidEmail(request.getEmail())) {
            throw new ValidationException("Invalid email format");
        }
        
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("Name is required");
        }
    }
}
```

### Global Exception Handler

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(BusinessException.class)
    public Response handleBusinessException(BusinessException e) {
        logger.warn("Business exception: {}", e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                      .entity(new ErrorResponse(e.getErrorCode(), e.getMessage()))
                      .build();
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public Response handleConstraintViolation(ConstraintViolationException e) {
        logger.warn("Constraint violation: {}", e.getMessage());
        return Response.status(Response.Status.CONFLICT)
                      .entity(new ErrorResponse("CONSTRAINT_VIOLATION", 
                                               "Data integrity violation"))
                      .build();
    }
    
    @ExceptionHandler(DatabaseConnectionException.class)
    public Response handleDatabaseConnection(DatabaseConnectionException e) {
        logger.error("Database connection error", e);
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                      .entity(new ErrorResponse("DATABASE_UNAVAILABLE", 
                                               "Service temporarily unavailable"))
                      .build();
    }
    
    @ExceptionHandler(DataAccessException.class)
    public Response handleDataAccess(DataAccessException e) {
        logger.error("Data access error", e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                      .entity(new ErrorResponse("INTERNAL_ERROR", 
                                               "An internal error occurred"))
                      .build();
    }
}
```

## MySQL Specifics

### MySQL Dialect Configuration

```properties
# MySQL-specific jOOQ settings
quarkus.jooq.dialect=MYSQL
quarkus.jooq.sql-dialect=MYSQL_8_0

# MySQL connection settings
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
quarkus.datasource.jdbc.additional-jdbc-properties.useUnicode=true
quarkus.datasource.jdbc.additional-jdbc-properties.characterEncoding=utf8mb4
```

### UUID Handling in MySQL

Since MySQL doesn't have native UUID support, use these patterns:

```java
// Custom UUID converter for BINARY(16)
public class UUIDBinaryConverter implements Converter<byte[], UUID> {
    
    @Override
    public UUID from(byte[] databaseObject) {
        if (databaseObject == null || databaseObject.length != 16) {
            return null;
        }
        
        ByteBuffer bb = ByteBuffer.wrap(databaseObject);
        long mostSigBits = bb.getLong();
        long leastSigBits = bb.getLong();
        return new UUID(mostSigBits, leastSigBits);
    }
    
    @Override
    public byte[] to(UUID userObject) {
        if (userObject == null) {
            return null;
        }
        
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(userObject.getMostSignificantBits());
        bb.putLong(userObject.getLeastSignificantBits());
        return bb.array();
    }
    
    @Override
    public Class<byte[]> fromType() {
        return byte[].class;
    }
    
    @Override
    public Class<UUID> toType() {
        return UUID.class;
    }
}

// Usage in repository
@ApplicationScoped
public class UUIDRepository {
    
    @Inject
    DSLContext dsl;
    
    public User createUserWithUUID() {
        UUID id = UUID.randomUUID();
        
        return dsl.insertInto(USER)
                  .set(USER.ID, id) // Automatically converted to BINARY(16)
                  .set(USER.NAME, "John Doe")
                  .set(USER.CREATED_AT, LocalDateTime.now())
                  .returning()
                  .fetchOneInto(User.class);
    }
}
```

### Auto-increment and Generated Keys

```java
@ApplicationScoped
public class AutoIncrementRepository {
    
    @Inject
    DSLContext dsl;
    
    public User createUser(String name, String email) {
        // MySQL auto-increment handling
        UserRecord record = dsl.insertInto(USER)
                              .set(USER.NAME, name)
                              .set(USER.EMAIL, email)
                              .set(USER.CREATED_AT, LocalDateTime.now())
                              .returning(USER.ID) // Get generated ID
                              .fetchOne();
        
        return record.into(User.class);
    }
    
    public List<User> batchCreateUsers(List<CreateUserRequest> requests) {
        List<UserRecord> records = new ArrayList<>();
        
        for (CreateUserRequest request : requests) {
            UserRecord record = dsl.insertInto(USER)
                                  .set(USER.NAME, request.getName())
                                  .set(USER.EMAIL, request.getEmail())
                                  .set(USER.CREATED_AT, LocalDateTime.now())
                                  .returning()
                                  .fetchOne();
            records.add(record);
        }
        
        return records.stream()
                     .map(record -> record.into(User.class))
                     .collect(Collectors.toList());
    }
}
```

### MySQL-specific Functions

```java
@ApplicationScoped
public class MySQLFunctionRepository {
    
    @Inject
    DSLContext dsl;
    
    public List<User> findUsersWithFullTextSearch(String searchTerm) {
        return dsl.selectFrom(USER)
                  .where(DSL.condition("MATCH({0}) AGAINST ({1} IN BOOLEAN MODE)", 
                                     USER.NAME, searchTerm))
                  .fetchInto(User.class);
    }
    
    public List<UserDistance> findNearbyUsers(double latitude, double longitude, double radiusKm) {
        Field<Double> distance = DSL.field(
            "ST_Distance_Sphere(POINT({0}, {1}), POINT(longitude, latitude)) / 1000",
            Double.class, longitude, latitude
        ).as("distance");
        
        return dsl.select(USER.asterisk(), distance)
                  .from(USER)
                  .where(DSL.condition("ST_Distance_Sphere(POINT({0}, {1}), POINT(longitude, latitude)) / 1000 <= {2}",
                                     longitude, latitude, radiusKm))
                  .orderBy(distance)
                  .fetchInto(UserDistance.class);
    }
    
    public void optimizeTable(String tableName) {
        dsl.execute("OPTIMIZE TABLE " + tableName);
    }
}
```

## Best Practices

### Repository Pattern Implementation

```java
public interface UserRepository {
    List<User> findAll();
    Optional<User> findById(Long id);
    List<User> findByEmail(String email);
    User save(User user);
    boolean delete(Long id);
    Page<User> findAllPaginated(Pageable pageable);
}

@ApplicationScoped
public class JooqUserRepository implements UserRepository {
    
    @Inject
    DSLContext dsl;
    
    @Override
    public List<User> findAll() {
        return dsl.selectFrom(USER)
                  .orderBy(USER.CREATED_AT.desc())
                  .fetchInto(User.class);
    }
    
    @Override
    public Optional<User> findById(Long id) {
        return dsl.selectFrom(USER)
                  .where(USER.ID.eq(id))
                  .fetchOptionalInto(User.class);
    }
    
    @Override
    public List<User> findByEmail(String email) {
        return dsl.selectFrom(USER)
                  .where(USER.EMAIL.eq(email))
                  .fetchInto(User.class);
    }
    
    @Override
    public User save(User user) {
        if (user.getId() == null) {
            return create(user);
        } else {
            return update(user);
        }
    }
    
    private User create(User user) {
        UserRecord record = dsl.newRecord(USER, user);
        record.setCreatedAt(LocalDateTime.now());
        record.store();
        return record.into(User.class);
    }
    
    private User update(User user) {
        return dsl.update(USER)
                  .set(USER.NAME, user.getName())
                  .set(USER.EMAIL, user.getEmail())
                  .set(USER.UPDATED_AT, LocalDateTime.now())
                  .where(USER.ID.eq(user.getId()))
                  .returning()
                  .fetchOneInto(User.class);
    }
    
    @Override
    public boolean delete(Long id) {
        return dsl.deleteFrom(USER)
                  .where(USER.ID.eq(id))
                  .execute() > 0;
    }
    
    @Override
    public Page<User> findAllPaginated(Pageable pageable) {
        List<User> users = dsl.selectFrom(USER)
                             .orderBy(USER.CREATED_AT.desc())
                             .limit(pageable.getSize())
                             .offset(pageable.getOffset())
                             .fetchInto(User.class);
        
        int total = dsl.fetchCount(USER);
        
        return new Page<>(users, pageable, total);
    }
}
```

### Service Layer Integration

```java
@ApplicationScoped
public class UserService {
    
    @Inject
    UserRepository userRepository;
    
    @Inject
    EmailService emailService;
    
    @Inject
    CacheService cacheService;
    
    public List<User> getAllUsers() {
        return cacheService.computeIfAbsent("all-users", 
            () -> userRepository.findAll(), Duration.ofMinutes(5));
    }
    
    public User getUserById(Long id) {
        return userRepository.findById(id)
                           .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }
    
    @Transactional
    public User createUser(CreateUserRequest request) {
        validateCreateRequest(request);
        
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        
        User savedUser = userRepository.save(user);
        
        // Async email sending
        emailService.sendWelcomeEmailAsync(savedUser);
        
        // Clear cache
        cacheService.evict("all-users");
        
        return savedUser;
    }
    
    @Transactional
    public User updateUser(Long id, UpdateUserRequest request) {
        User existingUser = getUserById(id);
        
        existingUser.setName(request.getName());
        existingUser.setEmail(request.getEmail());
        
        User updatedUser = userRepository.save(existingUser);
        
        // Clear cache
        cacheService.evict("all-users");
        cacheService.evict("user-" + id);
        
        return updatedUser;
    }
    
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.delete(id)) {
            throw new UserNotFoundException("User not found: " + id);
        }
        
        // Clear cache
        cacheService.evict("all-users");
        cacheService.evict("user-" + id);
    }
    
    private void validateCreateRequest(CreateUserRequest request) {
        if (userRepository.findByEmail(request.getEmail()).size() > 0) {
            throw new BusinessException("Email already exists");
        }
    }
}
```

### Code Organization

```
src/main/java/
├── com/example/
│   ├── config/
│   │   ├── JooqConfiguration.java
│   │   └── DatabaseConfiguration.java
│   ├── domain/
│   │   ├── user/
│   │   │   ├── User.java
│   │   │   ├── UserRepository.java
│   │   │   ├── UserService.java
│   │   │   └── UserController.java
│   │   └── order/
│   │       ├── Order.java
│   │       ├── OrderRepository.java
│   │       ├── OrderService.java
│   │       └── OrderController.java
│   ├── infrastructure/
│   │   ├── database/
│   │   │   ├── JooqUserRepository.java
│   │   │   ├── JooqOrderRepository.java
│   │   │   └── DatabaseExceptionMapper.java
│   │   └── web/
│   │       └── GlobalExceptionHandler.java
│   ├── generated/ (jOOQ generated code)
│   │   ├── tables/
│   │   ├── records/
│   │   └── pojos/
│   └── shared/
│       ├── exception/
│       ├── dto/
│       └── util/
```

### Configuration Management

```java
@ConfigMapping(prefix = "app.database")
public interface DatabaseConfig {
    
    @WithDefault("30s")
    Duration queryTimeout();
    
    @WithDefault("100")
    int batchSize();
    
    @WithDefault("true")
    boolean enableQueryLogging();
    
    @WithDefault("1000")
    long slowQueryThresholdMs();
    
    Optional<String> readOnlyDataSource();
}

@ApplicationScoped
public class OptimizedJooqConfiguration implements JooqCustomContext {
    
    @Inject
    DatabaseConfig config;
    
    @Override
    public void apply(Configuration.Builder configBuilder) {
        Settings settings = new Settings()
            .withRenderNameCase(RenderNameCase.LOWER)
            .withRenderKeywordCase(RenderKeywordCase.UPPER)
            .withRenderFormatted(true)
            .withExecuteLogging(config.enableQueryLogging())
            .withQueryTimeout((int) config.queryTimeout().toSeconds());
        
        configBuilder
            .set(settings)
            .set(new PerformanceExecuteListener(config))
            .set(new DatabaseExceptionMapper());
    }
}
```

### Production-Ready Configuration

```properties
# Production database settings
quarkus.datasource.jdbc.min-size=10
quarkus.datasource.jdbc.max-size=50
quarkus.datasource.jdbc.acquisition-timeout=30s
quarkus.datasource.jdbc.leak-detection-interval=10m
quarkus.datasource.jdbc.transaction-requirement=strict

# jOOQ settings
quarkus.jooq.dialect=MYSQL_8_0
app.database.query-timeout=30s
app.database.batch-size=500
app.database.enable-query-logging=false
app.database.slow-query-threshold-ms=1000

# Logging
quarkus.log.category."org.jooq".level=WARN
quarkus.log.category."com.example.infrastructure.database".level=INFO

# Health checks
quarkus.smallrye-health.check."database".enabled=true

# Metrics
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true
```

This comprehensive guide provides a solid foundation for integrating jOOQ with Quarkus, covering all aspects from basic setup to advanced enterprise patterns. The examples demonstrate type-safe database access while maintaining good separation of concerns and following enterprise best practices.