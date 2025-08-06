package com.example.workflow;

import com.example.activity.*;
import com.example.model.OrderRequest;
import com.example.model.OrderResult;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderSagaWorkflowTest {
    
    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    
    @BeforeEach
    public void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("test-task-queue");
        worker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        worker.registerActivitiesImplementations(
            new PaymentActivityImpl(),
            new InventoryActivityImpl(),
            new ShippingActivityImpl(),
            new NotificationActivityImpl()
        );
        testEnv.start();
    }
    
    @AfterEach
    public void tearDown() {
        testEnv.close();
    }
    
    @Test
    public void testSuccessfulOrderProcessing() {
        // Create a test-specific activity implementation that always succeeds
        PaymentActivityImpl testPaymentActivity = new PaymentActivityImpl() {
            @Override
            public PaymentResult chargePayment(String customerId, BigDecimal amount) {
                return new PaymentResult("TEST-TXN-SUCCESS", true, "Payment successful");
            }
        };
        
        InventoryActivityImpl testInventoryActivity = new InventoryActivityImpl() {
            @Override
            public ReservationResult reserveInventory(List<OrderRequest.OrderItem> items) {
                return new ReservationResult("TEST-RES-SUCCESS", true, "Inventory reserved");
            }
        };
        
        ShippingActivityImpl testShippingActivity = new ShippingActivityImpl() {
            @Override
            public ShippingResult scheduleShipping(String orderId, String address) {
                return new ShippingResult("TEST-TRACK-SUCCESS", true, "2025-01-01", "Shipping scheduled");
            }
        };
        
        // Re-register with test-specific implementations
        testEnv.close();
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("test-task-queue");
        worker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        worker.registerActivitiesImplementations(
            testPaymentActivity,
            testInventoryActivity,
            testShippingActivity,
            new NotificationActivityImpl()
        );
        testEnv.start();
        
        OrderSagaWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        OrderRequest request = createSampleOrder();
        OrderResult result = workflow.processOrder(request);
        
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getPaymentTransactionId());
        assertNotNull(result.getShippingTrackingNumber());
        assertEquals(4, result.getCompletedSteps().size());
        assertNull(result.getFailureReason());
    }
    
    @Test
    public void testPaymentFailureWithCompensation() {
        // Create a payment activity that always fails
        PaymentActivityImpl failingPaymentActivity = new PaymentActivityImpl() {
            @Override
            public PaymentResult chargePayment(String customerId, BigDecimal amount) {
                return new PaymentResult(null, false, "Payment declined - test failure");
            }
        };
        
        // Re-register with failing payment activity
        testEnv.close();
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("test-task-queue");
        worker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        worker.registerActivitiesImplementations(
            failingPaymentActivity,
            new InventoryActivityImpl(),
            new ShippingActivityImpl(),
            new NotificationActivityImpl()
        );
        testEnv.start();
        
        OrderSagaWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        OrderRequest request = createSampleOrder();
        OrderResult result = workflow.processOrder(request);
        
        assertEquals("FAILED", result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("Payment"));
        assertTrue(result.getCompletedSteps().isEmpty());
    }
    
    @Test
    public void testInventoryFailureWithCompensation() {
        // Create activities where inventory fails but payment succeeds
        PaymentActivityImpl successPaymentActivity = new PaymentActivityImpl() {
            @Override
            public PaymentResult chargePayment(String customerId, BigDecimal amount) {
                return new PaymentResult("TEST-PAY-123", true, "Payment successful");
            }
        };
        
        InventoryActivityImpl failingInventoryActivity = new InventoryActivityImpl() {
            @Override
            public ReservationResult reserveInventory(List<OrderRequest.OrderItem> items) {
                return new ReservationResult(null, false, "Inventory out of stock - test failure");
            }
        };
        
        // Re-register with failing inventory activity
        testEnv.close();
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("test-task-queue");
        worker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        worker.registerActivitiesImplementations(
            successPaymentActivity,
            failingInventoryActivity,
            new ShippingActivityImpl(),
            new NotificationActivityImpl()
        );
        testEnv.start();
        
        OrderSagaWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        OrderRequest request = createSampleOrder();
        OrderResult result = workflow.processOrder(request);
        
        assertEquals("FAILED", result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("Inventory"));
        assertEquals(1, result.getCompletedSteps().size());
        assertTrue(result.getCompletedSteps().contains("PAYMENT_CHARGED"));
    }
    
    @Test
    public void testShippingFailureWithCompensation() {
        // Create activities where shipping fails but payment and inventory succeed
        PaymentActivityImpl successPaymentActivity = new PaymentActivityImpl() {
            @Override
            public PaymentResult chargePayment(String customerId, BigDecimal amount) {
                return new PaymentResult("TEST-PAY-456", true, "Payment successful");
            }
        };
        
        InventoryActivityImpl successInventoryActivity = new InventoryActivityImpl() {
            @Override
            public ReservationResult reserveInventory(List<OrderRequest.OrderItem> items) {
                return new ReservationResult("TEST-INV-789", true, "Inventory reserved");
            }
        };
        
        ShippingActivityImpl failingShippingActivity = new ShippingActivityImpl() {
            @Override
            public ShippingResult scheduleShipping(String orderId, String address) {
                return new ShippingResult(null, false, null, "Shipping service unavailable - test failure");
            }
        };
        
        // Re-register with failing shipping activity
        testEnv.close();
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("test-task-queue");
        worker.registerWorkflowImplementationTypes(OrderSagaWorkflowImpl.class);
        worker.registerActivitiesImplementations(
            successPaymentActivity,
            successInventoryActivity,
            failingShippingActivity,
            new NotificationActivityImpl()
        );
        testEnv.start();
        
        OrderSagaWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        OrderRequest request = createSampleOrder();
        OrderResult result = workflow.processOrder(request);
        
        assertEquals("FAILED", result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("Shipping"));
        assertEquals(2, result.getCompletedSteps().size());
        assertTrue(result.getCompletedSteps().contains("PAYMENT_CHARGED"));
        assertTrue(result.getCompletedSteps().contains("INVENTORY_RESERVED"));
    }
    
    @Test
    public void testWorkflowQueries() {
        OrderSagaWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        // Start workflow asynchronously
        OrderRequest request = createSampleOrder();
        io.temporal.client.WorkflowClient.start(workflow::processOrder, request);
        
        // Query workflow state
        assertEquals("PENDING", workflow.getOrderStatus());
        assertNotNull(workflow.getCompletedSteps());
        assertNull(workflow.getFailureReason());
        
        // Let it complete
        testEnv.sleep(Duration.ofSeconds(2));
        
        // Query again
        String status = workflow.getOrderStatus();
        assertTrue("COMPLETED".equals(status) || "FAILED".equals(status));
    }
    
    private OrderRequest createSampleOrder() {
        OrderRequest request = new OrderRequest();
        request.setCustomerId("TEST-CUSTOMER");
        request.setShippingAddress("123 Test Street");
        request.setTotalAmount(new BigDecimal("100.00"));
        
        List<OrderRequest.OrderItem> items = new ArrayList<>();
        items.add(new OrderRequest.OrderItem("PRODUCT-001", 1, new BigDecimal("100.00")));
        request.setItems(items);
        
        return request;
    }
}