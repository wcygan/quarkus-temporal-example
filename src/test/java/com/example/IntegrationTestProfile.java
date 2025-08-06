package com.example;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile for integration tests that connect to real external services.
 * 
 * This profile ensures that integration tests:
 * - Connect to real Temporal server at localhost:7233
 * - Use the default namespace
 * - Have proper timeouts for real service calls
 */
public class IntegrationTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Temporal configuration for real server
            "temporal.target", "localhost:7233",
            "temporal.namespace", "default",
            "temporal.task-queue", "doc-approval-queue",
            
            // Test configuration
            "quarkus.http.test-port", "7576",
            "quarkus.http.test-timeout", "60s",
            
            // Logging for better debugging
            "quarkus.log.level", "INFO",
            "quarkus.log.category.\"com.example\".level", "DEBUG",
            "quarkus.log.category.\"io.temporal\".level", "INFO"
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "integration-test";
    }
    
    @Override
    public boolean disableGlobalTestResources() {
        // Don't disable global test resources
        return false;
    }
}