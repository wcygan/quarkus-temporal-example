package com.example.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.SignalMethod;

@WorkflowInterface
public interface ErrorHandlingWorkflow {
    
    @WorkflowMethod
    String execute(String testType, String input);
    
    @SignalMethod
    void cancel();
}