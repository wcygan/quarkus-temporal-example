package com.example.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ChildActivity {
    
    @ActivityMethod
    String executeFirst(String input);
    
    @ActivityMethod
    String executeSecond(String input);
    
    @ActivityMethod
    String executeThird(String input);
}