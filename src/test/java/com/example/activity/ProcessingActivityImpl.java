package com.example.activity;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessingActivityImpl implements ProcessingActivity {
    
    @Override
    public String startProcessing(String input) {
        System.out.println("Starting processing for: " + input);
        return "PROCESSING_" + input;
    }
    
    @Override
    public String completeProcessing(String intermediateResult) {
        System.out.println("Completing processing for: " + intermediateResult);
        return "COMPLETED_" + intermediateResult;
    }
}