package com.example.resource;

import com.example.workflow.ParentWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/parent-workflow")
@Produces(MediaType.APPLICATION_JSON)
public class ParentWorkflowResource {
    
    @Inject
    WorkflowClient workflowClient;
    
    @POST
    @Path("/start")
    public Response startParentWorkflow(@QueryParam("input") String input) {
        if (input == null || input.trim().isEmpty()) {
            input = "default-input";
        }
        
        try {
            String workflowId = "parent-workflow-" + System.currentTimeMillis();
            
            ParentWorkflow workflow = workflowClient.newWorkflowStub(
                ParentWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue("doc-approval-queue")
                    .build()
            );
            
            WorkflowClient.start(workflow::executeParent, input);
            
            return Response.ok()
                .entity("{\"status\": \"started\", \"workflowId\": \"" + workflowId + "\"}")
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    @GET
    @Path("/result")
    public Response getWorkflowResult(@QueryParam("workflowId") String workflowId) {
        if (workflowId == null || workflowId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"workflowId is required\"}")
                .build();
        }
        
        try {
            ParentWorkflow workflow = workflowClient.newWorkflowStub(
                ParentWorkflow.class,
                workflowId
            );
            
            String result = workflow.executeParent(null);
            
            return Response.ok()
                .entity("{\"result\": \"" + result + "\"}")
                .build();
        } catch (Exception e) {
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
}