package com.example.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ReportActivity {
    
    @ActivityMethod
    String captureTimestamp(String timestamp);
}