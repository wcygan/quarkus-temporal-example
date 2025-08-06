package com.example.document.model;

import java.io.Serializable;
import java.time.Instant;

public class DocumentRequest implements Serializable {
    private String workflowId;
    private String fileName;
    private String mimeType;
    private byte[] content;
    private Priority priority;
    private DocumentType documentType;
    private String uploadedBy;
    private Instant uploadTimestamp;

    public DocumentRequest() {
    }

    public DocumentRequest(String workflowId, String fileName, String mimeType, byte[] content) {
        this.workflowId = workflowId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.content = content;
        this.uploadTimestamp = Instant.now();
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(Instant uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }
}