package com.example.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.example.activity.FaultyActivityImpl;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.failure.TimeoutFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.activity.Activity;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class ErrorHandlingWorkflowTest {
    
    @RegisterExtension
    public static final TestWorkflowExtension testWorkflowExtension = 
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(ErrorHandlingWorkflowImpl.class)
            .setDoNotStart(true)
            .build();
    
    @Test
    public void testActivityRetrySuccess(TestWorkflowEnvironment testEnv, Worker worker) {
        // Register activity implementation
        worker.registerActivitiesImplementations(new FaultyActivityImpl());
        testEnv.start();
        
        ErrorHandlingWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            ErrorHandlingWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .build()
        );
        
        // This should fail twice then succeed on third attempt
        String result = workflow.execute("RETRY", "TEST_DATA");
        
        // Verify it succeeded after retries
        assertEquals("Processed TEST_DATA after 3 attempts", result);
    }
    
    @Test
    public void testNonRetryableError(TestWorkflowEnvironment testEnv, Worker worker) {
        worker.registerActivitiesImplementations(new FaultyActivityImpl());
        testEnv.start();
        
        ErrorHandlingWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            ErrorHandlingWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .build()
        );
        
        // This should catch the non-retryable error and return fallback
        String result = workflow.execute("NON_RETRYABLE", "INVALID_DATA");
        
        // Verify fallback was used
        assertTrue(result.startsWith("Failed to process INVALID_DATA:"));
        // Check for indicators of non-retryable failure
        assertTrue(result.contains("RETRY_STATE_NON_RETRYABLE_FAILURE") || 
                   result.contains("non-retryable") || 
                   result.contains("INVALID_INPUT"));
    }
    
    @Test
    public void testActivityCancellation(TestWorkflowEnvironment testEnv, Worker worker) throws Exception {
        worker.registerActivitiesImplementations(new FaultyActivityImpl());
        testEnv.start();
        
        WorkflowClient client = testEnv.getWorkflowClient();
        ErrorHandlingWorkflow workflow = client.newWorkflowStub(
            ErrorHandlingWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .setWorkflowId("cancellation-test")
                .build()
        );
        
        // Start workflow asynchronously
        CompletableFuture<String> future = WorkflowClient.execute(
            workflow::execute, "CANCELLATION", "LONG_RUNNING"
        );
        
        // Let it run for a bit
        testEnv.sleep(Duration.ofMillis(500));
        
        // Cancel the workflow
        WorkflowStub stub = WorkflowStub.fromTyped(workflow);
        stub.cancel();
        
        // Expect workflow failure with cancellation
        WorkflowFailedException ex = assertThrows(WorkflowFailedException.class, () -> {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof WorkflowFailedException) {
                    throw (WorkflowFailedException) e.getCause();
                }
                throw e;
            }
        });
        
        // Verify it was cancelled
        assertTrue(ex.getCause() instanceof CanceledFailure);
    }
    
    @Test
    public void testActivityHeartbeatTimeout(TestWorkflowEnvironment testEnv, Worker worker) {
        // Create a custom activity that doesn't send heartbeats
        worker.registerActivitiesImplementations(new FaultyActivityImpl() {
            @Override
            public void processWithHeartbeat(String input, int iterations) {
                // Don't send any heartbeats, just do work
                // The heartbeat timeout (2 seconds) will expire before we complete
                for (int i = 0; i < 10; i++) {
                    try {
                        Thread.sleep(500); // Sleep in small increments
                    } catch (InterruptedException e) {
                        return; // Exit gracefully if interrupted
                    }
                }
            }
        });
        testEnv.start();
        
        ErrorHandlingWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            ErrorHandlingWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .build()
        );
        
        // This should fail with workflow failure (which wraps the activity failure)
        WorkflowFailedException ex = assertThrows(WorkflowFailedException.class, () -> {
            workflow.execute("CANCELLATION", "TIMEOUT_TEST");
        });
        
        // Verify it was due to activity failure caused by timeout
        assertTrue(ex.getCause() instanceof ActivityFailure);
        ActivityFailure activityFailure = (ActivityFailure) ex.getCause();
        
        // Check that the activity failure was due to heartbeat timeout
        assertTrue(activityFailure.getCause() instanceof TimeoutFailure || 
                   ex.getMessage().contains("timed out"));
    }
}