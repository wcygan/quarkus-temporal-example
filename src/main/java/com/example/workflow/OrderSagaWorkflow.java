package com.example.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import com.example.model.OrderRequest;
import com.example.model.OrderResult;
import java.util.List;

@WorkflowInterface
public interface OrderSagaWorkflow {
    
    @WorkflowMethod
    OrderResult processOrder(OrderRequest request);
    
    @QueryMethod
    String getOrderStatus();
    
    @QueryMethod
    List<String> getCompletedSteps();
    
    @QueryMethod
    String getFailureReason();
    
    @SignalMethod
    void simulateFailure(String step);
}