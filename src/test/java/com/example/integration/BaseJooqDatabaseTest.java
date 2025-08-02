package com.example.integration;

import com.example.generated.jooq.Userapi;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.example.generated.jooq.Tables.*;

/**
 * Base test class for jOOQ database integration tests.
 *
 * Features:
 * - TestContainers MySQL database with Flyway migrations
 * - Automatic table cleanup after each test using jOOQ DSL
 * - CDI-managed DSLContext injection
 * - Test isolation with sequential execution
 *
 * Usage:
 * 1. Extend this class in your integration tests
 * 2. Use @Inject DSLContext to perform database operations
 * 3. Tests run sequentially to avoid conflicts
 * 4. All tables are cleaned automatically after each test
 *
 * Prerequisites:
 * - Docker must be running for TestContainers
 * - Run with: mvn test -Dtest.database=true
 * - jOOQ code generation must have completed: mvn generate-sources
 *
 * Example:
 * <pre>
 * {@code
 * public class UserRepositoryTest extends BaseJooqDatabaseTest {
 *
 *     @Test
 *     void testCreateUser() {
 *         // Database is clean and ready
 *         // Use dslContext for operations
 *         // Cleanup happens automatically after test
 *     }
 * }
 * }
 * </pre>
 */
@QuarkusTest
@TestProfile(MySQLTestProfile.class)
@EnabledIfSystemProperty(named = "test.database", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseJooqDatabaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseJooqDatabaseTest.class);

    @Inject
    protected DSLContext dslContext;

    /**
     * List of all tables in dependency order for cleanup.
     * Order matters: child tables (with foreign keys) must come before parent tables.
     *
     * Note: This list should be updated when new tables are added to the schema.
     * Consider using jOOQ's Tables.getTables() method if available in your jOOQ version.
     */
    private static final List<Table<?>> TABLES_FOR_CLEANUP = List.of(
        USERS  // Add new tables here in dependency order
    );

    @BeforeEach
    void setUpTest() {
        LOG.debug("Setting up test with clean database state");
        verifyDSLContextInjection();
    }

    @AfterEach
    void cleanUpTest() {
        LOG.debug("Cleaning up database after test");
        cleanAllTables();
    }

    /**
     * Verifies that DSLContext is properly injected and database is accessible.
     */
    private void verifyDSLContextInjection() {
        if (dslContext == null) {
            throw new IllegalStateException("DSLContext not injected. Check CDI configuration.");
        }

        try {
            // Simple connectivity test
            dslContext.selectOne().fetch();
            LOG.debug("Database connectivity verified");
        } catch (DataAccessException e) {
            throw new IllegalStateException("Database not accessible: " + e.getMessage(), e);
        }
    }

    /**
     * Cleans all tables using jOOQ DSL DELETE statements.
     * Tables are cleaned in dependency order to avoid foreign key constraint violations.
     *
     * This approach is faster than TRUNCATE and works with foreign key constraints.
     */
    private void cleanAllTables() {
        try {
            // Disable foreign key checks temporarily for MySQL
            dslContext.execute("SET FOREIGN_KEY_CHECKS = 0");

            for (Table<?> table : TABLES_FOR_CLEANUP) {
                int deletedRows = dslContext.deleteFrom(table).execute();
                LOG.debug("Cleaned table {}: {} rows deleted", table.getName(), deletedRows);
            }

            // Re-enable foreign key checks
            dslContext.execute("SET FOREIGN_KEY_CHECKS = 1");

            LOG.debug("All tables cleaned successfully");

        } catch (DataAccessException e) {
            LOG.error("Failed to clean tables: {}", e.getMessage(), e);
            throw new RuntimeException("Database cleanup failed", e);
        }
    }

    /**
     * Alternative cleanup method using jOOQ's table introspection (if available).
     * This method dynamically discovers all tables and cleans them.
     *
     * Note: Requires jOOQ Pro or Enterprise edition for getTables() method.
     * Use TABLES_FOR_CLEANUP list for jOOQ Open Source edition.
     */
    @SuppressWarnings("unused")
    private void cleanAllTablesUsingIntrospection() {
        try {
            // This would work with jOOQ Pro/Enterprise
            // var allTables = Tables.getTables();
            // for (Table<?> table : allTables) {
            //     dslContext.deleteFrom(table).execute();
            // }

            // For jOOQ Open Source, use the explicit table list
            cleanAllTables();

        } catch (Exception e) {
            LOG.error("Failed to clean tables using introspection: {}", e.getMessage(), e);
            throw new RuntimeException("Database cleanup failed", e);
        }
    }

    /**
     * Utility method to get the current row count for a table.
     * Useful for test assertions.
     */
    protected int getRowCount(Table<?> table) {
        return dslContext.selectCount().from(table).fetchOne(0, int.class);
    }

    /**
     * Utility method to verify a table is empty.
     * Useful for test setup verification.
     */
    protected void assertTableIsEmpty(Table<?> table) {
        int count = getRowCount(table);
        if (count != 0) {
            throw new AssertionError("Table " + table.getName() + " should be empty but contains " + count + " rows");
        }
    }

    /**
     * Utility method to check if the database schema matches expectations.
     * Useful for verifying migrations are applied correctly.
     */
    protected void verifySchemaExists() {
        try {
            // Verify users table exists with expected structure
            dslContext.select().from(USERS).limit(0).fetch();
            LOG.debug("Schema verification passed");
        } catch (DataAccessException e) {
            throw new IllegalStateException("Schema verification failed. Check migrations: " + e.getMessage(), e);
        }
    }
}