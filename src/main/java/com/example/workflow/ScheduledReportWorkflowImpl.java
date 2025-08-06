package com.example.workflow;

import com.example.activity.ReportActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ScheduledReportWorkflowImpl implements ScheduledReportWorkflow {
    
    private static final Logger logger = Workflow.getLogger(ScheduledReportWorkflowImpl.class);
    
    private final List<String> reportTimestamps = new ArrayList<>();
    private int reportCount = 0;
    private String lastReportTime = "No reports generated yet";
    
    private final ReportActivity reportActivity = Workflow.newActivityStub(
        ReportActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(1))
            .build()
    );
    
    @Override
    public void generateReports() {
        logger.info("Starting scheduled report workflow");
        
        // This workflow will run indefinitely based on the cron schedule
        // Each execution captures a timestamp
        String timestamp = Instant.now().toString();
        String reportId = reportActivity.captureTimestamp(timestamp);
        
        reportTimestamps.add(timestamp);
        reportCount++;
        lastReportTime = timestamp;
        
        logger.info("Report generated with ID: {} at {}", reportId, timestamp);
        
        // The workflow completes here and will be restarted by the cron schedule
    }
    
    @Override
    public String getLastReportTime() {
        return lastReportTime;
    }
    
    @Override
    public int getReportCount() {
        return reportCount;
    }
    
    @Override
    public List<String> getRecentReports() {
        // Return last 10 reports
        int size = reportTimestamps.size();
        int fromIndex = Math.max(0, size - 10);
        return new ArrayList<>(reportTimestamps.subList(fromIndex, size));
    }
}