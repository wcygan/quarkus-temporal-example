# Document Processing Pipeline Implementation Plan

## Overview

This document outlines the complete implementation plan for the DocumentProcessingPipeline workflow, which demonstrates advanced Temporal features including asynchronous activity completion, heartbeating, update methods, and search attributes while processing real documents stored in MySQL.

## Implementation Steps

### Phase 1: Infrastructure Setup

#### Step 1.1: Add Dedicated MySQL Database for Document Storage
- Create a separate MySQL instance for the application (docstore database)
- Keep Temporal's database isolated from application data
- Configure health checks and volumes for persistence

#### Step 1.2: Configure Quarkus Datasources
- Set up named datasource for docstore
- Configure connection pooling
- Add MySQL JDBC driver dependency

#### Step 1.3: Set Up Flyway Migrations
- Create migration directory structure
- Write initial schema migration (V1__Create_document_tables.sql)
- Configure Flyway for automatic migration on startup

#### Step 1.4: Configure jOOQ Code Generation
- Set up jOOQ Maven plugin
- Configure code generation from database schema
- Generate type-safe query classes

### Phase 2: Core Workflow Implementation

#### Step 2.1: Define Workflow Interface
```java
@WorkflowInterface
public interface DocumentProcessingWorkflow {
    @WorkflowMethod
    ProcessingResult processDocument(DocumentRequest request);
    
    @UpdateMethod
    void updatePriority(Priority priority);
    
    @UpdateMethod  
    ReviewDecision submitReview(ReviewDecision decision);
    
    @QueryMethod
    DocumentStatus getStatus();
    
    @QueryMethod
    ProcessingMetrics getMetrics();
    
    @SignalMethod
    void cancelProcessing();
}
```

#### Step 2.2: Implement Workflow Logic
- Orchestrate activity calls in sequence
- Handle async activity completion for human review
- Implement update and query methods
- Add compensation logic for failures

#### Step 2.3: Configure Search Attributes
- DocumentType (Invoice, Contract, Report, Letter)
- ProcessingStatus (Uploaded, Processing, Review, Complete)
- Priority (High, Medium, Low)
- UploadDate (ISO timestamp)

### Phase 3: Activity Implementation

#### Step 3.1: Document Storage Activities
```java
@ActivityInterface
public interface DocumentStorageActivities {
    Long storeDocument(String workflowId, String name, byte[] content);
    byte[] retrieveDocument(Long documentId);
    void updateDocumentStatus(Long documentId, String status);
}
```

#### Step 3.2: Processing Activities
```java
@ActivityInterface
public interface ProcessingActivities {
    // Pre-processing
    DocumentMetadata extractMetadata(Long documentId);
    
    // OCR with heartbeating
    OcrResult performOcr(Long documentId);
    
    // ML Classification
    Classification classifyDocument(Long documentId, String text);
    
    // Post-processing
    void finalizeDocument(Long documentId, ReviewDecision decision);
}
```

#### Step 3.3: Review Activities (Async Completion)
```java
@ActivityInterface
public interface ReviewActivities {
    // Returns immediately with activity token stored in DB
    void requestReview(Long documentId);
    
    // Called by REST endpoint to complete activity
    void completeReview(String activityToken, ReviewDecision decision);
}
```

### Phase 4: REST API Implementation

#### Step 4.1: Document Upload Endpoint
- POST /api/documents/upload
- Accept multipart form data
- Store document in MySQL
- Start workflow execution
- Return workflow ID for tracking

#### Step 4.2: Review Management Endpoints
- GET /api/documents/review/pending - List pending reviews
- GET /api/documents/review/{token} - Get document for review
- POST /api/documents/review/{token}/complete - Submit review decision

#### Step 4.3: Monitoring Endpoints
- GET /api/documents/{workflowId}/status - Query workflow status
- GET /api/documents/{workflowId}/metrics - Get processing metrics
- PUT /api/documents/{workflowId}/priority - Update document priority

### Phase 5: Advanced Features

#### Step 5.1: Implement Heartbeating in OCR Activity
```java
public OcrResult performOcr(Long documentId) {
    Activity.getExecutionContext().heartbeat("Starting OCR");
    
    for (int page = 1; page <= totalPages; page++) {
        // Process page
        Activity.getExecutionContext().heartbeat(
            String.format("Processing page %d/%d", page, totalPages)
        );
        Thread.sleep(1000); // Simulate processing
    }
    
    return ocrResult;
}
```

#### Step 5.2: Implement Update Methods
- Priority updates while workflow is running
- Direct review submission via update
- Validation in update validators

#### Step 5.3: Add Local Activities
- Quick validations without queue overhead
- Notification sending
- Metric recording

### Phase 6: Testing

#### Step 6.1: Unit Tests with TestWorkflowEnvironment
- Test happy path document processing
- Test compensation on failures
- Test async activity completion
- Test update method validation

#### Step 6.2: Integration Tests
- Test with real MySQL database (TestContainers)
- Test REST endpoints
- Test end-to-end document processing

#### Step 6.3: Performance Tests
- Test heartbeating under load
- Test concurrent document processing
- Measure database query performance

## Database Schema

```sql
-- Main documents table
CREATE TABLE documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content LONGBLOB NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_uploaded_at (uploaded_at)
);

-- Document metadata and processing results
CREATE TABLE document_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED',
    document_type VARCHAR(50),
    ocr_text LONGTEXT,
    ocr_confidence DECIMAL(5,2),
    classification_result JSON,
    processing_started_at TIMESTAMP NULL,
    processing_completed_at TIMESTAMP NULL,
    error_message TEXT,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    INDEX idx_status (status),
    INDEX idx_document_type (document_type)
);

-- Human review tracking
CREATE TABLE document_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    activity_token TEXT NOT NULL,
    reviewer_assigned VARCHAR(255),
    review_status VARCHAR(50) DEFAULT 'PENDING',
    review_decision BOOLEAN,
    review_comments TEXT,
    review_tags JSON,
    required_actions JSON,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    INDEX idx_review_status (review_status),
    INDEX idx_activity_token_hash (SHA2(activity_token, 256))
);

-- Processing history for audit
CREATE TABLE document_processing_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    stage VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    details JSON,
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    INDEX idx_document_stage (document_id, stage)
);
```

## Key Temporal Features Demonstrated

1. **Asynchronous Activity Completion**
   - Human review activities complete externally via REST API
   - Activity tokens stored in MySQL for correlation

2. **Heartbeating**
   - OCR activity sends progress updates every 2 seconds
   - Allows detection of stuck activities

3. **Update Methods**
   - Change document priority during processing
   - Submit review decisions directly to workflow

4. **Search Attributes**
   - Enable finding workflows by document type, status, priority
   - Support for complex queries in Temporal Web UI

5. **Local Activities**
   - Fast validations without task queue overhead
   - Notification sending

6. **Side Effects**
   - Deterministic document ID generation
   - Consistent random sampling for ML

7. **Query Methods**
   - Real-time status monitoring
   - Processing metrics retrieval

8. **Compensation Logic**
   - Cleanup on failures
   - Rollback document status

## Testing Strategy

### Unit Tests
```java
@Test
void testDocumentProcessingHappyPath() {
    // Arrange
    TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker("document-processing");
    
    // Register workflow and mocked activities
    worker.registerWorkflowImplementationTypes(
        DocumentProcessingWorkflowImpl.class
    );
    
    // Mock activities
    DocumentStorageActivities storage = mock(DocumentStorageActivities.class);
    when(storage.storeDocument(any(), any(), any())).thenReturn(1L);
    worker.registerActivitiesImplementations(storage);
    
    // Act
    DocumentProcessingWorkflow workflow = env.getWorkflowClient()
        .newWorkflowStub(DocumentProcessingWorkflow.class);
    
    ProcessingResult result = workflow.processDocument(
        new DocumentRequest("test.pdf", "application/pdf", content)
    );
    
    // Assert
    assertEquals("COMPLETED", result.getStatus());
}

@Test
void testAsyncActivityCompletion() {
    // Test human review async completion
    // Use TestWorkflowEnvironment with manual activity completion
}

@Test
void testHeartbeating() {
    // Test OCR activity heartbeat reporting
    // Verify heartbeat details are captured
}
```

### Integration Tests
```java
@QuarkusTest
@TestProfile(IntegrationTestProfile.class)
void testEndToEndDocumentProcessing() {
    // Upload document via REST
    // Verify workflow starts
    // Complete human review via REST
    // Verify document is processed
    // Check database state
}
```

## Success Criteria

1. ✅ Documents successfully stored in MySQL BLOB columns
2. ✅ Workflow processes documents through all stages
3. ✅ Human review works with async activity completion
4. ✅ OCR activity reports progress via heartbeating
5. ✅ Update methods allow priority changes
6. ✅ Search attributes enable workflow discovery
7. ✅ All tests pass including integration tests
8. ✅ Performance: Process 100 documents concurrently

## Next Steps After Implementation

1. Add document preview generation (thumbnails)
2. Implement virus scanning activity
3. Add document versioning support
4. Create web UI for document review
5. Add metrics and monitoring dashboards
6. Implement document retention policies
7. Add support for batch document processing