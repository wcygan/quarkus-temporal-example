package com.example.document.activity;

import com.example.document.repository.DocumentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class DocumentStorageActivitiesImpl implements DocumentStorageActivities {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentStorageActivitiesImpl.class);
    
    @Inject
    DocumentRepository repository;
    
    @Override
    public Long storeDocument(String workflowId, String fileName, String mimeType, byte[] content) {
        logger.info("Storing document: {} ({})", fileName, mimeType);
        
        // Validate inputs
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Document content cannot be empty");
        }
        
        if (content.length > 10 * 1024 * 1024) { // 10MB limit
            throw new IllegalArgumentException("Document size exceeds 10MB limit");
        }
        
        Long documentId = repository.saveDocument(workflowId, fileName, mimeType, content);
        
        logger.info("Document stored with ID: {}", documentId);
        return documentId;
    }
    
    @Override
    public byte[] retrieveDocument(Long documentId) {
        logger.info("Retrieving document: {}", documentId);
        return repository.getDocumentContent(documentId);
    }
    
    @Override
    public void updateDocumentStatus(Long documentId, String status) {
        logger.info("Updating document {} status to: {}", documentId, status);
        repository.updateDocumentStatus(documentId, status);
    }
    
    @Override
    public void deleteDocument(Long documentId) {
        logger.info("Deleting document: {}", documentId);
        // Implement soft delete or actual deletion based on requirements
        repository.updateDocumentStatus(documentId, "DELETED");
    }
}