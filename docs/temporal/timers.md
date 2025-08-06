# Temporal Java Timers and Time-Based Workflow Orchestration

## Overview

Temporal Timers provide a robust, durable mechanism for controlling time-based operations in workflows. Unlike traditional thread-based sleeping, Temporal timers are:

- **Persistent**: They survive Worker restarts and Temporal Service downtime
- **Lightweight**: Can run millions of timers off a single Worker
- **Durable**: Guaranteed execution even during service interruptions
- **Scalable**: Support long durations (seconds to months/years)

## Core Timer Concepts

### Workflow.sleep() vs Thread.sleep()

**❌ Never use Thread.sleep() in workflows:**
```java
// WRONG - Blocks worker threads and breaks determinism
Thread.sleep(5000); // Don't do this!
```

**✅ Always use Workflow.sleep():**
```java
// CORRECT - Durable, non-blocking timer
import static io.temporal.workflow.Workflow.sleep;

@WorkflowMethod
public void processOrder() {
    // Process payment
    processPayment();
    
    // Wait 30 seconds for payment confirmation
    sleep(Duration.ofSeconds(30));
    
    // Continue with order fulfillment
    fulfillOrder();
}
```

### Key Differences

| Feature | Thread.sleep() | Workflow.sleep() |
|---------|----------------|------------------|
| **Durability** | ❌ Lost on restart | ✅ Survives restarts |
| **Worker Threads** | ❌ Blocks threads | ✅ Non-blocking |
| **Determinism** | ❌ Non-deterministic | ✅ Deterministic |
| **Duration** | ❌ Process lifetime | ✅ Unlimited duration |
| **Resource Usage** | ❌ High overhead | ✅ Minimal overhead |

## Timer Patterns and Use Cases

### 1. Simple Delay Pattern
```java
@WorkflowMethod
public void delayedProcessing() {
    // Wait 5 minutes before processing
    sleep(Duration.ofMinutes(5));
    processDelayedTask();
}
```

### 2. Retry with Exponential Backoff
```java
@WorkflowMethod
public void retryWithBackoff() {
    int attempt = 0;
    int maxAttempts = 5;
    
    while (attempt < maxAttempts) {
        try {
            performOperation();
            return; // Success
        } catch (Exception e) {
            attempt++;
            if (attempt >= maxAttempts) {
                throw e;
            }
            
            // Exponential backoff: 1s, 2s, 4s, 8s, 16s
            Duration backoff = Duration.ofSeconds((long) Math.pow(2, attempt));
            sleep(backoff);
        }
    }
}
```

### 3. Time-Based Workflow Control
```java
@WorkflowMethod
public void timeBasedOrchestration() {
    // Start background processing
    Async.procedure(this::backgroundTask);
    
    // Wait for business hours (9 AM)
    LocalTime now = Workflow.getInfo().getCurrentLocalDateTime().toLocalTime();
    if (now.isBefore(LocalTime.of(9, 0))) {
        Duration waitTime = Duration.between(now, LocalTime.of(9, 0));
        sleep(waitTime);
    }
    
    // Process during business hours
    processDuringBusinessHours();
}
```

### 4. Periodic Processing with Continue-As-New
```java
@WorkflowMethod
public void periodicMonitoring() {
    int iterations = 0;
    int maxIterations = 100; // Prevent history growth
    
    while (iterations < maxIterations) {
        // Perform monitoring task
        monitorSystem();
        
        // Wait 1 hour before next check
        sleep(Duration.ofHours(1));
        iterations++;
    }
    
    // Continue as new to reset history
    Workflow.continueAsNew();
}
```

## Timeout Configurations

### Activity Timeouts

Temporal provides four types of activity timeouts:

#### 1. Start-To-Close Timeout (Required)
```java
@ActivityOptions(
    startToCloseTimeout = "PT30S" // Always set this!
)
PaymentActivity paymentActivity;

@WorkflowMethod
public void processPayment() {
    // Activity will timeout after 30 seconds of execution
    paymentActivity.processPayment(amount);
}
```

#### 2. Schedule-To-Close Timeout (With Retries)
```java
@ActivityOptions(
    startToCloseTimeout = "PT30S",
    scheduleToCloseTimeout = "PT5M", // Total time including retries
    retryOptions = @RetryOptions(
        maximumAttempts = 3,
        backoffCoefficient = 2.0
    )
)
PaymentActivity paymentActivity;
```

#### 3. Heartbeat Timeout (Long-Running Activities)
```java
@ActivityOptions(
    startToCloseTimeout = "PT10M",
    heartbeatTimeout = "PT30S" // Heartbeat every 30 seconds
)
DataProcessingActivity dataActivity;

// In the activity implementation
public void processLargeDataset(List<Data> data) {
    for (int i = 0; i < data.size(); i++) {
        processItem(data.get(i));
        
        // Send heartbeat every 100 items
        if (i % 100 == 0) {
            Activity.getExecutionContext().heartbeat(i);
        }
    }
}
```

#### 4. Schedule-To-Start Timeout (Rarely Used)
```java
@ActivityOptions(
    startToCloseTimeout = "PT30S",
    scheduleToStartTimeout = "PT1M" // Max time in task queue
)
PaymentActivity paymentActivity;
```

### Workflow Timeouts

#### Workflow Execution Timeout
```java
@WorkflowOptions(
    workflowExecutionTimeout = "PT1H" // Total workflow duration
)
public interface OrderWorkflow {
    @WorkflowMethod
    void processOrder(OrderRequest request);
}
```

#### Workflow Run Timeout
```java
@WorkflowOptions(
    workflowRunTimeout = "PT30M" // Single workflow run duration
)
public interface OrderWorkflow {
    @WorkflowMethod
    void processOrder(OrderRequest request);
}
```

## Scheduled Workflows and Cron Expressions

### Schedule API (Recommended)

#### Creating a Schedule
```java
// Create schedule client
ScheduleClient scheduleClient = ScheduleClient.newInstance(
    WorkflowServiceStubs.newLocalServiceStub());

// Define the schedule
Schedule schedule = Schedule.newBuilder()
    .setAction(
        ScheduleActionStartWorkflow.newBuilder()
            .setWorkflowType(ReportGenerationWorkflow.class)
            .setArguments("monthly-report")
            .setOptions(
                WorkflowOptions.newBuilder()
                    .setWorkflowId("monthly-report-" + System.currentTimeMillis())
                    .setTaskQueue("report-queue")
                    .build())
            .build())
    .setSpec(
        ScheduleSpec.newBuilder()
            .setCronExpressions(Arrays.asList("0 9 1 * *")) // 9 AM on 1st of each month
            .setTimeZone("America/New_York")
            .build())
    .build();

// Create the schedule
ScheduleHandle handle = scheduleClient.createSchedule(
    "monthly-report-schedule", 
    schedule,
    ScheduleOptions.newBuilder().build());
```

#### Schedule Management Operations
```java
// Pause schedule
handle.pause("Maintenance window");

// Resume schedule
handle.unpause("Maintenance complete");

// Trigger immediately
handle.trigger(ScheduleTriggerInput.newBuilder()
    .setOverlapPolicy(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_ALLOW_ALL)
    .build());

// Update schedule
handle.update((ScheduleUpdateInput input) -> {
    Schedule.Builder builder = input.getDescription().getSchedule().toBuilder();
    builder.getSpecBuilder().setCronExpressions(Arrays.asList("0 6 * * *")); // Change to 6 AM
    return new ScheduleUpdate(builder.build());
});

// Delete schedule
handle.delete();
```

### Cron Expressions (Legacy Approach)

#### Basic Cron Format
```
┌───────────── minute (0 - 59)
│ ┌───────────── hour (0 - 23)
│ │ ┌───────────── day of month (1 - 31)
│ │ │ ┌───────────── month (1 - 12)
│ │ │ │ ┌───────────── day of week (0 - 6) (Sunday to Saturday)
│ │ │ │ │
* * * * *
```

#### Common Cron Examples
```java
// Every minute
WorkflowOptions.newBuilder().setCronSchedule("* * * * *")

// Every hour at minute 0
WorkflowOptions.newBuilder().setCronSchedule("0 * * * *")

// Daily at 2:30 AM
WorkflowOptions.newBuilder().setCronSchedule("30 2 * * *")

// Weekly on Sundays at 3:00 AM
WorkflowOptions.newBuilder().setCronSchedule("0 3 * * 0")

// Monthly on the 1st at 9:00 AM
WorkflowOptions.newBuilder().setCronSchedule("0 9 1 * *")

// Weekdays at 6:00 PM
WorkflowOptions.newBuilder().setCronSchedule("0 18 * * 1-5")
```

#### Implementing Cron Workflows
```java
@WorkflowInterface
public interface ScheduledReportWorkflow {
    @WorkflowMethod
    void generateReport();
}

@Component
public class ScheduledReportWorkflowImpl implements ScheduledReportWorkflow {
    
    @ActivityOptions(startToCloseTimeout = "PT5M")
    private ReportActivity reportActivity;
    
    @Override
    public void generateReport() {
        // Get current execution info
        WorkflowInfo info = Workflow.getInfo();
        
        // Generate timestamp-based report ID
        String reportId = "report-" + info.getWorkflowId() + "-" + 
                         Instant.now().getEpochSecond();
        
        // Generate and send report
        ReportData data = reportActivity.generateReportData();
        reportActivity.sendReport(reportId, data);
        
        // Log completion
        Workflow.getLogger(ScheduledReportWorkflowImpl.class)
               .info("Report {} generated at {}", reportId, Instant.now());
    }
}
```

### Time Zone Handling

#### Setting Time Zones in Schedules
```java
Schedule schedule = Schedule.newBuilder()
    .setSpec(
        ScheduleSpec.newBuilder()
            .setCronExpressions(Arrays.asList("0 9 * * 1-5")) // 9 AM weekdays
            .setTimeZone("America/New_York") // EST/EDT
            .build())
    .build();
```

#### Daylight Saving Time Considerations
```java
// Schedule adjusts automatically for DST
Schedule schedule = Schedule.newBuilder()
    .setSpec(
        ScheduleSpec.newBuilder()
            .setCronExpressions(Arrays.asList("0 14 * * *")) // 2 PM local time
            .setTimeZone("Europe/London") // Handles GMT/BST transition
            .build())
    .build();
```

## Best Practices for Timer Usage

### 1. Timer Duration Guidelines

```java
// ✅ Good: Appropriate timer durations
sleep(Duration.ofSeconds(30));    // Short delays
sleep(Duration.ofMinutes(15));    // Medium delays  
sleep(Duration.ofHours(2));       // Long delays
sleep(Duration.ofDays(7));        // Very long delays

// ❌ Avoid: Extremely short timers that might impact performance
sleep(Duration.ofMillis(100));    // Too granular
```

### 2. Use Timers for Business Logic, Not Technical Concerns

```java
// ✅ Good: Business-driven delays
@WorkflowMethod
public void processLoan() {
    submitApplication();
    
    // Wait for standard processing time
    sleep(Duration.ofDays(3));
    
    checkApprovalStatus();
}

// ❌ Bad: Using timers for retry logic (use RetryOptions instead)
@WorkflowMethod
public void badRetryPattern() {
    for (int i = 0; i < 3; i++) {
        try {
            unreliableOperation();
            return;
        } catch (Exception e) {
            sleep(Duration.ofSeconds(5)); // Use RetryOptions instead
        }
    }
}
```

### 3. Combine Timers with Signals for Flexible Control

```java
@WorkflowInterface
public interface FlexibleTimerWorkflow {
    @WorkflowMethod
    void processWithDelay();
    
    @SignalMethod
    void skipDelay();
    
    @QueryMethod
    String getStatus();
}

public class FlexibleTimerWorkflowImpl implements FlexibleTimerWorkflow {
    private boolean skipDelaySignal = false;
    private String status = "STARTED";
    
    @Override
    public void processWithDelay() {
        status = "WAITING";
        
        // Wait for signal or timeout
        boolean signalReceived = Workflow.await(
            Duration.ofMinutes(30),
            () -> skipDelaySignal
        );
        
        if (signalReceived) {
            status = "DELAY_SKIPPED";
        } else {
            status = "DELAY_COMPLETED";
        }
        
        // Continue processing
        processNextStep();
        status = "COMPLETED";
    }
    
    @Override
    public void skipDelay() {
        skipDelaySignal = true;
    }
    
    @Override
    public String getStatus() {
        return status;
    }
}
```

### 4. Handle Long-Running Workflows with Continue-As-New

```java
@WorkflowMethod
public void longRunningMonitor() {
    int maxIterations = 1000; // Prevent unbounded history
    
    for (int i = 0; i < maxIterations; i++) {
        // Perform monitoring
        monitorSystemHealth();
        
        // Wait before next check
        sleep(Duration.ofMinutes(5));
        
        // Check if we should continue as new
        if (i > 0 && i % 100 == 0) {
            // Continue as new every 100 iterations to limit history size
            Workflow.continueAsNew(i); // Pass current iteration count
        }
    }
}
```

### 5. Testing Timer-Based Workflows

```java
@Test
public void testTimerWorkflow() {
    TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
    
    try {
        // Create workflow stub
        TimerWorkflow workflow = testEnv.newWorkflowStub(TimerWorkflow.class);
        
        // Start workflow asynchronously
        WorkflowClient.start(workflow::processWithTimer);
        
        // Skip time in test environment
        testEnv.sleep(Duration.ofMinutes(30));
        
        // Verify workflow completed
        String result = workflow.getResult();
        assertEquals("COMPLETED", result);
        
    } finally {
        testEnv.close();
    }
}
```

### 6. Timer Performance Considerations

```java
// ✅ Efficient: Batch timer operations
@WorkflowMethod
public void efficientBatchProcessing(List<Task> tasks) {
    // Process all tasks first
    for (Task task : tasks) {
        processTask(task);
    }
    
    // Single timer for the entire batch
    sleep(Duration.ofMinutes(5));
    
    // Complete batch processing
    completeBatch(tasks);
}

// ❌ Inefficient: Timer per item
@WorkflowMethod
public void inefficientProcessing(List<Task> tasks) {
    for (Task task : tasks) {
        processTask(task);
        sleep(Duration.ofSeconds(1)); // Avoid this pattern
    }
}
```

## Common Timer Patterns in Production

### 1. Grace Period Pattern
```java
@WorkflowMethod
public void orderProcessingWithGracePeriod(OrderRequest order) {
    // Process order immediately
    String orderId = processOrder(order);
    
    // Grace period for cancellation
    boolean cancelled = Workflow.await(
        Duration.ofMinutes(15),
        () -> orderCancelled
    );
    
    if (cancelled) {
        cancelOrder(orderId);
    } else {
        fulfillOrder(orderId);
    }
}
```

### 2. Escalation Pattern
```java
@WorkflowMethod
public void supportTicketEscalation(TicketRequest ticket) {
    // Initial assignment
    assignToLevel1Support(ticket);
    
    // Wait for Level 1 resolution
    boolean resolved = Workflow.await(
        Duration.ofHours(4),
        () -> ticketResolved
    );
    
    if (!resolved) {
        // Escalate to Level 2
        assignToLevel2Support(ticket);
        
        // Wait for Level 2 resolution
        resolved = Workflow.await(
            Duration.ofHours(8),
            () -> ticketResolved
        );
        
        if (!resolved) {
            // Final escalation to management
            escalateToManagement(ticket);
        }
    }
}
```

### 3. Deadline Pattern
```java
@WorkflowMethod
public void deadlineBasedProcessing(ProcessingRequest request) {
    // Calculate deadline
    Instant deadline = request.getCreatedAt().plus(Duration.ofDays(7));
    Duration timeToDeadline = Duration.between(Instant.now(), deadline);
    
    // Start processing
    Async.procedure(() -> performProcessing(request));
    
    // Wait until deadline or completion
    boolean completed = Workflow.await(
        timeToDeadline,
        () -> processingCompleted
    );
    
    if (!completed) {
        // Handle deadline exceeded
        handleDeadlineExceeded(request);
    }
}
```

## Error Handling with Timers

### 1. Timeout with Fallback
```java
@WorkflowMethod
public void processWithFallback() {
    try {
        // Attempt primary processing with timeout
        boolean success = Workflow.await(
            Duration.ofMinutes(10),
            () -> primaryProcessingCompleted
        );
        
        if (!success) {
            // Primary processing timed out, use fallback
            performFallbackProcessing();
        }
        
    } catch (Exception e) {
        // Handle any processing errors
        handleProcessingError(e);
    }
}
```

### 2. Circuit Breaker Pattern
```java
@WorkflowMethod
public void circuitBreakerPattern() {
    int failures = 0;
    int maxFailures = 5;
    boolean circuitOpen = false;
    
    while (!completed) {
        if (circuitOpen) {
            // Wait before attempting to close circuit
            sleep(Duration.ofMinutes(5));
            circuitOpen = false; // Try to close circuit
            failures = 0; // Reset failure count
        }
        
        try {
            performOperation();
            completed = true;
            failures = 0; // Reset on success
            
        } catch (Exception e) {
            failures++;
            if (failures >= maxFailures) {
                circuitOpen = true;
            }
            
            if (!circuitOpen) {
                // Short delay before retry
                sleep(Duration.ofSeconds(30));
            }
        }
    }
}
```

This comprehensive guide covers all aspects of Temporal Java timers, from basic concepts to advanced patterns. Use these examples as a foundation for building robust, time-aware distributed applications with Temporal.