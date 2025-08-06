package com.example.resource;

import com.example.workflow.ScheduledReportWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.api.common.v1.WorkflowExecution;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/scheduled-report")
@Produces(MediaType.APPLICATION_JSON)
public class ScheduledReportResource {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduledReportResource.class);
    private static final String SCHEDULED_WORKFLOW_ID = "scheduled-report-workflow";
    
    @Inject
    WorkflowClient workflowClient;
    
    @POST
    @Path("/start")
    public Response startScheduledWorkflow() {
        try {
            // Create workflow options with cron schedule (every 5 minutes)
            WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(SCHEDULED_WORKFLOW_ID)
                .setTaskQueue("doc-approval-queue")
                .setCronSchedule("*/5 * * * *") // Every 5 minutes
                .build();
            
            ScheduledReportWorkflow workflow = workflowClient.newWorkflowStub(
                ScheduledReportWorkflow.class,
                options
            );
            
            // Start the scheduled workflow
            WorkflowExecution execution = WorkflowClient.start(workflow::generateReports);
            
            logger.info("Started scheduled report workflow with ID: {}", SCHEDULED_WORKFLOW_ID);
            
            return Response.ok()
                .entity("{\"status\": \"started\", \"workflowId\": \"" + SCHEDULED_WORKFLOW_ID + 
                       "\", \"runId\": \"" + execution.getRunId() + 
                       "\", \"schedule\": \"*/5 * * * * (every 5 minutes)\"}")
                .build();
        } catch (Exception e) {
            logger.error("Failed to start scheduled workflow", e);
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @DELETE
    @Path("/stop")
    public Response stopScheduledWorkflow() {
        try {
            // Cancel the workflow using WorkflowClient
            workflowClient.newUntypedWorkflowStub(SCHEDULED_WORKFLOW_ID).cancel();
            
            logger.info("Cancelled scheduled report workflow with ID: {}", SCHEDULED_WORKFLOW_ID);
            
            return Response.ok()
                .entity("{\"status\": \"cancelled\", \"workflowId\": \"" + SCHEDULED_WORKFLOW_ID + "\"}")
                .build();
        } catch (Exception e) {
            logger.error("Failed to stop scheduled workflow", e);
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/status")
    public Response getWorkflowStatus() {
        try {
            // Try to query the workflow to check if it exists
            ScheduledReportWorkflow workflow = workflowClient.newWorkflowStub(
                ScheduledReportWorkflow.class,
                SCHEDULED_WORKFLOW_ID
            );
            
            // If we can query it, it exists
            int reportCount = workflow.getReportCount();
            
            return Response.ok()
                .entity("{\"workflowId\": \"" + SCHEDULED_WORKFLOW_ID + 
                       "\", \"status\": \"RUNNING\", \"reportCount\": " + reportCount + "}")
                .build();
        } catch (Exception e) {
            return Response.ok()
                .entity("{\"workflowId\": \"" + SCHEDULED_WORKFLOW_ID + 
                       "\", \"status\": \"NOT_FOUND\", \"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/query/last-report")
    public Response queryLastReportTime() {
        try {
            ScheduledReportWorkflow workflow = workflowClient.newWorkflowStub(
                ScheduledReportWorkflow.class,
                SCHEDULED_WORKFLOW_ID
            );
            
            String lastReportTime = workflow.getLastReportTime();
            
            return Response.ok()
                .entity("{\"lastReportTime\": \"" + lastReportTime + "\"}")
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/query/count")
    public Response queryReportCount() {
        try {
            ScheduledReportWorkflow workflow = workflowClient.newWorkflowStub(
                ScheduledReportWorkflow.class,
                SCHEDULED_WORKFLOW_ID
            );
            
            int count = workflow.getReportCount();
            
            return Response.ok()
                .entity("{\"reportCount\": " + count + "}")
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/query/recent")
    public Response queryRecentReports() {
        try {
            ScheduledReportWorkflow workflow = workflowClient.newWorkflowStub(
                ScheduledReportWorkflow.class,
                SCHEDULED_WORKFLOW_ID
            );
            
            java.util.List<String> recentReports = workflow.getRecentReports();
            
            return Response.ok()
                .entity("{\"recentReports\": " + 
                       recentReports.stream()
                           .map(r -> "\"" + r + "\"")
                           .collect(java.util.stream.Collectors.joining(", ", "[", "]")) + 
                       "}")
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
}