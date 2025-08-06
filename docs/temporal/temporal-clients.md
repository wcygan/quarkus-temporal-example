# Temporal Java Clients

## Overview

Temporal clients serve as the primary interface between your application code and the Temporal Service. They enable you to start, manage, and monitor workflow executions, providing a bridge between your business logic and the Temporal platform's orchestration capabilities.

## Client Types

### WorkflowClient

The `WorkflowClient` is the main client interface for interacting with workflows. It provides methods to:
- Start new workflow executions
- Create workflow stubs for type-safe interactions
- Query workflow state and results
- Send signals to running workflows

### WorkflowServiceStubs

`WorkflowServiceStubs` represents the low-level connection to the Temporal Service. It handles:
- Network communication with the Temporal Server
- Connection pooling and management
- Authentication and security configuration

### Workflow Stubs

Temporal provides two types of workflow stubs:

1. **Typed Stubs**: Type-safe interfaces that match your workflow definitions
2. **Untyped Stubs**: Generic interfaces for dynamic workflow interactions

## Client Configuration

### Basic Connection Setup

```java
// Connect to local Temporal Service
WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
WorkflowClient client = WorkflowClient.newInstance(service);
```

### Custom Namespace Connection

```java
WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
    WorkflowServiceStubsOptions.newBuilder()
        .setTarget("temporal-server:7233")
        .build()
);

WorkflowClient client = WorkflowClient.newInstance(service, 
    WorkflowClientOptions.newBuilder()
        .setNamespace("my-namespace")
        .build()
);
```

### Temporal Cloud Connection

```java
// Using API Key
WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
    WorkflowServiceStubsOptions.newBuilder()
        .setTarget("my-namespace.tmprl.cloud:7233")
        .setChannelInitializer(channelBuilder -> {
            channelBuilder.intercept(
                new ApiKeyAuthInterceptor(apiKey)
            );
        })
        .build()
);
```

## Starting Workflows

### Synchronous Workflow Execution

```java
// Create workflow options
WorkflowOptions options = WorkflowOptions.newBuilder()
    .setTaskQueue("my-task-queue")
    .setWorkflowId("unique-workflow-id")
    .setWorkflowExecutionTimeout(Duration.ofMinutes(30))
    .build();

// Create typed workflow stub
MyWorkflow workflow = client.newWorkflowStub(MyWorkflow.class, options);

// Start and wait for result
String result = workflow.processOrder(orderData);
```

### Asynchronous Workflow Execution

```java
// Start workflow asynchronously
WorkflowExecution execution = WorkflowClient.start(workflow::processOrder, orderData);

// Get result later
CompletableFuture<String> future = Async.function(workflow::processOrder, orderData);
String result = future.get();
```

### Untyped Workflow Execution

```java
WorkflowStub untypedWorkflow = client.newUntypedWorkflowStub("MyWorkflow", options);
WorkflowExecution execution = untypedWorkflow.start(orderData);
String result = untypedWorkflow.getResult(String.class);
```

## Querying Workflow State

### Getting Workflow Results

```java
// Get existing workflow by ID
WorkflowStub workflow = client.newUntypedWorkflowStub(workflowId);

// Get result (blocks until completion)
String result = workflow.getResult(String.class);

// Get result with timeout
String result = workflow.getResult(Duration.ofMinutes(5), String.class);
```

### Workflow Queries

```java
// Query workflow state
String status = workflow.query("getStatus", String.class);

// Query with arguments
List<String> items = workflow.query("getCompletedItems", List.class, "filter");
```

### Workflow Signals

```java
// Send signal to workflow
workflow.signal("updateOrder", newOrderData);

// Signal with start (start workflow if not running)
WorkflowOptions options = WorkflowOptions.newBuilder()
    .setTaskQueue("my-task-queue")
    .build();
    
workflow.signalWithStart("processUpdate", new Object[]{updateData}, 
                        new Object[]{initialData});
```

## Client Options and Configuration

### Workflow Options

```java
WorkflowOptions options = WorkflowOptions.newBuilder()
    .setTaskQueue("order-processing")
    .setWorkflowId("order-12345")
    .setWorkflowExecutionTimeout(Duration.ofHours(2))
    .setWorkflowRunTimeout(Duration.ofMinutes(30))
    .setWorkflowTaskTimeout(Duration.ofSeconds(10))
    .setCronSchedule("0 12 * * MON-FRI")  // Weekdays at noon
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(3)
        .setBackoffCoefficient(2.0)
        .build())
    .setMemo(Map.of("department", "sales"))
    .setSearchAttributes(Map.of("CustomerId", "12345"))
    .build();
```

### Client Options

```java
WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
    .setNamespace("production")
    .setDataConverter(JsonDataConverter.newDefaultInstance())
    .setContextPropagators(Collections.singletonList(
        new MDCContextPropagator()
    ))
    .build();
```

## Security and Authentication

### API Key Authentication

```java
WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
    WorkflowServiceStubsOptions.newBuilder()
        .setTarget("namespace.tmprl.cloud:7233")
        .setChannelInitializer(channelBuilder -> {
            channelBuilder.intercept(
                MetadataUtils.newAttachHeadersInterceptor(
                    Metadata.newBuilder()
                        .put(Metadata.Key.of("authorization", ASCII_STRING_MARSHALLER), 
                             "Bearer " + apiKey)
                        .build()
                )
            );
        })
        .build()
);
```

### Mutual TLS (mTLS)

```java
SslContext sslContext = GrpcSslContexts.configure(
    SslContextBuilder.forClient()
        .keyManager(clientCertInputStream, clientKeyInputStream)
        .trustManager(caCertInputStream)
).build();

WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
    WorkflowServiceStubsOptions.newBuilder()
        .setTarget("namespace.tmprl.cloud:7233")
        .setSslContext(sslContext)
        .build()
);
```

## Error Handling

### Workflow Execution Exceptions

```java
try {
    String result = workflow.processOrder(orderData);
} catch (WorkflowException e) {
    if (e.getCause() instanceof ApplicationFailure) {
        ApplicationFailure failure = (ApplicationFailure) e.getCause();
        // Handle business logic failures
        String errorType = failure.getType();
        String errorMessage = failure.getOriginalMessage();
    }
} catch (WorkflowFailedException e) {
    // Handle workflow failures
    System.err.println("Workflow failed: " + e.getMessage());
}
```

### Connection Error Handling

```java
try {
    WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(options);
    WorkflowClient client = WorkflowClient.newInstance(service);
} catch (Exception e) {
    // Handle connection failures
    System.err.println("Failed to connect to Temporal Service: " + e.getMessage());
    // Implement retry logic or fallback behavior
}
```

## Best Practices

### Client Lifecycle Management

1. **Reuse Client Instances**: Create one client per application, not per workflow
2. **Proper Shutdown**: Always shutdown service stubs when application terminates

```java
// Application shutdown
service.shutdown();
service.awaitTermination(10, TimeUnit.SECONDS);
```

### Workflow ID Management

1. **Use Meaningful IDs**: Include business context in workflow IDs
2. **Ensure Uniqueness**: Use UUIDs or business keys to prevent conflicts

```java
String workflowId = String.format("order-processing-%s-%s", 
    customerId, UUID.randomUUID().toString());
```

### Task Queue Organization

1. **Group Related Workflows**: Use task queues to group similar workflows
2. **Environment Separation**: Use different task queues for dev/staging/prod

```java
String taskQueue = String.format("%s-order-processing", environment);
```

### Connection Pooling

1. **Configure Channel Options**: Tune gRPC channel settings for your load
2. **Monitor Connections**: Implement health checks for Temporal Service connectivity

```java
WorkflowServiceStubsOptions options = WorkflowServiceStubsOptions.newBuilder()
    .setChannelInitializer(channelBuilder -> {
        channelBuilder
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(5, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .maxInboundMessageSize(2 * 1024 * 1024);
    })
    .build();
```

### Search Attributes and Metadata

1. **Index Important Data**: Use search attributes for workflow discoverability
2. **Add Context**: Use memo fields for debugging and observability

```java
Map<String, Object> searchAttributes = Map.of(
    "CustomerId", customerId,
    "OrderAmount", orderAmount,
    "Priority", "HIGH"
);

Map<String, Object> memo = Map.of(
    "correlationId", correlationId,
    "source", "mobile-app",
    "version", "1.2.3"
);
```

## Integration Examples

### CDI/Dependency Injection

```java
@ApplicationScoped
public class TemporalClientProducer {
    
    @Produces
    @ApplicationScoped
    public WorkflowServiceStubs produceServiceStubs() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }
    
    @Produces
    @ApplicationScoped
    public WorkflowClient produceWorkflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace("default")
                .build());
    }
}
```

### Spring Boot Configuration

```java
@Configuration
public class TemporalConfiguration {
    
    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }
    
    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs);
    }
}
```

This comprehensive guide covers all aspects of Temporal Java client usage, from basic setup to advanced configuration and best practices for production deployments.