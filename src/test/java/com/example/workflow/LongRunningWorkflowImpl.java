package com.example.workflow;

import com.example.activity.ProcessingActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class LongRunningWorkflowImpl implements LongRunningWorkflow {
    
    private final ProcessingActivity processingActivity = Workflow.newActivityStub(
        ProcessingActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .build()
    );
    
    @Override
    public String processWithDelay(String input, int delayHours) {
        // Start processing
        String startResult = processingActivity.startProcessing(input);
        
        // Sleep for the specified hours
        Workflow.sleep(Duration.ofHours(delayHours));
        
        // Complete processing after delay
        String finalResult = processingActivity.completeProcessing(startResult);
        
        return finalResult;
    }
}