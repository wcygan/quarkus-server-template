package com.example.config;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * jOOQ configuration for Quarkus CDI integration.
 * 
 * This configuration class:
 * - Creates a CDI-managed DSLContext bean
 * - Integrates with Quarkus Agroal DataSource
 * - Configures MySQL dialect for jOOQ
 * - Enables proper transaction management
 * 
 * The DSLContext can be injected into any CDI bean using @Inject.
 */
@ApplicationScoped
public class JooqConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(JooqConfiguration.class);

    @Inject
    AgroalDataSource dataSource;

    /**
     * Produces a CDI-managed DSLContext bean configured for MySQL.
     * 
     * The DSLContext provides the entry point for all jOOQ operations
     * and integrates with Quarkus transaction management.
     * 
     * @return configured DSLContext instance
     */
    @Produces
    @ApplicationScoped
    public DSLContext dslContext() {
        LOG.info("Creating jOOQ DSLContext with MySQL dialect");
        
        Configuration configuration = new DefaultConfiguration()
            .set(dataSource)
            .set(SQLDialect.MYSQL);
        
        return DSL.using(configuration);
    }
}