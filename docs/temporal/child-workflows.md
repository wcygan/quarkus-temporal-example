# Temporal Java Child Workflows

## Overview

Child Workflows are Workflow Executions initiated from within another Workflow (the parent). They provide a powerful mechanism for hierarchical workflow decomposition and complex business process orchestration.

### Key Characteristics

- **Event History Integration**: Child Workflow execution is logged in the parent's Event History
- **Execution Models**: Support both synchronous and asynchronous execution patterns
- **Communication**: Enable bidirectional communication between parent and child workflows
- **Lifecycle Management**: Parent can control child lifecycle through various close policies

## Starting Child Workflows

### Synchronous Execution

Synchronous child workflows block the parent until completion:

```java
public String getGreeting(String name) {
    GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class);
    // Blocking call - parent waits for child completion
    return child.composeGreeting("Hello", name);
}
```

**Use Cases:**
- Sequential processing where parent needs child results
- Simple parent-child coordination
- Error propagation from child to parent

### Asynchronous Execution

Asynchronous execution allows parent to continue while child runs:

```java
GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class);
Promise<String> greeting = Async.function(child::composeGreeting, "Hello", name);

// Parent can do other work here
doOtherWork();

// Get result when ready
String result = greeting.get();
```

**Use Cases:**
- Parallel processing
- Fire-and-forget operations
- Complex orchestration patterns

### Parallel Child Workflow Execution

Execute multiple child workflows concurrently:

```java
GreetingChild child1 = Workflow.newChildWorkflowStub(GreetingChild.class);
Promise<String> greeting1 = Async.function(child1::composeGreeting, "Hello", name);

GreetingChild child2 = Workflow.newChildWorkflowStub(GreetingChild.class);
Promise<String> greeting2 = Async.function(child2::composeGreeting, "Bye", name);

// Wait for both to complete
Promise.allOf(greeting1, greeting2).get();
```

### Untyped Child Workflows

For dynamic workflow execution:

```java
ChildWorkflowStub childUntyped = Workflow.newUntypedChildWorkflowStub(
    "GreetingChild", 
    ChildWorkflowOptions.newBuilder()
        .setWorkflowId("childWorkflow")
        .build()
);
Promise<String> greeting = childUntyped.executeAsync(String.class, String.class, "Hello", name);
```

## Child Workflow Configuration

### ChildWorkflowOptions

Configure child workflow behavior through comprehensive options:

```java
ChildWorkflowOptions options = ChildWorkflowOptions.newBuilder()
    .setWorkflowId("unique-child-id")
    .setTaskQueue("child-task-queue")
    .setWorkflowExecutionTimeout(Duration.ofMinutes(30))
    .setWorkflowRunTimeout(Duration.ofMinutes(10))
    .setWorkflowTaskTimeout(Duration.ofSeconds(30))
    .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON)
    .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE)
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(3)
        .setBackoffCoefficient(2.0)
        .build())
    .build();

GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class, options);
```

### Key Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `setWorkflowId()` | Unique identifier for child workflow | Auto-generated |
| `setTaskQueue()` | Task queue for child execution | Parent's task queue |
| `setWorkflowExecutionTimeout()` | Maximum time for entire execution | Unlimited |
| `setWorkflowRunTimeout()` | Maximum time for single run | Unlimited |
| `setParentClosePolicy()` | Behavior when parent closes | TERMINATE |
| `setRetryOptions()` | Retry configuration for failures | Default retry policy |

## Parent Close Policies

Control child workflow behavior when parent workflow completes or fails:

### PARENT_CLOSE_POLICY_TERMINATE (Default)
```java
.setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_TERMINATE)
```
- **Behavior**: Child workflows are terminated when parent closes
- **Use Case**: Tightly coupled parent-child relationships
- **Event**: Sends `ChildWorkflowExecutionTerminated` event

### PARENT_CLOSE_POLICY_ABANDON
```java
.setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON)
```
- **Behavior**: Child workflows continue independently after parent closes
- **Use Case**: Fire-and-forget operations, independent long-running processes
- **Event**: No termination event sent

### PARENT_CLOSE_POLICY_REQUEST_CANCEL
```java
.setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_REQUEST_CANCEL)
```
- **Behavior**: Child workflows receive cancellation request but can handle gracefully
- **Use Case**: Graceful shutdown scenarios
- **Event**: Sends `ChildWorkflowExecutionCanceled` event

## Communication Patterns

### Parent to Child Communication

#### Signals
Send signals to child workflows for runtime control:

```java
@WorkflowInterface
public interface GreetingChild {
    @WorkflowMethod
    String composeGreeting(String greeting, String name);
    
    @SignalMethod
    void updateGreeting(String newGreeting);
}

// In parent workflow
GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class);
Promise<String> execution = Async.function(child::composeGreeting, "Hello", name);

// Send signal to child
child.updateGreeting("Hi there");
```

#### Queries
Query child workflow state:

```java
@WorkflowInterface
public interface GreetingChild {
    @WorkflowMethod
    String composeGreeting(String greeting, String name);
    
    @QueryMethod
    String getCurrentStatus();
}

// In parent workflow
String status = child.getCurrentStatus();
```

### Child to Parent Communication

Child workflows can signal parent workflows:

```java
// In child workflow
@Override
public String composeGreeting(String greeting, String name) {
    // Get parent workflow info
    WorkflowInfo parentInfo = Workflow.getInfo().getParentWorkflowExecution();
    
    if (parentInfo != null) {
        // Create stub for parent workflow
        ParentWorkflow parent = Workflow.newExternalWorkflowStub(
            ParentWorkflow.class, 
            parentInfo.getWorkflowId()
        );
        
        // Signal parent
        parent.childProgress("Started processing");
    }
    
    return greeting + " " + name;
}
```

## Error Handling and Termination

### Child Workflow Failures

Child workflow failures propagate to parent based on execution model:

```java
// Synchronous - exception propagates immediately
try {
    String result = child.composeGreeting("Hello", name);
} catch (ChildWorkflowException e) {
    // Handle child workflow failure
    logger.error("Child workflow failed", e);
}

// Asynchronous - check promise for exceptions
Promise<String> greeting = Async.function(child::composeGreeting, "Hello", name);
try {
    String result = greeting.get();
} catch (ChildWorkflowException e) {
    // Handle child workflow failure
    logger.error("Child workflow failed", e);
}
```

### Manual Termination

Terminate child workflows explicitly:

```java
ChildWorkflowStub childStub = Workflow.newUntypedChildWorkflowStub("ChildWorkflow");
Promise<String> execution = childStub.executeAsync(String.class, "input");

// Terminate child workflow
childStub.cancel();
```

## Advanced Patterns

### Fan-Out/Fan-In Pattern

Execute multiple child workflows and aggregate results:

```java
@Override
public String processOrder(Order order) {
    List<Promise<String>> promises = new ArrayList<>();
    
    // Start multiple child workflows
    for (OrderItem item : order.getItems()) {
        ProcessItemChild child = Workflow.newChildWorkflowStub(ProcessItemChild.class);
        Promise<String> promise = Async.function(child::processItem, item);
        promises.add(promise);
    }
    
    // Wait for all to complete
    Promise.allOf(promises).get();
    
    // Aggregate results
    List<String> results = promises.stream()
        .map(Promise::get)
        .collect(Collectors.toList());
    
    return aggregateResults(results);
}
```

### Dynamic Child Workflow Creation

Create child workflows based on runtime conditions:

```java
@Override
public String processData(ProcessingRequest request) {
    String workflowType = determineWorkflowType(request);
    
    ChildWorkflowOptions options = ChildWorkflowOptions.newBuilder()
        .setWorkflowId("dynamic-child-" + UUID.randomUUID())
        .build();
    
    ChildWorkflowStub child = Workflow.newUntypedChildWorkflowStub(workflowType, options);
    return child.execute(String.class, request);
}
```

### Child Workflow Chaining

Chain child workflows in sequence:

```java
@Override
public String processWorkflow(String input) {
    // First child workflow
    PreprocessChild preprocess = Workflow.newChildWorkflowStub(PreprocessChild.class);
    String preprocessed = preprocess.preprocess(input);
    
    // Second child workflow (depends on first)
    ProcessChild process = Workflow.newChildWorkflowStub(ProcessChild.class);
    String processed = process.process(preprocessed);
    
    // Third child workflow (depends on second)
    PostprocessChild postprocess = Workflow.newChildWorkflowStub(PostprocessChild.class);
    return postprocess.postprocess(processed);
}
```

## Use Cases and Best Practices

### When to Use Child Workflows

**Ideal Scenarios:**
- **Complex Business Processes**: Break down large workflows into manageable components
- **Parallel Processing**: Execute independent operations concurrently
- **Reusable Components**: Create modular workflow components
- **Different Execution Requirements**: Separate task queues, timeouts, or retry policies
- **Hierarchical Organization**: Model real-world business hierarchies

**Example Use Cases:**
- Order processing with payment, inventory, and shipping workflows
- Document approval workflows with multiple reviewers
- Data pipeline with preprocessing, processing, and postprocessing stages
- Multi-tenant workflows with tenant-specific child workflows

### Best Practices

#### 1. Design for Modularity
```java
// Good: Focused, single-responsibility child workflows
PaymentChild payment = Workflow.newChildWorkflowStub(PaymentChild.class);
InventoryChild inventory = Workflow.newChildWorkflowStub(InventoryChild.class);
ShippingChild shipping = Workflow.newChildWorkflowStub(ShippingChild.class);
```

#### 2. Handle Failures Gracefully
```java
@Override
public OrderResult processOrder(OrderRequest request) {
    try {
        PaymentChild payment = Workflow.newChildWorkflowStub(PaymentChild.class);
        PaymentResult paymentResult = payment.processPayment(request.getPayment());
        
        // Continue with other steps...
        
    } catch (ChildWorkflowException e) {
        // Implement compensation logic
        return OrderResult.failed("Payment processing failed: " + e.getMessage());
    }
}
```

#### 3. Use Appropriate Parent Close Policies
```java
// For dependent workflows
ChildWorkflowOptions dependentOptions = ChildWorkflowOptions.newBuilder()
    .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_TERMINATE)
    .build();

// For independent workflows
ChildWorkflowOptions independentOptions = ChildWorkflowOptions.newBuilder()
    .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON)
    .build();
```

#### 4. Implement Proper Error Boundaries
```java
@Override
public ProcessingResult processWithErrorBoundary(ProcessingRequest request) {
    List<Promise<String>> promises = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    
    for (ProcessingItem item : request.getItems()) {
        try {
            ProcessItemChild child = Workflow.newChildWorkflowStub(ProcessItemChild.class);
            Promise<String> promise = Async.function(child::processItem, item);
            promises.add(promise);
        } catch (Exception e) {
            errors.add("Failed to start processing for item " + item.getId());
        }
    }
    
    // Collect results and errors
    return new ProcessingResult(
        promises.stream().map(Promise::get).collect(Collectors.toList()),
        errors
    );
}
```

#### 5. Monitor Child Workflow Execution
```java
@Override
public String monitoredProcessing(String input) {
    ProcessingChild child = Workflow.newChildWorkflowStub(ProcessingChild.class);
    Promise<String> execution = Async.function(child::process, input);
    
    // Periodically check child status
    while (!execution.isCompleted()) {
        String status = child.getCurrentStatus();
        Workflow.getLogger(this.getClass()).info("Child status: " + status);
        Workflow.sleep(Duration.ofSeconds(30));
    }
    
    return execution.get();
}
```

## Testing Child Workflows

### Unit Testing with TestWorkflowEnvironment

```java
@Test
public void testParentChildWorkflow() {
    TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
    
    try {
        // Register workflows and activities
        Worker worker = testEnv.newWorker("test-task-queue");
        worker.registerWorkflowImplementationTypes(
            ParentWorkflowImpl.class,
            ChildWorkflowImpl.class
        );
        worker.registerActivitiesImplementations(new TestActivityImpl());
        
        testEnv.start();
        
        // Create workflow client
        WorkflowClient client = testEnv.getWorkflowClient();
        ParentWorkflow workflow = client.newWorkflowStub(
            ParentWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId("test-parent")
                .setTaskQueue("test-task-queue")
                .build()
        );
        
        // Execute and verify
        String result = workflow.processWithChild("test-input");
        assertEquals("Expected result", result);
        
    } finally {
        testEnv.close();
    }
}
```

### Integration Testing

```java
@Test
public void testParentChildIntegration() {
    // Start parent workflow
    ParentWorkflow parent = workflowClient.newWorkflowStub(
        ParentWorkflow.class,
        WorkflowOptions.newBuilder()
            .setWorkflowId("integration-test-parent")
            .setTaskQueue(TASK_QUEUE)
            .build()
    );
    
    WorkflowExecution execution = WorkflowClient.start(parent::processOrder, orderRequest);
    
    // Verify parent and child workflows are running
    WorkflowStub stub = workflowClient.newUntypedWorkflowStub(execution, Optional.empty());
    
    // Wait for completion and verify results
    OrderResult result = stub.getResult(OrderResult.class);
    assertTrue(result.isSuccess());
}
```

## Performance Considerations

### Resource Management
- Child workflows consume additional resources (memory, compute)
- Consider limits on concurrent child workflows
- Monitor Event History growth with many child workflows

### Scalability Patterns
- Use task queue partitioning for different child workflow types
- Implement backpressure mechanisms for high-volume scenarios
- Consider Continue-As-New pattern for parent workflows with many children

### Optimization Techniques
```java
// Batch child workflow creation
List<Promise<String>> promises = items.stream()
    .map(item -> {
        ProcessItemChild child = Workflow.newChildWorkflowStub(
            ProcessItemChild.class,
            ChildWorkflowOptions.newBuilder()
                .setWorkflowId("process-" + item.getId())
                .build()
        );
        return Async.function(child::processItem, item);
    })
    .collect(Collectors.toList());

// Process in batches to manage resource usage
int batchSize = 10;
for (int i = 0; i < promises.size(); i += batchSize) {
    List<Promise<String>> batch = promises.subList(
        i, 
        Math.min(i + batchSize, promises.size())
    );
    Promise.allOf(batch).get();
}
```

This documentation provides a comprehensive guide to implementing and using Child Workflows in Temporal Java applications, covering everything from basic concepts to advanced patterns and best practices.