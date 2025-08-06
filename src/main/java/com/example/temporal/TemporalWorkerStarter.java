package com.example.temporal;

import com.example.activity.HelloActivityImpl;
import com.example.activity.ChildActivityImpl;
import com.example.activity.ParentActivityImpl;
import com.example.activity.ReportActivityImpl;
import com.example.activity.MetricsActivityImpl;
import com.example.activity.AlertActivityImpl;
import com.example.activity.PaymentActivityImpl;
import com.example.activity.InventoryActivityImpl;
import com.example.activity.ShippingActivityImpl;
import com.example.activity.NotificationActivityImpl;
import com.example.document.activity.DocumentStorageActivitiesImpl;
import com.example.document.activity.ProcessingActivitiesImpl;
import com.example.document.activity.ReviewActivitiesImpl;
import com.example.document.activity.NotificationActivitiesImpl;
import com.example.document.workflow.DocumentProcessingWorkflowImpl;
import com.example.workflow.HelloWorkflowImpl;
import com.example.workflow.ChildWorkflowImpl;
import com.example.workflow.ParentWorkflowImpl;
import com.example.workflow.ScheduledReportWorkflowImpl;
import com.example.workflow.MetricsMonitorWorkflowImpl;
import com.example.workflow.OrderSagaWorkflowImpl;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Startup
public class TemporalWorkerStarter {
    
    private static final Logger logger = LoggerFactory.getLogger(TemporalWorkerStarter.class);
    
    @Inject
    io.temporal.client.WorkflowClient client;
    
    @Inject
    HelloActivityImpl helloActivity;
    
    @Inject
    ChildActivityImpl childActivity;
    
    @Inject
    ParentActivityImpl parentActivity;
    
    @Inject
    ReportActivityImpl reportActivity;
    
    @Inject
    MetricsActivityImpl metricsActivity;
    
    @Inject
    AlertActivityImpl alertActivity;
    
    @Inject
    PaymentActivityImpl paymentActivity;
    
    @Inject
    InventoryActivityImpl inventoryActivity;
    
    @Inject
    ShippingActivityImpl shippingActivity;
    
    @Inject
    NotificationActivityImpl notificationActivity;
    
    @Inject
    DocumentStorageActivitiesImpl documentStorageActivities;
    
    @Inject
    ProcessingActivitiesImpl processingActivities;
    
    @Inject
    ReviewActivitiesImpl reviewActivities;
    
    @Inject
    NotificationActivitiesImpl documentNotificationActivities;

    void onStart(@Observes StartupEvent ev) {
        startIfTemporalAvailable();
    }

    void startIfTemporalAvailable() {
        try {
            logger.info("Starting Temporal worker...");
            
            WorkerFactory factory = WorkerFactory.newInstance(client);
            
            // Create worker for existing workflows
            Worker worker = factory.newWorker("doc-approval-queue");
            
            // Register workflow implementations
            worker.registerWorkflowImplementationTypes(
                HelloWorkflowImpl.class,
                ChildWorkflowImpl.class,
                ParentWorkflowImpl.class,
                ScheduledReportWorkflowImpl.class,
                MetricsMonitorWorkflowImpl.class,
                OrderSagaWorkflowImpl.class
            );
            
            // Register activity implementations
            worker.registerActivitiesImplementations(
                helloActivity,
                childActivity,
                parentActivity,
                reportActivity,
                metricsActivity,
                alertActivity,
                paymentActivity,
                inventoryActivity,
                shippingActivity,
                notificationActivity
            );
            
            // Create worker for document processing
            Worker documentWorker = factory.newWorker("document-processing");
            
            // Register document processing workflow
            documentWorker.registerWorkflowImplementationTypes(
                DocumentProcessingWorkflowImpl.class
            );
            
            // Register document processing activities
            documentWorker.registerActivitiesImplementations(
                documentStorageActivities,
                processingActivities,
                reviewActivities,
                documentNotificationActivities
            );
            
            factory.start();
            logger.info("Temporal worker started successfully");
        } catch (Exception e) {
            logger.error("Failed to start Temporal worker", e);
        }
    }
}
