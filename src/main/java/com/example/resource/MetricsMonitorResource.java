package com.example.resource;

import com.example.workflow.MetricsMonitorWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.api.common.v1.WorkflowExecution;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

@Path("/metrics-monitor")
@Produces(MediaType.APPLICATION_JSON)
public class MetricsMonitorResource {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsMonitorResource.class);
    private static final String MONITOR_WORKFLOW_ID = "metrics-monitor-workflow";
    
    @Inject
    WorkflowClient workflowClient;
    
    @POST
    @Path("/start")
    public Response startMonitoring() {
        try {
            MetricsMonitorWorkflow workflow = workflowClient.newWorkflowStub(
                MetricsMonitorWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setWorkflowId(MONITOR_WORKFLOW_ID)
                    .setTaskQueue("doc-approval-queue")
                    .build()
            );
            
            WorkflowExecution execution = WorkflowClient.start(workflow::monitorMetrics, 0);
            
            logger.info("Started metrics monitor workflow with ID: {}", MONITOR_WORKFLOW_ID);
            
            return Response.ok()
                .entity("{\"status\": \"started\", \"workflowId\": \"" + MONITOR_WORKFLOW_ID + 
                       "\", \"runId\": \"" + execution.getRunId() + "\"}")
                .build();
        } catch (Exception e) {
            logger.error("Failed to start metrics monitor workflow", e);
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @DELETE
    @Path("/stop")
    public Response stopMonitoring() {
        try {
            workflowClient.newUntypedWorkflowStub(MONITOR_WORKFLOW_ID).cancel();
            
            logger.info("Cancelled metrics monitor workflow with ID: {}", MONITOR_WORKFLOW_ID);
            
            return Response.ok()
                .entity("{\"status\": \"cancelled\", \"workflowId\": \"" + MONITOR_WORKFLOW_ID + "\"}")
                .build();
        } catch (Exception e) {
            logger.error("Failed to stop metrics monitor workflow", e);
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @PUT
    @Path("/pause")
    public Response pauseMonitoring() {
        try {
            MetricsMonitorWorkflow workflow = workflowClient.newWorkflowStub(
                MetricsMonitorWorkflow.class,
                MONITOR_WORKFLOW_ID
            );
            
            workflow.pauseMonitoring();
            
            return Response.ok()
                .entity("{\"status\": \"paused\", \"workflowId\": \"" + MONITOR_WORKFLOW_ID + "\"}")
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @PUT
    @Path("/resume")
    public Response resumeMonitoring() {
        try {
            MetricsMonitorWorkflow workflow = workflowClient.newWorkflowStub(
                MetricsMonitorWorkflow.class,
                MONITOR_WORKFLOW_ID
            );
            
            workflow.resumeMonitoring();
            
            return Response.ok()
                .entity("{\"status\": \"resumed\", \"workflowId\": \"" + MONITOR_WORKFLOW_ID + "\"}")
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @PUT
    @Path("/threshold")
    public Response updateThreshold(@QueryParam("metric") String metric, @QueryParam("value") Double value) {
        if (metric == null || value == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"Both metric and value parameters are required\"}")
                .build();
        }
        
        try {
            MetricsMonitorWorkflow workflow = workflowClient.newWorkflowStub(
                MetricsMonitorWorkflow.class,
                MONITOR_WORKFLOW_ID
            );
            
            workflow.updateThreshold(metric, value);
            
            return Response.ok()
                .entity("{\"status\": \"updated\", \"metric\": \"" + metric + 
                       "\", \"threshold\": " + value + "}")
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/status")
    public Response getStatus() {
        try {
            MetricsMonitorWorkflow workflow = workflowClient.newWorkflowStub(
                MetricsMonitorWorkflow.class,
                MONITOR_WORKFLOW_ID
            );
            
            int eventsProcessed = workflow.getTotalEventsProcessed();
            int alertsTriggered = workflow.getAlertsTriggered();
            List<String> recentMetrics = workflow.getRecentMetrics();
            Map<String, Double> thresholds = workflow.getCurrentThresholds();
            
            String json = String.format(
                "{\"workflowId\": \"%s\", \"eventsProcessed\": %d, \"alertsTriggered\": %d, " +
                "\"recentMetricsCount\": %d, \"thresholds\": %s}",
                MONITOR_WORKFLOW_ID, eventsProcessed, alertsTriggered, 
                recentMetrics.size(), thresholds.toString()
            );
            
            return Response.ok().entity(json).build();
        } catch (Exception e) {
            return Response.ok()
                .entity("{\"workflowId\": \"" + MONITOR_WORKFLOW_ID + 
                       "\", \"status\": \"NOT_FOUND\", \"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/metrics")
    public Response getRecentMetrics() {
        try {
            MetricsMonitorWorkflow workflow = workflowClient.newWorkflowStub(
                MetricsMonitorWorkflow.class,
                MONITOR_WORKFLOW_ID
            );
            
            List<String> recentMetrics = workflow.getRecentMetrics();
            
            String metricsJson = recentMetrics.stream()
                .map(m -> "\"" + m + "\"")
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
            
            return Response.ok()
                .entity("{\"recentMetrics\": " + metricsJson + "}")
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
}