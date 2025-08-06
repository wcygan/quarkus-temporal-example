package com.example.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import java.util.List;
import java.util.Map;

@WorkflowInterface
public interface MetricsMonitorWorkflow {
    
    @WorkflowMethod
    void monitorMetrics(int eventsProcessed);
    
    @QueryMethod
    int getTotalEventsProcessed();
    
    @QueryMethod
    int getAlertsTriggered();
    
    @QueryMethod
    List<String> getRecentMetrics();
    
    @QueryMethod
    Map<String, Double> getCurrentThresholds();
    
    @SignalMethod
    void updateThreshold(String metric, double threshold);
    
    @SignalMethod
    void pauseMonitoring();
    
    @SignalMethod
    void resumeMonitoring();
}