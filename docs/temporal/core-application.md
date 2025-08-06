# Temporal Java Core Application

## Overview

A Temporal Core Application is the foundation of distributed workflow orchestration using the Temporal platform. Core applications consist of three primary components: **Workflows**, **Activities**, and **Workers** that work together to execute reliable, durable, and scalable business processes.

The core philosophy centers around:
- **Deterministic execution** for Workflows
- **External system integration** through Activities
- **Reliable orchestration** via Workers
- **Clear separation of concerns** between business logic and infrastructure

## Core Components

### 1. Workflows

Workflows are the fundamental orchestration unit that defines the execution flow of your business process. They coordinate Activities and handle complex decision-making logic.

#### Key Characteristics

- **Deterministic execution**: Must produce the same result when replayed
- **Durable**: Automatically persisted and can resume from any point
- **Fault-tolerant**: Handle failures gracefully with retries and compensation
- **Long-running**: Can execute for days, weeks, or months

#### Workflow Interface Definition

```java
@WorkflowInterface
public interface GreetingWorkflow {
    @WorkflowMethod
    String getGreeting(String name);
    
    @QueryMethod
    String getStatus();
    
    @SignalMethod
    void updateGreeting(String newGreeting);
}
```

#### Workflow Implementation

```java
public class GreetingWorkflowImpl implements GreetingWorkflow {
    private final GreetingActivities activities = 
        Workflow.newActivityStub(GreetingActivities.class,
            ActivityOptions.newBuilder()
                .setScheduleToCloseTimeout(Duration.ofSeconds(10))
                .build());
    
    private String currentGreeting = "Hello";
    
    @Override
    public String getGreeting(String name) {
        return activities.composeGreeting(currentGreeting, name);
    }
    
    @Override
    public String getStatus() {
        return "Active";
    }
    
    @Override
    public void updateGreeting(String newGreeting) {
        this.currentGreeting = newGreeting;
    }
}
```

#### Workflow Constraints

**Must Never:**
- Use mutable global variables
- Call non-deterministic functions directly
- Use native Java threading (`Thread`, `ExecutorService`)
- Access system time with `System.currentTimeMillis()`
- Generate random numbers with `Math.random()`
- Access file systems or networks directly

**Must Always:**
- Use `Workflow.currentTimeMillis()` for time
- Use `Workflow.sleep()` for delays
- Use `Workflow.newRandom()` for randomness
- Interact with external systems through Activities only

### 2. Activities

Activities represent discrete, well-defined business operations that interact with external systems. They are the only components allowed to perform side effects.

#### Activity Interface Definition

```java
@ActivityInterface
public interface GreetingActivities {
    String composeGreeting(String greeting, String name);
    
    void sendNotification(String message);
    
    PaymentResult processPayment(PaymentRequest request);
}
```

#### Activity Implementation

```java
public class GreetingActivitiesImpl implements GreetingActivities {
    
    @Override
    public String composeGreeting(String greeting, String name) {
        // Can access external systems, databases, APIs
        return greeting + ", " + name + "!";
    }
    
    @Override
    public void sendNotification(String message) {
        // Example: Send email, SMS, push notification
        emailService.send(message);
    }
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // Can perform non-deterministic operations
        return paymentGateway.charge(request);
    }
}
```

#### Activity Best Practices

- **Thread-safe**: Activities can be called concurrently
- **Idempotent**: Same input should produce same output
- **Timeout aware**: Configure appropriate timeouts
- **Error handling**: Throw specific exceptions for different failure modes
- **Heartbeats**: Use for long-running activities

#### Activity Options Configuration

```java
ActivityOptions options = ActivityOptions.newBuilder()
    .setScheduleToCloseTimeout(Duration.ofMinutes(5))
    .setScheduleToStartTimeout(Duration.ofMinutes(1))
    .setStartToCloseTimeout(Duration.ofMinutes(4))
    .setHeartbeatTimeout(Duration.ofSeconds(30))
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(3)
        .setBackoffCoefficient(2.0)
        .setInitialInterval(Duration.ofSeconds(1))
        .build())
    .build();
```

### 3. Workers

Workers are responsible for executing Workflow and Activity code. They poll Task Queues for tasks and execute the corresponding implementations.

#### Worker Creation and Registration

```java
// Create WorkerFactory
WorkerFactory workerFactory = WorkerFactory.newInstance(workflowClient);

// Create Worker for specific Task Queue
Worker worker = workerFactory.newWorker("greeting-task-queue");

// Register Workflow implementations
worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);

// Register Activity implementations
worker.registerActivitiesImplementations(new GreetingActivitiesImpl());

// Start all registered Workers
workerFactory.start();
```

#### Worker Configuration

```java
WorkerOptions workerOptions = WorkerOptions.newBuilder()
    .setMaxConcurrentWorkflowTaskExecutions(100)
    .setMaxConcurrentActivityExecutions(200)
    .setWorkerActivationRateLimit(400.0)
    .build();

Worker worker = workerFactory.newWorker("task-queue", workerOptions);
```

## Configuration Patterns

### 1. Timeout Configuration

#### Workflow Timeouts

```java
WorkflowOptions options = WorkflowOptions.newBuilder()
    .setWorkflowExecutionTimeout(Duration.ofDays(1))
    .setWorkflowRunTimeout(Duration.ofHours(2))
    .setWorkflowTaskTimeout(Duration.ofSeconds(10))
    .build();
```

#### Activity Timeouts

| Timeout Type | Description | Use Case |
|--------------|-------------|----------|
| `ScheduleToCloseTimeout` | Total time for Activity | Overall deadline |
| `ScheduleToStartTimeout` | Time until Activity starts | Queue wait time |
| `StartToCloseTimeout` | Time to complete once started | Processing time |
| `HeartbeatTimeout` | Heartbeat interval | Long-running tasks |

### 2. Retry Policies

```java
RetryOptions retryOptions = RetryOptions.newBuilder()
    .setMaximumAttempts(5)
    .setInitialInterval(Duration.ofSeconds(1))
    .setMaximumInterval(Duration.ofMinutes(1))
    .setBackoffCoefficient(2.0)
    .setDoNotRetry(IllegalArgumentException.class.getName())
    .build();
```

### 3. Task Queue Configuration

```java
// Workflow execution
WorkflowOptions workflowOptions = WorkflowOptions.newBuilder()
    .setTaskQueue("priority-tasks")
    .setWorkflowId("unique-workflow-id")
    .build();

// Activity stub configuration
ActivityOptions activityOptions = ActivityOptions.newBuilder()
    .setTaskQueue("activity-specific-queue")
    .build();
```

## Key APIs and Interfaces

### 1. WorkflowClient

Primary interface for starting and managing Workflows:

```java
// Create client
WorkflowClient client = WorkflowClient.newInstance(
    WorkflowServiceStubs.newInstance());

// Start Workflow
GreetingWorkflow workflow = client.newWorkflowStub(
    GreetingWorkflow.class, workflowOptions);
    
// Execute Workflow
String result = workflow.getGreeting("World");

// Query Workflow
String status = workflow.getStatus();

// Signal Workflow
workflow.updateGreeting("Hi");
```

### 2. Workflow Context APIs

Available within Workflow implementations:

```java
// Time operations
long now = Workflow.currentTimeMillis();
Workflow.sleep(Duration.ofMinutes(5));

// Random operations
Random random = Workflow.newRandom();
int value = random.nextInt(100);

// Activity stubs
ActivityStub activities = Workflow.newActivityStub(ActivityInterface.class, options);

// Child Workflows
ChildWorkflow child = Workflow.newChildWorkflowStub(ChildWorkflow.class);

// Logging
Workflow.getLogger(MyWorkflow.class).info("Workflow started");
```

### 3. Activity Context APIs

Available within Activity implementations:

```java
// Get current activity info
ActivityInfo info = Activity.getExecutionContext().getInfo();

// Send heartbeats
Activity.getExecutionContext().heartbeat("progress update");

// Check for cancellation
if (Activity.getExecutionContext().isCancelRequested()) {
    throw new CancellationException("Activity cancelled");
}

// Get workflow info from activity
WorkflowInfo workflowInfo = Activity.getExecutionContext().getWorkflowInfo();
```

## Advanced Patterns

### 1. Async Activity Execution

```java
// In Workflow
Promise<String> promise1 = Async.function(activities::longRunningTask1);
Promise<String> promise2 = Async.function(activities::longRunningTask2);

// Wait for both to complete
String result1 = promise1.get();
String result2 = promise2.get();
```

### 2. Dynamic Workflows

```java
@WorkflowInterface
public interface DynamicWorkflow {
    @WorkflowMethod
    Object execute(Object[] args);
}

public class DynamicWorkflowImpl implements DynamicWorkflow {
    @Override
    public Object execute(Object[] args) {
        String workflowType = Workflow.getInfo().getWorkflowType();
        // Route based on workflow type
        return handleDynamicExecution(workflowType, args);
    }
}
```

### 3. Saga Pattern Implementation

```java
public class SagaWorkflowImpl implements SagaWorkflow {
    
    @Override
    public void processSaga(SagaRequest request) {
        Saga saga = new Saga(new Saga.Options.Builder()
            .setParallelCompensation(false)
            .build());
            
        try {
            saga.addCompensation(activities::compensateStep1);
            activities.step1(request.getStep1Data());
            
            saga.addCompensation(activities::compensateStep2);
            activities.step2(request.getStep2Data());
            
            saga.addCompensation(activities::compensateStep3);
            activities.step3(request.getStep3Data());
            
        } catch (Exception e) {
            saga.compensate();
            throw e;
        }
    }
}
```

### 4. Continue-As-New Pattern

```java
public class ContinuousWorkflowImpl implements ContinuousWorkflow {
    
    @Override
    public void processEvents(ProcessingState state) {
        while (state.shouldContinue()) {
            // Process events
            state = processNextBatch(state);
            
            // Continue as new to prevent history growth
            if (state.getEventCount() > 1000) {
                Workflow.continueAsNew(state);
                return; // This line never executes
            }
        }
    }
}
```

## Best Practices

### 1. Workflow Design

- **Single Responsibility**: One workflow per business process
- **Idempotent Operations**: Design for retries and replays
- **State Management**: Use signals and queries for external interaction
- **Error Handling**: Use appropriate exception types
- **Versioning**: Plan for workflow evolution

### 2. Activity Design

- **Atomic Operations**: Each activity should be a single unit of work
- **Timeout Configuration**: Set realistic timeouts
- **Heartbeats**: Use for long-running activities
- **Resource Management**: Clean up resources properly
- **Testing**: Make activities easily testable

### 3. Data Serialization

```java
// Use POJOs for workflow parameters
public class WorkflowInput {
    private String customerId;
    private BigDecimal amount;
    // ... getters and setters
}

// Avoid complex objects that may not serialize well
// Prefer immutable objects where possible
```

### 4. Error Handling Strategy

```java
// Workflow-level error handling
try {
    activities.riskyOperation();
} catch (ActivityFailure e) {
    if (e.getCause() instanceof ApplicationFailure) {
        ApplicationFailure af = (ApplicationFailure) e.getCause();
        if ("INSUFFICIENT_FUNDS".equals(af.getType())) {
            // Handle specific business error
            return handleInsufficientFunds();
        }
    }
    throw e; // Re-throw for retry
}
```

### 5. Testing Strategies

```java
@Test
public void testWorkflow() {
    TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
    Worker worker = testEnv.newWorker("test-task-queue");
    
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    worker.registerActivitiesImplementations(new TestGreetingActivities());
    
    testEnv.start();
    
    WorkflowClient client = testEnv.getWorkflowClient();
    GreetingWorkflow workflow = client.newWorkflowStub(
        GreetingWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("test-task-queue")
            .build());
    
    String result = workflow.getGreeting("Test");
    assertEquals("Hello, Test!", result);
    
    testEnv.close();
}
```

## Summary

Temporal Java Core Applications provide a robust foundation for building distributed, fault-tolerant systems through:

- **Workflows**: Orchestrate business processes with deterministic execution
- **Activities**: Perform side effects and interact with external systems
- **Workers**: Execute workflow and activity code reliably
- **Rich APIs**: Comprehensive set of tools for building complex applications
- **Best Practices**: Proven patterns for scalable, maintainable systems

The key to success with Temporal is understanding the constraints and capabilities of each component, properly configuring timeouts and retries, and designing for the distributed, asynchronous nature of the platform.