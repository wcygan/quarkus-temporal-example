package com.example.workflow;

import com.example.activity.ChildActivityImpl;
import com.example.activity.ParentActivityImpl;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParentWorkflowTest {
    
    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    
    @BeforeEach
    public void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("doc-approval-queue");
        worker.registerWorkflowImplementationTypes(ParentWorkflowImpl.class, ChildWorkflowImpl.class);
        worker.registerActivitiesImplementations(new ParentActivityImpl(), new ChildActivityImpl());
        testEnv.start();
    }
    
    @AfterEach
    public void tearDown() {
        testEnv.close();
    }
    
    @Test
    public void testParentWorkflowExecution() {
        ParentWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            ParentWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("doc-approval-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        String result = workflow.executeParent("parent-input");
        
        assertTrue(result.contains("Parent completed"));
        assertTrue(result.contains("third:second:first:parent-input"));
    }
    
    @Test
    public void testParentWorkflowWithEmptyInput() {
        ParentWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            ParentWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("doc-approval-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        String result = workflow.executeParent("");
        
        assertTrue(result.contains("Parent completed"));
        assertTrue(result.contains("third:second:first:"));
    }
    
    @Test
    public void testParentWorkflowWithSpecialInput() {
        ParentWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            ParentWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("doc-approval-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        String result = workflow.executeParent("test-123");
        
        assertTrue(result.contains("Parent completed"));
        assertTrue(result.contains("third:second:first:test-123"));
    }
}