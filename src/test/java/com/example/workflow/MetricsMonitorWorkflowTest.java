package com.example.workflow;

import com.example.activity.MetricsActivityImpl;
import com.example.activity.AlertActivityImpl;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsMonitorWorkflowTest {
    
    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    
    @BeforeEach
    public void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("test-task-queue");
        worker.registerWorkflowImplementationTypes(MetricsMonitorWorkflowImpl.class);
        worker.registerActivitiesImplementations(new MetricsActivityImpl(), new AlertActivityImpl());
        testEnv.start();
    }
    
    @AfterEach
    public void tearDown() {
        testEnv.close();
    }
    
    @Test
    public void testMetricsMonitoring() {
        MetricsMonitorWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            MetricsMonitorWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(2))
                .build()
        );
        
        // Start workflow
        io.temporal.client.WorkflowClient.start(workflow::monitorMetrics, 0);
        
        // Let it run for a bit
        testEnv.sleep(Duration.ofSeconds(35)); // More than one cycle
        
        // Query workflow state
        assertTrue(workflow.getTotalEventsProcessed() > 0);
        assertNotNull(workflow.getRecentMetrics());
        assertFalse(workflow.getRecentMetrics().isEmpty());
    }
    
    @Test
    public void testThresholdUpdates() {
        MetricsMonitorWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            MetricsMonitorWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        // Start workflow
        io.temporal.client.WorkflowClient.start(workflow::monitorMetrics, 0);
        
        // Get initial thresholds
        Map<String, Double> initialThresholds = workflow.getCurrentThresholds();
        assertEquals(80.0, initialThresholds.get("cpu"));
        assertEquals(90.0, initialThresholds.get("memory"));
        assertEquals(85.0, initialThresholds.get("disk"));
        
        // Update threshold
        workflow.updateThreshold("cpu", 70.0);
        
        // Verify update
        Map<String, Double> updatedThresholds = workflow.getCurrentThresholds();
        assertEquals(70.0, updatedThresholds.get("cpu"));
    }
    
    @Test
    public void testPauseResume() {
        MetricsMonitorWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            MetricsMonitorWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        // Start workflow
        io.temporal.client.WorkflowClient.start(workflow::monitorMetrics, 0);
        
        // Let it process some events
        testEnv.sleep(Duration.ofSeconds(35));
        int eventsBeforePause = workflow.getTotalEventsProcessed();
        assertTrue(eventsBeforePause > 0);
        
        // Pause monitoring
        workflow.pauseMonitoring();
        
        // Sleep while paused
        testEnv.sleep(Duration.ofSeconds(35));
        
        // Should not have processed more events (or very few due to timing)
        int eventsAfterPause = workflow.getTotalEventsProcessed();
        assertTrue(eventsAfterPause - eventsBeforePause <= 1, 
            "Should not process many events while paused");
        
        // Resume monitoring
        workflow.resumeMonitoring();
        
        // Let it process more
        testEnv.sleep(Duration.ofSeconds(35));
        
        // Should have processed more events
        assertTrue(workflow.getTotalEventsProcessed() > eventsBeforePause);
    }
    
    @Test
    public void testContinueAsNewWithPreviousState() {
        MetricsMonitorWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(
            MetricsMonitorWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue("test-task-queue")
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
                .build()
        );
        
        // Start workflow with 50 previous events (simulating continue-as-new)
        io.temporal.client.WorkflowClient.start(workflow::monitorMetrics, 50);
        
        // Let it run
        testEnv.sleep(Duration.ofSeconds(35));
        
        // Should have events from previous execution plus new ones
        assertTrue(workflow.getTotalEventsProcessed() > 50);
    }
}