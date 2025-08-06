package com.example.activity;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ParentActivityImpl implements ParentActivity {
    
    private static final Logger logger = LoggerFactory.getLogger(ParentActivityImpl.class);
    
    @Override
    public String startChildWorkflow(String input) {
        String childWorkflowId = "child-workflow-" + System.currentTimeMillis();
        logger.info("Starting child workflow with ID: {} and input: {}", childWorkflowId, input);
        return childWorkflowId;
    }
    
    @Override
    public String waitForChildWorkflowCompletion(String childResult) {
        logger.info("Child workflow completed with result: {}", childResult);
        return "Parent completed. Child result: " + childResult;
    }
}