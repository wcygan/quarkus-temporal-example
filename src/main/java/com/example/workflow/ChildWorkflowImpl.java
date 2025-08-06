package com.example.workflow;

import com.example.activity.ChildActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ChildWorkflowImpl implements ChildWorkflow {
    
    private final ChildActivity childActivity = Workflow.newActivityStub(
        ChildActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .build()
    );
    
    @Override
    public String executeChild(String input) {
        String firstResult = childActivity.executeFirst(input);
        String secondResult = childActivity.executeSecond(firstResult);
        String thirdResult = childActivity.executeThird(secondResult);
        
        return thirdResult;
    }
}