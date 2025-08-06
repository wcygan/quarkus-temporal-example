package com.example.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.example.activity.ProcessingActivityImpl;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

public class LongRunningWorkflowTimeSkippingTest {
    
    @RegisterExtension
    public static final TestWorkflowExtension testWorkflowExtension = 
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(LongRunningWorkflowImpl.class)
            .setDoNotStart(true)
            .build();
    
    @Test
    public void testTimeSkipping24Hours(TestWorkflowEnvironment testEnv, Worker worker, LongRunningWorkflow workflow) {
        // Register activity implementation
        worker.registerActivitiesImplementations(new ProcessingActivityImpl());
        
        // Start test environment
        testEnv.start();
        
        // Record real start time
        long realStartTime = System.currentTimeMillis();
        
        // Execute workflow with 24-hour delay
        String result = workflow.processWithDelay("DATA", 24);
        
        // Record real end time
        long realEndTime = System.currentTimeMillis();
        
        // Verify the result
        assertEquals("COMPLETED_PROCESSING_DATA", result);
        
        // Verify time was skipped (should complete in seconds, not 24 hours)
        long actualDurationMs = realEndTime - realStartTime;
        assertTrue(actualDurationMs < 60000, "Test should complete in less than 60 seconds, but took " + actualDurationMs + " ms");
        
        // Verify the workflow internally experienced 24 hours
        // This is implicit in the successful completion of the workflow
    }
    
    @Test
    public void testTimeSkippingMultipleDays(TestWorkflowEnvironment testEnv, Worker worker, LongRunningWorkflow workflow) {
        worker.registerActivitiesImplementations(new ProcessingActivityImpl());
        testEnv.start();
        
        long realStartTime = System.currentTimeMillis();
        
        // Execute workflow with 7-day delay (168 hours)
        String result = workflow.processWithDelay("WEEKLY_JOB", 168);
        
        long realEndTime = System.currentTimeMillis();
        
        // Verify the result
        assertEquals("COMPLETED_PROCESSING_WEEKLY_JOB", result);
        
        // Verify time was skipped
        long actualDurationMs = realEndTime - realStartTime;
        assertTrue(actualDurationMs < 60000, "Test should complete quickly, but took " + actualDurationMs + " ms");
    }
    
    @Test
    public void testManualTimeSkipping(TestWorkflowEnvironment testEnv, Worker worker) {
        worker.registerActivitiesImplementations(new ProcessingActivityImpl());
        testEnv.start();
        
        // Create workflow stub asynchronously
        LongRunningWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            LongRunningWorkflow.class,
            io.temporal.client.WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .build()
        );
        
        // Start workflow execution asynchronously
        var execution = io.temporal.client.WorkflowClient.start(workflow::processWithDelay, "MANUAL_TEST", 48);
        
        // Sleep for a moment to let the workflow start
        testEnv.sleep(Duration.ofSeconds(1));
        
        // Manually skip time by 24 hours
        testEnv.sleep(Duration.ofHours(24));
        
        // Workflow should still be running (48 hour delay)
        var stub = testEnv.getWorkflowClient().newUntypedWorkflowStub(execution.getWorkflowId());
        var resultFuture = stub.getResultAsync(String.class);
        assertFalse(resultFuture.isDone());
        
        // Skip another 24 hours
        testEnv.sleep(Duration.ofHours(24));
        
        // Now workflow should complete
        try {
            String result = resultFuture.get();
            assertEquals("COMPLETED_PROCESSING_MANUAL_TEST", result);
        } catch (InterruptedException | ExecutionException e) {
            fail("Failed to get workflow result: " + e.getMessage());
        }
    }
    
    @Test
    public void testZeroDelayWorkflow(TestWorkflowEnvironment testEnv, Worker worker, LongRunningWorkflow workflow) {
        worker.registerActivitiesImplementations(new ProcessingActivityImpl());
        testEnv.start();
        
        // Execute workflow with zero delay
        String result = workflow.processWithDelay("INSTANT", 0);
        
        // Should complete immediately
        assertEquals("COMPLETED_PROCESSING_INSTANT", result);
    }
}