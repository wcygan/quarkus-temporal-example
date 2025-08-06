package com.example.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface FaultyActivity {
    
    @ActivityMethod
    String processWithRetry(String input, boolean shouldFail);
    
    @ActivityMethod
    String processNonRetryable(String input);
    
    @ActivityMethod
    void processWithHeartbeat(String input, int iterations);
}