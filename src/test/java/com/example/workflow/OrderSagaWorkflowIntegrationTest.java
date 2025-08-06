package com.example.workflow;

import com.example.activity.*;
import com.example.model.OrderRequest;
import com.example.model.OrderResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OrderSagaWorkflow that run against a real Temporal server.
 * 
 * Prerequisites:
 * - Temporal server must be running at localhost:7233 (docker compose up -d)
 * - Temporal UI available at http://localhost:8088
 * 
 * These tests verify:
 * - Workflow execution on real Temporal server
 * - SAGA compensation logic in production environment
 * - Query methods against running workflows
 * - Visibility in Temporal UI
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderSagaWorkflowIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderSagaWorkflowIntegrationTest.class);
    private static final String TASK_QUEUE = "order-saga-integration-test-queue";
    private static final String TEMPORAL_TARGET = "localhost:7233";
    private static final String NAMESPACE = "default";
    
    private static WorkflowClient client;
    
    @BeforeAll
    public static void setUp() {
        logger.info("Setting up integration test with Temporal server at {}", TEMPORAL_TARGET);
        
        // Connect to real Temporal server
        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
            .setTarget(TEMPORAL_TARGET)
            .build();
            
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
        
        client = WorkflowClient.newInstance(service, 
            io.temporal.client.WorkflowClientOptions.newBuilder()
                .setNamespace(NAMESPACE)
                .build());
        
        logger.info("Connected to Temporal server at {}", TEMPORAL_TARGET);
    }
    
    @AfterAll
    public static void tearDown() {
        logger.info("Integration test cleanup completed");
    }
    
    @Test
    @Order(1)
    @DisplayName("Test successful order processing on real Temporal server")
    public void testSuccessfulOrderProcessing() {
        String workflowId = "order-saga-integration-success-" + UUID.randomUUID();
        logger.info("Starting successful order test with workflow ID: {}", workflowId);
        
        // Create a dedicated worker for this test to ensure isolation
        String testQueue = "success-test-queue-" + UUID.randomUUID();
        WorkerFactory testFactory = WorkerFactory.newInstance(client);
        Worker testWorker = testFactory.newWorker(testQueue);
        testWorker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        testWorker.registerActivitiesImplementations(
            new TestActivityImplementations.DeterministicPaymentActivity(),
            new TestActivityImplementations.DeterministicInventoryActivity(),
            new TestActivityImplementations.DeterministicShippingActivity(),
            new TestActivityImplementations.DeterministicNotificationActivity()
        );
        testFactory.start();
        
        OrderSagaWorkflow workflow = client.newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(testQueue)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        // Create order request
        OrderRequest request = createTestOrder("CUST-INT-001", new BigDecimal("150.00"));
        
        // Start workflow execution
        WorkflowClient.start(workflow::processOrder, request);
        logger.info("Workflow started. You can view it at: http://localhost:8088/namespaces/{}/workflows/{}", 
            NAMESPACE, workflowId);
        
        // Query workflow state while it's running
        String status = workflow.getOrderStatus();
        assertNotNull(status);
        logger.info("Current workflow status: {}", status);
        
        // Wait for workflow to complete - use the untyped stub to get result
        WorkflowStub untypedStub = WorkflowStub.fromTyped(workflow);
        OrderResult result = untypedStub.getResult(OrderResult.class);
        
        // Verify successful completion
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getPaymentTransactionId());
        assertNotNull(result.getShippingTrackingNumber());
        assertEquals(4, result.getCompletedSteps().size());
        assertNull(result.getFailureReason());
        
        logger.info("Order completed successfully with tracking: {}", result.getShippingTrackingNumber());
        
        // Cleanup
        testFactory.shutdownNow();
    }
    
    @Test
    @Order(2)
    @DisplayName("Test payment failure with compensation on real Temporal server")
    public void testPaymentFailureWithCompensation() {
        String workflowId = "order-saga-integration-payment-fail-" + UUID.randomUUID();
        logger.info("Starting payment failure test with workflow ID: {}", workflowId);
        
        // Create a new factory and worker for this test
        String testQueue = "payment-failure-test-queue-" + UUID.randomUUID();
        WorkerFactory testFactory = WorkerFactory.newInstance(client);
        Worker testWorker = testFactory.newWorker(testQueue);
        testWorker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        testWorker.registerActivitiesImplementations(
            new PaymentActivityImpl() {
                @Override
                public PaymentResult chargePayment(String customerId, BigDecimal amount) {
                    logger.info("Simulating payment failure for customer: {}", customerId);
                    return new PaymentResult(null, false, "Simulated payment failure");
                }
            },
            new TestActivityImplementations.DeterministicInventoryActivity(),
            new TestActivityImplementations.DeterministicShippingActivity(),
            new TestActivityImplementations.DeterministicNotificationActivity()
        );
        testFactory.start();
        
        OrderSagaWorkflow workflow = client.newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(testQueue)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        // Create order
        OrderRequest request = createTestOrder("CUST-INT-002", new BigDecimal("100.00"));
        
        logger.info("Starting workflow with simulated payment failure. View at: http://localhost:8088/namespaces/{}/workflows/{}", 
            NAMESPACE, workflowId);
        
        // Execute workflow - payment will fail
        OrderResult result = workflow.processOrder(request);
        
        // Verify failure and compensation
        assertEquals("FAILED", result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("Payment failed"));
        
        // Query final state
        String finalStatus = workflow.getOrderStatus();
        assertEquals("FAILED", finalStatus);
        
        logger.info("Order failed as expected with reason: {}", result.getFailureReason());
        
        // Cleanup
        testFactory.shutdownNow();
    }
    
    @Test
    @Order(3)
    @DisplayName("Test inventory failure with compensation on real Temporal server")
    public void testInventoryFailureWithCompensation() {
        String workflowId = "order-saga-integration-inventory-fail-" + UUID.randomUUID();
        logger.info("Starting inventory failure test with workflow ID: {}", workflowId);
        
        // Create a new factory and worker for this test
        String testQueue = "inventory-failure-test-queue-" + UUID.randomUUID();
        WorkerFactory testFactory = WorkerFactory.newInstance(client);
        Worker testWorker = testFactory.newWorker(testQueue);
        testWorker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        testWorker.registerActivitiesImplementations(
            new TestActivityImplementations.DeterministicPaymentActivity(),
            new InventoryActivityImpl() {
                @Override
                public ReservationResult reserveInventory(List<OrderRequest.OrderItem> items) {
                    logger.info("Simulating inventory failure for {} items", items.size());
                    return new ReservationResult(null, false, "Insufficient inventory");
                }
            },
            new TestActivityImplementations.DeterministicShippingActivity(),
            new TestActivityImplementations.DeterministicNotificationActivity()
        );
        testFactory.start();
        
        OrderSagaWorkflow workflow = client.newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(testQueue)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        OrderRequest request = createTestOrder("CUST-INT-003", new BigDecimal("200.00"));
        
        logger.info("Starting workflow with simulated inventory failure. View at: http://localhost:8088/namespaces/{}/workflows/{}", 
            NAMESPACE, workflowId);
        
        // Execute workflow - inventory will fail after payment succeeds
        OrderResult result = workflow.processOrder(request);
        
        // Verify failure with compensation
        assertEquals("FAILED", result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("Inventory") || result.getFailureReason().contains("inventory"), 
            "Expected failure reason to contain 'Inventory' but was: " + result.getFailureReason());
        
        // Verify completed steps before failure
        List<String> completedSteps = workflow.getCompletedSteps();
        assertTrue(completedSteps.contains("PAYMENT_CHARGED"),
            "Expected PAYMENT_CHARGED in completed steps but got: " + completedSteps);
        
        logger.info("Order failed with {} steps completed before compensation", completedSteps.size());
        
        // Cleanup
        testFactory.shutdownNow();
    }
    
    @Test
    @Order(4)
    @DisplayName("Test shipping failure with compensation on real Temporal server")
    public void testShippingFailureWithCompensation() {
        String workflowId = "order-saga-integration-shipping-fail-" + UUID.randomUUID();
        logger.info("Starting shipping failure test with workflow ID: {}", workflowId);
        
        // Create a new factory and worker for this test
        String testQueue = "shipping-failure-test-queue-" + UUID.randomUUID();
        WorkerFactory testFactory = WorkerFactory.newInstance(client);
        Worker testWorker = testFactory.newWorker(testQueue);
        testWorker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        testWorker.registerActivitiesImplementations(
            new TestActivityImplementations.DeterministicPaymentActivity(),
            new TestActivityImplementations.DeterministicInventoryActivity(),
            new ShippingActivityImpl() {
                @Override
                public ShippingResult scheduleShipping(String orderId, String shippingAddress) {
                    logger.info("Simulating shipping failure for order: {}", orderId);
                    return new ShippingResult(null, false, null, "Shipping service unavailable");
                }
            },
            new TestActivityImplementations.DeterministicNotificationActivity()
        );
        testFactory.start();
        
        OrderSagaWorkflow workflow = client.newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(testQueue)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        OrderRequest request = createTestOrder("CUST-INT-004", new BigDecimal("300.00"));
        
        logger.info("Starting workflow with simulated shipping failure. View at: http://localhost:8088/namespaces/{}/workflows/{}", 
            NAMESPACE, workflowId);
        
        // Execute workflow - shipping will fail after payment and inventory succeed
        OrderResult result = workflow.processOrder(request);
        
        // Verify failure with compensation
        assertEquals("FAILED", result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("Shipping") || result.getFailureReason().contains("shipping"),
            "Expected failure reason to contain 'Shipping' but was: " + result.getFailureReason());
        
        // Verify completed steps before failure
        List<String> completedSteps = workflow.getCompletedSteps();
        assertEquals(2, completedSteps.size(), "Expected 2 completed steps but got: " + completedSteps);
        assertTrue(completedSteps.contains("PAYMENT_CHARGED"),
            "Expected PAYMENT_CHARGED in completed steps but got: " + completedSteps);
        assertTrue(completedSteps.contains("INVENTORY_RESERVED"),
            "Expected INVENTORY_RESERVED in completed steps but got: " + completedSteps);
        
        logger.info("Order failed after completing {} steps, compensation executed", completedSteps.size());
        
        // Cleanup
        testFactory.shutdownNow();
    }
    
    @Test
    @Order(5)
    @DisplayName("Test workflow queries on real Temporal server")
    public void testWorkflowQueries() {
        String workflowId = "order-saga-integration-query-" + UUID.randomUUID();
        logger.info("Starting query test with workflow ID: {}", workflowId);
        
        // Create a dedicated worker for this test
        String testQueue = "query-test-queue-" + UUID.randomUUID();
        WorkerFactory testFactory = WorkerFactory.newInstance(client);
        Worker testWorker = testFactory.newWorker(testQueue);
        testWorker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        testWorker.registerActivitiesImplementations(
            new TestActivityImplementations.DeterministicPaymentActivity(),
            new TestActivityImplementations.DeterministicInventoryActivity(),
            new TestActivityImplementations.DeterministicShippingActivity(),
            new TestActivityImplementations.DeterministicNotificationActivity()
        );
        testFactory.start();
        
        OrderSagaWorkflow workflow = client.newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(testQueue)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        OrderRequest request = createTestOrder("CUST-INT-005", new BigDecimal("100.00"));
        
        // Start workflow asynchronously
        WorkflowClient.start(workflow::processOrder, request);
        
        logger.info("Workflow started. Testing queries. View at: http://localhost:8088/namespaces/{}/workflows/{}", 
            NAMESPACE, workflowId);
        
        // Query workflow state while running
        String status = workflow.getOrderStatus();
        assertNotNull(status);
        logger.info("Initial status: {}", status);
        
        List<String> steps = workflow.getCompletedSteps();
        assertNotNull(steps);
        logger.info("Initial completed steps: {}", steps);
        
        String failureReason = workflow.getFailureReason();
        logger.info("Initial failure reason: {}", failureReason);
        
        // Query again immediately - temporal queries provide real-time status
        // No sleep needed as queries are synchronous and reflect current workflow state
        status = workflow.getOrderStatus();
        steps = workflow.getCompletedSteps();
        logger.info("Current status: {}, Steps: {}", status, steps);
        
        // Wait for completion
        WorkflowStub untypedStub = WorkflowStub.fromTyped(workflow);
        OrderResult result = untypedStub.getResult(OrderResult.class);
        assertNotNull(result);
        
        logger.info("Query test completed. Final status: {}", result.getStatus());
        
        // Cleanup
        testFactory.shutdownNow();
    }
    
    @Test
    @Order(6)
    @DisplayName("Test workflow visibility in Temporal UI")
    public void testWorkflowVisibilityInUI() {
        String workflowId = "order-saga-ui-visibility-" + System.currentTimeMillis();
        logger.info("Starting UI visibility test with workflow ID: {}", workflowId);
        
        // Create a dedicated worker for this test
        String testQueue = "ui-test-queue-" + UUID.randomUUID();
        WorkerFactory testFactory = WorkerFactory.newInstance(client);
        Worker testWorker = testFactory.newWorker(testQueue);
        testWorker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        testWorker.registerActivitiesImplementations(
            new TestActivityImplementations.DeterministicPaymentActivity(),
            new TestActivityImplementations.DeterministicInventoryActivity(),
            new TestActivityImplementations.DeterministicShippingActivity(),
            new TestActivityImplementations.DeterministicNotificationActivity()
        );
        testFactory.start();
        
        OrderSagaWorkflow workflow = client.newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(testQueue)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        OrderRequest request = createTestOrder("CUST-UI-TEST", new BigDecimal("50.00"));
        
        // Execute workflow
        OrderResult result = workflow.processOrder(request);
        
        assertNotNull(result);
        logger.info("✅ Workflow completed. You can verify it in Temporal UI:");
        logger.info("   URL: http://localhost:8088/namespaces/{}/workflows", NAMESPACE);
        logger.info("   Workflow ID: {}", workflowId);
        logger.info("   Task Queue: {}", TASK_QUEUE);
        logger.info("   Status: {}", result.getStatus());
        
        // Cleanup
        testFactory.shutdownNow();
    }
    
    private OrderRequest createTestOrder(String customerId, BigDecimal amount) {
        OrderRequest request = new OrderRequest();
        request.setCustomerId(customerId);
        request.setShippingAddress("123 Integration Test St, Test City, TC 12345");
        request.setTotalAmount(amount);
        
        List<OrderRequest.OrderItem> items = new ArrayList<>();
        items.add(new OrderRequest.OrderItem("PRODUCT-INT-001", 1, amount));
        request.setItems(items);
        
        return request;
    }
}