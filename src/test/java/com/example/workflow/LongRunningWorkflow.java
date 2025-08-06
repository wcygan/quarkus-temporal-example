package com.example.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface LongRunningWorkflow {
    
    @WorkflowMethod
    String processWithDelay(String input, int delayHours);
}