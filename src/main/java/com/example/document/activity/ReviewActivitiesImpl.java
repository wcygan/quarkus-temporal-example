package com.example.document.activity;

import com.example.document.model.ReviewDecision;
import com.example.document.repository.DocumentRepository;
import io.temporal.activity.Activity;
import io.temporal.client.ActivityCompletionClient;
import io.temporal.client.WorkflowClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;

@ApplicationScoped
public class ReviewActivitiesImpl implements ReviewActivities {

    private static final Logger LOGGER = Logger.getLogger(ReviewActivitiesImpl.class);

    @Inject
    DocumentRepository documentRepository;

    @Inject
    WorkflowClient workflowClient;

    @Override
    public void requestReview(Long documentId) {
        LOGGER.infof("Requesting human review for document %d", documentId);
        
        // Get the activity task token for async completion
        byte[] taskToken = Activity.getExecutionContext().getTaskToken();
        String encodedToken = Base64.getEncoder().encodeToString(taskToken);
        
        try {
            // Store the activity token in the database for later completion
            documentRepository.createReviewRequest(documentId, encodedToken);
            
            LOGGER.infof("Review request created for document %d with token: %s", 
                documentId, encodedToken.substring(0, 20) + "...");
            
            // Activity will complete asynchronously when reviewer submits decision
            // The activity does NOT return here - it waits for external completion
            Activity.getExecutionContext().doNotCompleteOnReturn();
            
        } catch (SQLException e) {
            LOGGER.error("Failed to create review request", e);
            throw new RuntimeException("Failed to create review request", e);
        }
    }

    @Override
    public void completeReview(String activityToken, ReviewDecision decision) {
        LOGGER.infof("Completing review with decision: approved=%s", decision.isApproved());
        
        try {
            // Decode the activity token
            byte[] taskToken = Base64.getDecoder().decode(activityToken);
            
            // Get completion client
            ActivityCompletionClient completionClient = workflowClient.newActivityCompletionClient();
            
            // Complete the activity with the review decision
            completionClient.complete(taskToken, decision);
            
            // We need to find the document ID from the activity token
            // For now, we'll add a method to retrieve document ID by token
            Long documentId = documentRepository.getDocumentIdByActivityToken(activityToken);
            documentRepository.completeReview(documentId, decision);
            
            LOGGER.infof("Review completed successfully for token: %s", 
                activityToken.substring(0, 20) + "...");
            
        } catch (Exception e) {
            LOGGER.error("Failed to complete review", e);
            
            // If we can't complete the activity, we should report failure
            try {
                byte[] taskToken = Base64.getDecoder().decode(activityToken);
                ActivityCompletionClient completionClient = workflowClient.newActivityCompletionClient();
                completionClient.completeExceptionally(taskToken, e);
            } catch (Exception failureReportException) {
                LOGGER.error("Failed to report activity failure", failureReportException);
            }
            
            throw new RuntimeException("Failed to complete review", e);
        }
    }
}