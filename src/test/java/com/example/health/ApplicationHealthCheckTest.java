package com.example.health;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ApplicationHealthCheckTest {

    @Inject
    @Liveness
    ApplicationHealthCheck healthCheck;

    @Test
    public void testHealthCheck() {
        HealthCheckResponse response = healthCheck.call();
        
        assertEquals("application", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertTrue(response.getData().isPresent());
        assertEquals("running", response.getData().get().get("status"));
        assertEquals("quarkus-temporal-example", response.getData().get().get("application"));
    }
}