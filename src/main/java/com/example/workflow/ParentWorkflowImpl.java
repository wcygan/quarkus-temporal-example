package com.example.workflow;

import com.example.activity.ParentActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ParentWorkflowImpl implements ParentWorkflow {
    
    private final ParentActivity parentActivity = Workflow.newActivityStub(
        ParentActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .build()
    );
    
    @Override
    public String executeParent(String input) {
        String childWorkflowId = parentActivity.startChildWorkflow(input);
        
        ChildWorkflow childWorkflow = Workflow.newChildWorkflowStub(
            ChildWorkflow.class,
            ChildWorkflowOptions.newBuilder()
                .setWorkflowId(childWorkflowId)
                .setTaskQueue("doc-approval-queue")
                .build()
        );
        
        Promise<String> childPromise = Async.function(childWorkflow::executeChild, input);
        
        String childResult = childPromise.get();
        
        String finalResult = parentActivity.waitForChildWorkflowCompletion(childResult);
        
        return finalResult;
    }
}