package com.example.test;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for all integration tests that require a MySQL database.
 * Automatically initializes the shared MySQL container.
 */
@QuarkusTest
@Testcontainers
public abstract class AbstractDatabaseTest {
    
    @BeforeAll
    public static void initDatabase() {
        // Initialize the shared MySQL container
        SharedMySQLContainer.init();
    }
    
    /**
     * Get the JDBC URL of the test database
     */
    protected static String getDatabaseUrl() {
        return SharedMySQLContainer.getJdbcUrl();
    }
    
    /**
     * Get the username for the test database
     */
    protected static String getDatabaseUsername() {
        return SharedMySQLContainer.getUsername();
    }
    
    /**
     * Get the password for the test database
     */
    protected static String getDatabasePassword() {
        return SharedMySQLContainer.getPassword();
    }
    
    /**
     * Get the host of the test database container
     */
    protected static String getDatabaseHost() {
        return SharedMySQLContainer.getHost();
    }
    
    /**
     * Get the mapped port of the test database container
     */
    protected static Integer getDatabasePort() {
        return SharedMySQLContainer.getMappedPort();
    }
}