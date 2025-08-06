package com.example.document.resource;

import com.example.document.activity.ReviewActivities;
import com.example.document.model.ReviewDecision;
import com.example.document.repository.DocumentRepository;
import com.example.document.workflow.DocumentProcessingWorkflow;
import io.temporal.client.WorkflowClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/api/documents/review")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReviewResource {

    private static final Logger LOGGER = Logger.getLogger(ReviewResource.class);

    @Inject
    WorkflowClient workflowClient;

    @Inject
    DocumentRepository documentRepository;

    @Inject
    ReviewActivities reviewActivities;

    @GET
    @Path("/pending")
    public Response getPendingReviews() {
        try {
            List<DocumentRepository.DocumentInfo> pendingDocs = documentRepository.getPendingReviews();
            List<Map<String, Object>> pendingReviews = pendingDocs.stream()
                .map(doc -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("documentId", doc.getDocumentId());
                    map.put("fileName", doc.getFileName());
                    map.put("mimeType", doc.getMimeType());
                    map.put("sizeBytes", doc.getSizeBytes());
                    return map;
                })
                .toList();
            
            LOGGER.infof("Found %d pending reviews", pendingReviews.size());
            
            return Response.ok(pendingReviews).build();
            
        } catch (Exception e) {
            LOGGER.error("Failed to get pending reviews", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to retrieve pending reviews"))
                .build();
        }
    }

    @GET
    @Path("/{token}")
    public Response getReviewDocument(@PathParam("token") String token) {
        try {
            DocumentRepository.ReviewTaskInfo reviewInfo = documentRepository.getReviewByToken(token);
            Map<String, Object> reviewData = reviewInfo != null ? new HashMap<>() : null;
            if (reviewInfo != null) {
                reviewData.put("document_id", reviewInfo.getDocumentId());
                reviewData.put("document_name", reviewInfo.getDocumentName());
                reviewData.put("review_status", reviewInfo.getStatus());
                reviewData.put("assigned_at", reviewInfo.getAssignedAt());
                reviewData.put("reviewer_assigned", reviewInfo.getReviewerAssigned());
            }
            
            if (reviewData == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Review not found"))
                    .build();
            }
            
            Long documentId = (Long) reviewData.get("document_id");
            DocumentRepository.DocumentInfo docInfo = documentRepository.retrieveDocument(documentId);
            byte[] content = documentRepository.getDocumentContent(documentId);
            
            Map<String, Object> response = new HashMap<>();
            response.putAll(reviewData);
            response.put("contentSize", content.length);
            // In a real app, you might return a URL to download the document
            // or return the content as base64
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOGGER.error("Failed to get review document", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to retrieve review document"))
                .build();
        }
    }

    @POST
    @Path("/{token}/complete")
    public Response completeReview(
            @PathParam("token") String token,
            ReviewDecision decision) {
        
        LOGGER.infof("Completing review for token %s with decision: approved=%s", 
            token.substring(0, Math.min(20, token.length())) + "...", decision.isApproved());
        
        try {
            // Validate the token exists and is pending
            DocumentRepository.ReviewTaskInfo reviewInfo = documentRepository.getReviewByToken(token);
            Map<String, Object> reviewData = reviewInfo != null ? new HashMap<>() : null;
            if (reviewInfo != null) {
                reviewData.put("document_id", reviewInfo.getDocumentId());
                reviewData.put("document_name", reviewInfo.getDocumentName());
                reviewData.put("review_status", reviewInfo.getStatus());
                reviewData.put("assigned_at", reviewInfo.getAssignedAt());
                reviewData.put("reviewer_assigned", reviewInfo.getReviewerAssigned());
            }
            if (reviewData == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Review not found"))
                    .build();
            }
            
            String status = (String) reviewData.get("review_status");
            if (!"PENDING".equals(status)) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Review already completed"))
                    .build();
            }
            
            // Set reviewer if not provided
            if (decision.getReviewedBy() == null) {
                decision.setReviewedBy("reviewer@example.com"); // In real app, get from auth context
            }
            decision.setReviewedAt(System.currentTimeMillis());
            
            // Complete the activity asynchronously
            reviewActivities.completeReview(token, decision);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Review completed successfully");
            response.put("approved", decision.isApproved());
            response.put("reviewedBy", decision.getReviewedBy());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOGGER.error("Failed to complete review", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to complete review: " + e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/workflow/{workflowId}/submit")
    public Response submitReviewDirectly(
            @PathParam("workflowId") String workflowId,
            ReviewDecision decision) {
        
        LOGGER.infof("Submitting review directly to workflow %s", workflowId);
        
        try {
            // Get workflow stub
            DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(
                DocumentProcessingWorkflow.class, workflowId);
            
            // Set reviewer if not provided
            if (decision.getReviewedBy() == null) {
                decision.setReviewedBy("reviewer@example.com"); // In real app, get from auth context
            }
            decision.setReviewedAt(System.currentTimeMillis());
            
            // Submit review via update method
            ReviewDecision result = workflow.submitReview(decision);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Review submitted successfully");
            response.put("workflowId", workflowId);
            response.put("decision", result);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOGGER.error("Failed to submit review directly", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to submit review: " + e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/stats")
    public Response getReviewStats() {
        try {
            Map<String, Object> stats = documentRepository.getReviewStatistics();
            
            return Response.ok(stats).build();
            
        } catch (Exception e) {
            LOGGER.error("Failed to get review statistics", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to retrieve review statistics"))
                .build();
        }
    }
}