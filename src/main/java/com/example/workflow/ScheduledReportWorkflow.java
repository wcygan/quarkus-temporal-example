package com.example.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.QueryMethod;
import java.util.List;

@WorkflowInterface
public interface ScheduledReportWorkflow {
    
    @WorkflowMethod
    void generateReports();
    
    @QueryMethod
    String getLastReportTime();
    
    @QueryMethod
    int getReportCount();
    
    @QueryMethod
    List<String> getRecentReports();
}