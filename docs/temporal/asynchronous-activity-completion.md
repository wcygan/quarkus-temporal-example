# Temporal Java Asynchronous Activity Completion

## Overview

Asynchronous Activity Completion is a powerful pattern in Temporal that allows an Activity Function to return without immediately completing the Activity Execution. This enables external systems or processes to control when and how an activity completes, making it ideal for integrating with third-party services, manual approval workflows, or long-running operations that need to be controlled externally.

The key concept is that instead of the activity completing when the function returns, it remains in a "waiting" state until explicitly completed via the Temporal Client API.

## When to Use Asynchronous Activity Completion

### Ideal Use Cases

1. **External System Integration**
   - Waiting for callbacks from third-party APIs
   - Integration with legacy systems that use polling
   - Webhook-based completion patterns

2. **Manual Approval Workflows**
   - Document approval processes
   - Human-in-the-loop operations
   - Administrative review steps

3. **Long-Running Operations**
   - File processing that takes hours/days
   - Machine learning model training
   - Large data migrations

4. **Event-Driven Processes**
   - Waiting for specific events to occur
   - Cross-system coordination
   - Multi-step approval chains

### When NOT to Use

- Simple synchronous operations
- Activities that can complete immediately
- Operations where you control the entire flow

## Core Concepts

### Activity Tokens

**Task Token**: A unique identifier that represents a specific Activity Execution. This token is used to complete the activity from external systems.

```java
ActivityExecutionContext context = Activity.getExecutionContext();
byte[] taskToken = context.getTaskToken();
```

### Manual Completion Control

**`doNotCompleteOnReturn()`**: This method tells Temporal not to automatically complete the activity when the function returns.

```java
context.doNotCompleteOnReturn();
```

## Implementation Patterns

### Pattern 1: Basic Asynchronous Completion

```java
@ActivityInterface
public interface AsyncGreetingActivity {
    String composeGreeting(String greeting, String name);
}

@ApplicationScoped
public class AsyncGreetingActivityImpl implements AsyncGreetingActivity {
    
    @Inject
    ActivityCompletionClient completionClient;
    
    @Override
    public String composeGreeting(String greeting, String name) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        byte[] taskToken = context.getTaskToken();
        
        // Start async processing
        ForkJoinPool.commonPool().execute(() -> 
            composeGreetingAsync(taskToken, greeting, name));
        
        // Prevent automatic completion
        context.doNotCompleteOnReturn();
        
        // Return value is ignored when doNotCompleteOnReturn() is called
        return "ignored";
    }
    
    private void composeGreetingAsync(byte[] taskToken, String greeting, String name) {
        try {
            // Simulate external processing
            Thread.sleep(5000);
            
            String result = greeting + " " + name + "!";
            
            // Complete the activity manually
            completionClient.complete(taskToken, result);
        } catch (Exception e) {
            // Complete with failure
            completionClient.completeExceptionally(taskToken, e);
        }
    }
}
```

### Pattern 2: External System Integration

```java
@ApplicationScoped
public class ExternalPaymentActivityImpl implements PaymentActivity {
    
    @Inject
    ActivityCompletionClient completionClient;
    
    @Inject
    ExternalPaymentService paymentService;
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        byte[] taskToken = context.getTaskToken();
        
        // Send to external system with callback
        paymentService.processPaymentWithCallback(
            request, 
            new PaymentCallback(taskToken, completionClient)
        );
        
        context.doNotCompleteOnReturn();
        return null; // Ignored
    }
    
    public static class PaymentCallback {
        private final byte[] taskToken;
        private final ActivityCompletionClient completionClient;
        
        public PaymentCallback(byte[] taskToken, ActivityCompletionClient completionClient) {
            this.taskToken = taskToken;
            this.completionClient = completionClient;
        }
        
        public void onSuccess(PaymentResult result) {
            completionClient.complete(taskToken, result);
        }
        
        public void onFailure(Exception error) {
            completionClient.completeExceptionally(taskToken, error);
        }
    }
}
```

### Pattern 3: Manual Approval Workflow

```java
@ApplicationScoped
public class DocumentApprovalActivityImpl implements DocumentApprovalActivity {
    
    @Inject
    ActivityCompletionClient completionClient;
    
    @Inject
    ApprovalService approvalService;
    
    @Override
    public ApprovalResult requestApproval(Document document) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        byte[] taskToken = context.getTaskToken();
        
        // Create approval request with task token
        ApprovalRequest approval = ApprovalRequest.builder()
            .document(document)
            .taskToken(taskToken)
            .requestedAt(Instant.now())
            .build();
        
        // Store approval request for later completion
        approvalService.createApprovalRequest(approval);
        
        context.doNotCompleteOnReturn();
        return null;
    }
}

// Separate service to handle approvals
@ApplicationScoped
public class ApprovalService {
    
    @Inject
    ActivityCompletionClient completionClient;
    
    public void approveDocument(String approvalId, String approverUserId) {
        ApprovalRequest request = findApprovalRequest(approvalId);
        
        ApprovalResult result = ApprovalResult.builder()
            .approved(true)
            .approverUserId(approverUserId)
            .approvedAt(Instant.now())
            .build();
        
        completionClient.complete(request.getTaskToken(), result);
    }
    
    public void rejectDocument(String approvalId, String approverUserId, String reason) {
        ApprovalRequest request = findApprovalRequest(approvalId);
        
        ApprovalResult result = ApprovalResult.builder()
            .approved(false)
            .approverUserId(approverUserId)
            .rejectionReason(reason)
            .rejectedAt(Instant.now())
            .build();
        
        completionClient.complete(request.getTaskToken(), result);
    }
}
```

## Heartbeating with Async Activities

When using asynchronous completion, you may still need to send heartbeats to indicate the activity is making progress and hasn't stalled.

### Heartbeat Implementation

```java
@ApplicationScoped
public class LongRunningAsyncActivityImpl implements LongRunningActivity {
    
    @Inject
    ActivityCompletionClient completionClient;
    
    @Override
    public ProcessingResult processLargeDataset(DatasetRequest request) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        byte[] taskToken = context.getTaskToken();
        
        // Start async processing with heartbeats
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        
        // Heartbeat every 30 seconds
        ScheduledFuture<?> heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                Activity.getExecutionContext().heartbeat("Processing...");
            } catch (Exception e) {
                // Activity may have been cancelled
            }
        }, 30, 30, TimeUnit.SECONDS);
        
        // Start actual processing
        CompletableFuture.supplyAsync(() -> {
            try {
                ProcessingResult result = performLongRunningTask(request);
                completionClient.complete(taskToken, result);
                return result;
            } catch (Exception e) {
                completionClient.completeExceptionally(taskToken, e);
                throw new RuntimeException(e);
            } finally {
                heartbeatTask.cancel(true);
                scheduler.shutdown();
            }
        });
        
        context.doNotCompleteOnReturn();
        return null;
    }
    
    private ProcessingResult performLongRunningTask(DatasetRequest request) {
        // Simulate long-running task with periodic heartbeats
        for (int i = 0; i < 100; i++) {
            try {
                Thread.sleep(1000); // Simulate work
                
                // Send heartbeat with progress
                Activity.getExecutionContext().heartbeat(
                    "Progress: " + (i + 1) + "/100"
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
        }
        
        return new ProcessingResult("Completed successfully");
    }
}
```

### Heartbeat Best Practices

1. **Regular Intervals**: Send heartbeats at regular intervals (typically 10-30 seconds)
2. **Progress Information**: Include meaningful progress data in heartbeats
3. **Cancellation Handling**: Check for cancellation in heartbeat handlers
4. **Resource Cleanup**: Properly clean up heartbeat timers

## Error Handling and Failure Scenarios

### Exception Handling Patterns

```java
@ApplicationScoped
public class RobustAsyncActivityImpl implements AsyncActivity {
    
    @Inject
    ActivityCompletionClient completionClient;
    
    @Override
    public String performAsyncOperation(OperationRequest request) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        byte[] taskToken = context.getTaskToken();
        
        CompletableFuture.supplyAsync(() -> {
            try {
                return doAsyncWork(request);
            } catch (BusinessException e) {
                // Business logic error - complete with application failure
                completionClient.completeExceptionally(taskToken, 
                    ApplicationFailure.newFailure(e.getMessage(), "BUSINESS_ERROR"));
                return null;
            } catch (TimeoutException e) {
                // Timeout - complete with timeout failure
                completionClient.completeExceptionally(taskToken,
                    ApplicationFailure.newFailure("Operation timed out", "TIMEOUT"));
                return null;
            } catch (Exception e) {
                // Unexpected error - complete with generic failure
                completionClient.completeExceptionally(taskToken, e);
                return null;
            }
        }).thenAccept(result -> {
            if (result != null) {
                completionClient.complete(taskToken, result);
            }
        });
        
        context.doNotCompleteOnReturn();
        return null;
    }
}
```

### Failure Types

1. **Application Failures**: Business logic errors that shouldn't retry
2. **Activity Task Failures**: Infrastructure errors that can retry
3. **Timeouts**: When operations exceed expected duration
4. **Cancellations**: When workflows are cancelled or activities are abandoned

### Retry Configuration

```java
@ActivityInterface
public interface AsyncActivityWithRetry {
    @ActivityMethod(
        name = "asyncOperation",
        activityOptions = @ActivityOptions(
            startToCloseTimeout = "PT10M", // 10 minutes total
            scheduleToCloseTimeout = "PT1H", // 1 hour including retries
            retryOptions = @RetryOptions(
                maximumAttempts = 3,
                backoffCoefficient = 2.0,
                initialInterval = "PT10S"
            )
        )
    )
    String performAsyncOperation(OperationRequest request);
}
```

## Testing Asynchronous Activities

### Unit Testing

```java
@Test
public void testAsyncActivityCompletion() {
    TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
    TestActivityEnvironment activityEnv = testEnv.newTestActivityEnvironment();
    
    // Register activity implementation
    activityEnv.registerActivitiesImplementations(new AsyncGreetingActivityImpl());
    
    AsyncGreetingActivity activity = activityEnv.newActivityStub(AsyncGreetingActivity.class);
    
    // Execute activity (will complete asynchronously)
    CompletableFuture<String> result = CompletableFuture.supplyAsync(() -> 
        activity.composeGreeting("Hello", "World"));
    
    // Activity should be waiting for completion
    assertFalse(result.isDone());
    
    // Complete manually (simulate external completion)
    // In real tests, you'd trigger the external system
    testEnv.sleep(Duration.ofSeconds(6)); // Wait for async completion
    
    assertTrue(result.isDone());
    assertEquals("Hello World!", result.get());
}
```

### Integration Testing

```java
@Test
public void testAsyncActivityIntegration() {
    WorkflowClient client = WorkflowClient.newInstance(service);
    
    TestWorkflow workflow = client.newWorkflowStub(TestWorkflow.class);
    
    // Start workflow execution
    WorkflowExecution execution = WorkflowClient.start(workflow::processWithAsync, "test");
    
    // Workflow should be waiting for activity completion
    String status = workflow.getStatus();
    assertEquals("WAITING_FOR_APPROVAL", status);
    
    // Simulate external completion
    approvalService.approveDocument(execution.getWorkflowId(), "approver123");
    
    // Wait for workflow completion
    String result = WorkflowStub.fromTyped(workflow).getResult(String.class);
    assertEquals("APPROVED", result);
}
```

## ActivityCompletionClient Configuration

### CDI Configuration

```java
@ApplicationScoped
public class TemporalConfig {
    
    @Produces
    @ApplicationScoped
    public WorkflowServiceStubs createWorkflowServiceStubs() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }
    
    @Produces
    @ApplicationScoped
    public WorkflowClient createWorkflowClient(WorkflowServiceStubs service) {
        return WorkflowClient.newInstance(service);
    }
    
    @Produces
    @ApplicationScoped
    public ActivityCompletionClient createActivityCompletionClient(WorkflowServiceStubs service) {
        return ActivityCompletionClient.newInstance(service);
    }
}
```

### Direct Instantiation

```java
WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
ActivityCompletionClient completionClient = ActivityCompletionClient.newInstance(service);
```

## Best Practices

### 1. Token Management
- Always store task tokens securely
- Include token validation in external systems
- Handle token expiration gracefully

### 2. Error Handling
- Use specific exception types for different failure scenarios
- Implement proper retry strategies
- Log failures for debugging

### 3. Resource Management
- Clean up threads and scheduled tasks
- Handle activity cancellation properly
- Avoid resource leaks in long-running processes

### 4. Monitoring
- Track async activity completion rates
- Monitor for stuck or abandoned activities
- Set up alerts for completion failures

### 5. Security
- Validate task tokens before completion
- Implement proper authorization checks
- Secure external completion endpoints

## Common Pitfalls

1. **Forgetting `doNotCompleteOnReturn()`**: Activity will complete immediately
2. **Not handling exceptions**: Unhandled exceptions leave activities stuck
3. **Token leakage**: Exposing task tokens inappropriately
4. **Resource leaks**: Not cleaning up threads/timers
5. **Missing heartbeats**: Activities may time out without progress indication

## Conclusion

Asynchronous Activity Completion is a powerful pattern for integrating Temporal workflows with external systems and long-running processes. By properly implementing task token management, error handling, and completion patterns, you can build robust, scalable workflow solutions that seamlessly integrate with your existing infrastructure.

The key to success is understanding when to use this pattern, properly managing the activity lifecycle, and implementing comprehensive error handling and monitoring strategies.