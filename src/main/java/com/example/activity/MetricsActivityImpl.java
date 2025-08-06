package com.example.activity;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@ApplicationScoped
public class MetricsActivityImpl implements MetricsActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsActivityImpl.class);
    private final Random random = new Random();
    
    @Override
    public Map<String, Double> collectMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        
        // Simulate collecting system metrics
        // In a real implementation, these would come from actual system monitoring
        double cpuUsage = 30 + random.nextDouble() * 60; // 30-90%
        double memoryUsage = 40 + random.nextDouble() * 55; // 40-95%
        double diskUsage = 50 + random.nextDouble() * 40; // 50-90%
        
        metrics.put("cpu", cpuUsage);
        metrics.put("memory", memoryUsage);
        metrics.put("disk", diskUsage);
        
        logger.info("Collected metrics - CPU: {}%, Memory: {}%, Disk: {}%", 
            String.format("%.2f", cpuUsage), 
            String.format("%.2f", memoryUsage), 
            String.format("%.2f", diskUsage));
        
        return metrics;
    }
}