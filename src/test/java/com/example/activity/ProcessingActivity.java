package com.example.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ProcessingActivity {
    
    @ActivityMethod
    String startProcessing(String input);
    
    @ActivityMethod
    String completeProcessing(String intermediateResult);
}