package com.example.document.activity;

import com.example.document.model.*;
import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface ProcessingActivities {
    
    DocumentMetadata extractMetadata(Long documentId);
    
    OcrResult performOcr(Long documentId);
    
    Classification classifyDocument(Long documentId, String text);
    
    void finalizeDocument(Long documentId, ReviewDecision decision);
}