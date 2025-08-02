package com.example.integration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple integration test to verify that TestContainer MySQL setup is working.
 * This test focuses on the basic infrastructure without jOOQ complexities.
 * 
 * Features tested:
 * - DataSource injection and availability
 * - DSLContext injection and jOOQ integration
 * - Basic database connectivity
 * 
 * To run: mvn test -Dtest.database=true -Dtest="SimpleMySQLIntegrationTest"
 */
@QuarkusTest
@QuarkusTestResource(MySQLTestResource.class)
@EnabledIfSystemProperty(named = "test.database", matches = "true")
class SimpleMySQLIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleMySQLIntegrationTest.class);

    @Inject
    DataSource dataSource;

    @Inject
    DSLContext dslContext;

    @Test
    void testDataSourceIsAvailable() {
        LOG.info("Testing DataSource availability");
        assertThat(dataSource).isNotNull();
        LOG.info("DataSource type: {}", dataSource.getClass().getSimpleName());
    }

    @Test
    void testDSLContextIsAvailable() {
        LOG.info("Testing DSLContext availability");
        assertThat(dslContext).isNotNull();
        LOG.info("DSLContext configuration: {}", dslContext.configuration().dialect());
    }

    @Test
    void testDatabaseConnectivity() {
        LOG.info("Testing database connectivity");
        Integer result = dslContext.selectOne().fetchOne(0, Integer.class);
        assertThat(result).isEqualTo(1);
        LOG.info("Database connectivity verified: SELECT 1 returned {}", result);
    }

    @Test
    void testFlywayMigrationExecuted() {
        LOG.info("Testing that Flyway migrations executed successfully");
        
        // Verify the users table exists by counting rows (should return 0 for empty table)
        Integer count = dslContext
                .selectCount()
                .from("users")
                .fetchOne(0, Integer.class);
        
        assertThat(count).isNotNull();
        assertThat(count).isGreaterThanOrEqualTo(0);
        
        LOG.info("Verified users table exists and can be queried, current count: {}", count);
    }

    @Test
    void testDatabaseDialect() {
        LOG.info("Testing that jOOQ is using correct MySQL dialect");
        
        String dialect = dslContext.configuration().dialect().name();
        assertThat(dialect).isEqualTo("MYSQL");
        
        LOG.info("Verified jOOQ is using MySQL dialect: {}", dialect);
    }
}