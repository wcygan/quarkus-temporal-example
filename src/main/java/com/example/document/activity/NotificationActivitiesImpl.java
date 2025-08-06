package com.example.document.activity;

import com.example.document.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class NotificationActivitiesImpl implements NotificationActivities {

    private static final Logger LOGGER = Logger.getLogger(NotificationActivitiesImpl.class);

    @Override
    public void sendNotification(String recipient, String subject, String message) {
        LOGGER.infof("Sending notification to %s: %s", recipient, subject);
        
        try {
            // Simulate sending email/notification
            // In a real application, this would integrate with email service, Slack, etc.
            Thread.sleep(100);
            
            LOGGER.infof("Notification sent successfully: [%s] %s - %s", 
                recipient, subject, message);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to send notification", e);
        }
    }

    @Override
    public void notifyReviewRequired(Long documentId, String documentName) {
        String recipient = "reviewers@example.com";
        String subject = String.format("Document Review Required: %s", documentName);
        String message = String.format(
            "A new document requires review.\n\n" +
            "Document ID: %d\n" +
            "Document Name: %s\n" +
            "Submitted: %s\n\n" +
            "Please review at your earliest convenience.",
            documentId, documentName, Instant.now()
        );
        
        sendNotification(recipient, subject, message);
    }

    @Override
    public void notifyProcessingComplete(Long documentId, ProcessingResult result) {
        String recipient = "admin@example.com";
        String status = result.getStatus().equals("COMPLETED") ? "Successfully Processed" : "Processing Failed";
        String subject = String.format("Document %s: %d", status, documentId);
        
        StringBuilder message = new StringBuilder();
        message.append(String.format("Document processing completed.\n\n"));
        message.append(String.format("Document ID: %d\n", documentId));
        message.append(String.format("Status: %s\n", result.getStatus()));
        message.append(String.format("Processing Time: %d ms\n", result.getProcessingDurationMs()));
        
        if (result.getClassification() != null) {
            message.append(String.format("Document Type: %s\n", result.getClassification().getDocumentType()));
            message.append(String.format("Confidence: %.2f%%\n", result.getClassification().getConfidence() * 100));
        }
        
        if (result.getReviewDecision() != null) {
            message.append(String.format("\nReview Decision: %s\n", 
                result.getReviewDecision().isApproved() ? "APPROVED" : "REJECTED"));
            if (result.getReviewDecision().getComments() != null) {
                message.append(String.format("Comments: %s\n", result.getReviewDecision().getComments()));
            }
        }
        
        if (result.getErrorMessage() != null) {
            message.append(String.format("\nError: %s\n", result.getErrorMessage()));
        }
        
        sendNotification(recipient, subject, message.toString());
    }

    @Override
    public void notifyProcessingError(Long documentId, String error) {
        String recipient = "admin@example.com";
        String subject = String.format("Document Processing Error: %d", documentId);
        String message = String.format(
            "An error occurred during document processing.\n\n" +
            "Document ID: %d\n" +
            "Error: %s\n" +
            "Timestamp: %s\n\n" +
            "Please investigate and take appropriate action.",
            documentId, error, Instant.now()
        );
        
        sendNotification(recipient, subject, message);
    }

    @Override
    public void sendStatusUpdate(Long documentId, String status, Map<String, Object> details) {
        LOGGER.infof("Sending status update for document %d: %s", documentId, status);
        
        // Format details for logging
        StringBuilder detailsStr = new StringBuilder();
        if (details != null && !details.isEmpty()) {
            details.forEach((key, value) -> 
                detailsStr.append(String.format("\n  %s: %s", key, value))
            );
        }
        
        LOGGER.infof("Status Update - Document: %d, Status: %s%s", 
            documentId, status, detailsStr);
        
        // In a real application, this might send to a message queue, webhook, or dashboard
        // For now, we just log it
    }

    @Override
    public void sendProcessingComplete(Long documentId, String fileName, boolean approved) {
        String recipient = "admin@example.com";
        String status = approved ? "Approved" : "Rejected";
        String subject = String.format("Document Processing Complete: %s (%s)", fileName, status);
        String message = String.format(
            "Document processing has been completed.\n\n" +
            "Document ID: %d\n" +
            "File Name: %s\n" +
            "Review Status: %s\n" +
            "Completed at: %s\n\n" +
            "The document is now ready for the next step in your workflow.",
            documentId, fileName, status, Instant.now()
        );
        
        sendNotification(recipient, subject, message);
    }

    @Override
    public void sendProcessingFailed(Long documentId, String fileName, String errorMessage) {
        String recipient = "admin@example.com";
        String subject = String.format("Document Processing Failed: %s", fileName);
        String message = String.format(
            "Document processing has failed.\n\n" +
            "Document ID: %d\n" +
            "File Name: %s\n" +
            "Error: %s\n" +
            "Failed at: %s\n\n" +
            "Please investigate and take corrective action.",
            documentId, fileName, errorMessage, Instant.now()
        );
        
        sendNotification(recipient, subject, message);
    }

    @Override
    public void sendReviewReminder(Long documentId, String reviewerEmail) {
        String subject = String.format("Review Reminder: Document %d requires attention", documentId);
        String message = String.format(
            "This is a reminder that document %d is awaiting your review.\n\n" +
            "Please complete the review at your earliest convenience.\n\n" +
            "Document ID: %d\n" +
            "Reminder sent: %s",
            documentId, documentId, Instant.now()
        );
        
        sendNotification(reviewerEmail, subject, message);
    }

    @Override
    public void notifyPriorityChange(Long documentId, Priority oldPriority, Priority newPriority) {
        Map<String, Object> details = new HashMap<>();
        details.put("oldPriority", oldPriority);
        details.put("newPriority", newPriority);
        details.put("timestamp", Instant.now().toString());
        
        sendStatusUpdate(documentId, "PRIORITY_CHANGED", details);
        
        // If changed to HIGH priority, send urgent notification
        if (newPriority == Priority.HIGH && oldPriority != Priority.HIGH) {
            String recipient = "admin@example.com";
            String subject = String.format("URGENT: Document %d Priority Escalated", documentId);
            String message = String.format(
                "Document priority has been escalated to HIGH.\n\n" +
                "Document ID: %d\n" +
                "Previous Priority: %s\n" +
                "New Priority: HIGH\n" +
                "Timestamp: %s\n\n" +
                "Immediate attention may be required.",
                documentId, oldPriority, Instant.now()
            );
            
            sendNotification(recipient, subject, message);
        }
    }
}