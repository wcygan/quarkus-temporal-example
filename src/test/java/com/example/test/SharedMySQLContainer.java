package com.example.test;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared MySQL container for all tests that need a database.
 * Uses the Singleton pattern to ensure only one container is started for all tests.
 */
public class SharedMySQLContainer {
    
    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String DATABASE_NAME = "testdb";
    private static final String USERNAME = "test";
    private static final String PASSWORD = "test";
    
    private static MySQLContainer<?> container;
    
    static {
        // Start the container once when the class is loaded
        container = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
            .withDatabaseName(DATABASE_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withReuse(true) // Enable container reuse for faster test runs
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
        
        container.start();
        
        // Set system properties for Quarkus to use
        System.setProperty("quarkus.datasource.jdbc.url", container.getJdbcUrl());
        System.setProperty("quarkus.datasource.username", container.getUsername());
        System.setProperty("quarkus.datasource.password", container.getPassword());
        
        // Also set for the reactive datasource if needed
        System.setProperty("quarkus.datasource.reactive.url", 
            container.getJdbcUrl().replace("jdbc:", ""));
        
        // Register shutdown hook to stop the container
        Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
    }
    
    public static MySQLContainer<?> getInstance() {
        return container;
    }
    
    public static String getJdbcUrl() {
        return container.getJdbcUrl();
    }
    
    public static String getUsername() {
        return container.getUsername();
    }
    
    public static String getPassword() {
        return container.getPassword();
    }
    
    public static Integer getMappedPort() {
        return container.getMappedPort(MySQLContainer.MYSQL_PORT);
    }
    
    public static String getHost() {
        return container.getHost();
    }
    
    /**
     * Initialize the container. Call this in your test class to ensure the container is started.
     */
    public static void init() {
        // This method is intentionally empty.
        // Its purpose is to trigger the static initializer block when called.
    }
}