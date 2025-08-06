# Temporal Java Testing Suite

This document provides a comprehensive guide to testing Temporal workflows and activities using the Temporal Java SDK testing framework.

## Overview

The Temporal Java SDK provides robust testing capabilities through the `temporal-testing` library, enabling three primary testing strategies:

1. **Unit Testing**: Testing individual workflows and activities in isolation
2. **Integration Testing**: Testing workflows with real activities but mocked external dependencies
3. **End-to-End Testing**: Testing complete workflows with real Temporal server and all dependencies

## Core Testing Components

### TestWorkflowEnvironment

`TestWorkflowEnvironment` is an in-memory Temporal service implementation that provides:

- Fast test execution through automatic time skipping
- Isolated test environments
- Full workflow execution capabilities
- Integration with popular testing frameworks (JUnit 4/5, TestNG)

### TestActivityEnvironment

`TestActivityEnvironment` enables testing activities in isolation with:

- Activity context simulation
- Heartbeat and cancellation testing
- Async completion testing
- Error handling validation

## Dependencies

Add the Temporal testing dependency to your project:

### Maven
```xml
<dependency>
    <groupId>io.temporal</groupId>
    <artifactId>temporal-testing</artifactId>
    <version>1.25.1</version>
    <scope>test</scope>
</dependency>
```

### Gradle
```groovy
testImplementation 'io.temporal:temporal-testing:1.25.1'
```

## Unit Testing Patterns

### Basic JUnit 5 Setup

```java
public class WorkflowUnitTest {
    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("test-task-queue");
        client = testEnv.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void testWorkflowExecution() {
        // Register workflow implementation
        worker.registerWorkflowImplementationTypes(MyWorkflowImpl.class);
        
        // Register activity implementations (or mocks)
        worker.registerActivitiesImplementations(new MyActivityImpl());
        
        // Start test environment
        testEnv.start();

        // Create workflow stub
        MyWorkflow workflow = client.newWorkflowStub(
            MyWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .build()
        );

        // Execute workflow
        String result = workflow.execute("test-input");
        
        // Assert results
        assertEquals("expected-result", result);
    }
}
```

### JUnit 5 Extension Pattern

```java
public class WorkflowExtensionTest {
    @RegisterExtension
    public static final TestWorkflowExtension testWorkflow =
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(MyWorkflowImpl.class)
            .setActivityImplementations(new MyActivityImpl())
            .build();

    @Test
    public void testWorkflow(
        TestWorkflowEnvironment testEnv,
        Worker worker,
        MyWorkflow workflow
    ) {
        String result = workflow.execute("test-input");
        assertEquals("expected-result", result);
    }
}
```

## Activity Testing

### Testing Activities in Isolation

```java
public class ActivityUnitTest {
    private TestActivityEnvironment testEnv;
    private MyActivity activity;

    @BeforeEach
    void setUp() {
        testEnv = TestActivityEnvironment.newInstance();
        activity = new MyActivityImpl();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void testActivityExecution() {
        String result = testEnv.run(
            () -> activity.processData("input")
        );
        
        assertEquals("processed-input", result);
    }

    @Test
    void testActivityWithHeartbeat() {
        testEnv.run(() -> {
            activity.longRunningTask("data");
            // Verify heartbeat was called
            verify(Activity.getExecutionContext()).heartbeat("progress");
        });
    }
}
```

### Testing Activity Cancellation

```java
@Test
void testActivityCancellation() {
    testEnv.run(() -> {
        try {
            activity.cancellableTask();
            fail("Expected CanceledFailure");
        } catch (CanceledFailure e) {
            // Expected cancellation
        }
    });
}
```

## Time Manipulation

### Automatic Time Skipping

```java
@Test
void testWorkflowWithTimers() {
    worker.registerWorkflowImplementationTypes(TimerWorkflowImpl.class);
    testEnv.start();

    TimerWorkflow workflow = client.newWorkflowStub(
        TimerWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("test-task-queue")
            .build()
    );

    // This workflow sleeps for 1 hour, but test completes quickly
    // due to automatic time skipping
    String result = workflow.executeWithDelay(Duration.ofHours(1));
    assertEquals("completed", result);
}
```

### Manual Time Control

```java
@Test
void testManualTimeSkipping() {
    testEnv.start();
    
    // Start workflow asynchronously
    WorkflowExecution execution = WorkflowClient.start(workflow::longRunningProcess);
    
    // Manually advance time
    testEnv.sleep(Duration.ofMinutes(30));
    
    // Check workflow state
    String status = workflow.getStatus();
    assertEquals("in-progress", status);
    
    // Advance more time
    testEnv.sleep(Duration.ofMinutes(30));
    
    // Workflow should complete
    String result = client.newUntypedWorkflowStub(execution)
        .getResult(String.class);
    assertEquals("completed", result);
}
```

## Activity Mocking

### Using Mockito

```java
@Test
void testWorkflowWithMockedActivities() {
    // Create mock activities
    PaymentActivity paymentActivity = mock(PaymentActivity.class);
    InventoryActivity inventoryActivity = mock(InventoryActivity.class);
    
    // Configure mock behavior
    when(paymentActivity.processPayment(any()))
        .thenReturn("payment-successful");
    when(inventoryActivity.reserveItems(any()))
        .thenReturn("items-reserved");

    // Register mocked activities
    worker.registerActivitiesImplementations(paymentActivity, inventoryActivity);
    worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
    
    testEnv.start();

    OrderWorkflow workflow = client.newWorkflowStub(
        OrderWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("test-task-queue")
            .build()
    );

    OrderResult result = workflow.processOrder(new OrderRequest("item1", 1));
    
    assertEquals("order-completed", result.getStatus());
    
    // Verify mock interactions
    verify(paymentActivity).processPayment(any());
    verify(inventoryActivity).reserveItems(any());
}
```

### Testing Activity Failures

```java
@Test
void testWorkflowWithActivityFailure() {
    PaymentActivity paymentActivity = mock(PaymentActivity.class);
    
    // Configure activity to fail
    when(paymentActivity.processPayment(any()))
        .thenThrow(new ApplicationFailure("Payment failed", "PAYMENT_ERROR"));

    worker.registerActivitiesImplementations(paymentActivity);
    worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
    
    testEnv.start();

    OrderWorkflow workflow = client.newWorkflowStub(
        OrderWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("test-task-queue")
            .build()
    );

    assertThrows(WorkflowException.class, () -> {
        workflow.processOrder(new OrderRequest("item1", 1));
    });
}
```

## Testing SAGA Patterns

### Testing Compensation Logic

```java
@Test
void testSagaCompensation() {
    // Mock activities to simulate failures
    PaymentActivity paymentActivity = mock(PaymentActivity.class);
    InventoryActivity inventoryActivity = mock(InventoryActivity.class);
    ShippingActivity shippingActivity = mock(ShippingActivity.class);
    
    // Payment succeeds, inventory succeeds, shipping fails
    when(paymentActivity.processPayment(any())).thenReturn("payment-id");
    when(paymentActivity.refundPayment(any())).thenReturn("refund-id");
    
    when(inventoryActivity.reserveItems(any())).thenReturn("reservation-id");
    when(inventoryActivity.releaseItems(any())).thenReturn("released");
    
    when(shippingActivity.createShipment(any()))
        .thenThrow(new ApplicationFailure("Shipping unavailable", "SHIPPING_ERROR"));

    worker.registerActivitiesImplementations(
        paymentActivity, inventoryActivity, shippingActivity
    );
    worker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
    
    testEnv.start();

    OrderSagaWorkflow workflow = client.newWorkflowStub(
        OrderSagaWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("test-task-queue")
            .build()
    );

    // Workflow should handle compensation
    OrderResult result = workflow.processOrder(new OrderRequest("item1", 1));
    
    assertEquals("compensated", result.getStatus());
    
    // Verify compensation activities were called
    verify(paymentActivity).refundPayment("payment-id");
    verify(inventoryActivity).releaseItems("reservation-id");
}
```

## Testing Scheduled Workflows

### Testing Cron Workflows

```java
@Test
void testScheduledWorkflow() {
    worker.registerWorkflowImplementationTypes(ReportWorkflowImpl.class);
    worker.registerActivitiesImplementations(new ReportActivityImpl());
    
    testEnv.start();

    // Start scheduled workflow
    ScheduleClient scheduleClient = ScheduleClient.newInstance(
        testEnv.getWorkflowClient().getWorkflowServiceStub()
    );
    
    Schedule schedule = Schedule.newBuilder()
        .setAction(ScheduleActionStartWorkflow.newBuilder()
            .setWorkflowType(ReportWorkflow.class)
            .setTaskQueue("test-task-queue")
            .build())
        .setSpec(ScheduleSpec.newBuilder()
            .setCronExpressions("0 */5 * * * *") // Every 5 minutes
            .build())
        .build();

    String scheduleId = "test-report-schedule";
    scheduleClient.createSchedule(scheduleId, schedule);
    
    // Skip time to trigger scheduled execution
    testEnv.sleep(Duration.ofMinutes(10));
    
    // Verify workflow executions
    List<WorkflowExecution> executions = getScheduledExecutions(scheduleId);
    assertTrue(executions.size() >= 2);
}
```

## Testing Signals and Queries

### Testing Workflow Signals

```java
@Test
void testWorkflowSignals() {
    worker.registerWorkflowImplementationTypes(SignalWorkflowImpl.class);
    testEnv.start();

    SignalWorkflow workflow = client.newWorkflowStub(
        SignalWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("test-task-queue")
            .build()
    );

    // Start workflow asynchronously
    WorkflowClient.start(workflow::processSignals);
    
    // Send signals
    workflow.updateStatus("processing");
    workflow.updateStatus("completed");
    workflow.finish();
    
    // Query final state
    String finalStatus = workflow.getCurrentStatus();
    assertEquals("completed", finalStatus);
}
```

### Testing Workflow Queries

```java
@Test
void testWorkflowQueries() {
    worker.registerWorkflowImplementationTypes(QueryWorkflowImpl.class);
    testEnv.start();

    QueryWorkflow workflow = client.newWorkflowStub(
        QueryWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("test-task-queue")
            .build()
    );

    // Start workflow asynchronously
    WorkflowClient.start(workflow::longRunningProcess);
    
    // Wait for workflow to start
    testEnv.sleep(Duration.ofSeconds(1));
    
    // Query workflow state
    String status = workflow.getStatus();
    assertEquals("running", status);
    
    int progress = workflow.getProgress();
    assertTrue(progress >= 0 && progress <= 100);
}
```

## Testing Continue-As-New

### Testing History Limits

```java
@Test
void testContinueAsNew() {
    worker.registerWorkflowImplementationTypes(ContinueAsNewWorkflowImpl.class);
    testEnv.start();

    ContinueAsNewWorkflow workflow = client.newWorkflowStub(
        ContinueAsNewWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("test-task-queue")
            .build()
    );

    // Start workflow that will continue-as-new after processing 1000 events
    WorkflowExecution execution = WorkflowClient.start(
        () -> workflow.processEvents(5000) // Process 5000 events
    );
    
    // Skip time to allow processing
    testEnv.sleep(Duration.ofMinutes(10));
    
    // Verify workflow continued as new at least 4 times (5000/1000 - 1)
    WorkflowExecutionHistory history = client.fetchHistory(execution.getWorkflowId());
    long continueAsNewCount = history.getEvents().stream()
        .filter(event -> event.hasWorkflowExecutionContinuedAsNewEventAttributes())
        .count();
    
    assertTrue(continueAsNewCount >= 4);
}
```

## Integration Testing

### Testing with Real Temporal Server

```java
@TestProfile(IntegrationTestProfile.class)
public class WorkflowIntegrationTest {
    @Test
    void testRealTemporalServer() {
        // This test runs against real Temporal server
        // configured in IntegrationTestProfile
        
        WorkflowServiceStubs service = WorkflowServiceStubs.newInstance();
        WorkflowClient client = WorkflowClient.newInstance(service);
        
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker("integration-task-queue");
        
        worker.registerWorkflowImplementationTypes(MyWorkflowImpl.class);
        worker.registerActivitiesImplementations(new MyActivityImpl());
        
        factory.start();
        
        try {
            MyWorkflow workflow = client.newWorkflowStub(
                MyWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue("integration-task-queue")
                    .build()
            );

            String result = workflow.execute("integration-test");
            assertEquals("expected-result", result);
            
        } finally {
            factory.shutdown();
        }
    }
}
```

## Best Practices

### Test Organization

1. **Separate Unit and Integration Tests**
   ```
   src/test/java/
   ├── unit/
   │   ├── workflow/
   │   └── activity/
   └── integration/
       ├── workflow/
       └── e2e/
   ```

2. **Use Test Profiles**
   ```java
   @TestProfile(UnitTestProfile.class)  // Uses TestWorkflowEnvironment
   @TestProfile(IntegrationTestProfile.class)  // Uses real Temporal server
   ```

### Mock Strategy

1. **Mock External Dependencies**: Always mock external services, databases, APIs
2. **Use Real Activities for Logic**: Test actual activity implementations when possible
3. **Mock for Error Scenarios**: Use mocks to simulate failures and edge cases

### Time Management

1. **Leverage Auto Time Skipping**: Let TestWorkflowEnvironment handle time advancement
2. **Manual Control When Needed**: Use `testEnv.sleep()` for precise timing control
3. **Test Time-based Logic**: Verify timer and scheduling behavior

### Error Testing

1. **Test All Failure Modes**: Activity failures, timeouts, retries, cancellations
2. **Verify Compensation**: Test SAGA compensation logic thoroughly
3. **Test Recovery**: Verify workflow recovery after failures

### Performance Considerations

1. **Fast Test Execution**: Unit tests should complete in milliseconds
2. **Minimize Integration Tests**: Use sparingly for critical end-to-end scenarios
3. **Parallel Test Execution**: Design tests to run independently

## Common Patterns in This Project

Based on the existing test files in this project, here are common testing patterns used:

### OrderSagaWorkflowTest Pattern
```java
@Test
void testCompensation() {
    // Configure activities to fail at specific steps
    workflow.failAtStep("SHIPPING");
    
    OrderResult result = workflow.processOrder(request);
    
    assertEquals("COMPENSATED", result.getStatus());
    
    // Verify compensation steps were executed
    List<String> steps = workflow.getCompletedSteps();
    assertTrue(steps.contains("REFUND_PAYMENT"));
    assertTrue(steps.contains("RELEASE_INVENTORY"));
}
```

### ScheduledReportWorkflowTest Pattern
```java
@Test
void testScheduledExecution() {
    // Use time skipping for scheduled workflows
    testEnv.sleep(Duration.ofMinutes(5));
    
    // Verify scheduled execution occurred
    String status = workflow.getLastReportStatus();
    assertEquals("COMPLETED", status);
}
```

### ParentWorkflowTest Pattern
```java
@Test
void testParentChildOrchestration() {
    // Test parent workflow coordinating multiple child workflows
    List<String> results = workflow.processInParallel(Arrays.asList("task1", "task2", "task3"));
    
    assertEquals(3, results.size());
    assertTrue(results.stream().allMatch(r -> r.startsWith("processed-")));
}
```

This comprehensive testing approach ensures robust, reliable workflow implementations that can handle various scenarios including failures, timeouts, and complex orchestration patterns.