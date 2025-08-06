package com.example.workflow;

import com.example.activity.FaultyActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ErrorHandlingWorkflowImpl implements ErrorHandlingWorkflow {
    
    private boolean cancelRequested = false;
    
    @Override
    public String execute(String testType, String input) {
        switch (testType) {
            case "RETRY":
                return executeWithRetries(input);
            case "NON_RETRYABLE":
                return executeWithNonRetryableError(input);
            case "CANCELLATION":
                return executeWithCancellation(input);
            default:
                throw new IllegalArgumentException("Unknown test type: " + testType);
        }
    }
    
    @Override
    public void cancel() {
        cancelRequested = true;
    }
    
    private String executeWithRetries(String input) {
        FaultyActivity activity = Workflow.newActivityStub(
            FaultyActivity.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setMaximumInterval(Duration.ofSeconds(10))
                    .setMaximumAttempts(5)
                    .build())
                .build()
        );
        
        // First call should fail but retry and succeed
        return activity.processWithRetry(input, true);
    }
    
    private String executeWithNonRetryableError(String input) {
        FaultyActivity activity = Workflow.newActivityStub(
            FaultyActivity.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .build()
        );
        
        try {
            return activity.processNonRetryable(input);
        } catch (Exception e) {
            // Log the error and return a fallback
            Workflow.getLogger(ErrorHandlingWorkflowImpl.class).error("Non-retryable error occurred", e);
            return "Failed to process " + input + ": " + e.getMessage();
        }
    }
    
    private String executeWithCancellation(String input) {
        // For timeout test, disable retries
        ActivityOptions.Builder optionsBuilder = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .setHeartbeatTimeout(Duration.ofSeconds(2));
        
        if ("TIMEOUT_TEST".equals(input)) {
            optionsBuilder.setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(1)
                .build());
        }
        
        FaultyActivity activity = Workflow.newActivityStub(
            FaultyActivity.class,
            optionsBuilder.build()
        );
        
        // Simple approach - just run the activity and handle cancellation
        try {
            activity.processWithHeartbeat(input, 100);
            return "Completed";
        } catch (CanceledFailure e) {
            return "Cancelled";
        } catch (Exception e) {
            // Check if cancelled via signal
            if (cancelRequested) {
                return "Cancelled";
            }
            throw e;
        }
    }
}