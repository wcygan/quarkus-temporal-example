package com.example.activity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AlertActivityImplTest {
    
    private AlertActivityImpl alertActivity;
    
    @BeforeEach
    public void setUp() {
        alertActivity = new AlertActivityImpl();
    }
    
    @Test
    public void testSendAlert() {
        String message = "Test alert message";
        
        // Should not throw exception
        assertDoesNotThrow(() -> alertActivity.sendAlert(message));
        
        // Verify alert was stored
        assertFalse(alertActivity.getAlertHistory().isEmpty());
        
        String lastAlert = alertActivity.getAlertHistory().peek();
        assertNotNull(lastAlert);
        assertTrue(lastAlert.contains(message));
    }
    
    @Test
    public void testMultipleAlerts() {
        alertActivity.sendAlert("Alert 1");
        alertActivity.sendAlert("Alert 2");
        alertActivity.sendAlert("Alert 3");
        
        assertEquals(3, alertActivity.getAlertHistory().size());
    }
    
    @Test
    public void testAlertHistoryLimit() {
        // Send more than 100 alerts
        for (int i = 0; i < 150; i++) {
            alertActivity.sendAlert("Alert " + i);
        }
        
        // Should only keep last 100
        assertEquals(100, alertActivity.getAlertHistory().size());
        
        // Verify the oldest alerts were removed
        boolean containsOldAlert = alertActivity.getAlertHistory().stream()
            .anyMatch(alert -> alert.contains("Alert 0"));
        assertFalse(containsOldAlert);
        
        // Verify recent alerts are kept
        boolean containsRecentAlert = alertActivity.getAlertHistory().stream()
            .anyMatch(alert -> alert.contains("Alert 149"));
        assertTrue(containsRecentAlert);
    }
}