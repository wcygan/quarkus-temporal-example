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