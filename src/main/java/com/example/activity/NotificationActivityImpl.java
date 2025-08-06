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