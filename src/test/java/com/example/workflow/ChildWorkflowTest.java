package com.example.workflow;

import com.example.activity.ChildActivity;
import com.example.activity.ChildActivityImpl;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChildWorkflowTest {
    
    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    
    @BeforeEach
    public void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("test-task-queue");
        worker.registerWorkflowImplementationTypes(ChildWorkflowImpl.class);
        worker.registerActivitiesImplementations(new ChildActivityImpl());
        testEnv.start();
    }
    
    @AfterEach
    public void tearDown() {
        testEnv.close();
    }
    
    @Test
    public void testChildWorkflowExecution() {
        ChildWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            ChildWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        String result = workflow.executeChild("test-input");
        
        assertEquals("third:second:first:test-input", result);
    }
    
    @Test
    public void testChildWorkflowWithEmptyInput() {
        ChildWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            ChildWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        String result = workflow.executeChild("");
        
        assertEquals("third:second:first:", result);
    }
    
    @Test
    public void testChildWorkflowWithSpecialCharacters() {
        ChildWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            ChildWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        String result = workflow.executeChild("test@#$%");
        
        assertEquals("third:second:first:test@#$%", result);
    }
}