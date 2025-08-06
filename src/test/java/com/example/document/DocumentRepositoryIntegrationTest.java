package com.example.document;

import com.example.document.model.ReviewDecision;
import com.example.document.repository.DocumentRepository;
import com.example.document.repository.DocumentRepository.DocumentInfo;
import com.example.document.repository.DocumentRepository.ReviewTaskInfo;
import com.example.test.MySQLTestResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DocumentRepository with real MySQL database.
 * Tests complex database operations including reviews, statistics, and queries.
 */
@QuarkusTest
@WithTestResource(value = MySQLTestResource.class, restrictToAnnotatedClass = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DocumentRepositoryIntegrationTest {
    
    @Inject
    DocumentRepository repository;
    
    @Inject
    DataSource dataSource;
    
    @Inject
    ObjectMapper objectMapper;
    
    private static Long testDocumentId;
    private static String testWorkflowId;
    private static String testActivityToken;
    
    @BeforeAll
    public static void setup() {
        testWorkflowId = "repo-test-" + UUID.randomUUID();
        testActivityToken = "activity-token-" + UUID.randomUUID();
    }
    
    @Test
    @Order(1)
    @DisplayName("Test document creation with metadata")
    public void testSaveDocumentWithMetadata() {
        // Save document
        byte[] content = "Repository test content".getBytes();
        testDocumentId = repository.saveDocument(testWorkflowId, "repo-test.pdf", "application/pdf", content);
        
        // Verify document and metadata were created
        assertNotNull(testDocumentId);
        
        DocumentInfo info = repository.getDocumentInfo(testDocumentId);
        assertNotNull(info);
        assertEquals(testWorkflowId, info.getWorkflowId());
        assertEquals("repo-test.pdf", info.getFileName());
        assertEquals("application/pdf", info.getMimeType());
        assertEquals(content.length, info.getSizeBytes());
        assertEquals("UPLOADED", info.getStatus());
    }
    
    @Test
    @Order(2)
    @DisplayName("Test OCR result update")
    public void testUpdateOcrResult() {
        assertNotNull(testDocumentId);
        
        String ocrText = "This is the extracted text from OCR processing";
        double confidence = 0.92;
        
        // Update OCR result
        repository.updateOcrResult(testDocumentId, ocrText, confidence);
        
        // Verify in database
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT ocr_text, ocr_confidence FROM document_metadata WHERE document_id = ?")) {
            
            stmt.setLong(1, testDocumentId);
            var rs = stmt.executeQuery();
            
            assertTrue(rs.next());
            assertEquals(ocrText, rs.getString("ocr_text"));
            assertEquals(confidence, rs.getDouble("ocr_confidence"), 0.01);
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
    }
    
    @Test
    @Order(3)
    @DisplayName("Test classification update")
    public void testUpdateClassification() throws Exception {
        assertNotNull(testDocumentId);
        
        String documentType = "INVOICE";
        Map<String, Object> classification = Map.of(
            "type", "INVOICE",
            "confidence", 0.88,
            "categories", List.of("financial", "accounting")
        );
        String classificationJson = objectMapper.writeValueAsString(classification);
        
        // Update classification
        repository.updateClassification(testDocumentId, documentType, classificationJson);
        
        // Verify in database
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT document_type, classification_result FROM document_metadata WHERE document_id = ?")) {
            
            stmt.setLong(1, testDocumentId);
            var rs = stmt.executeQuery();
            
            assertTrue(rs.next());
            assertEquals(documentType, rs.getString("document_type"));
            
            String storedJson = rs.getString("classification_result");
            Map<String, Object> storedClassification = objectMapper.readValue(storedJson, Map.class);
            assertEquals("INVOICE", storedClassification.get("type"));
            assertEquals(0.88, ((Number) storedClassification.get("confidence")).doubleValue(), 0.01);
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
    }
    
    @Test
    @Order(4)
    @DisplayName("Test review task creation")
    public void testCreateReviewTask() {
        assertNotNull(testDocumentId);
        
        // Create review task
        Long reviewId = repository.createReviewTask(testDocumentId, testActivityToken, "reviewer@example.com");
        
        assertNotNull(reviewId);
        assertTrue(reviewId > 0);
        
        // Verify review task exists
        Optional<String> token = repository.getActivityTokenForDocument(testDocumentId);
        assertTrue(token.isPresent());
        assertEquals(testActivityToken, token.get());
    }
    
    @Test
    @Order(5)
    @DisplayName("Test getting document by activity token")
    public void testGetDocumentByActivityToken() {
        assertNotNull(testActivityToken);
        
        Long documentId = repository.getDocumentIdByActivityToken(testActivityToken);
        
        assertNotNull(documentId);
        assertEquals(testDocumentId, documentId);
    }
    
    @Test
    @Order(6)
    @DisplayName("Test review completion")
    public void testCompleteReview() {
        assertNotNull(testDocumentId);
        
        // Create review decision
        ReviewDecision decision = new ReviewDecision();
        decision.setApproved(true);
        decision.setComments("Document looks good");
        decision.setReviewedBy("reviewer@example.com");
        decision.setTags(List.of("verified", "complete"));
        decision.setRequiredActions(List.of("archive", "notify"));
        
        // Complete review
        repository.completeReview(testDocumentId, decision);
        
        // Verify review is completed
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT review_status, review_decision, review_comments FROM document_reviews " +
                 "WHERE document_id = ? AND activity_token = ?")) {
            
            stmt.setLong(1, testDocumentId);
            stmt.setString(2, testActivityToken);
            var rs = stmt.executeQuery();
            
            assertTrue(rs.next());
            assertEquals("COMPLETED", rs.getString("review_status"));
            assertTrue(rs.getBoolean("review_decision"));
            assertEquals("Document looks good", rs.getString("review_comments"));
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("Test getting pending reviews")
    public void testGetPendingReviews() {
        // Create documents with PENDING_REVIEW status
        String workflowId1 = "pending-1-" + UUID.randomUUID();
        String workflowId2 = "pending-2-" + UUID.randomUUID();
        
        Long doc1 = repository.saveDocument(workflowId1, "pending1.pdf", "application/pdf", "content1".getBytes());
        Long doc2 = repository.saveDocument(workflowId2, "pending2.pdf", "application/pdf", "content2".getBytes());
        
        // Update status to PENDING_REVIEW
        repository.updateDocumentStatus(doc1, "PENDING_REVIEW");
        repository.updateDocumentStatus(doc2, "PENDING_REVIEW");
        
        // Get pending reviews
        List<DocumentInfo> pendingReviews = repository.getPendingReviews();
        
        assertNotNull(pendingReviews);
        assertTrue(pendingReviews.size() >= 2, "Should have at least 2 pending reviews");
        
        // Verify our documents are in the list
        boolean found1 = pendingReviews.stream().anyMatch(info -> info.getDocumentId().equals(doc1));
        boolean found2 = pendingReviews.stream().anyMatch(info -> info.getDocumentId().equals(doc2));
        
        assertTrue(found1, "Document 1 should be in pending reviews");
        assertTrue(found2, "Document 2 should be in pending reviews");
    }
    
    @Test
    @Order(8)
    @DisplayName("Test review statistics")
    public void testReviewStatistics() {
        // Create some review data
        String workflowId = "stats-" + UUID.randomUUID();
        Long docId = repository.saveDocument(workflowId, "stats.pdf", "application/pdf", "content".getBytes());
        
        // Create multiple review tasks
        repository.createReviewTask(docId, "token-1", "reviewer1@example.com");
        
        // Get statistics
        Map<String, Object> stats = repository.getReviewStatistics();
        
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalReviews"));
        assertTrue(stats.containsKey("pendingReviews"));
        assertTrue(stats.containsKey("completedReviews"));
        
        // Verify counts are reasonable
        Integer totalReviews = (Integer) stats.get("totalReviews");
        Integer pendingReviews = (Integer) stats.get("pendingReviews");
        Integer completedReviews = (Integer) stats.get("completedReviews");
        
        assertTrue(totalReviews >= 0);
        assertTrue(pendingReviews >= 0);
        assertTrue(completedReviews >= 0);
        assertEquals(totalReviews, pendingReviews + completedReviews, 
            "Total should equal pending + completed");
    }
    
    @Test
    @Order(9)
    @DisplayName("Test review task info retrieval")
    public void testGetReviewByToken() {
        // Create a new review task
        String workflowId = "review-info-" + UUID.randomUUID();
        String activityToken = "review-token-" + UUID.randomUUID();
        
        Long docId = repository.saveDocument(workflowId, "review-info.pdf", "application/pdf", "content".getBytes());
        repository.createReviewTask(docId, activityToken, "info-reviewer@example.com");
        
        // Get review task info
        ReviewTaskInfo taskInfo = repository.getReviewByToken(activityToken);
        
        assertNotNull(taskInfo);
        assertEquals(docId, taskInfo.getDocumentId());
        assertEquals("info-reviewer@example.com", taskInfo.getReviewerAssigned());
        assertEquals("review-info.pdf", taskInfo.getDocumentName());
        assertNotNull(taskInfo.getAssignedAt());
    }
    
    @Test
    @Order(10)
    @DisplayName("Test error handling for non-existent document")
    public void testErrorHandlingForNonExistentDocument() {
        // Try to get info for a document that doesn't exist
        Long nonExistentId = 999999L;
        
        assertThrows(RuntimeException.class, () -> {
            repository.getDocumentInfo(nonExistentId);
        }, "Should throw exception for non-existent document");
        
        assertThrows(RuntimeException.class, () -> {
            repository.getDocumentContent(nonExistentId);
        }, "Should throw exception when retrieving content of non-existent document");
        
        // Try to get activity token for non-existent document
        assertThrows(RuntimeException.class, () -> {
            repository.getDocumentIdByActivityToken("non-existent-token");
        }, "Should throw exception for non-existent activity token");
    }
    
    @Test
    @Order(11)
    @DisplayName("Test complex query with multiple documents")
    public void testComplexQueries() {
        // Create multiple documents with different statuses
        String baseWorkflow = "complex-" + UUID.randomUUID() + "-";
        
        Long[] docIds = new Long[5];
        String[] statuses = {"UPLOADED", "PROCESSING", "PENDING_REVIEW", "COMPLETED", "FAILED"};
        
        for (int i = 0; i < 5; i++) {
            docIds[i] = repository.saveDocument(
                baseWorkflow + i,
                "doc-" + i + ".pdf",
                "application/pdf",
                ("content-" + i).getBytes()
            );
            repository.updateDocumentStatus(docIds[i], statuses[i]);
        }
        
        // Query documents by status
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM document_metadata WHERE status IN ('COMPLETED', 'FAILED')")) {
            
            var rs = stmt.executeQuery();
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 2, "Should have at least 2 documents with COMPLETED or FAILED status");
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
        
        // Query documents with JOIN
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT d.name, dm.status FROM documents d " +
                 "JOIN document_metadata dm ON d.id = dm.document_id " +
                 "WHERE d.workflow_id LIKE ? ORDER BY d.id")) {
            
            stmt.setString(1, baseWorkflow + "%");
            var rs = stmt.executeQuery();
            
            int count = 0;
            while (rs.next()) {
                assertEquals("doc-" + count + ".pdf", rs.getString("name"));
                assertEquals(statuses[count], rs.getString("status"));
                count++;
            }
            assertEquals(5, count, "Should have found all 5 documents");
            
        } catch (SQLException e) {
            fail("Database query failed: " + e.getMessage());
        }
    }
    
    @AfterEach
    public void verifyDatabaseConsistency() {
        // Verify foreign key constraints are maintained
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM document_metadata dm " +
                 "LEFT JOIN documents d ON dm.document_id = d.id " +
                 "WHERE d.id IS NULL")) {
            
            var rs = stmt.executeQuery();
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "No orphaned metadata records should exist");
            
        } catch (SQLException e) {
            fail("Database consistency check failed: " + e.getMessage());
        }
    }
}