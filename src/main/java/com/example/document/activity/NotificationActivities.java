package com.example.document.activity;

import com.example.document.model.Priority;
import com.example.document.model.ProcessingResult;
import io.temporal.activity.ActivityInterface;

import java.util.Map;

@ActivityInterface
public interface NotificationActivities {
    
    void sendProcessingComplete(Long documentId, String fileName, boolean approved);
    
    void sendProcessingFailed(Long documentId, String fileName, String errorMessage);
    
    void sendReviewReminder(Long documentId, String reviewerEmail);
    
    void sendNotification(String recipient, String subject, String message);
    
    void notifyReviewRequired(Long documentId, String documentName);
    
    void notifyProcessingComplete(Long documentId, ProcessingResult result);
    
    void notifyProcessingError(Long documentId, String error);
    
    void sendStatusUpdate(Long documentId, String status, Map<String, Object> details);
    
    void notifyPriorityChange(Long documentId, Priority oldPriority, Priority newPriority);
}