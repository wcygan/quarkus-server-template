package com.example.config;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
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
 * - Creates a CDI-managed DSLContext bean when DataSource is available
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
    Instance<AgroalDataSource> dataSourceInstance;

    /**
     * Produces a CDI-managed DSLContext bean configured for MySQL.
     * Only created when a DataSource is available (i.e., not in test mode without DB).
     * 
     * The DSLContext provides the entry point for all jOOQ operations
     * and integrates with Quarkus transaction management.
     * 
     * @return configured DSLContext instance
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public DSLContext dslContext() {
        if (dataSourceInstance.isUnsatisfied()) {
            LOG.warn("DataSource not available, creating jOOQ DSLContext without connection");
            // Return a mock DSLContext for environments without database
            Configuration configuration = new DefaultConfiguration()
                .set(SQLDialect.MYSQL);
            return DSL.using(configuration);
        }
        
        AgroalDataSource dataSource = dataSourceInstance.get();
        LOG.info("Creating jOOQ DSLContext with MySQL dialect and DataSource");
        
        Configuration configuration = new DefaultConfiguration()
            .set(dataSource)
            .set(SQLDialect.MYSQL);
        
        return DSL.using(configuration);
    }
}