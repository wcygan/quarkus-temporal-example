package com.example.document.resource;

import com.example.document.model.*;
import com.example.document.repository.DocumentRepository;
import com.example.document.workflow.DocumentProcessingWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.SearchAttributeKey;
import io.temporal.common.SearchAttributes;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("/api/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DocumentResource {

    private static final Logger LOGGER = Logger.getLogger(DocumentResource.class);
    private static final String TASK_QUEUE = "document-processing";

    @Inject
    WorkflowClient workflowClient;

    @Inject
    DocumentRepository documentRepository;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadDocument(
            @FormParam("file") FileUpload fileUpload,
            @FormParam("priority") @DefaultValue("MEDIUM") String priorityStr,
            @FormParam("documentType") @DefaultValue("UNKNOWN") String documentTypeStr) {
        
        LOGGER.infof("Uploading document: %s", fileUpload.fileName());
        
        try {
            // Read file content
            byte[] content = Files.readAllBytes(fileUpload.uploadedFile());
            
            // Parse priority and document type
            Priority priority = Priority.valueOf(priorityStr.toUpperCase());
            DocumentType documentType = DocumentType.valueOf(documentTypeStr.toUpperCase());
            
            // Create document request
            DocumentRequest request = new DocumentRequest();
            request.setFileName(fileUpload.fileName());
            request.setMimeType(fileUpload.contentType());
            request.setContent(content);
            request.setPriority(priority);
            request.setDocumentType(documentType);
            request.setUploadedBy("user@example.com"); // In real app, get from auth context
            request.setUploadTimestamp(Instant.now());
            
            // Generate workflow ID
            String workflowId = "doc-processing-" + UUID.randomUUID().toString();
            
            // Set up search attributes
            SearchAttributes searchAttributes = SearchAttributes.newBuilder()
                .set(SearchAttributeKey.forKeyword("DocumentType"), documentType.toString())
                .set(SearchAttributeKey.forKeyword("ProcessingStatus"), "UPLOADED")
                .set(SearchAttributeKey.forKeyword("Priority"), priority.toString())
                .set(SearchAttributeKey.forText("UploadDate"), Instant.now().toString())
                .build();
            
            // Start workflow
            WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TASK_QUEUE)
                .setTypedSearchAttributes(searchAttributes)
                .build();
            
            DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(
                DocumentProcessingWorkflow.class, options);
            
            // Start workflow execution asynchronously
            WorkflowClient.start(workflow::processDocument, request);
            
            // Return workflow ID for tracking
            Map<String, Object> response = new HashMap<>();
            response.put("workflowId", workflowId);
            response.put("status", "PROCESSING_STARTED");
            response.put("fileName", fileUpload.fileName());
            response.put("fileSize", content.length);
            response.put("priority", priority);
            response.put("documentType", documentType);
            
            LOGGER.infof("Started workflow %s for document %s", workflowId, fileUpload.fileName());
            
            return Response.ok(response).build();
            
        } catch (IOException e) {
            LOGGER.error("Failed to read uploaded file", e);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Failed to read uploaded file"))
                .build();
        } catch (Exception e) {
            LOGGER.error("Failed to start document processing", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to start document processing"))
                .build();
        }
    }

    @GET
    @Path("/{workflowId}/status")
    public Response getDocumentStatus(@PathParam("workflowId") String workflowId) {
        try {
            DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(
                DocumentProcessingWorkflow.class, workflowId);
            
            DocumentStatus status = workflow.getStatus();
            ProcessingMetrics metrics = workflow.getMetrics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("workflowId", workflowId);
            response.put("status", status);
            response.put("metrics", metrics);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOGGER.error("Failed to get document status", e);
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Workflow not found or not accessible"))
                .build();
        }
    }

    @PUT
    @Path("/{workflowId}/priority")
    public Response updatePriority(
            @PathParam("workflowId") String workflowId,
            Map<String, String> request) {
        
        try {
            String priorityStr = request.get("priority");
            if (priorityStr == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Priority is required"))
                    .build();
            }
            
            Priority priority = Priority.valueOf(priorityStr.toUpperCase());
            
            DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(
                DocumentProcessingWorkflow.class, workflowId);
            
            workflow.updatePriority(priority);
            
            Map<String, Object> response = new HashMap<>();
            response.put("workflowId", workflowId);
            response.put("priority", priority);
            response.put("status", "Priority updated successfully");
            
            return Response.ok(response).build();
            
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Invalid priority value"))
                .build();
        } catch (Exception e) {
            LOGGER.error("Failed to update priority", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to update priority"))
                .build();
        }
    }

    @DELETE
    @Path("/{workflowId}")
    public Response cancelProcessing(@PathParam("workflowId") String workflowId) {
        try {
            DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(
                DocumentProcessingWorkflow.class, workflowId);
            
            workflow.cancelProcessing();
            
            Map<String, Object> response = new HashMap<>();
            response.put("workflowId", workflowId);
            response.put("status", "Processing cancelled");
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOGGER.error("Failed to cancel processing", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to cancel processing"))
                .build();
        }
    }

    @GET
    @Path("/{workflowId}/metrics")
    public Response getMetrics(@PathParam("workflowId") String workflowId) {
        try {
            DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(
                DocumentProcessingWorkflow.class, workflowId);
            
            ProcessingMetrics metrics = workflow.getMetrics();
            
            return Response.ok(metrics).build();
            
        } catch (Exception e) {
            LOGGER.error("Failed to get metrics", e);
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Workflow not found or not accessible"))
                .build();
        }
    }
}