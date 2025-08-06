package com.example.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ParentActivity {
    
    @ActivityMethod
    String startChildWorkflow(String input);
    
    @ActivityMethod
    String waitForChildWorkflowCompletion(String childResult);
}