# Temporal Java Message Passing

This document provides a comprehensive guide to message passing patterns in Temporal Java workflows, covering signals, queries, updates, and best practices for workflow communication.

## Overview

Message passing in Temporal enables communication between workflows and external systems through three primary mechanisms:

- **Queries**: Synchronous read-only operations for retrieving workflow state
- **Signals**: Asynchronous messages that can modify workflow state
- **Updates**: Synchronous, trackable requests that can both read and modify state

## Signal Methods

### Purpose and Characteristics

Signals are asynchronous messages that can change workflow state but cannot return values. They are ideal for triggering state changes or providing external input to running workflows.

**Key Properties:**
- Asynchronous execution
- Can modify workflow state
- Cannot return values
- Can perform blocking operations
- Use `@SignalMethod` annotation

### Signal Method Implementation

```java
@WorkflowInterface
public interface OrderWorkflow {
    @WorkflowMethod
    void processOrder(OrderRequest request);
    
    @SignalMethod
    void cancelOrder();
    
    @SignalMethod
    void updateShippingAddress(String newAddress);
    
    @SignalMethod
    void failAtStep(String step);
}

@WorkflowImpl
public class OrderWorkflowImpl implements OrderWorkflow {
    private boolean cancelled = false;
    private String shippingAddress;
    private String failureStep;
    
    @Override
    public void cancelOrder() {
        this.cancelled = true;
        // Can perform blocking operations
        Workflow.await(() -> isProcessingComplete());
    }
    
    @Override
    public void updateShippingAddress(String newAddress) {
        this.shippingAddress = newAddress;
    }
    
    @Override
    public void failAtStep(String step) {
        this.failureStep = step;
    }
}
```

### Signal Usage Patterns

**External Signal Sending:**
```java
// From client code
OrderWorkflow workflow = client.newWorkflowStub(OrderWorkflow.class, workflowId);
workflow.cancelOrder();
workflow.updateShippingAddress("123 New Street, City, State");
```

**Conditional Waiting with Signals:**
```java
@Override
public void processOrder(OrderRequest request) {
    // Wait for external approval signal
    Workflow.await(() -> approvalReceived || cancelled);
    
    if (cancelled) {
        return;
    }
    
    // Continue processing...
}
```

## Query Methods

### Purpose and Characteristics

Queries provide synchronous read-only access to workflow state without modifying it. They must return quickly and cannot perform blocking operations.

**Key Properties:**
- Synchronous execution
- Read-only operations
- Cannot modify workflow state
- Must return quickly (no blocking)
- Use `@QueryMethod` annotation

### Query Method Implementation

```java
@WorkflowInterface
public interface OrderWorkflow {
    @WorkflowMethod
    void processOrder(OrderRequest request);
    
    @QueryMethod
    String getOrderStatus();
    
    @QueryMethod
    List<String> getCompletedSteps();
    
    @QueryMethod
    OrderSummary getOrderSummary();
}

@WorkflowImpl
public class OrderWorkflowImpl implements OrderWorkflow {
    private String currentStatus = "PENDING";
    private List<String> completedSteps = new ArrayList<>();
    private OrderRequest orderRequest;
    
    @Override
    public String getOrderStatus() {
        return currentStatus;
    }
    
    @Override
    public List<String> getCompletedSteps() {
        return new ArrayList<>(completedSteps); // Return copy for safety
    }
    
    @Override
    public OrderSummary getOrderSummary() {
        return new OrderSummary(
            orderRequest.getOrderId(),
            currentStatus,
            completedSteps.size(),
            Workflow.currentTimeMillis()
        );
    }
}
```

### Query Usage Patterns

**External Query Execution:**
```java
// From client code
OrderWorkflow workflow = client.newWorkflowStub(OrderWorkflow.class, workflowId);
String status = workflow.getOrderStatus();
List<String> steps = workflow.getCompletedSteps();
```

**Query Best Practices:**
- Keep query methods lightweight and fast
- Return immutable copies of collections
- Avoid complex computations in query methods
- Use queries for monitoring and debugging workflow state

## Update Methods

### Purpose and Characteristics

Updates provide synchronous, trackable requests that can both read and modify workflow state. They can return values and support optional validation.

**Key Properties:**
- Synchronous execution with return values
- Can modify workflow state
- Trackable through workflow history
- Support optional validation
- Use `@UpdateMethod` annotation
- Optional `@UpdateValidatorMethod` for validation

### Update Method Implementation

```java
@WorkflowInterface
public interface LanguageWorkflow {
    @WorkflowMethod
    void run();
    
    @UpdateMethod
    Language setLanguage(Language language);
    
    @UpdateValidatorMethod(updateName = "setLanguage")
    void validateLanguage(Language language);
    
    @QueryMethod
    Language getCurrentLanguage();
}

@WorkflowImpl
public class LanguageWorkflowImpl implements LanguageWorkflow {
    private Language language = Language.ENGLISH;
    private Map<Language, String> greetings = new HashMap<>();
    private GreetingActivity activity;
    
    @Override
    public void run() {
        activity = Workflow.newActivityStub(GreetingActivity.class);
        // Workflow logic...
    }
    
    @Override
    public void validateLanguage(Language language) {
        if (language == null) {
            throw new IllegalArgumentException("Language cannot be null");
        }
        if (!Language.isSupported(language)) {
            throw ApplicationFailure.newFailure(
                "Unsupported language: " + language, 
                "INVALID_LANGUAGE"
            );
        }
    }
    
    @Override
    public Language setLanguage(Language language) {
        // Validation already performed by validator
        
        // Load greeting if not cached
        if (!greetings.containsKey(language)) {
            String greeting = activity.greetingService(language);
            if (greeting == null) {
                throw ApplicationFailure.newFailure(
                    "Failed to load greeting for language", 
                    "GREETING_LOAD_ERROR"
                );
            }
            greetings.put(language, greeting);
        }
        
        Language previousLanguage = this.language;
        this.language = language;
        return previousLanguage;
    }
    
    @Override
    public Language getCurrentLanguage() {
        return language;
    }
}
```

### Update Usage Patterns

**External Update Execution:**
```java
// From client code
LanguageWorkflow workflow = client.newWorkflowStub(LanguageWorkflow.class, workflowId);
try {
    Language previous = workflow.setLanguage(Language.SPANISH);
    System.out.println("Changed from " + previous + " to Spanish");
} catch (WorkflowUpdateException e) {
    System.err.println("Update failed: " + e.getMessage());
}
```

**Update with Validation:**
```java
@Override
public void validateUpdateQuantity(int newQuantity) {
    if (newQuantity < 0) {
        throw new IllegalArgumentException("Quantity cannot be negative");
    }
    if (newQuantity > maxAllowedQuantity) {
        throw ApplicationFailure.newFailure(
            "Quantity exceeds maximum allowed", 
            "QUANTITY_EXCEEDED"
        );
    }
}

@Override
public int updateQuantity(int newQuantity) {
    int oldQuantity = this.quantity;
    this.quantity = newQuantity;
    
    // Trigger activity to update external systems
    activity.updateInventory(orderId, newQuantity);
    
    return oldQuantity;
}
```

## Message Handlers and Validation

### Handler Execution Model

Message handlers (signals, queries, updates) execute concurrently with the main workflow method and with each other. This requires careful consideration of thread safety and state consistency.

**Concurrency Characteristics:**
- Handlers run concurrently with workflow method
- Multiple handlers can execute simultaneously
- Use `WorkflowLock` for synchronization when needed
- Use `Workflow.isEveryHandlerFinished()` to wait for completion

### Thread Safety Patterns

**Using WorkflowLock for Synchronization:**
```java
@WorkflowImpl
public class ThreadSafeWorkflowImpl implements ThreadSafeWorkflow {
    private final WorkflowLock lock = Workflow.newWorkflowLock();
    private int counter = 0;
    private List<String> items = new ArrayList<>();
    
    @Override
    public void increment() {
        lock.lock();
        try {
            counter++;
            items.add("Item " + counter);
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public int getCounter() {
        lock.lock();
        try {
            return counter;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void processWorkflow() {
        // Wait for all handlers to complete before ending
        Workflow.await(() -> Workflow.isEveryHandlerFinished());
    }
}
```

### Validation Strategies

**Comprehensive Update Validation:**
```java
@UpdateValidatorMethod(updateName = "updateOrderStatus")
public void validateStatusUpdate(String newStatus) {
    // Null check
    if (newStatus == null || newStatus.trim().isEmpty()) {
        throw new IllegalArgumentException("Status cannot be null or empty");
    }
    
    // Valid status check
    if (!OrderStatus.isValid(newStatus)) {
        throw ApplicationFailure.newFailure(
            "Invalid status: " + newStatus,
            "INVALID_STATUS"
        );
    }
    
    // State transition validation
    if (!OrderStatus.canTransition(currentStatus, newStatus)) {
        throw ApplicationFailure.newFailure(
            "Invalid transition from " + currentStatus + " to " + newStatus,
            "INVALID_TRANSITION"
        );
    }
    
    // Business rule validation
    if (newStatus.equals("SHIPPED") && shippingAddress == null) {
        throw ApplicationFailure.newFailure(
            "Cannot ship order without shipping address",
            "MISSING_SHIPPING_ADDRESS"
        );
    }
}
```

## Best Practices for Workflow Communication

### Design Principles

**1. Handler Design:**
```java
// Good: Use single class with multiple fields
public class OrderUpdateRequest {
    private String orderId;
    private int quantity;
    private String priority;
    // getters/setters
}

@SignalMethod
void updateOrder(OrderUpdateRequest request);

// Avoid: Multiple primitive parameters
@SignalMethod
void updateOrder(String orderId, int quantity, String priority, ...);
```

**2. Serialization Safety:**
```java
// Ensure all parameters are serializable
public class WorkflowState implements Serializable {
    private final String status;
    private final List<String> completedSteps;
    private final Instant lastUpdated;
    
    // Immutable design preferred
    public WorkflowState(String status, List<String> steps, Instant updated) {
        this.status = status;
        this.completedSteps = List.copyOf(steps);
        this.lastUpdated = updated;
    }
}
```

**3. Idempotency:**
```java
@SignalMethod
public void processPayment(String paymentId) {
    // Make handler idempotent
    if (processedPayments.contains(paymentId)) {
        return; // Already processed
    }
    
    // Process payment
    PaymentResult result = activity.processPayment(paymentId);
    processedPayments.add(paymentId);
    
    if (result.isSuccessful()) {
        currentStatus = "PAID";
    }
}
```

### Error Handling Patterns

**Client-Side Error Handling:**
```java
public class WorkflowMessageService {
    
    public void sendSignalSafely(String workflowId, Runnable signalAction) {
        try {
            signalAction.run();
        } catch (WorkflowNotFoundException e) {
            logger.warn("Workflow not found: {}", workflowId);
            // Handle workflow not found
        } catch (Exception e) {
            logger.error("Failed to send signal to workflow {}", workflowId, e);
            // Handle other errors
        }
    }
    
    public <T> Optional<T> queryWorkflowSafely(String workflowId, Supplier<T> queryAction) {
        try {
            return Optional.of(queryAction.get());
        } catch (WorkflowNotFoundException e) {
            logger.warn("Workflow not found for query: {}", workflowId);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Query failed for workflow {}", workflowId, e);
            return Optional.empty();
        }
    }
    
    public <T> T updateWorkflowWithRetry(String workflowId, Supplier<T> updateAction) {
        int attempts = 0;
        int maxAttempts = 3;
        
        while (attempts < maxAttempts) {
            try {
                return updateAction.get();
            } catch (WorkflowUpdateException e) {
                if (e.getCause() instanceof ApplicationFailure) {
                    // Business logic error - don't retry
                    throw e;
                }
                
                attempts++;
                if (attempts >= maxAttempts) {
                    throw e;
                }
                
                // Wait before retry
                try {
                    Thread.sleep(1000 * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
        
        throw new RuntimeException("Should not reach here");
    }
}
```

### Advanced Communication Patterns

**1. Workflow-to-Workflow Communication:**
```java
@WorkflowImpl
public class ParentWorkflowImpl implements ParentWorkflow {
    
    @Override
    public void orchestrateChildWorkflows() {
        // Start child workflows
        ChildWorkflow child1 = Workflow.newChildWorkflowStub(ChildWorkflow.class);
        ChildWorkflow child2 = Workflow.newChildWorkflowStub(ChildWorkflow.class);
        
        // Start asynchronously
        Promise<String> result1 = Async.function(child1::process, "data1");
        Promise<String> result2 = Async.function(child2::process, "data2");
        
        // Send signals to children based on progress
        Promise.allOf(result1, result2).thenApply((results) -> {
            child1.notifyCompletion("All tasks completed");
            child2.notifyCompletion("All tasks completed");
            return null;
        });
    }
}
```

**2. Event-Driven State Management:**
```java
@WorkflowImpl
public class EventDrivenWorkflowImpl implements EventDrivenWorkflow {
    private WorkflowState state = WorkflowState.INITIAL;
    private final Queue<WorkflowEvent> eventQueue = new LinkedList<>();
    
    @Override
    public void run() {
        while (!isComplete()) {
            // Process queued events
            while (!eventQueue.isEmpty()) {
                WorkflowEvent event = eventQueue.poll();
                handleEvent(event);
            }
            
            // Wait for new events or timeout
            Workflow.await(
                Duration.ofMinutes(1),
                () -> !eventQueue.isEmpty() || isComplete()
            );
        }
    }
    
    @SignalMethod
    public void handleExternalEvent(WorkflowEvent event) {
        eventQueue.offer(event);
    }
    
    private void handleEvent(WorkflowEvent event) {
        WorkflowState newState = state.transition(event);
        if (newState != state) {
            onStateChange(state, newState);
            state = newState;
        }
    }
}
```

### Testing Message Passing

**Unit Testing with TestWorkflowEnvironment:**
```java
@Test
public void testSignalHandling() {
    TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
    
    try {
        WorkflowClient client = testEnv.getWorkflowClient();
        WorkerFactory factory = testEnv.getWorkerFactory();
        Worker worker = factory.newWorker("test-queue");
        
        worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
        
        testEnv.start();
        
        // Start workflow
        OrderWorkflow workflow = client.newWorkflowStub(
            OrderWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId("test-order")
                .setTaskQueue("test-queue")
                .build()
        );
        
        WorkflowClient.start(workflow::processOrder, new OrderRequest("ORDER-123"));
        
        // Send signal
        workflow.cancelOrder();
        
        // Query state
        String status = workflow.getOrderStatus();
        assertEquals("CANCELLED", status);
        
    } finally {
        testEnv.close();
    }
}
```

**Integration Testing with Time Skipping:**
```java
@Test
public void testUpdateWithTimeSkipping() {
    // Start workflow
    TimedWorkflow workflow = client.newWorkflowStub(TimedWorkflow.class, workflowId);
    WorkflowClient.start(workflow::runWithTimeout);
    
    // Skip forward in time
    testEnv.sleep(Duration.ofMinutes(30));
    
    // Send update
    String result = workflow.updateConfiguration("new-config");
    assertEquals("Configuration updated", result);
    
    // Verify state
    String config = workflow.getCurrentConfiguration();
    assertEquals("new-config", config);
}
```

## Summary

Message passing in Temporal Java provides powerful mechanisms for workflow communication:

- **Signals** for asynchronous state changes
- **Queries** for synchronous read-only operations  
- **Updates** for synchronous, trackable state modifications

Key considerations:
- Handle concurrency carefully with appropriate synchronization
- Implement proper validation for updates
- Design handlers to be idempotent when possible
- Use appropriate error handling patterns
- Test message passing thoroughly with both unit and integration tests

These patterns enable robust, scalable workflow communication while maintaining the reliability and observability that Temporal provides.