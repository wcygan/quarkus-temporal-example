package com.example.document.activity;

import com.example.document.model.*;
import com.example.document.repository.DocumentRepository;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class ProcessingActivitiesImpl implements ProcessingActivities {

    private static final Logger LOGGER = Logger.getLogger(ProcessingActivitiesImpl.class);

    @Inject
    DocumentRepository documentRepository;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public DocumentMetadata extractMetadata(Long documentId) {
        LOGGER.infof("Extracting metadata for document %d", documentId);
        
        try {
            // Simulate metadata extraction
            Thread.sleep(500);
            
            DocumentMetadata metadata = new DocumentMetadata();
            metadata.setPageCount(new Random().nextInt(10) + 1);
            metadata.setWordCount(new Random().nextInt(5000) + 100);
            metadata.setLanguage("en");
            metadata.setAuthor("System");
            metadata.setCreatedDate(System.currentTimeMillis());
            
            // Update status
            documentRepository.updateDocumentStatus(documentId, DocumentStatus.PROCESSING.toString());
            
            return metadata;
        } catch (Exception e) {
            LOGGER.error("Failed to extract metadata", e);
            throw new RuntimeException("Metadata extraction failed", e);
        }
    }

    @Override
    public OcrResult performOcr(Long documentId) {
        LOGGER.infof("Starting OCR for document %d", documentId);
        
        ActivityExecutionContext context = Activity.getExecutionContext();
        
        try {
            // Get document size to simulate page count
            byte[] content = documentRepository.getDocumentContent(documentId);
            int estimatedPages = Math.max(1, content.length / 10000); // Rough estimate
            
            OcrResult result = new OcrResult();
            StringBuilder extractedText = new StringBuilder();
            
            // Report initial heartbeat
            context.heartbeat("Starting OCR processing");
            
            // Simulate OCR processing with heartbeating
            for (int page = 1; page <= estimatedPages; page++) {
                // Report progress
                String progress = String.format("Processing page %d/%d", page, estimatedPages);
                LOGGER.info(progress);
                context.heartbeat(progress);
                
                // Simulate OCR processing time (1-2 seconds per page)
                Thread.sleep(1000 + new Random().nextInt(1000));
                
                // Simulate extracted text
                extractedText.append(String.format("Page %d content: Lorem ipsum dolor sit amet, consectetur adipiscing elit. ", page));
                
                // Check for cancellation (simplified for now)
                // Activity.getExecutionContext().heartbeat() handles cancellation checks
            }
            
            // Final heartbeat
            context.heartbeat("OCR processing completed");
            
            result.setExtractedText(extractedText.toString());
            result.setConfidence(0.85 + new Random().nextDouble() * 0.14); // 85-99% confidence
            result.setPageCount(estimatedPages);
            result.setProcessingTimeMs(estimatedPages * 1500L);
            
            LOGGER.infof("OCR completed for document %d with confidence %.2f", documentId, result.getConfidence());
            
            // Store OCR text in database
            documentRepository.updateOcrResult(documentId, result.getExtractedText(), result.getConfidence());
            
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OCR processing interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Database error during OCR", e);
        }
    }

    @Override
    public Classification classifyDocument(Long documentId, String text) {
        LOGGER.infof("Classifying document %d", documentId);
        
        try {
            // Simulate ML classification
            Thread.sleep(1000);
            
            Classification classification = new Classification();
            
            // Simple keyword-based classification (in real app, would use ML model)
            DocumentType type;
            double confidence;
            
            String lowerText = text.toLowerCase();
            if (lowerText.contains("invoice") || lowerText.contains("bill") || lowerText.contains("payment")) {
                type = DocumentType.INVOICE;
                confidence = 0.92;
            } else if (lowerText.contains("contract") || lowerText.contains("agreement") || lowerText.contains("terms")) {
                type = DocumentType.CONTRACT;
                confidence = 0.88;
            } else if (lowerText.contains("report") || lowerText.contains("analysis") || lowerText.contains("summary")) {
                type = DocumentType.REPORT;
                confidence = 0.85;
            } else {
                type = DocumentType.LETTER;
                confidence = 0.75;
            }
            
            classification.setDocumentType(type);
            classification.setConfidence(confidence);
            
            // Add tags based on content
            List<String> tags = new ArrayList<>();
            if (lowerText.contains("urgent")) tags.add("urgent");
            if (lowerText.contains("confidential")) tags.add("confidential");
            if (lowerText.contains("financial")) tags.add("financial");
            if (lowerText.contains("legal")) tags.add("legal");
            
            classification.setTags(tags);
            
            // Add entities (simulated)
            List<String> entities = new ArrayList<>();
            entities.add("organization:ACME Corp");
            entities.add("date:2024-01-15");
            entities.add("amount:$10,000");
            classification.setEntities(entities);
            
            LOGGER.infof("Document %d classified as %s with confidence %.2f", 
                documentId, type, confidence);
            
            // Store classification in database
            try {
                String classificationJson = objectMapper.writeValueAsString(classification);
                documentRepository.updateClassification(documentId, type.toString(), classificationJson);
            } catch (Exception jsonEx) {
                LOGGER.error("Failed to serialize classification", jsonEx);
                documentRepository.updateClassification(documentId, type.toString(), "{}");
            }
            
            return classification;
        } catch (Exception e) {
            LOGGER.error("Classification failed", e);
            throw new RuntimeException("Document classification failed", e);
        }
    }

    @Override
    public void finalizeDocument(Long documentId, ReviewDecision decision) {
        LOGGER.infof("Finalizing document %d with decision: approved=%s", 
            documentId, decision.isApproved());
        
        try {
            String finalStatus = decision.isApproved() ? 
                DocumentStatus.APPROVED.toString() : 
                DocumentStatus.REJECTED.toString();
            
            documentRepository.updateDocumentStatus(documentId, finalStatus);
            
            // Store review decision details
            documentRepository.updateReviewDecision(documentId, decision);
            
            // Simulate post-processing (archiving, indexing, etc.)
            Thread.sleep(500);
            
            LOGGER.infof("Document %d finalized successfully", documentId);
        } catch (Exception e) {
            LOGGER.error("Failed to finalize document", e);
            throw new RuntimeException("Document finalization failed", e);
        }
    }
}