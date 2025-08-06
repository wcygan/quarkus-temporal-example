package com.example.document;

import com.example.document.activity.DocumentStorageActivities;
import com.example.document.repository.DocumentRepository;
import com.example.document.repository.DocumentRepository.DocumentInfo;
import com.example.test.MySQLTestResource;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DocumentStorageActivities with real MySQL database.
 * Tests the complete flow of document storage, retrieval, and status updates.
 */
@QuarkusTest
@WithTestResource(value = MySQLTestResource.class, restrictToAnnotatedClass = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DocumentStorageIntegrationTest {
    
    @Inject
    DocumentStorageActivities storageActivities;
    
    @Inject
    DocumentRepository repository;
    
    @Inject
    DataSource dataSource;
    
    private static Long testDocumentId;
    private static String testWorkflowId;
    
    @BeforeAll
    public static void setup() {
        testWorkflowId = "test-workflow-" + UUID.randomUUID();
    }
    
    @Test
    @Order(1)
    @DisplayName("Test storing a document in MySQL database")
    public void testStoreDocument() {
        // Prepare test data
        String fileName = "test-document.pdf";
        String mimeType = "application/pdf";
        byte[] content = "This is test document content".getBytes();
        
        // Store document
        Long documentId = storageActivities.storeDocument(testWorkflowId, fileName, mimeType, content);
        
        // Verify
        assertNotNull(documentId);
        assertTrue(documentId > 0);
        
        // Store for later tests
        testDocumentId = documentId;
        
        // Verify in database
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM documents WHERE id = ?")) {
            
            stmt.setLong(1, documentId);
            ResultSet rs = stmt.executeQuery();
            
            assertTrue(rs.next(), "Document should exist in database");
            assertEquals(testWorkflowId, rs.getString("workflow_id"));
            assertEquals(fileName, rs.getString("name"));
            assertEquals(mimeType, rs.getString("mime_type"));
            assertEquals(content.length, rs.getLong("size_bytes"));
            assertArrayEquals(content, rs.getBytes("content"));
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
    }
    
    @Test
    @Order(2)
    @DisplayName("Test retrieving a document from MySQL database")
    public void testRetrieveDocument() {
        assertNotNull(testDocumentId, "Test document should have been created");
        
        // Retrieve document content
        byte[] retrievedContent = storageActivities.retrieveDocument(testDocumentId);
        
        // Verify
        assertNotNull(retrievedContent);
        assertEquals("This is test document content", new String(retrievedContent));
    }
    
    @Test
    @Order(3)
    @DisplayName("Test updating document status in MySQL database")
    public void testUpdateDocumentStatus() {
        assertNotNull(testDocumentId, "Test document should have been created");
        
        // Update status
        storageActivities.updateDocumentStatus(testDocumentId, "PROCESSING");
        
        // Verify in database
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT status FROM document_metadata WHERE document_id = ?")) {
            
            stmt.setLong(1, testDocumentId);
            ResultSet rs = stmt.executeQuery();
            
            assertTrue(rs.next(), "Document metadata should exist");
            assertEquals("PROCESSING", rs.getString("status"));
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
        
        // Update to completed
        storageActivities.updateDocumentStatus(testDocumentId, "COMPLETED");
        
        // Verify completed status
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT status, processing_completed_at FROM document_metadata WHERE document_id = ?")) {
            
            stmt.setLong(1, testDocumentId);
            ResultSet rs = stmt.executeQuery();
            
            assertTrue(rs.next(), "Document metadata should exist");
            assertEquals("COMPLETED", rs.getString("status"));
            assertNotNull(rs.getTimestamp("processing_completed_at"), 
                "Completion timestamp should be set");
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
    }
    
    @Test
    @Order(4)
    @DisplayName("Test soft delete of document")
    public void testDeleteDocument() {
        assertNotNull(testDocumentId, "Test document should have been created");
        
        // Soft delete
        storageActivities.deleteDocument(testDocumentId);
        
        // Verify status is DELETED
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT status FROM document_metadata WHERE document_id = ?")) {
            
            stmt.setLong(1, testDocumentId);
            ResultSet rs = stmt.executeQuery();
            
            assertTrue(rs.next(), "Document metadata should exist");
            assertEquals("DELETED", rs.getString("status"));
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
        
        // Document should still exist in database (soft delete)
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM documents WHERE id = ?")) {
            
            stmt.setLong(1, testDocumentId);
            ResultSet rs = stmt.executeQuery();
            
            assertTrue(rs.next(), "Document should still exist (soft delete)");
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("Test storing document with validation")
    public void testDocumentValidation() {
        // Test empty content
        assertThrows(IllegalArgumentException.class, () -> {
            storageActivities.storeDocument("workflow-1", "empty.pdf", "application/pdf", new byte[0]);
        }, "Should reject empty content");
        
        // Test null content
        assertThrows(IllegalArgumentException.class, () -> {
            storageActivities.storeDocument("workflow-2", "null.pdf", "application/pdf", null);
        }, "Should reject null content");
        
        // Test oversized document (> 10MB)
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        assertThrows(IllegalArgumentException.class, () -> {
            storageActivities.storeDocument("workflow-3", "large.pdf", "application/pdf", largeContent);
        }, "Should reject documents larger than 10MB");
    }
    
    @Test
    @Order(6)
    @DisplayName("Test document info retrieval")
    public void testGetDocumentInfo() {
        // Store a new document
        String workflowId = "info-test-" + UUID.randomUUID();
        Long docId = storageActivities.storeDocument(
            workflowId, 
            "info-test.txt", 
            "text/plain", 
            "Test content for info".getBytes()
        );
        
        // Get document info
        DocumentInfo info = repository.getDocumentInfo(docId);
        
        // Verify
        assertNotNull(info);
        assertEquals(docId, info.getDocumentId());
        assertEquals(workflowId, info.getWorkflowId());
        assertEquals("info-test.txt", info.getFileName());
        assertEquals("text/plain", info.getMimeType());
        assertEquals(21, info.getSizeBytes()); // "Test content for info".length()
        assertEquals("UPLOADED", info.getStatus());
    }
    
    @Test
    @Order(7)
    @DisplayName("Test processing history recording")
    public void testProcessingHistory() {
        // Store a new document
        String workflowId = "history-test-" + UUID.randomUUID();
        Long docId = storageActivities.storeDocument(
            workflowId, 
            "history-test.pdf", 
            "application/pdf", 
            "History test content".getBytes()
        );
        
        // Record processing history
        repository.recordProcessingHistory(docId, "OCR", "STARTED", "{\"page\": 1}");
        repository.recordProcessingHistory(docId, "OCR", "COMPLETED", "{\"confidence\": 0.95}");
        repository.recordProcessingHistory(docId, "CLASSIFICATION", "STARTED", null);
        repository.recordProcessingHistory(docId, "CLASSIFICATION", "COMPLETED", "{\"type\": \"INVOICE\"}");
        
        // Verify history in database
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM document_processing_history WHERE document_id = ?")) {
            
            stmt.setLong(1, docId);
            ResultSet rs = stmt.executeQuery();
            
            assertTrue(rs.next());
            assertEquals(4, rs.getInt(1), "Should have 4 history records");
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
        
        // Verify specific history entries
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT stage, status, details FROM document_processing_history " +
                 "WHERE document_id = ? ORDER BY id")) {
            
            stmt.setLong(1, docId);
            ResultSet rs = stmt.executeQuery();
            
            // Verify we have all 4 records with correct stages and statuses
            int ocrStarted = 0, ocrCompleted = 0, classStarted = 0, classCompleted = 0;
            
            while (rs.next()) {
                String stage = rs.getString("stage");
                String status = rs.getString("status");
                
                if ("OCR".equals(stage) && "STARTED".equals(status)) ocrStarted++;
                if ("OCR".equals(stage) && "COMPLETED".equals(status)) ocrCompleted++;
                if ("CLASSIFICATION".equals(stage) && "STARTED".equals(status)) classStarted++;
                if ("CLASSIFICATION".equals(stage) && "COMPLETED".equals(status)) classCompleted++;
            }
            
            assertEquals(1, ocrStarted, "Should have one OCR STARTED record");
            assertEquals(1, ocrCompleted, "Should have one OCR COMPLETED record");
            assertEquals(1, classStarted, "Should have one CLASSIFICATION STARTED record");
            assertEquals(1, classCompleted, "Should have one CLASSIFICATION COMPLETED record");
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
    }
    
    @Test
    @Order(8)
    @DisplayName("Test concurrent document storage")
    public void testConcurrentDocumentStorage() throws InterruptedException {
        int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        Long[] documentIds = new Long[threadCount];
        Exception[] exceptions = new Exception[threadCount];
        
        // Create multiple threads to store documents concurrently
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    String workflowId = "concurrent-" + index + "-" + UUID.randomUUID();
                    documentIds[index] = storageActivities.storeDocument(
                        workflowId,
                        "concurrent-doc-" + index + ".txt",
                        "text/plain",
                        ("Concurrent content " + index).getBytes()
                    );
                } catch (Exception e) {
                    exceptions[index] = e;
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all documents were stored successfully
        for (int i = 0; i < threadCount; i++) {
            assertNull(exceptions[i], "Thread " + i + " should not have thrown exception");
            assertNotNull(documentIds[i], "Thread " + i + " should have stored document");
            assertTrue(documentIds[i] > 0, "Thread " + i + " should have valid document ID");
        }
        
        // Verify all documents exist in database
        for (Long docId : documentIds) {
            byte[] content = storageActivities.retrieveDocument(docId);
            assertNotNull(content, "Document " + docId + " should be retrievable");
        }
    }
    
    @AfterAll
    public static void cleanup() {
        // Cleanup is handled by database transaction rollback or test container restart
    }
}