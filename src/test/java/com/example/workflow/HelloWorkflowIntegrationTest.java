package com.example.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.example.activity.HelloActivity;
import com.example.activity.HelloActivityImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

public class HelloWorkflowIntegrationTest {
    
    @RegisterExtension
    public static final TestWorkflowExtension testWorkflowExtension = 
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(HelloWorkflowImpl.class)
            .setDoNotStart(true)
            .build();
    
    @Test
    public void testHelloWorkflow(TestWorkflowEnvironment testEnv, Worker worker, HelloWorkflow workflow) {
        // Register activity implementation
        worker.registerActivitiesImplementations(new HelloActivityImpl());
        
        // Start test environment
        testEnv.start();
        
        // Execute workflow
        String result = workflow.executeHello("Temporal");
        
        // Verify result
        assertEquals("Hello, Temporal!", result);
    }
    
    @Test
    public void testHelloWorkflowWithDifferentNames(TestWorkflowEnvironment testEnv, Worker worker) {
        // Register workflow and activity implementations
        worker.registerActivitiesImplementations(new HelloActivityImpl());
        testEnv.start();
        
        // Create workflow client
        WorkflowClient client = testEnv.getWorkflowClient();
        
        // Test with multiple names
        String[] names = {"Alice", "Bob", "Charlie", "世界", "Düsseldorf"};
        
        for (String name : names) {
            // Create workflow stub
            HelloWorkflow workflow = client.newWorkflowStub(
                HelloWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(worker.getTaskQueue())
                    .setWorkflowId("hello-workflow-" + name)
                    .build()
            );
            
            // Execute workflow
            String result = workflow.executeHello(name);
            
            // Verify result
            assertEquals("Hello, " + name + "!", result);
        }
    }
    
    @Test
    public void testHelloWorkflowWithEmptyName(TestWorkflowEnvironment testEnv, Worker worker, HelloWorkflow workflow) {
        worker.registerActivitiesImplementations(new HelloActivityImpl());
        testEnv.start();
        
        // Execute workflow with empty string
        String result = workflow.executeHello("");
        
        // Verify result handles empty string
        assertEquals("Hello, !", result);
    }
    
    @Test
    public void testHelloWorkflowWithNullName(TestWorkflowEnvironment testEnv, Worker worker, HelloWorkflow workflow) {
        worker.registerActivitiesImplementations(new HelloActivityImpl());
        testEnv.start();
        
        // Execute workflow with null
        String result = workflow.executeHello(null);
        
        // Verify result handles null
        assertEquals("Hello, null!", result);
    }
}