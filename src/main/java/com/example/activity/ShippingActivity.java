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