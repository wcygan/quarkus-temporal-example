package com.example.jooq;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;

import javax.sql.DataSource;

/**
 * CDI producer for jOOQ DSLContext.
 * Provides a configured jOOQ DSLContext for dependency injection throughout the application.
 */
@ApplicationScoped
public class JooqConfiguration {
    
    @Inject
    DataSource dataSource;
    
    /**
     * Produces a jOOQ Configuration bean.
     * This configuration is used by jOOQ to interact with the database.
     */
    @Produces
    @ApplicationScoped
    public Configuration jooqConfiguration() {
        DefaultConfiguration configuration = new DefaultConfiguration();
        configuration.setDataSource(dataSource);
        configuration.setSQLDialect(SQLDialect.MYSQL);
        
        // Enable logging for development
        configuration.set(new org.jooq.impl.DefaultExecuteListenerProvider(
            new org.jooq.tools.LoggerListener()
        ));
        
        return configuration;
    }
    
    /**
     * Produces a jOOQ DSLContext bean.
     * The DSLContext is the main entry point for building and executing SQL queries with jOOQ.
     */
    @Produces
    @ApplicationScoped
    public DSLContext dslContext(Configuration configuration) {
        return DSL.using(configuration);
    }
}