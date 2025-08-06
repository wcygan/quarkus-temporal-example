package com.example.activity;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApplicationScoped
public class AlertActivityImpl implements AlertActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertActivityImpl.class);
    
    // In-memory storage for demo purposes
    private final ConcurrentLinkedQueue<String> alertHistory = new ConcurrentLinkedQueue<>();
    
    @Override
    public void sendAlert(String message) {
        String timestamp = Instant.now().toString();
        String alertEntry = String.format("[%s] %s", timestamp, message);
        
        // Log the alert
        logger.warn("ALERT: {}", message);
        
        // Store in history (in production, this would send to alerting system)
        alertHistory.add(alertEntry);
        
        // Keep only last 100 alerts
        while (alertHistory.size() > 100) {
            alertHistory.poll();
        }
        
        // In a real implementation, this would:
        // - Send email/SMS notifications
        // - Post to Slack/PagerDuty
        // - Update monitoring dashboards
        // - Trigger automated responses
    }
    
    // Helper method for testing/debugging
    public ConcurrentLinkedQueue<String> getAlertHistory() {
        return new ConcurrentLinkedQueue<>(alertHistory);
    }
}