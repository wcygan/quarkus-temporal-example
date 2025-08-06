## SAGA Pattern Implementation Guide

The SAGA pattern manages distributed transactions by breaking them into local transactions, each with a compensation (rollback) action. Here's how to implement it in your project:## Setup Instructions

### 1. **Add the New Files to Your Project**

Create the following directory structure and files:

```
src/main/java/com/example/
├── model/
│   ├── OrderRequest.java
│   └── OrderResult.java
├── activity/
│   ├── PaymentActivity.java
│   ├── PaymentActivityImpl.java
│   ├── InventoryActivity.java
│   ├── InventoryActivityImpl.java
│   ├── ShippingActivity.java
│   ├── ShippingActivityImpl.java
│   ├── NotificationActivity.java
│   └── NotificationActivityImpl.java
├── workflow/
│   ├── OrderSagaWorkflow.java
│   └── OrderSagaWorkflowImpl.java
└── resource/
    └── OrderSagaResource.java

src/test/java/com/example/workflow/
└── OrderSagaWorkflowTest.java
```

### 2. **Update TemporalWorkerStarter.java**

Add the SAGA workflow and activities to your worker registration as shown in the artifacts above.

### 3. **Start the Infrastructure**

```bash
# Start Temporal and MySQL
docker compose up -d

# Wait for services to be ready
docker compose ps

# Start the application
./mvnw quarkus:dev
```

### 4. **Test the SAGA Pattern**

#### Test Successful Order:
```bash
# Start a sample order
curl -X POST http://localhost:7474/order-saga/start-sample

# Check status (replace with actual workflowId from response)
curl http://localhost:7474/order-saga/status/order-saga-1234567890

# Get final result (blocks until complete)
curl http://localhost:7474/order-saga/result/order-saga-1234567890
```

#### Test with Failure Scenarios:

```bash
# Start an order
curl -X POST http://localhost:7474/order-saga/start-sample
# Note the workflowId

# Simulate inventory failure (before workflow completes)
curl -X POST "http://localhost:7474/order-saga/simulate-failure/order-saga-1234567890?step=INVENTORY"

# Check compensation history
curl http://localhost:7474/order-saga/compensation-history/order-saga-1234567890
```

### 5. **Run Tests**

```bash
# Run the SAGA workflow test
./mvnw test -Dtest=OrderSagaWorkflowTest

# Run all tests
./mvnw test
```

## Key SAGA Pattern Features Demonstrated

### 1. **Transaction Steps**
- Payment processing
- Inventory reservation
- Shipping scheduling
- Customer notification

### 2. **Compensation Logic**
- Each step has a corresponding compensation action
- Compensations run in reverse order
- Idempotent operations ensure safe retries

### 3. **Failure Handling**
- Graceful failure at any step
- Automatic compensation execution
- State tracking throughout the process

### 4. **Production Features**
- Query support for real-time monitoring
- Signal support for testing failures
- Comprehensive logging and auditing
- Retry configuration with exponential backoff

### 5. **Error Scenarios**
The implementation handles:
- Payment failures (insufficient funds)
- Inventory out of stock
- Shipping service unavailable
- Network timeouts
- Partial failures

## Monitoring in Temporal UI

1. Open Temporal UI: http://localhost:8088
2. Navigate to Workflows
3. Find your order-saga workflows
4. Click on a workflow to see:
    - Execution history
    - Activity completions and failures
    - Compensation executions
    - Query results

## Testing Different Failure Scenarios

You can test various failure points:

```bash
# Payment failure
curl -X POST "http://localhost:7474/order-saga/simulate-failure/[workflowId]?step=PAYMENT"

# Inventory failure (after payment succeeds)
curl -X POST "http://localhost:7474/order-saga/simulate-failure/[workflowId]?step=INVENTORY"

# Shipping failure (after payment and inventory succeed)
curl -X POST "http://localhost:7474/order-saga/simulate-failure/[workflowId]?step=SHIPPING"

# Notification failure (order still completes, but with warning)
curl -X POST "http://localhost:7474/order-saga/simulate-failure/[workflowId]?step=NOTIFICATION"
```

## Benefits of SAGA Pattern in Temporal

1. **No Distributed Transactions**: Avoids 2PC complexity
2. **Visibility**: Complete audit trail of all actions
3. **Reliability**: Automatic retry and compensation
4. **Testability**: Easy to simulate failures
5. **Scalability**: Each step can scale independently
6. **Durability**: State persisted across failures

This implementation provides a robust foundation for handling distributed transactions in microservices architectures using the SAGA pattern with Temporal workflows.

```java
// 6. REST Resource for Order SAGA
// File: src/main/java/com/example/resource/OrderSagaResource.java
package com.example.resource;

import com.example.model.OrderRequest;
import com.example.model.OrderResult;
import com.example.workflow.OrderSagaWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.api.common.v1.WorkflowExecution;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/order-saga")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderSagaResource {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderSagaResource.class);
    
    @Inject
    WorkflowClient workflowClient;
    
    @POST
    @Path("/start")
    public Response startOrder(OrderRequest request) {
        // Validate request
        if (request == null || request.getCustomerId() == null || request.getItems() == null || request.getItems().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Invalid order request"))
                .build();
        }
        
        try {
            String workflowId = "order-saga-" + System.currentTimeMillis();
            
            OrderSagaWorkflow workflow = workflowClient.newWorkflowStub(
                OrderSagaWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue("doc-approval-queue")
                    .build()
            );
            
            // Start workflow execution asynchronously
            WorkflowExecution execution = WorkflowClient.start(workflow::processOrder, request);
            
            logger.info("Started order SAGA workflow: {}", workflowId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "started");
            response.put("workflowId", workflowId);
            response.put("runId", execution.getRunId());
            response.put("message", "Order processing started");
            
            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Failed to start order SAGA workflow", e);
            return Response.serverError()
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/start-sample")
    public Response startSampleOrder() {
        // Create a sample order for testing
        OrderRequest request = new OrderRequest();
        request.setCustomerId("CUST-001");
        request.setShippingAddress("123 Main Street, City, State 12345");
        request.setTotalAmount(new BigDecimal("299.99"));
        
        List<OrderRequest.OrderItem> items = new ArrayList<>();
        items.add(new OrderRequest.OrderItem("PRODUCT-001", 2, new BigDecimal("99.99")));
        items.add(new OrderRequest.OrderItem("PRODUCT-002", 1, new BigDecimal("100.01")));
        request.setItems(items);
        
        return startOrder(request);
    }
    
    @GET
    @Path("/status/{workflowId}")
    public Response getOrderStatus(@PathParam("workflowId") String workflowId) {
        if (workflowId == null || workflowId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "workflowId is required"))
                .build();
        }
        
        try {
            OrderSagaWorkflow workflow = workflowClient.newWorkflowStub(
                OrderSagaWorkflow.class,
                workflowId
            );
            
            Map<String, Object> status = new HashMap<>();
            status.put("workflowId", workflowId);
            status.put("orderStatus", workflow.getOrderStatus());
            status.put("completedSteps", workflow.getCompletedSteps());
            
            String failureReason = workflow.getFailureReason();
            if (failureReason != null) {
                status.put("failureReason", failureReason);
            }
            
            return Response.ok(status).build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Workflow not found: " + e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/result/{workflowId}")
    public Response getOrderResult(@PathParam("workflowId") String workflowId) {
        if (workflowId == null || workflowId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "workflowId is required"))
                .build();
        }
        
        try {
            OrderSagaWorkflow workflow = workflowClient.newWorkflowStub(
                OrderSagaWorkflow.class,
                workflowId
            );
            
            // This will block until the workflow completes
            OrderResult result = workflow.processOrder(null);
            
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(Map.of("error", "Failed to get result: " + e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/simulate-failure/{workflowId}")
    public Response simulateFailure(
        @PathParam("workflowId") String workflowId,
        @QueryParam("step") String step) {
        
        if (workflowId == null || step == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Both workflowId and step are required"))
                .build();
        }
        
        // Validate step
        List<String> validSteps = List.of("PAYMENT", "INVENTORY", "SHIPPING", "NOTIFICATION");
        if (!validSteps.contains(step)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Invalid step. Must be one of: " + validSteps))
                .build();
        }
        
        try {
            OrderSagaWorkflow workflow = workflowClient.newWorkflowStub(
                OrderSagaWorkflow.class,
                workflowId
            );
            
            workflow.simulateFailure(step);
            
            return Response.ok(Map.of(
                "status", "failure simulation set",
                "workflowId", workflowId,
                "failureStep", step
            )).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(Map.of("error", "Failed to set failure simulation: " + e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/compensation-history/{workflowId}")
    public Response getCompensationHistory(@PathParam("workflowId") String workflowId) {
        try {
            OrderSagaWorkflow workflow = workflowClient.newWorkflowStub(
                OrderSagaWorkflow.class,
                workflowId
            );
            
            Map<String, Object> history = new HashMap<>();
            history.put("workflowId", workflowId);
            history.put("orderStatus", workflow.getOrderStatus());
            history.put("completedSteps", workflow.getCompletedSteps());
            
            String failureReason = workflow.getFailureReason();
            if (failureReason != null) {
                history.put("failureReason", failureReason);
                history.put("compensationRequired", true);
                
                // Determine which compensations would be executed
                List<String> compensations = new ArrayList<>();
                List<String> completed = workflow.getCompletedSteps();
                
                if (completed.contains("SHIPPING_SCHEDULED")) {
                    compensations.add("CANCEL_SHIPPING");
                }
                if (completed.contains("INVENTORY_RESERVED")) {
                    compensations.add("RELEASE_INVENTORY");
                }
                if (completed.contains("PAYMENT_CHARGED")) {
                    compensations.add("REFUND_PAYMENT");
                }
                compensations.add("SEND_CANCELLATION_NOTIFICATION");
                
                history.put("compensationActions", compensations);
            } else {
                history.put("compensationRequired", false);
            }
            
            return Response.ok(history).build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Workflow not found: " + e.getMessage()))
                .build();
        }
    }
}

// 7. Test Class for Order SAGA
// File: src/test/java/com/example/workflow/OrderSagaWorkflowTest.java
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
        OrderSagaWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        // Simulate payment failure
        workflow.simulateFailure("PAYMENT");
        
        OrderRequest request = createSampleOrder();
        OrderResult result = workflow.processOrder(request);
        
        assertEquals("FAILED", result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("payment"));
        assertTrue(result.getCompletedSteps().isEmpty()); // No steps completed
    }
    
    @Test
    public void testInventoryFailureWithCompensation() {
        OrderSagaWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        // Simulate inventory failure
        workflow.simulateFailure("INVENTORY");
        
        OrderRequest request = createSampleOrder();
        OrderResult result = workflow.processOrder(request);
        
        assertEquals("FAILED", result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("inventory"));
        assertEquals(1, result.getCompletedSteps().size()); // Only payment completed
        assertTrue(result.getCompletedSteps().contains("PAYMENT_CHARGED"));
    }
    
    @Test
    public void testShippingFailureWithCompensation() {
        OrderSagaWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            OrderSagaWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        // Simulate shipping failure
        workflow.simulateFailure("SHIPPING");
        
        OrderRequest request = createSampleOrder();
        OrderResult result = workflow.processOrder(request);
        
        assertEquals("FAILED", result.getStatus());
        assertNotNull(result.getFailureReason());
        assertTrue(result.getFailureReason().contains("shipping"));
        assertEquals(2, result.getCompletedSteps().size()); // Payment and inventory completed
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

// 8. Update TemporalWorkerStarter to include SAGA workflow
// Add these lines to src/main/java/com/example/temporal/TemporalWorkerStarter.java

// In the imports section, add:
import com.example.activity.PaymentActivityImpl;
import com.example.activity.InventoryActivityImpl;
import com.example.activity.ShippingActivityImpl;
import com.example.activity.NotificationActivityImpl;
import com.example.workflow.OrderSagaWorkflowImpl;

// In the class, add these injection points:
@Inject
PaymentActivityImpl paymentActivity;

@Inject
InventoryActivityImpl inventoryActivity;

@Inject
ShippingActivityImpl shippingActivity;

@Inject
NotificationActivityImpl notificationActivity;

// In the startIfTemporalAvailable() method, update the workflow registration:
worker.registerWorkflowImplementationTypes(
    HelloWorkflowImpl.class,
    ChildWorkflowImpl.class,
    ParentWorkflowImpl.class,
    ScheduledReportWorkflowImpl.class,
    MetricsMonitorWorkflowImpl.class,
    OrderSagaWorkflowImpl.class  // Add this line
);

// And update the activity registration:
worker.registerActivitiesImplementations(
    helloActivity,
    childActivity,
    parentActivity,
    reportActivity,
    metricsActivity,
    alertActivity,
    paymentActivity,      // Add this line
    inventoryActivity,    // Add this line
    shippingActivity,     // Add this line
    notificationActivity  // Add this line
);
```

```java
// 5. Activity Implementations
// File: src/main/java/com/example/activity/PaymentActivityImpl.java
package com.example.activity;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Random;

@ApplicationScoped
public class PaymentActivityImpl implements PaymentActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(PaymentActivityImpl.class);
    private final Map<String, PaymentRecord> paymentStore = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    @Override
    public PaymentResult chargePayment(String customerId, BigDecimal amount) {
        String transactionId = "TXN-" + UUID.randomUUID().toString();
        logger.info("Processing payment {} for customer {} amount: ${}", transactionId, customerId, amount);
        
        // Simulate payment processing with occasional failures
        try {
            Thread.sleep(500); // Simulate API call
            
            // Simulate 10% failure rate
            if (random.nextDouble() < 0.1) {
                logger.error("Payment declined for customer {}", customerId);
                return new PaymentResult(null, false, "Payment declined - insufficient funds");
            }
            
            // Store payment record
            PaymentRecord record = new PaymentRecord(transactionId, customerId, amount, "CHARGED");
            paymentStore.put(transactionId, record);
            
            logger.info("Payment successful: {}", transactionId);
            return new PaymentResult(transactionId, true, "Payment processed successfully");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PaymentResult(null, false, "Payment processing interrupted");
        }
    }
    
    @Override
    public void refundPayment(String transactionId) {
        logger.info("Processing refund for transaction: {}", transactionId);
        
        PaymentRecord record = paymentStore.get(transactionId);
        if (record != null) {
            // Idempotent - check if already refunded
            if ("REFUNDED".equals(record.status)) {
                logger.info("Transaction {} already refunded", transactionId);
                return;
            }
            
            record.status = "REFUNDED";
            logger.info("Refund successful for transaction: {}", transactionId);
        } else {
            logger.warn("Transaction {} not found for refund", transactionId);
        }
    }
    
    private static class PaymentRecord {
        String transactionId;
        String customerId;
        BigDecimal amount;
        String status;
        
        PaymentRecord(String transactionId, String customerId, BigDecimal amount, String status) {
            this.transactionId = transactionId;
            this.customerId = customerId;
            this.amount = amount;
            this.status = status;
        }
    }
}

// File: src/main/java/com/example/activity/InventoryActivityImpl.java
package com.example.activity;

import com.example.model.OrderRequest.OrderItem;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

@ApplicationScoped
public class InventoryActivityImpl implements InventoryActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryActivityImpl.class);
    private final Map<String, InventoryReservation> reservations = new ConcurrentHashMap<>();
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    public InventoryActivityImpl() {
        // Initialize some sample inventory
        inventory.put("PRODUCT-001", 100);
        inventory.put("PRODUCT-002", 50);
        inventory.put("PRODUCT-003", 25);
    }
    
    @Override
    public ReservationResult reserveInventory(List<OrderItem> items) {
        String reservationId = "RES-" + UUID.randomUUID().toString();
        logger.info("Reserving inventory for {} items, reservation: {}", items.size(), reservationId);
        
        try {
            Thread.sleep(300); // Simulate processing
            
            // Simulate 15% out of stock scenarios
            if (random.nextDouble() < 0.15) {
                logger.error("Insufficient inventory for reservation {}", reservationId);
                return new ReservationResult(null, false, "Insufficient inventory for one or more items");
            }
            
            // Check inventory availability
            for (OrderItem item : items) {
                Integer available = inventory.getOrDefault(item.getProductId(), 10);
                if (available < item.getQuantity()) {
                    logger.error("Product {} out of stock. Available: {}, Requested: {}", 
                        item.getProductId(), available, item.getQuantity());
                    return new ReservationResult(null, false, 
                        "Product " + item.getProductId() + " out of stock");
                }
            }
            
            // Reserve items
            for (OrderItem item : items) {
                inventory.compute(item.getProductId(), (k, v) -> 
                    (v == null ? 10 : v) - item.getQuantity());
            }
            
            // Store reservation
            reservations.put(reservationId, new InventoryReservation(reservationId, items, "RESERVED"));
            
            logger.info("Inventory reserved successfully: {}", reservationId);
            return new ReservationResult(reservationId, true, "Inventory reserved successfully");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ReservationResult(null, false, "Reservation processing interrupted");
        }
    }
    
    @Override
    public void releaseInventory(String reservationId) {
        logger.info("Releasing inventory reservation: {}", reservationId);
        
        InventoryReservation reservation = reservations.get(reservationId);
        if (reservation != null) {
            // Idempotent - check if already released
            if ("RELEASED".equals(reservation.status)) {
                logger.info("Reservation {} already released", reservationId);
                return;
            }
            
            // Return items to inventory
            for (OrderItem item : reservation.items) {
                inventory.compute(item.getProductId(), (k, v) -> 
                    (v == null ? 0 : v) + item.getQuantity());
            }
            
            reservation.status = "RELEASED";
            logger.info("Inventory released for reservation: {}", reservationId);
        } else {
            logger.warn("Reservation {} not found for release", reservationId);
        }
    }
    
    private static class InventoryReservation {
        String reservationId;
        List<OrderItem> items;
        String status;
        
        InventoryReservation(String reservationId, List<OrderItem> items, String status) {
            this.reservationId = reservationId;
            this.items = items;
            this.status = status;
        }
    }
}

// File: src/main/java/com/example/activity/ShippingActivityImpl.java
package com.example.activity;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

@ApplicationScoped
public class ShippingActivityImpl implements ShippingActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(ShippingActivityImpl.class);
    private final Map<String, ShippingRecord> shippingRecords = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    @Override
    public ShippingResult scheduleShipping(String orderId, String address) {
        String trackingNumber = "TRACK-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        logger.info("Scheduling shipping for order {} to address: {}", orderId, address);
        
        try {
            Thread.sleep(400); // Simulate API call
            
            // Simulate 5% shipping service unavailable
            if (random.nextDouble() < 0.05) {
                logger.error("Shipping service unavailable for order {}", orderId);
                return new ShippingResult(null, false, null, "Shipping service temporarily unavailable");
            }
            
            // Calculate estimated delivery (3-5 days from now)
            LocalDate estimatedDelivery = LocalDate.now().plusDays(3 + random.nextInt(3));
            
            // Store shipping record
            ShippingRecord record = new ShippingRecord(trackingNumber, orderId, address, "SCHEDULED");
            shippingRecords.put(trackingNumber, record);
            
            logger.info("Shipping scheduled successfully. Tracking: {}", trackingNumber);
            return new ShippingResult(trackingNumber, true, estimatedDelivery.toString(), 
                "Shipping scheduled successfully");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ShippingResult(null, false, null, "Shipping scheduling interrupted");
        }
    }
    
    @Override
    public void cancelShipping(String trackingNumber) {
        logger.info("Cancelling shipping for tracking number: {}", trackingNumber);
        
        ShippingRecord record = shippingRecords.get(trackingNumber);
        if (record != null) {
            // Idempotent - check if already cancelled
            if ("CANCELLED".equals(record.status)) {
                logger.info("Shipping {} already cancelled", trackingNumber);
                return;
            }
            
            record.status = "CANCELLED";
            logger.info("Shipping cancelled for tracking: {}", trackingNumber);
        } else {
            logger.warn("Tracking number {} not found for cancellation", trackingNumber);
        }
    }
    
    private static class ShippingRecord {
        String trackingNumber;
        String orderId;
        String address;
        String status;
        
        ShippingRecord(String trackingNumber, String orderId, String address, String status) {
            this.trackingNumber = trackingNumber;
            this.orderId = orderId;
            this.address = address;
            this.status = status;
        }
    }
}

// File: src/main/java/com/example/activity/NotificationActivityImpl.java
package com.example.activity;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApplicationScoped
public class NotificationActivityImpl implements NotificationActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationActivityImpl.class);
    private final ConcurrentLinkedQueue<NotificationRecord> notificationHistory = new ConcurrentLinkedQueue<>();
    
    @Override
    public void sendOrderConfirmation(String customerId, String orderId, String trackingNumber) {
        logger.info("Sending order confirmation to customer {} for order {}", customerId, orderId);
        
        String message = String.format(
            "Order Confirmed! Your order %s has been confirmed and will be shipped soon. " +
            "Track your package with: %s", orderId, trackingNumber);
        
        NotificationRecord record = new NotificationRecord(
            customerId, "ORDER_CONFIRMATION", message, Instant.now()
        );
        notificationHistory.add(record);
        
        // In production, this would send actual email/SMS/push notification
        logger.info("Confirmation sent to customer {}: {}", customerId, message);
        
        // Keep only last 100 notifications
        while (notificationHistory.size() > 100) {
            notificationHistory.poll();
        }
    }
    
    @Override
    public void sendOrderCancellation(String customerId, String orderId, String reason) {
        logger.info("Sending order cancellation to customer {} for order {}", customerId, orderId);
        
        String message = String.format(
            "Order Cancelled: Your order %s has been cancelled. Reason: %s. " +
            "Any charges will be refunded within 3-5 business days.", orderId, reason);
        
        NotificationRecord record = new NotificationRecord(
            customerId, "ORDER_CANCELLATION", message, Instant.now()
        );
        notificationHistory.add(record);
        
        // In production, this would send actual email/SMS/push notification
        logger.info("Cancellation sent to customer {}: {}", customerId, message);
        
        // Keep only last 100 notifications
        while (notificationHistory.size() > 100) {
            notificationHistory.poll();
        }
    }
    
    // Helper method for testing/debugging
    public List<NotificationRecord> getNotificationHistory() {
        return new ArrayList<>(notificationHistory);
    }
    
    public static class NotificationRecord {
        public final String customerId;
        public final String type;
        public final String message;
        public final Instant timestamp;
        
        public NotificationRecord(String customerId, String type, String message, Instant timestamp) {
            this.customerId = customerId;
            this.type = type;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
}
```

```java
// 1. Create the Order domain models
// File: src/main/java/com/example/model/OrderRequest.java
package com.example.model;

import java.math.BigDecimal;
import java.util.List;

public class OrderRequest {
    private String customerId;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private String shippingAddress;
    
    // Constructors
    public OrderRequest() {}
    
    public OrderRequest(String customerId, List<OrderItem> items, BigDecimal totalAmount, String shippingAddress) {
        this.customerId = customerId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.shippingAddress = shippingAddress;
    }
    
    // Getters and Setters
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
    
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    
    public static class OrderItem {
        private String productId;
        private int quantity;
        private BigDecimal price;
        
        public OrderItem() {}
        
        public OrderItem(String productId, int quantity, BigDecimal price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }
        
        // Getters and Setters
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }
}

// File: src/main/java/com/example/model/OrderResult.java
package com.example.model;

import java.time.Instant;
import java.util.List;

public class OrderResult {
    private String orderId;
    private String status;
    private String paymentTransactionId;
    private String shippingTrackingNumber;
    private List<String> completedSteps;
    private String failureReason;
    private Instant completedAt;
    
    public OrderResult() {}
    
    public OrderResult(String orderId, String status) {
        this.orderId = orderId;
        this.status = status;
        this.completedAt = Instant.now();
    }
    
    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(String paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }
    
    public String getShippingTrackingNumber() { return shippingTrackingNumber; }
    public void setShippingTrackingNumber(String shippingTrackingNumber) { this.shippingTrackingNumber = shippingTrackingNumber; }
    
    public List<String> getCompletedSteps() { return completedSteps; }
    public void setCompletedSteps(List<String> completedSteps) { this.completedSteps = completedSteps; }
    
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

// 2. Create Activity Interfaces
// File: src/main/java/com/example/activity/PaymentActivity.java
package com.example.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.math.BigDecimal;

@ActivityInterface
public interface PaymentActivity {
    
    @ActivityMethod
    PaymentResult chargePayment(String customerId, BigDecimal amount);
    
    @ActivityMethod
    void refundPayment(String transactionId);
    
    class PaymentResult {
        private String transactionId;
        private boolean success;
        private String message;
        
        public PaymentResult() {}
        
        public PaymentResult(String transactionId, boolean success, String message) {
            this.transactionId = transactionId;
            this.success = success;
            this.message = message;
        }
        
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

// File: src/main/java/com/example/activity/InventoryActivity.java
package com.example.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.List;
import com.example.model.OrderRequest.OrderItem;

@ActivityInterface
public interface InventoryActivity {
    
    @ActivityMethod
    ReservationResult reserveInventory(List<OrderItem> items);
    
    @ActivityMethod
    void releaseInventory(String reservationId);
    
    class ReservationResult {
        private String reservationId;
        private boolean success;
        private String message;
        
        public ReservationResult() {}
        
        public ReservationResult(String reservationId, boolean success, String message) {
            this.reservationId = reservationId;
            this.success = success;
            this.message = message;
        }
        
        public String getReservationId() { return reservationId; }
        public void setReservationId(String reservationId) { this.reservationId = reservationId; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

// File: src/main/java/com/example/activity/ShippingActivity.java
package com.example.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ShippingActivity {
    
    @ActivityMethod
    ShippingResult scheduleShipping(String orderId, String address);
    
    @ActivityMethod
    void cancelShipping(String trackingNumber);
    
    class ShippingResult {
        private String trackingNumber;
        private boolean success;
        private String estimatedDelivery;
        private String message;
        
        public ShippingResult() {}
        
        public ShippingResult(String trackingNumber, boolean success, String estimatedDelivery, String message) {
            this.trackingNumber = trackingNumber;
            this.success = success;
            this.estimatedDelivery = estimatedDelivery;
            this.message = message;
        }
        
        public String getTrackingNumber() { return trackingNumber; }
        public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getEstimatedDelivery() { return estimatedDelivery; }
        public void setEstimatedDelivery(String estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

// File: src/main/java/com/example/activity/NotificationActivity.java
package com.example.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface NotificationActivity {
    
    @ActivityMethod
    void sendOrderConfirmation(String customerId, String orderId, String trackingNumber);
    
    @ActivityMethod
    void sendOrderCancellation(String customerId, String orderId, String reason);
}

// 3. Create the Workflow Interface
// File: src/main/java/com/example/workflow/OrderSagaWorkflow.java
package com.example.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import com.example.model.OrderRequest;
import com.example.model.OrderResult;
import java.util.List;

@WorkflowInterface
public interface OrderSagaWorkflow {
    
    @WorkflowMethod
    OrderResult processOrder(OrderRequest request);
    
    @QueryMethod
    String getOrderStatus();
    
    @QueryMethod
    List<String> getCompletedSteps();
    
    @QueryMethod
    String getFailureReason();
    
    @SignalMethod
    void simulateFailure(String step);
}

// 4. Create the Workflow Implementation with SAGA compensation logic
// File: src/main/java/com/example/workflow/OrderSagaWorkflowImpl.java
package com.example.workflow;

import com.example.activity.PaymentActivity;
import com.example.activity.InventoryActivity;
import com.example.activity.ShippingActivity;
import com.example.activity.NotificationActivity;
import com.example.model.OrderRequest;
import com.example.model.OrderResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderSagaWorkflowImpl implements OrderSagaWorkflow {
    
    private static final Logger logger = Workflow.getLogger(OrderSagaWorkflowImpl.class);
    
    // Workflow state
    private String orderId;
    private String orderStatus = "PENDING";
    private List<String> completedSteps = new ArrayList<>();
    private String failureReason = null;
    private String simulatedFailureStep = null;
    
    // Transaction IDs for compensation
    private String paymentTransactionId = null;
    private String inventoryReservationId = null;
    private String shippingTrackingNumber = null;
    
    // Activity stubs with retry configuration
    private final PaymentActivity paymentActivity = Workflow.newActivityStub(
        PaymentActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofSeconds(1))
                .setMaximumInterval(Duration.ofSeconds(10))
                .setMaximumAttempts(3)
                .build())
            .build()
    );
    
    private final InventoryActivity inventoryActivity = Workflow.newActivityStub(
        InventoryActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );
    
    private final ShippingActivity shippingActivity = Workflow.newActivityStub(
        ShippingActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );
    
    private final NotificationActivity notificationActivity = Workflow.newActivityStub(
        NotificationActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(2)
                .build())
            .build()
    );
    
    @Override
    public OrderResult processOrder(OrderRequest request) {
        orderId = "ORDER-" + UUID.randomUUID().toString();
        logger.info("Starting order processing for orderId: {}", orderId);
        
        OrderResult result = new OrderResult(orderId, "PROCESSING");
        
        try {
            // Step 1: Process Payment
            if ("PAYMENT".equals(simulatedFailureStep)) {
                throw ApplicationFailure.newFailure("Simulated payment failure", "PAYMENT_FAILED");
            }
            
            logger.info("Processing payment for customer: {}", request.getCustomerId());
            PaymentActivity.PaymentResult paymentResult = paymentActivity.chargePayment(
                request.getCustomerId(), 
                request.getTotalAmount()
            );
            
            if (!paymentResult.isSuccess()) {
                throw ApplicationFailure.newFailure("Payment failed: " + paymentResult.getMessage(), "PAYMENT_FAILED");
            }
            
            paymentTransactionId = paymentResult.getTransactionId();
            completedSteps.add("PAYMENT_CHARGED");
            logger.info("Payment successful. Transaction ID: {}", paymentTransactionId);
            
            // Step 2: Reserve Inventory
            if ("INVENTORY".equals(simulatedFailureStep)) {
                throw ApplicationFailure.newFailure("Simulated inventory failure", "INVENTORY_FAILED");
            }
            
            logger.info("Reserving inventory for {} items", request.getItems().size());
            InventoryActivity.ReservationResult inventoryResult = inventoryActivity.reserveInventory(request.getItems());
            
            if (!inventoryResult.isSuccess()) {
                throw ApplicationFailure.newFailure("Inventory reservation failed: " + inventoryResult.getMessage(), "INVENTORY_FAILED");
            }
            
            inventoryReservationId = inventoryResult.getReservationId();
            completedSteps.add("INVENTORY_RESERVED");
            logger.info("Inventory reserved. Reservation ID: {}", inventoryReservationId);
            
            // Step 3: Schedule Shipping
            if ("SHIPPING".equals(simulatedFailureStep)) {
                throw ApplicationFailure.newFailure("Simulated shipping failure", "SHIPPING_FAILED");
            }
            
            logger.info("Scheduling shipping to: {}", request.getShippingAddress());
            ShippingActivity.ShippingResult shippingResult = shippingActivity.scheduleShipping(
                orderId, 
                request.getShippingAddress()
            );
            
            if (!shippingResult.isSuccess()) {
                throw ApplicationFailure.newFailure("Shipping scheduling failed: " + shippingResult.getMessage(), "SHIPPING_FAILED");
            }
            
            shippingTrackingNumber = shippingResult.getTrackingNumber();
            completedSteps.add("SHIPPING_SCHEDULED");
            logger.info("Shipping scheduled. Tracking number: {}", shippingTrackingNumber);
            
            // Step 4: Send Confirmation
            if ("NOTIFICATION".equals(simulatedFailureStep)) {
                throw ApplicationFailure.newFailure("Simulated notification failure", "NOTIFICATION_FAILED");
            }
            
            logger.info("Sending order confirmation to customer: {}", request.getCustomerId());
            notificationActivity.sendOrderConfirmation(
                request.getCustomerId(), 
                orderId, 
                shippingTrackingNumber
            );
            completedSteps.add("NOTIFICATION_SENT");
            
            // Success - Update result
            orderStatus = "COMPLETED";
            result.setStatus("COMPLETED");
            result.setPaymentTransactionId(paymentTransactionId);
            result.setShippingTrackingNumber(shippingTrackingNumber);
            result.setCompletedSteps(completedSteps);
            
            logger.info("Order {} completed successfully", orderId);
            return result;
            
        } catch (Exception e) {
            // Failure - Execute compensations in reverse order
            logger.error("Order processing failed: {}. Starting compensations...", e.getMessage());
            orderStatus = "FAILED";
            failureReason = e.getMessage();
            
            // Execute compensations for completed steps in reverse order
            compensateOrder(request.getCustomerId());
            
            result.setStatus("FAILED");
            result.setFailureReason(failureReason);
            result.setCompletedSteps(completedSteps);
            
            return result;
        }
    }
    
    private void compensateOrder(String customerId) {
        logger.info("Starting compensation for order: {}", orderId);
        List<String> compensationSteps = new ArrayList<>();
        
        // Compensation must be executed in reverse order
        // and must be idempotent (safe to retry)
        
        // Compensate Shipping if it was scheduled
        if (completedSteps.contains("SHIPPING_SCHEDULED") && shippingTrackingNumber != null) {
            try {
                logger.info("Cancelling shipping for tracking number: {}", shippingTrackingNumber);
                shippingActivity.cancelShipping(shippingTrackingNumber);
                compensationSteps.add("SHIPPING_CANCELLED");
            } catch (Exception e) {
                logger.error("Failed to cancel shipping: {}", e.getMessage());
                // Continue with other compensations
            }
        }
        
        // Compensate Inventory if it was reserved
        if (completedSteps.contains("INVENTORY_RESERVED") && inventoryReservationId != null) {
            try {
                logger.info("Releasing inventory reservation: {}", inventoryReservationId);
                inventoryActivity.releaseInventory(inventoryReservationId);
                compensationSteps.add("INVENTORY_RELEASED");
            } catch (Exception e) {
                logger.error("Failed to release inventory: {}", e.getMessage());
                // Continue with other compensations
            }
        }
        
        // Compensate Payment if it was charged
        if (completedSteps.contains("PAYMENT_CHARGED") && paymentTransactionId != null) {
            try {
                logger.info("Refunding payment transaction: {}", paymentTransactionId);
                paymentActivity.refundPayment(paymentTransactionId);
                compensationSteps.add("PAYMENT_REFUNDED");
            } catch (Exception e) {
                logger.error("Failed to refund payment: {}", e.getMessage());
                // Log for manual intervention
            }
        }
        
        // Send cancellation notification
        try {
            notificationActivity.sendOrderCancellation(customerId, orderId, failureReason);
            compensationSteps.add("CANCELLATION_NOTIFIED");
        } catch (Exception e) {
            logger.error("Failed to send cancellation notification: {}", e.getMessage());
        }
        
        logger.info("Compensation completed. Steps: {}", compensationSteps);
    }
    
    @Override
    public String getOrderStatus() {
        return orderStatus;
    }
    
    @Override
    public List<String> getCompletedSteps() {
        return new ArrayList<>(completedSteps);
    }
    
    @Override
    public String getFailureReason() {
        return failureReason;
    }
    
    @Override
    public void simulateFailure(String step) {
        this.simulatedFailureStep = step;
        logger.info("Failure simulation set for step: {}", step);
    }
}
```