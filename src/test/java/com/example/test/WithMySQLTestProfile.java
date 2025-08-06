package com.example.test;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * Test profile that includes MySQL TestContainer for integration tests.
 */
public class WithMySQLTestProfile implements QuarkusTestProfile {
    
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Override any specific properties for tests if needed
            "quarkus.hibernate-orm.database.generation", "drop-and-create",
            "quarkus.hibernate-orm.log.sql", "false"
        );
    }
    
    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(
            new TestResourceEntry(MySQLTestResource.class)
        );
    }
    
    @Override
    public String getConfigProfile() {
        return "test-with-mysql";
    }
}