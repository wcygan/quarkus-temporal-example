package com.example.document.model;

public enum DocumentStatus {
    UPLOADED("Document uploaded"),
    VALIDATING("Validating document"),
    PRE_PROCESSING("Pre-processing document"),
    PROCESSING("Processing document"),
    OCR_PROCESSING("Performing OCR"),
    CLASSIFYING("Classifying document"),
    PENDING_REVIEW("Awaiting human review"),
    REVIEWING("Under review"),
    APPROVED("Document approved"),
    REJECTED("Document rejected"),
    POST_PROCESSING("Post-processing document"),
    COMPLETED("Processing completed"),
    FAILED("Processing failed"),
    CANCELLED("Processing cancelled");

    private final String description;

    DocumentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}