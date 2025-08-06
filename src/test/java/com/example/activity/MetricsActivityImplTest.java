package com.example.activity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsActivityImplTest {
    
    private MetricsActivityImpl metricsActivity;
    
    @BeforeEach
    public void setUp() {
        metricsActivity = new MetricsActivityImpl();
    }
    
    @Test
    public void testCollectMetrics() {
        Map<String, Double> metrics = metricsActivity.collectMetrics();
        
        assertNotNull(metrics);
        assertEquals(3, metrics.size());
        
        // Verify all metrics are present
        assertTrue(metrics.containsKey("cpu"));
        assertTrue(metrics.containsKey("memory"));
        assertTrue(metrics.containsKey("disk"));
        
        // Verify values are in expected ranges
        double cpu = metrics.get("cpu");
        double memory = metrics.get("memory");
        double disk = metrics.get("disk");
        
        assertTrue(cpu >= 30 && cpu <= 90, "CPU should be between 30-90%");
        assertTrue(memory >= 40 && memory <= 95, "Memory should be between 40-95%");
        assertTrue(disk >= 50 && disk <= 90, "Disk should be between 50-90%");
    }
    
    @Test
    public void testMultipleCollections() {
        // Collect metrics multiple times to ensure randomness
        Map<String, Double> metrics1 = metricsActivity.collectMetrics();
        Map<String, Double> metrics2 = metricsActivity.collectMetrics();
        Map<String, Double> metrics3 = metricsActivity.collectMetrics();
        
        // At least one metric should be different (due to randomness)
        boolean hasDifference = 
            !metrics1.get("cpu").equals(metrics2.get("cpu")) ||
            !metrics1.get("memory").equals(metrics2.get("memory")) ||
            !metrics1.get("disk").equals(metrics2.get("disk")) ||
            !metrics2.get("cpu").equals(metrics3.get("cpu")) ||
            !metrics2.get("memory").equals(metrics3.get("memory")) ||
            !metrics2.get("disk").equals(metrics3.get("disk"));
        
        assertTrue(hasDifference, "Metrics should vary between collections");
    }
}