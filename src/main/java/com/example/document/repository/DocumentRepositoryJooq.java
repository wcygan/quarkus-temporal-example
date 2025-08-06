package com.example.document.repository;

import com.example.document.model.ReviewDecision;
import com.example.jooq.generated.tables.DocumentMetadata;
import com.example.jooq.generated.tables.DocumentProcessingHistory;
import com.example.jooq.generated.tables.DocumentReviews;
import com.example.jooq.generated.tables.Documents;
import com.example.jooq.generated.tables.records.DocumentMetadataRecord;
import com.example.jooq.generated.tables.records.DocumentProcessingHistoryRecord;
import com.example.jooq.generated.tables.records.DocumentReviewsRecord;
import com.example.jooq.generated.tables.records.DocumentsRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.jooq.generated.Tables.*;

/**
 * jOOQ-based implementation of DocumentRepository.
 * Uses type-safe SQL queries generated from the database schema.
 */
@ApplicationScoped
public class DocumentRepositoryJooq {
    
    @Inject
    DSLContext dsl;
    
    @Inject
    ObjectMapper objectMapper;
    
    // Table references
    private static final Documents DOCS = DOCUMENTS;
    private static final DocumentMetadata META = DOCUMENT_METADATA;
    private static final DocumentReviews REVIEWS = DOCUMENT_REVIEWS;
    private static final DocumentProcessingHistory HISTORY = DOCUMENT_PROCESSING_HISTORY;
    
    public Long saveDocument(String workflowId, String fileName, String mimeType, byte[] content) {
        return dsl.transactionResult(config -> {
            DSLContext ctx = DSL.using(config);
            
            // Insert document
            DocumentsRecord docRecord = ctx.newRecord(DOCS);
            docRecord.setWorkflowId(workflowId);
            docRecord.setName(fileName);
            docRecord.setMimeType(mimeType);
            docRecord.setSizeBytes((long) content.length);
            docRecord.setContent(content);
            docRecord.store();
            
            Long documentId = docRecord.getId();
            
            // Create metadata record
            DocumentMetadataRecord metaRecord = ctx.newRecord(META);
            metaRecord.setDocumentId(documentId);
            metaRecord.setStatus("UPLOADED");
            metaRecord.store();
            
            return documentId;
        });
    }
    
    public byte[] getDocumentContent(Long documentId) {
        return dsl.select(DOCS.CONTENT)
            .from(DOCS)
            .where(DOCS.ID.eq(documentId))
            .fetchOptional(DOCS.CONTENT)
            .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
    }
    
    public void updateDocumentStatus(Long documentId, String status) {
        LocalDateTime now = LocalDateTime.now();
        
        dsl.update(META)
            .set(META.STATUS, status)
            .set(META.PROCESSING_STARTED_AT, 
                DSL.coalesce(META.PROCESSING_STARTED_AT, now))
            .set(META.PROCESSING_COMPLETED_AT,
                DSL.when(DSL.val(status).in("COMPLETED", "FAILED", "CANCELLED"), now)
                   .otherwise(META.PROCESSING_COMPLETED_AT))
            .where(META.DOCUMENT_ID.eq(documentId))
            .execute();
        
        // Record in history
        recordProcessingHistory(documentId, status, "UPDATED", null);
    }
    
    public void updateOcrResult(Long documentId, String ocrText, Double confidence) {
        dsl.update(META)
            .set(META.OCR_TEXT, ocrText)
            .set(META.OCR_CONFIDENCE, java.math.BigDecimal.valueOf(confidence))
            .where(META.DOCUMENT_ID.eq(documentId))
            .execute();
    }
    
    public void updateClassification(Long documentId, String documentType, String classificationJson) {
        dsl.update(META)
            .set(META.DOCUMENT_TYPE, documentType)
            .set(META.CLASSIFICATION_RESULT, org.jooq.JSON.json(classificationJson))
            .where(META.DOCUMENT_ID.eq(documentId))
            .execute();
    }
    
    public Long createReviewTask(Long documentId, String activityToken, String reviewer) {
        DocumentReviewsRecord record = dsl.newRecord(REVIEWS);
        record.setDocumentId(documentId);
        record.setActivityToken(activityToken);
        record.setReviewerAssigned(reviewer);
        record.store();
        
        return record.getId();
    }
    
    public Optional<String> getActivityTokenForDocument(Long documentId) {
        return dsl.select(REVIEWS.ACTIVITY_TOKEN)
            .from(REVIEWS)
            .where(REVIEWS.DOCUMENT_ID.eq(documentId)
                .and(REVIEWS.REVIEW_STATUS.eq("PENDING")))
            .orderBy(REVIEWS.ASSIGNED_AT.desc())
            .limit(1)
            .fetchOptionalInto(String.class);
    }
    
    public Long getDocumentIdByActivityToken(String activityToken) {
        return dsl.select(REVIEWS.DOCUMENT_ID)
            .from(REVIEWS)
            .where(REVIEWS.ACTIVITY_TOKEN.eq(activityToken)
                .and(REVIEWS.REVIEW_STATUS.eq("PENDING")))
            .limit(1)
            .fetchOptional(REVIEWS.DOCUMENT_ID)
            .orElseThrow(() -> new RuntimeException("Document not found for activity token: " + activityToken));
    }
    
    public void completeReview(Long documentId, ReviewDecision decision) {
        try {
            dsl.update(REVIEWS)
                .set(REVIEWS.REVIEW_STATUS, "COMPLETED")
                .set(REVIEWS.REVIEW_DECISION, (byte) (decision.isApproved() ? 1 : 0))
                .set(REVIEWS.REVIEW_COMMENTS, decision.getComments())
                .set(REVIEWS.REVIEW_TAGS, org.jooq.JSON.json(objectMapper.writeValueAsString(decision.getTags())))
                .set(REVIEWS.REQUIRED_ACTIONS, org.jooq.JSON.json(objectMapper.writeValueAsString(decision.getRequiredActions())))
                .set(REVIEWS.COMPLETED_AT, LocalDateTime.now())
                .where(REVIEWS.DOCUMENT_ID.eq(documentId)
                    .and(REVIEWS.REVIEW_STATUS.eq("PENDING")))
                .execute();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to complete review", e);
        }
    }
    
    public void recordProcessingHistory(Long documentId, String stage, String status, String details) {
        try {
            DocumentProcessingHistoryRecord record = dsl.newRecord(HISTORY);
            record.setDocumentId(documentId);
            record.setStage(stage);
            record.setStatus(status);
            record.setDetails(details != null ? org.jooq.JSON.json(details) : null);
            record.store();
        } catch (Exception e) {
            // Don't fail the main operation if history recording fails
            System.err.println("Failed to record processing history: " + e.getMessage());
        }
    }
    
    public DocumentRepository.DocumentInfo getDocumentInfo(Long documentId) {
        Record record = dsl.select(
                DOCS.ID,
                DOCS.WORKFLOW_ID,
                DOCS.NAME,
                DOCS.MIME_TYPE,
                DOCS.SIZE_BYTES,
                META.STATUS,
                META.DOCUMENT_TYPE
            )
            .from(DOCS)
            .join(META).on(DOCS.ID.eq(META.DOCUMENT_ID))
            .where(DOCS.ID.eq(documentId))
            .fetchOptional()
            .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        
        DocumentRepository.DocumentInfo info = new DocumentRepository.DocumentInfo();
        info.setDocumentId(record.get(DOCS.ID));
        info.setWorkflowId(record.get(DOCS.WORKFLOW_ID));
        info.setFileName(record.get(DOCS.NAME));
        info.setMimeType(record.get(DOCS.MIME_TYPE));
        info.setSizeBytes(record.get(DOCS.SIZE_BYTES));
        info.setStatus(record.get(META.STATUS));
        info.setDocumentType(record.get(META.DOCUMENT_TYPE));
        return info;
    }
    
    public List<DocumentRepository.DocumentInfo> getPendingReviews() {
        return dsl.select(
                DOCS.ID,
                DOCS.WORKFLOW_ID,
                DOCS.NAME,
                DOCS.MIME_TYPE,
                DOCS.SIZE_BYTES,
                META.STATUS,
                META.DOCUMENT_TYPE
            )
            .from(DOCS)
            .join(META).on(DOCS.ID.eq(META.DOCUMENT_ID))
            .where(META.STATUS.eq("PENDING_REVIEW"))
            .fetch()
            .map(record -> {
                DocumentRepository.DocumentInfo info = new DocumentRepository.DocumentInfo();
                info.setDocumentId(record.get(DOCS.ID));
                info.setWorkflowId(record.get(DOCS.WORKFLOW_ID));
                info.setFileName(record.get(DOCS.NAME));
                info.setMimeType(record.get(DOCS.MIME_TYPE));
                info.setSizeBytes(record.get(DOCS.SIZE_BYTES));
                info.setStatus(record.get(META.STATUS));
                info.setDocumentType(record.get(META.DOCUMENT_TYPE));
                return info;
            });
    }
    
    public Map<String, Object> getReviewStatistics() {
        // Total reviews
        int totalReviews = dsl.selectCount()
            .from(REVIEWS)
            .fetchOne(0, int.class);
        
        // Pending reviews
        int pendingReviews = dsl.selectCount()
            .from(REVIEWS)
            .where(REVIEWS.REVIEW_STATUS.eq("PENDING"))
            .fetchOne(0, int.class);
        
        // Completed reviews
        int completedReviews = dsl.selectCount()
            .from(REVIEWS)
            .where(REVIEWS.REVIEW_STATUS.eq("COMPLETED"))
            .fetchOne(0, int.class);
        
        return Map.of(
            "totalReviews", totalReviews,
            "pendingReviews", pendingReviews,
            "completedReviews", completedReviews
        );
    }
    
    public DocumentRepository.ReviewTaskInfo getReviewByToken(String activityToken) {
        Record record = dsl.select(
                REVIEWS.DOCUMENT_ID,
                REVIEWS.REVIEWER_ASSIGNED,
                REVIEWS.ASSIGNED_AT,
                DOCS.NAME,
                META.STATUS
            )
            .from(REVIEWS)
            .join(DOCS).on(REVIEWS.DOCUMENT_ID.eq(DOCS.ID))
            .join(META).on(DOCS.ID.eq(META.DOCUMENT_ID))
            .where(REVIEWS.ACTIVITY_TOKEN.eq(activityToken)
                .and(REVIEWS.REVIEW_STATUS.eq("PENDING")))
            .fetchOptional()
            .orElseThrow(() -> new RuntimeException("Review task not found for token: " + activityToken));
        
        DocumentRepository.ReviewTaskInfo taskInfo = new DocumentRepository.ReviewTaskInfo();
        taskInfo.setDocumentId(record.get(REVIEWS.DOCUMENT_ID));
        taskInfo.setReviewerAssigned(record.get(REVIEWS.REVIEWER_ASSIGNED));
        taskInfo.setAssignedAt(Timestamp.valueOf(record.get(REVIEWS.ASSIGNED_AT)));
        taskInfo.setDocumentName(record.get(DOCS.NAME));
        taskInfo.setStatus(record.get(META.STATUS));
        return taskInfo;
    }
    
    /**
     * Example of a complex jOOQ query with multiple joins and conditions.
     * Shows the power of type-safe SQL building.
     */
    public List<Map<String, Object>> getDocumentProcessingStats(LocalDateTime startDate, LocalDateTime endDate) {
        return dsl.select(
                META.STATUS,
                META.DOCUMENT_TYPE,
                DSL.count().as("count"),
                DSL.avg(DOCS.SIZE_BYTES).as("avg_size"),
                DSL.max(META.OCR_CONFIDENCE).as("max_confidence"),
                DSL.min(META.OCR_CONFIDENCE).as("min_confidence")
            )
            .from(DOCS)
            .join(META).on(DOCS.ID.eq(META.DOCUMENT_ID))
            .where(DOCS.UPLOADED_AT.between(startDate, endDate))
            .groupBy(META.STATUS, META.DOCUMENT_TYPE)
            .orderBy(DSL.count().desc())
            .fetchMaps();
    }
    
    /**
     * Example of using jOOQ for batch operations.
     */
    public void batchUpdateStatuses(Map<Long, String> documentStatuses) {
        dsl.batch(
            documentStatuses.entrySet().stream()
                .map(entry -> 
                    dsl.update(META)
                        .set(META.STATUS, entry.getValue())
                        .where(META.DOCUMENT_ID.eq(entry.getKey()))
                )
                .toList()
        ).execute();
    }
    
    /**
     * Example of using jOOQ with window functions.
     */
    public List<Map<String, Object>> getDocumentRankingBySize() {
        return dsl.select(
                DOCS.ID,
                DOCS.NAME,
                DOCS.SIZE_BYTES,
                DSL.rowNumber().over(DSL.orderBy(DOCS.SIZE_BYTES.desc())).as("size_rank"),
                DSL.percentRank().over(DSL.orderBy(DOCS.SIZE_BYTES)).as("size_percentile")
            )
            .from(DOCS)
            .orderBy(DOCS.SIZE_BYTES.desc())
            .limit(100)
            .fetchMaps();
    }
}