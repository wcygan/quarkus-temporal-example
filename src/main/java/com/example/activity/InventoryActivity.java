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