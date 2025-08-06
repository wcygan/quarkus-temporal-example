package com.example.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Quarkus test resource that manages a MySQL container for all tests.
 * This resource is automatically started before tests and stopped after.
 */
public class MySQLTestResource implements QuarkusTestResourceLifecycleManager {
    
    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static MySQLContainer<?> container;
    
    @Override
    public Map<String, String> start() {
        if (container == null || !container.isRunning()) {
            container = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
                .withDatabaseName("docstore")
                .withUsername("root")
                .withPassword("test")
                .withReuse(true)
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
            
            container.start();
        }
        
        // Return configuration properties for Quarkus
        return Map.of(
            "quarkus.datasource.jdbc.url", container.getJdbcUrl(),
            "quarkus.datasource.username", "root",
            "quarkus.datasource.password", container.getPassword(),
            "quarkus.datasource.db-kind", "mysql",
            "quarkus.flyway.migrate-at-start", "true"
        );
    }
    
    @Override
    public void stop() {
        // Container will be stopped by testcontainers lifecycle
        // We don't stop it here to allow reuse between test runs
    }
    
    /**
     * Get the container instance for direct access if needed
     */
    public static MySQLContainer<?> getContainer() {
        return container;
    }
}