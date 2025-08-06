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