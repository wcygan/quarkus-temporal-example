package com.example.workflow;

import com.example.activity.*;
import com.example.model.OrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Test-specific activity implementations that don't have random failures.
 * These are deterministic for reliable integration testing.
 */
public class TestActivityImplementations {
    
    private static final Logger logger = LoggerFactory.getLogger(TestActivityImplementations.class);
    
    public static class DeterministicPaymentActivity implements PaymentActivity {
        @Override
        public PaymentResult chargePayment(String customerId, BigDecimal amount) {
            String transactionId = "TXN-" + UUID.randomUUID().toString();
            logger.info("Test payment processing {} for customer {} amount: ${}", transactionId, customerId, amount);
            
            try {
                Thread.sleep(100); // Shorter delay for tests
                logger.info("Test payment successful: {}", transactionId);
                return new PaymentResult(transactionId, true, "Payment processed successfully");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new PaymentResult(null, false, "Payment processing interrupted");
            }
        }
        
        @Override
        public void refundPayment(String transactionId) {
            logger.info("Test refund for transaction: {}", transactionId);
            try {
                Thread.sleep(50); // Shorter delay for tests
                logger.info("Test refund successful for transaction: {}", transactionId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public static class DeterministicInventoryActivity implements InventoryActivity {
        @Override
        public ReservationResult reserveInventory(List<OrderRequest.OrderItem> items) {
            String reservationId = "RES-" + UUID.randomUUID().toString();
            logger.info("Test inventory reservation {} for {} items", reservationId, items.size());
            
            try {
                Thread.sleep(100); // Shorter delay for tests
                logger.info("Test inventory reserved successfully: {}", reservationId);
                return new ReservationResult(reservationId, true, "Inventory reserved successfully");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ReservationResult(null, false, "Reservation interrupted");
            }
        }
        
        @Override
        public void releaseInventory(String reservationId) {
            logger.info("Test releasing inventory reservation: {}", reservationId);
            logger.info("Test inventory released for reservation: {}", reservationId);
        }
    }
    
    public static class DeterministicShippingActivity implements ShippingActivity {
        @Override
        public ShippingResult scheduleShipping(String orderId, String shippingAddress) {
            logger.info("Test scheduling shipping for order {} to address: {}", orderId, shippingAddress);
            
            try {
                Thread.sleep(100); // Shorter delay for tests
                String trackingNumber = "TRACK-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
                LocalDate estimatedDelivery = LocalDate.now().plusDays(3);
                
                logger.info("Test shipping scheduled successfully. Tracking: {}", trackingNumber);
                return new ShippingResult(trackingNumber, true, estimatedDelivery.toString(), "Shipping scheduled");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ShippingResult(null, false, null, "Shipping scheduling interrupted");
            }
        }
        
        @Override
        public void cancelShipping(String trackingNumber) {
            logger.info("Test cancelling shipping for tracking number: {}", trackingNumber);
            logger.info("Test shipping cancelled for tracking: {}", trackingNumber);
        }
    }
    
    public static class DeterministicNotificationActivity implements NotificationActivity {
        @Override
        public void sendOrderConfirmation(String customerId, String orderId, String trackingNumber) {
            logger.info("Test sending order confirmation to customer {} for order {}", customerId, orderId);
            String message = String.format("Order Confirmed! Your order %s has been confirmed and will be shipped soon. Track your package with: %s", 
                orderId, trackingNumber);
            logger.info("Test confirmation sent to customer {}: {}", customerId, message);
        }
        
        @Override
        public void sendOrderCancellation(String customerId, String orderId, String reason) {
            logger.info("Test sending order cancellation to customer {} for order {}", customerId, orderId);
            String message = String.format("Order Cancelled: Your order %s has been cancelled. Reason: %s. Any charges will be refunded within 3-5 business days.", 
                orderId, reason);
            logger.info("Test cancellation sent to customer {}: {}", customerId, message);
        }
    }
}