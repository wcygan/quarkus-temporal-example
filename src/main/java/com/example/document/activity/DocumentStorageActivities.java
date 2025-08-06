package com.example.document.activity;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface DocumentStorageActivities {
    
    Long storeDocument(String workflowId, String fileName, String mimeType, byte[] content);
    
    byte[] retrieveDocument(Long documentId);
    
    void updateDocumentStatus(Long documentId, String status);
    
    void deleteDocument(Long documentId);
}