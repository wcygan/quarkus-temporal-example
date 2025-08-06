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