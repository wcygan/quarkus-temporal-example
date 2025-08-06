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