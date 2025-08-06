package com.example.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.Map;

@ActivityInterface
public interface MetricsActivity {
    
    @ActivityMethod
    Map<String, Double> collectMetrics();
}