package com.example.workflow;

import com.example.activity.MetricsActivity;
import com.example.activity.AlertActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsMonitorWorkflowImpl implements MetricsMonitorWorkflow {
    
    private static final Logger logger = Workflow.getLogger(MetricsMonitorWorkflowImpl.class);
    private static final int MAX_EVENTS_BEFORE_CONTINUE = 100;
    private static final Duration MAX_DURATION_BEFORE_CONTINUE = Duration.ofHours(24);
    
    private int totalEventsProcessed = 0;
    private int alertsTriggered = 0;
    private final List<String> recentMetrics = new ArrayList<>();
    private final Map<String, Double> thresholds = new HashMap<>();
    private boolean isPaused = false;
    private long workflowStartTime;
    
    private final MetricsActivity metricsActivity = Workflow.newActivityStub(
        MetricsActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(1))
            .build()
    );
    
    private final AlertActivity alertActivity = Workflow.newActivityStub(
        AlertActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(1))
            .build()
    );
    
    public MetricsMonitorWorkflowImpl() {
        // Initialize default thresholds
        thresholds.put("cpu", 80.0);
        thresholds.put("memory", 90.0);
        thresholds.put("disk", 85.0);
    }
    
    @Override
    public void monitorMetrics(int previousEventsProcessed) {
        // Restore state from previous execution
        this.totalEventsProcessed = previousEventsProcessed;
        this.workflowStartTime = Workflow.currentTimeMillis();
        
        logger.info("Starting metrics monitor with {} previous events", previousEventsProcessed);
        
        while (true) {
            // Check if we should continue as new
            if (shouldContinueAsNew()) {
                logger.info("Continuing as new workflow. Events processed: {}", totalEventsProcessed);
                Workflow.continueAsNew(totalEventsProcessed);
            }
            
            // Wait if paused
            Workflow.await(() -> !isPaused);
            
            // Collect metrics
            Map<String, Double> metrics = metricsActivity.collectMetrics();
            
            // Store recent metrics
            String metricsSummary = String.format("CPU: %.2f%%, Memory: %.2f%%, Disk: %.2f%%", 
                metrics.get("cpu"), metrics.get("memory"), metrics.get("disk"));
            recentMetrics.add(metricsSummary);
            
            // Keep only last 20 metrics
            if (recentMetrics.size() > 20) {
                recentMetrics.remove(0);
            }
            
            // Check thresholds and trigger alerts if needed
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                String metricName = entry.getKey();
                Double value = entry.getValue();
                Double threshold = thresholds.get(metricName);
                
                if (threshold != null && value > threshold) {
                    String alertMessage = String.format("ALERT: %s usage (%.2f%%) exceeds threshold (%.2f%%)", 
                        metricName, value, threshold);
                    alertActivity.sendAlert(alertMessage);
                    alertsTriggered++;
                    logger.warn(alertMessage);
                }
            }
            
            totalEventsProcessed++;
            
            // Sleep for 30 seconds before next check
            Workflow.sleep(Duration.ofSeconds(30));
        }
    }
    
    private boolean shouldContinueAsNew() {
        // Continue as new if we've processed too many events or been running too long
        boolean tooManyEvents = totalEventsProcessed >= MAX_EVENTS_BEFORE_CONTINUE;
        boolean runningTooLong = (Workflow.currentTimeMillis() - workflowStartTime) > MAX_DURATION_BEFORE_CONTINUE.toMillis();
        
        return tooManyEvents || runningTooLong;
    }
    
    @Override
    public int getTotalEventsProcessed() {
        return totalEventsProcessed;
    }
    
    @Override
    public int getAlertsTriggered() {
        return alertsTriggered;
    }
    
    @Override
    public List<String> getRecentMetrics() {
        return new ArrayList<>(recentMetrics);
    }
    
    @Override
    public Map<String, Double> getCurrentThresholds() {
        return new HashMap<>(thresholds);
    }
    
    @Override
    public void updateThreshold(String metric, double threshold) {
        logger.info("Updating threshold for {} to {}", metric, threshold);
        thresholds.put(metric, threshold);
    }
    
    @Override
    public void pauseMonitoring() {
        logger.info("Pausing monitoring");
        isPaused = true;
    }
    
    @Override
    public void resumeMonitoring() {
        logger.info("Resuming monitoring");
        isPaused = false;
    }
}