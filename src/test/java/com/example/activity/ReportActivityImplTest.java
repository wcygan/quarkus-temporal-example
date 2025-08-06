package com.example.activity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class ReportActivityImplTest {
    
    private ReportActivityImpl reportActivity;
    
    @BeforeEach
    public void setUp() {
        reportActivity = new ReportActivityImpl();
    }
    
    @Test
    public void testCaptureTimestamp() {
        String timestamp = Instant.now().toString();
        String reportId = reportActivity.captureTimestamp(timestamp);
        
        assertNotNull(reportId);
        assertTrue(reportId.startsWith("report-"));
        
        // Verify report was stored
        assertEquals(1, reportActivity.getAllReports().size());
        assertEquals(timestamp, reportActivity.getAllReports().get(reportId));
    }
    
    @Test
    public void testMultipleReports() {
        String timestamp1 = "2024-01-01T10:00:00Z";
        String timestamp2 = "2024-01-01T10:05:00Z";
        String timestamp3 = "2024-01-01T10:10:00Z";
        
        String reportId1 = reportActivity.captureTimestamp(timestamp1);
        String reportId2 = reportActivity.captureTimestamp(timestamp2);
        String reportId3 = reportActivity.captureTimestamp(timestamp3);
        
        // All report IDs should be unique
        assertNotEquals(reportId1, reportId2);
        assertNotEquals(reportId2, reportId3);
        assertNotEquals(reportId1, reportId3);
        
        // All reports should be stored
        assertEquals(3, reportActivity.getAllReports().size());
        assertEquals(timestamp1, reportActivity.getAllReports().get(reportId1));
        assertEquals(timestamp2, reportActivity.getAllReports().get(reportId2));
        assertEquals(timestamp3, reportActivity.getAllReports().get(reportId3));
    }
}