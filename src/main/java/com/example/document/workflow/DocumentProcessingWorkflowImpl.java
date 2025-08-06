package com.example.document.workflow;

import com.example.document.activity.*;
import com.example.document.model.*;
import io.temporal.activity.ActivityOptions;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class DocumentProcessingWorkflowImpl implements DocumentProcessingWorkflow {
    
    private static final Logger logger = Workflow.getLogger(DocumentProcessingWorkflowImpl.class);
    
    private DocumentStatus currentStatus = DocumentStatus.UPLOADED;
    private Priority priority = Priority.MEDIUM;
    private ProcessingMetrics metrics;
    private DocumentRequest request;
    private Long documentId;
    private boolean cancelled = false;
    private String failedStage = null;
    private ReviewDecision pendingReviewDecision = null;
    
    private final DocumentStorageActivities storageActivities = 
        Workflow.newActivityStub(DocumentStorageActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .build())
                .build());
    
    private final ProcessingActivities processingActivities = 
        Workflow.newActivityStub(ProcessingActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(2))
                .setHeartbeatTimeout(Duration.ofSeconds(5))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setBackoffCoefficient(2.0)
                    .build())
                .build());
    
    private final ReviewActivities reviewActivities = 
        Workflow.newActivityStub(ReviewActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofHours(24))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(1)
                    .build())
                .build());
    
    private final NotificationActivities notificationActivities = 
        Workflow.newLocalActivityStub(NotificationActivities.class,
            LocalActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(5))
                .build());
    
    @Override
    public ProcessingResult processDocument(DocumentRequest request) {
        this.request = request;
        this.metrics = new ProcessingMetrics(request.getWorkflowId(), null);
        this.metrics.setStartTime(Instant.now());
        this.metrics.setPriority(priority);
        this.metrics.setTotalStages(7);
        
        ProcessingResult result = new ProcessingResult();
        result.setWorkflowId(request.getWorkflowId());
        result.setStartTime(Instant.now());
        
        try {
            // Stage 1: Store document in MySQL
            updateStatus(DocumentStatus.VALIDATING);
            long stageStart = System.currentTimeMillis();
            documentId = storageActivities.storeDocument(
                request.getWorkflowId(),
                request.getFileName(),
                request.getMimeType(),
                request.getContent()
            );
            metrics.setDocumentId(documentId);
            result.setDocumentId(documentId);
            recordStageDuration("storage", stageStart);
            
            checkCancellation();
            
            // Stage 2: Pre-process and extract metadata
            updateStatus(DocumentStatus.PRE_PROCESSING);
            stageStart = System.currentTimeMillis();
            DocumentMetadata metadata = processingActivities.extractMetadata(documentId);
            recordStageDuration("preprocessing", stageStart);
            
            checkCancellation();
            
            // Stage 3: OCR Processing with heartbeating
            updateStatus(DocumentStatus.OCR_PROCESSING);
            stageStart = System.currentTimeMillis();
            OcrResult ocrResult = processingActivities.performOcr(documentId);
            result.setOcrText(ocrResult.getText());
            result.setOcrConfidence(ocrResult.getConfidence());
            recordStageDuration("ocr", stageStart);
            
            checkCancellation();
            
            // Stage 4: ML Classification
            updateStatus(DocumentStatus.CLASSIFYING);
            stageStart = System.currentTimeMillis();
            Classification classification = processingActivities.classifyDocument(
                documentId, 
                ocrResult.getText()
            );
            result.setDocumentType(classification.getDocumentType());
            recordStageDuration("classification", stageStart);
            
            checkCancellation();
            
            // Stage 5: Human Review (async completion)
            updateStatus(DocumentStatus.PENDING_REVIEW);
            stageStart = System.currentTimeMillis();
            
            // Start async review activity
            reviewActivities.requestReview(documentId);
            
            // Wait for review decision (via update method or timeout)
            ReviewDecision reviewDecision = waitForReviewDecision();
            result.setReviewDecision(reviewDecision);
            recordStageDuration("review", stageStart);
            
            checkCancellation();
            
            // Stage 6: Post-processing
            updateStatus(DocumentStatus.POST_PROCESSING);
            stageStart = System.currentTimeMillis();
            processingActivities.finalizeDocument(documentId, reviewDecision);
            recordStageDuration("postprocessing", stageStart);
            
            // Stage 7: Send notification (local activity)
            notificationActivities.sendProcessingComplete(
                documentId, 
                request.getFileName(),
                reviewDecision.isApproved()
            );
            
            // Mark as completed
            updateStatus(DocumentStatus.COMPLETED);
            result.setStatus(DocumentStatus.COMPLETED);
            result.setEndTime(Instant.now());
            result.setProcessingDurationMs(
                result.getEndTime().toEpochMilli() - result.getStartTime().toEpochMilli()
            );
            
            logger.info("Document processing completed for workflow: {}", request.getWorkflowId());
            
        } catch (Exception e) {
            // Re-throw cancellation exceptions to preserve cancellation behavior
            if (e instanceof ApplicationFailure) {
                ApplicationFailure af = (ApplicationFailure) e;
                if ("Cancelled".equals(af.getType())) {
                    throw af;
                }
            }
            
            updateStatus(DocumentStatus.FAILED);
            result.setStatus(DocumentStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(Instant.now());
            
            // Send failure notification
            notificationActivities.sendProcessingFailed(
                documentId, 
                request.getFileName(), 
                e.getMessage()
            );
            
            logger.error("Document processing failed for workflow: {}", request.getWorkflowId(), e);
        }
        
        return result;
    }
    
    private ReviewDecision waitForReviewDecision() {
        // Wait for review decision to be submitted via update method
        // or timeout after 24 hours
        Workflow.await(Duration.ofHours(24), () -> pendingReviewDecision != null || cancelled);
        
        if (cancelled) {
            throw ApplicationFailure.newFailure("Processing cancelled", "Cancelled");
        }
        
        if (pendingReviewDecision == null) {
            // Timeout - auto-reject
            ReviewDecision timeoutDecision = new ReviewDecision(false, "Review timed out");
            return timeoutDecision;
        }
        
        return pendingReviewDecision;
    }
    
    @Override
    public void updatePriority(Priority priority) {
        Priority oldPriority = this.priority;
        this.priority = priority;
        this.metrics.setPriority(priority);
        logger.info("Priority updated from {} to {}", oldPriority, priority);
    }
    
    @Override
    public void validatePriorityUpdate(Priority priority) {
        if (priority == null) {
            throw ApplicationFailure.newFailure("Priority cannot be null", "ValidationError");
        }
        if (currentStatus == DocumentStatus.COMPLETED || currentStatus == DocumentStatus.FAILED) {
            throw ApplicationFailure.newFailure(
                "Cannot update priority for completed/failed workflow", 
                "ValidationError"
            );
        }
    }
    
    @Override
    public ReviewDecision submitReview(ReviewDecision decision) {
        if (currentStatus != DocumentStatus.PENDING_REVIEW && currentStatus != DocumentStatus.REVIEWING) {
            throw ApplicationFailure.newFailure(
                "Document is not pending review", 
                "InvalidState"
            );
        }
        
        this.pendingReviewDecision = decision;
        updateStatus(DocumentStatus.POST_PROCESSING);
        logger.info("Review decision submitted: approved={}", decision.isApproved());
        return decision;
    }
    
    @Override
    public void validateReviewSubmission(ReviewDecision decision) {
        if (decision == null) {
            throw ApplicationFailure.newFailure("Review decision cannot be null", "ValidationError");
        }
        if (decision.getComments() == null || decision.getComments().trim().isEmpty()) {
            throw ApplicationFailure.newFailure("Review comments are required", "ValidationError");
        }
    }
    
    @Override
    public DocumentStatus getStatus() {
        return currentStatus;
    }
    
    @Override
    public ProcessingMetrics getMetrics() {
        metrics.setLastUpdateTime(Instant.now());
        metrics.updateProgress();
        return metrics;
    }
    
    @Override
    public String getWorkflowInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("workflowId", request != null ? request.getWorkflowId() : "N/A");
        info.put("documentId", documentId);
        info.put("fileName", request != null ? request.getFileName() : "N/A");
        info.put("status", currentStatus);
        info.put("priority", priority);
        info.put("completedStages", metrics.getCompletedStages());
        info.put("totalStages", metrics.getTotalStages());
        return info.toString();
    }
    
    @Override
    public void cancelProcessing() {
        this.cancelled = true;
        updateStatus(DocumentStatus.CANCELLED);
        logger.info("Processing cancelled for workflow: {}", request.getWorkflowId());
    }
    
    @Override
    public void retryFailedStage() {
        if (failedStage != null && currentStatus == DocumentStatus.FAILED) {
            logger.info("Retrying failed stage: {}", failedStage);
            // Implementation would retry the specific failed stage
        }
    }
    
    private void updateStatus(DocumentStatus newStatus) {
        this.currentStatus = newStatus;
        this.metrics.setCurrentStatus(newStatus);
        this.metrics.setLastUpdateTime(Instant.now());
        
        if (documentId != null) {
            storageActivities.updateDocumentStatus(documentId, newStatus.name());
        }
        
        logger.info("Status updated to: {}", newStatus);
    }
    
    private void recordStageDuration(String stage, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        metrics.recordStageDuration(stage, duration);
        metrics.setCompletedStages(metrics.getCompletedStages() + 1);
        metrics.updateProgress();
    }
    
    private void checkCancellation() {
        if (cancelled) {
            throw ApplicationFailure.newFailure("Processing cancelled", "Cancelled");
        }
    }
}