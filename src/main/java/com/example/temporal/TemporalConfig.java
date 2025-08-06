package com.example.temporal;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.client.WorkflowClientOptions;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TemporalConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(TemporalConfig.class);
    
    @ConfigProperty(name = "temporal.target", defaultValue = "localhost:7233")
    String temporalTarget;
    
    @ConfigProperty(name = "temporal.namespace", defaultValue = "default")
    String temporalNamespace;
    
    @Produces
    @ApplicationScoped
    public WorkflowClient workflowClient() {
        logger.info("Connecting to Temporal server at: {}", temporalTarget);
        
        // Connect to the real Temporal server
        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
            .setTarget(temporalTarget)
            .build();
            
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
        
        WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
            .setNamespace(temporalNamespace)
            .build();
            
        WorkflowClient client = WorkflowClient.newInstance(service, clientOptions);
        logger.info("Successfully connected to Temporal server");
        
        return client;
    }
}
