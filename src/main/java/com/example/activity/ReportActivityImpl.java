package com.example.activity;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@ApplicationScoped
public class ReportActivityImpl implements ReportActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportActivityImpl.class);
    
    // In-memory storage for demo purposes
    private final Map<String, String> reports = new ConcurrentHashMap<>();
    
    @Override
    public String captureTimestamp(String timestamp) {
        String reportId = "report-" + UUID.randomUUID().toString();
        logger.info("Capturing timestamp for report {}: {}", reportId, timestamp);
        
        // Store the report (in production, this would go to a database)
        reports.put(reportId, timestamp);
        
        // Log report generation
        logger.info("Report generated - ID: {}, Timestamp: {}", reportId, timestamp);
        logger.info("Total reports generated: {}", reports.size());
        
        return reportId;
    }
    
    // Helper method to retrieve reports (for testing/debugging)
    public Map<String, String> getAllReports() {
        return new ConcurrentHashMap<>(reports);
    }
}