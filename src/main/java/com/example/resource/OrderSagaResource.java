package com.example.resource;

import com.example.model.OrderRequest;
import com.example.model.OrderResult;
import com.example.workflow.OrderSagaWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.api.common.v1.WorkflowExecution;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/order-saga")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderSagaResource {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderSagaResource.class);
    
    @Inject
    WorkflowClient workflowClient;
    
    @POST
    @Path("/start")
    public Response startOrder(OrderRequest request) {
        // Validate request
        if (request == null || request.getCustomerId() == null || request.getItems() == null || request.getItems().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Invalid order request"))
                .build();
        }
        
        try {
            String workflowId = "order-saga-" + System.currentTimeMillis();
            
            OrderSagaWorkflow workflow = workflowClient.newWorkflowStub(
                OrderSagaWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setWorkflowId(workflowId)
                    .setTaskQueue("doc-approval-queue")
                    .build()
            );
            
            // Start workflow execution asynchronously
            WorkflowExecution execution = WorkflowClient.start(workflow::processOrder, request);
            
            logger.info("Started order SAGA workflow: {}", workflowId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "started");
            response.put("workflowId", workflowId);
            response.put("runId", execution.getRunId());
            response.put("message", "Order processing started");
            
            return Response.ok(response).build();
        } catch (Exception e) {
            logger.error("Failed to start order SAGA workflow", e);
            return Response.serverError()
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/start-sample")
    public Response startSampleOrder() {
        // Create a sample order for testing
        OrderRequest request = new OrderRequest();
        request.setCustomerId("CUST-001");
        request.setShippingAddress("123 Main Street, City, State 12345");
        request.setTotalAmount(new BigDecimal("299.99"));
        
        List<OrderRequest.OrderItem> items = new ArrayList<>();
        items.add(new OrderRequest.OrderItem("PRODUCT-001", 2, new BigDecimal("99.99")));
        items.add(new OrderRequest.OrderItem("PRODUCT-002", 1, new BigDecimal("100.01")));
        request.setItems(items);
        
        return startOrder(request);
    }
    
    @GET
    @Path("/status/{workflowId}")
    public Response getOrderStatus(@PathParam("workflowId") String workflowId) {
        if (workflowId == null || workflowId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "workflowId is required"))
                .build();
        }
        
        try {
            OrderSagaWorkflow workflow = workflowClient.newWorkflowStub(
                OrderSagaWorkflow.class,
                workflowId
            );
            
            Map<String, Object> status = new HashMap<>();
            status.put("workflowId", workflowId);
            status.put("orderStatus", workflow.getOrderStatus());
            status.put("completedSteps", workflow.getCompletedSteps());
            
            String failureReason = workflow.getFailureReason();
            if (failureReason != null) {
                status.put("failureReason", failureReason);
            }
            
            return Response.ok(status).build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Workflow not found: " + e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/result/{workflowId}")
    public Response getOrderResult(@PathParam("workflowId") String workflowId) {
        if (workflowId == null || workflowId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "workflowId is required"))
                .build();
        }
        
        try {
            OrderSagaWorkflow workflow = workflowClient.newWorkflowStub(
                OrderSagaWorkflow.class,
                workflowId
            );
            
            // This will block until the workflow completes
            OrderResult result = workflow.processOrder(null);
            
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(Map.of("error", "Failed to get result: " + e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/simulate-failure/{workflowId}")
    public Response simulateFailure(
        @PathParam("workflowId") String workflowId,
        @QueryParam("step") String step) {
        
        if (workflowId == null || step == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Both workflowId and step are required"))
                .build();
        }
        
        // Validate step
        List<String> validSteps = List.of("PAYMENT", "INVENTORY", "SHIPPING", "NOTIFICATION");
        if (!validSteps.contains(step)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Invalid step. Must be one of: " + validSteps))
                .build();
        }
        
        try {
            OrderSagaWorkflow workflow = workflowClient.newWorkflowStub(
                OrderSagaWorkflow.class,
                workflowId
            );
            
            workflow.simulateFailure(step);
            
            return Response.ok(Map.of(
                "status", "failure simulation set",
                "workflowId", workflowId,
                "failureStep", step
            )).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(Map.of("error", "Failed to set failure simulation: " + e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/compensation-history/{workflowId}")
    public Response getCompensationHistory(@PathParam("workflowId") String workflowId) {
        try {
            OrderSagaWorkflow workflow = workflowClient.newWorkflowStub(
                OrderSagaWorkflow.class,
                workflowId
            );
            
            Map<String, Object> history = new HashMap<>();
            history.put("workflowId", workflowId);
            history.put("orderStatus", workflow.getOrderStatus());
            history.put("completedSteps", workflow.getCompletedSteps());
            
            String failureReason = workflow.getFailureReason();
            if (failureReason != null) {
                history.put("failureReason", failureReason);
                history.put("compensationRequired", true);
                
                // Determine which compensations would be executed
                List<String> compensations = new ArrayList<>();
                List<String> completed = workflow.getCompletedSteps();
                
                if (completed.contains("SHIPPING_SCHEDULED")) {
                    compensations.add("CANCEL_SHIPPING");
                }
                if (completed.contains("INVENTORY_RESERVED")) {
                    compensations.add("RELEASE_INVENTORY");
                }
                if (completed.contains("PAYMENT_CHARGED")) {
                    compensations.add("REFUND_PAYMENT");
                }
                compensations.add("SEND_CANCELLATION_NOTIFICATION");
                
                history.put("compensationActions", compensations);
            } else {
                history.put("compensationRequired", false);
            }
            
            return Response.ok(history).build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Workflow not found: " + e.getMessage()))
                .build();
        }
    }
}