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
}