package com.example.workflow;

import com.example.activity.HelloActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class HelloWorkflowImpl implements HelloWorkflow {
    
    private final HelloActivity helloActivity = Workflow.newActivityStub(
        HelloActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .build()
    );
    
    @Override
    public String executeHello(String name) {
        return helloActivity.sayHello(name);
    }
}