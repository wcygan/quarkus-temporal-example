package com.example.activity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParentActivityImplTest {
    
    private ParentActivityImpl parentActivity;
    
    @BeforeEach
    public void setUp() {
        parentActivity = new ParentActivityImpl();
    }
    
    @Test
    public void testStartChildWorkflow() {
        String workflowId = parentActivity.startChildWorkflow("test-input");
        assertTrue(workflowId.startsWith("child-workflow-"));
        assertTrue(workflowId.matches("child-workflow-\\d+"));
    }
    
    @Test
    public void testWaitForChildWorkflowCompletion() {
        String result = parentActivity.waitForChildWorkflowCompletion("child-result");
        assertEquals("Parent completed. Child result: child-result", result);
    }
    
    @Test
    public void testWaitForChildWorkflowCompletionWithEmptyResult() {
        String result = parentActivity.waitForChildWorkflowCompletion("");
        assertEquals("Parent completed. Child result: ", result);
    }
    
    @Test
    public void testWaitForChildWorkflowCompletionWithNullResult() {
        String result = parentActivity.waitForChildWorkflowCompletion(null);
        assertEquals("Parent completed. Child result: null", result);
    }
}