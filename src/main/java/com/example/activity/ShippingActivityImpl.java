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