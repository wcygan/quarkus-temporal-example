package com.example.resource;

import com.example.workflow.HelloWorkflow;
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

@Path("/hello-workflow")
@Produces(MediaType.APPLICATION_JSON)
public class HelloWorkflowResource {
    
    @Inject
    WorkflowClient workflowClient;
    
    @POST
    @Path("/start")
    public Response startHelloWorkflow(@QueryParam("name") String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "World";
        }
        
        try {
            String workflowId = "hello-workflow-" + System.currentTimeMillis();
            
            HelloWorkflow workflow = workflowClient.newWorkflowStub(
                HelloWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue("doc-approval-queue")
                    .build()
            );
            
            // Start workflow execution asynchronously
            WorkflowClient.start(workflow::executeHello, name);
            
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
            HelloWorkflow workflow = workflowClient.newWorkflowStub(
                HelloWorkflow.class,
                workflowId
            );
            
            // This will block until the workflow completes
            String result = workflow.executeHello(null);
            
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