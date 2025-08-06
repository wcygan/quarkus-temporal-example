package com.example.document.workflow;

import com.example.document.model.*;
import io.temporal.workflow.*;

@WorkflowInterface
public interface DocumentProcessingWorkflow {
    
    @WorkflowMethod
    ProcessingResult processDocument(DocumentRequest request);
    
    @UpdateMethod
    void updatePriority(Priority priority);
    
    @UpdateValidatorMethod(updateName = "updatePriority")
    void validatePriorityUpdate(Priority priority);
    
    @UpdateMethod
    ReviewDecision submitReview(ReviewDecision decision);
    
    @UpdateValidatorMethod(updateName = "submitReview")
    void validateReviewSubmission(ReviewDecision decision);
    
    @QueryMethod
    DocumentStatus getStatus();
    
    @QueryMethod
    ProcessingMetrics getMetrics();
    
    @QueryMethod
    String getWorkflowInfo();
    
    @SignalMethod
    void cancelProcessing();
    
    @SignalMethod
    void retryFailedStage();
}