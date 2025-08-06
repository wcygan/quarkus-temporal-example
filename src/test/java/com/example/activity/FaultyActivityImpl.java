package com.example.activity;

import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;

@ApplicationScoped
public class FaultyActivityImpl implements FaultyActivity {
    
    private int retryCount = 0;
    
    @Override
    public String processWithRetry(String input, boolean shouldFail) {
        retryCount++;
        System.out.println("Attempt #" + retryCount + " for input: " + input);
        
        if (shouldFail && retryCount < 3) {
            throw new RuntimeException("Simulated failure, attempt " + retryCount);
        }
        
        // Reset counter after success
        int attempts = retryCount;
        retryCount = 0;
        return "Processed " + input + " after " + attempts + " attempts";
    }
    
    @Override
    public String processNonRetryable(String input) {
        // Throw non-retryable error
        throw ApplicationFailure.newNonRetryableFailure(
            "This is a non-retryable error for input: " + input,
            "INVALID_INPUT"
        );
    }
    
    @Override
    public void processWithHeartbeat(String input, int iterations) {
        for (int i = 0; i < iterations; i++) {
            // Send heartbeat and check for cancellation
            try {
                Activity.getExecutionContext().heartbeat(i);
            } catch (CanceledFailure e) {
                System.out.println("Activity cancelled at iteration " + i);
                throw e;
            }
            
            // Simulate work
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw Activity.wrap(e);
            }
            
            System.out.println("Heartbeat iteration " + i + " for " + input);
        }
    }
}