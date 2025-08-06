package com.example.document.repository;

import com.example.document.model.ReviewDecision;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class DocumentRepository {
    
    @Inject
    DataSource dataSource;
    
    @Inject
    ObjectMapper objectMapper;
    
    public Long saveDocument(String workflowId, String fileName, String mimeType, byte[] content) {
        String sql = "INSERT INTO documents (workflow_id, name, mime_type, size_bytes, content) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, workflowId);
            stmt.setString(2, fileName);
            stmt.setString(3, mimeType);
            stmt.setLong(4, content.length);
            stmt.setBytes(5, content);
            
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    Long documentId = rs.getLong(1);
                    
                    // Also create metadata record
                    createMetadataRecord(documentId);
                    
                    return documentId;
                }
            }
            
            throw new RuntimeException("Failed to get generated document ID");
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save document", e);
        }
    }
    
    private void createMetadataRecord(Long documentId) throws SQLException {
        String sql = "INSERT INTO document_metadata (document_id, status) VALUES (?, 'UPLOADED')";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, documentId);
            stmt.executeUpdate();
        }
    }
    
    public byte[] getDocumentContent(Long documentId) {
        String sql = "SELECT content FROM documents WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, documentId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("content");
                }
            }
            
            throw new RuntimeException("Document not found: " + documentId);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve document", e);
        }
    }
    
    public void updateDocumentStatus(Long documentId, String status) {
        String sql = "UPDATE document_metadata SET status = ?, processing_started_at = COALESCE(processing_started_at, ?), processing_completed_at = CASE WHEN ? IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN ? ELSE processing_completed_at END WHERE document_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            Timestamp now = Timestamp.from(Instant.now());
            stmt.setString(1, status);
            stmt.setTimestamp(2, now);
            stmt.setString(3, status);
            stmt.setTimestamp(4, now);
            stmt.setLong(5, documentId);
            
            stmt.executeUpdate();
            
            // Also record in history
            recordProcessingHistory(documentId, status, "UPDATED", null);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update document status", e);
        }
    }
    
    public void updateOcrResult(Long documentId, String ocrText, Double confidence) {
        String sql = "UPDATE document_metadata SET ocr_text = ?, ocr_confidence = ? WHERE document_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, ocrText);
            stmt.setDouble(2, confidence);
            stmt.setLong(3, documentId);
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update OCR result", e);
        }
    }
    
    public void updateClassification(Long documentId, String documentType, String classificationJson) {
        String sql = "UPDATE document_metadata SET document_type = ?, classification_result = ? WHERE document_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, documentType);
            stmt.setString(2, classificationJson);
            stmt.setLong(3, documentId);
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update classification", e);
        }
    }
    
    public Long createReviewTask(Long documentId, String activityToken, String reviewer) {
        String sql = "INSERT INTO document_reviews (document_id, activity_token, reviewer_assigned) VALUES (?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setLong(1, documentId);
            stmt.setString(2, activityToken);
            stmt.setString(3, reviewer);
            
            stmt.executeUpdate();
            
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            
            throw new RuntimeException("Failed to get generated review ID");
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create review task", e);
        }
    }
    
    public Optional<String> getActivityTokenForDocument(Long documentId) {
        String sql = "SELECT activity_token FROM document_reviews WHERE document_id = ? AND review_status = 'PENDING' ORDER BY assigned_at DESC LIMIT 1";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, documentId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("activity_token"));
                }
            }
            
            return Optional.empty();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get activity token", e);
        }
    }
    
    public Long getDocumentIdByActivityToken(String activityToken) {
        String sql = "SELECT document_id FROM document_reviews WHERE activity_token = ? AND review_status = 'PENDING' LIMIT 1";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, activityToken);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("document_id");
                }
            }
            
            throw new RuntimeException("Document not found for activity token: " + activityToken);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get document ID by activity token", e);
        }
    }

    public void createReviewRequest(Long documentId, String activityToken) throws SQLException {
        String sql = "INSERT INTO document_reviews (document_id, activity_token, review_status) VALUES (?, ?, 'PENDING')";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, documentId);
            stmt.setString(2, activityToken);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new SQLException("Failed to create review request", e);
        }
    }

    public void completeReview(Long documentId, ReviewDecision decision) {
        String sql = "UPDATE document_reviews SET review_status = 'COMPLETED', review_decision = ?, review_comments = ?, review_tags = ?, required_actions = ?, completed_at = ? WHERE document_id = ? AND review_status = 'PENDING'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setBoolean(1, decision.isApproved());
            stmt.setString(2, decision.getComments());
            stmt.setString(3, objectMapper.writeValueAsString(decision.getTags()));
            stmt.setString(4, objectMapper.writeValueAsString(decision.getRequiredActions()));
            stmt.setTimestamp(5, Timestamp.from(Instant.now()));
            stmt.setLong(6, documentId);
            
            stmt.executeUpdate();
            
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Failed to complete review", e);
        }
    }
    
    public void recordProcessingHistory(Long documentId, String stage, String status, String details) {
        String sql = "INSERT INTO document_processing_history (document_id, stage, status, details) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, documentId);
            stmt.setString(2, stage);
            stmt.setString(3, status);
            stmt.setString(4, details);
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            // Don't fail the main operation if history recording fails
            System.err.println("Failed to record processing history: " + e.getMessage());
        }
    }
    
    public DocumentInfo getDocumentInfo(Long documentId) {
        String sql = "SELECT d.workflow_id, d.name, d.mime_type, d.size_bytes, dm.status, dm.document_type FROM documents d JOIN document_metadata dm ON d.id = dm.document_id WHERE d.id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, documentId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    DocumentInfo info = new DocumentInfo();
                    info.setDocumentId(documentId);
                    info.setWorkflowId(rs.getString("workflow_id"));
                    info.setFileName(rs.getString("name"));
                    info.setMimeType(rs.getString("mime_type"));
                    info.setSizeBytes(rs.getLong("size_bytes"));
                    info.setStatus(rs.getString("status"));
                    info.setDocumentType(rs.getString("document_type"));
                    return info;
                }
            }
            
            throw new RuntimeException("Document not found: " + documentId);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get document info", e);
        }
    }
    
    public static class DocumentInfo {
        private Long documentId;
        private String workflowId;
        private String fileName;
        private String mimeType;
        private long sizeBytes;
        private String status;
        private String documentType;
        
        // Getters and setters
        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }
        
        public String getWorkflowId() { return workflowId; }
        public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        
        public long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
    }

    public DocumentInfo retrieveDocument(Long documentId) {
        return getDocumentInfo(documentId);
    }

    public void updateReviewDecision(Long documentId, ReviewDecision reviewDecision) {
        String sql = "UPDATE document_metadata SET review_decision = ? WHERE document_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, objectMapper.writeValueAsString(reviewDecision));
            stmt.setLong(2, documentId);
            stmt.executeUpdate();
            
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Failed to update review decision", e);
        }
    }

    public java.util.List<DocumentInfo> getPendingReviews() {
        String sql = "SELECT d.id, d.workflow_id, d.name, d.mime_type, d.size_bytes, dm.status, dm.document_type " +
                    "FROM documents d JOIN document_metadata dm ON d.id = dm.document_id " +
                    "WHERE dm.status = 'PENDING_REVIEW'";
        
        java.util.List<DocumentInfo> reviews = new java.util.ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                DocumentInfo info = new DocumentInfo();
                info.setDocumentId(rs.getLong("id"));
                info.setWorkflowId(rs.getString("workflow_id"));
                info.setFileName(rs.getString("name"));
                info.setMimeType(rs.getString("mime_type"));
                info.setSizeBytes(rs.getLong("size_bytes"));
                info.setStatus(rs.getString("status"));
                info.setDocumentType(rs.getString("document_type"));
                reviews.add(info);
            }
            
            return reviews;
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get pending reviews", e);
        }
    }

    public ReviewTaskInfo getReviewByToken(String activityToken) {
        String sql = "SELECT dr.document_id, dr.reviewer_assigned, dr.assigned_at, d.name, dm.status " +
                    "FROM document_reviews dr " +
                    "JOIN documents d ON dr.document_id = d.id " +
                    "JOIN document_metadata dm ON d.id = dm.document_id " +
                    "WHERE dr.activity_token = ? AND dr.review_status = 'PENDING'";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, activityToken);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ReviewTaskInfo taskInfo = new ReviewTaskInfo();
                    taskInfo.setDocumentId(rs.getLong("document_id"));
                    taskInfo.setReviewerAssigned(rs.getString("reviewer_assigned"));
                    taskInfo.setAssignedAt(rs.getTimestamp("assigned_at"));
                    taskInfo.setDocumentName(rs.getString("name"));
                    taskInfo.setStatus(rs.getString("status"));
                    return taskInfo;
                }
            }
            
            throw new RuntimeException("Review task not found for token: " + activityToken);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get review by token", e);
        }
    }

    public java.util.Map<String, Object> getReviewStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        try (Connection conn = dataSource.getConnection()) {
            // Total reviews
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM document_reviews")) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    stats.put("totalReviews", rs.getInt(1));
                }
            }
            
            // Pending reviews
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM document_reviews WHERE review_status = 'PENDING'")) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    stats.put("pendingReviews", rs.getInt(1));
                }
            }
            
            // Completed reviews
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM document_reviews WHERE review_status = 'COMPLETED'")) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    stats.put("completedReviews", rs.getInt(1));
                }
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get review statistics", e);
        }
        
        return stats;
    }

    public static class ReviewTaskInfo {
        private Long documentId;
        private String reviewerAssigned;
        private java.sql.Timestamp assignedAt;
        private String documentName;
        private String status;
        
        // Getters and setters
        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }
        
        public String getReviewerAssigned() { return reviewerAssigned; }
        public void setReviewerAssigned(String reviewerAssigned) { this.reviewerAssigned = reviewerAssigned; }
        
        public java.sql.Timestamp getAssignedAt() { return assignedAt; }
        public void setAssignedAt(java.sql.Timestamp assignedAt) { this.assignedAt = assignedAt; }
        
        public String getDocumentName() { return documentName; }
        public void setDocumentName(String documentName) { this.documentName = documentName; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}