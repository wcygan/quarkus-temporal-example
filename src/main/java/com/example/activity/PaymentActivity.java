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