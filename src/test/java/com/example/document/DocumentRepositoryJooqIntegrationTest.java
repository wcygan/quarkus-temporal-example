package com.example.document;

import com.example.document.model.ReviewDecision;
import com.example.document.repository.DocumentRepository;
import com.example.document.repository.DocumentRepositoryJooq;
import com.example.jooq.generated.tables.DocumentMetadata;
import com.example.jooq.generated.tables.Documents;
import com.example.test.MySQLTestResource;
import com.example.test.NotParallelizable;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.example.jooq.generated.Tables.*;
import static org.jooq.impl.DSL.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for jOOQ-based DocumentRepository.
 * Tests type-safe SQL queries using jOOQ DSL with real MySQL database.
 * 
 * Note: This test class uses sequential execution due to shared test data
 * and dependencies between test methods. Individual tests modify and rely
 * on the same document records.
 */
@QuarkusTest
@WithTestResource(value = MySQLTestResource.class, restrictToAnnotatedClass = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@NotParallelizable(reason = "Tests share state via static testDocumentId and have sequential dependencies")
@Execution(ExecutionMode.SAME_THREAD)
public class DocumentRepositoryJooqIntegrationTest {

    @Inject
    DocumentRepositoryJooq repository;

    @Inject
    DSLContext dsl;

    private static Long testDocumentId;
    private static String testWorkflowId;

    @BeforeAll
    public static void setup() {
        testWorkflowId = "jooq-test-" + UUID.randomUUID();
    }

    @Test
    @Order(1)
    @DisplayName("Test saving document with jOOQ")
    public void testSaveDocumentWithJooq() {
        // Save document using repository
        byte[] content = "jOOQ test content".getBytes();
        testDocumentId = repository.saveDocument(testWorkflowId, "jooq-test.pdf", "application/pdf", content);

        assertNotNull(testDocumentId);

        // Verify using direct jOOQ query
        Record document = dsl.select()
            .from(DOCUMENTS)
            .where(DOCUMENTS.ID.eq(testDocumentId))
            .fetchOne();

        assertNotNull(document);
        assertEquals(testWorkflowId, document.get(DOCUMENTS.WORKFLOW_ID));
        assertEquals("jooq-test.pdf", document.get(DOCUMENTS.NAME));
        assertEquals("application/pdf", document.get(DOCUMENTS.MIME_TYPE));
        assertEquals(content.length, document.get(DOCUMENTS.SIZE_BYTES).intValue());
        assertArrayEquals(content, document.get(DOCUMENTS.CONTENT));

        // Verify metadata was created
        Record metadata = dsl.select()
            .from(DOCUMENT_METADATA)
            .where(DOCUMENT_METADATA.DOCUMENT_ID.eq(testDocumentId))
            .fetchOne();

        assertNotNull(metadata);
        assertEquals("UPLOADED", metadata.get(DOCUMENT_METADATA.STATUS));
    }

    @Test
    @Order(2)
    @DisplayName("Test type-safe queries with jOOQ")
    public void testTypeSafeQueries() {
        assertNotNull(testDocumentId);

        // Update document status using repository
        repository.updateDocumentStatus(testDocumentId, "PROCESSING");

        // Query with jOOQ DSL
        String status = dsl.select(DOCUMENT_METADATA.STATUS)
            .from(DOCUMENT_METADATA)
            .where(DOCUMENT_METADATA.DOCUMENT_ID.eq(testDocumentId))
            .fetchOne(DOCUMENT_METADATA.STATUS);

        assertEquals("PROCESSING", status);

        // Complex query with joins
        Result<Record> results = dsl.select()
            .from(DOCUMENTS)
            .join(DOCUMENT_METADATA)
            .on(DOCUMENTS.ID.eq(DOCUMENT_METADATA.DOCUMENT_ID))
            .where(DOCUMENTS.WORKFLOW_ID.eq(testWorkflowId))
            .fetch();

        assertEquals(1, results.size());
        Record record = results.get(0);
        assertEquals("PROCESSING", record.get(DOCUMENT_METADATA.STATUS));
    }

    @Test
    @Order(3)
    @DisplayName("Test OCR and classification updates with jOOQ")
    public void testOcrAndClassificationUpdates() {
        assertNotNull(testDocumentId);

        // Update OCR result
        String ocrText = "This is the extracted text from jOOQ test";
        repository.updateOcrResult(testDocumentId, ocrText, 0.93);

        // Update classification
        String classification = "{\"type\": \"INVOICE\", \"confidence\": 0.87}";
        repository.updateClassification(testDocumentId, "INVOICE", classification);

        // Verify with jOOQ query
        Record metadata = dsl.select(
                DOCUMENT_METADATA.OCR_TEXT,
                DOCUMENT_METADATA.OCR_CONFIDENCE,
                DOCUMENT_METADATA.DOCUMENT_TYPE,
                DOCUMENT_METADATA.CLASSIFICATION_RESULT
            )
            .from(DOCUMENT_METADATA)
            .where(DOCUMENT_METADATA.DOCUMENT_ID.eq(testDocumentId))
            .fetchOne();

        assertEquals(ocrText, metadata.get(DOCUMENT_METADATA.OCR_TEXT));
        assertEquals(0.93, metadata.get(DOCUMENT_METADATA.OCR_CONFIDENCE).doubleValue(), 0.01);
        assertEquals("INVOICE", metadata.get(DOCUMENT_METADATA.DOCUMENT_TYPE));
        assertEquals(classification, metadata.get(DOCUMENT_METADATA.CLASSIFICATION_RESULT).data());
    }

    @Test
    @Order(4)
    @DisplayName("Test review workflow with jOOQ")
    public void testReviewWorkflow() {
        assertNotNull(testDocumentId);

        // Create review task
        String activityToken = "jooq-token-" + UUID.randomUUID();
        Long reviewId = repository.createReviewTask(testDocumentId, activityToken, "jooq-reviewer@example.com");

        assertNotNull(reviewId);

        // Verify with jOOQ
        Record review = dsl.select()
            .from(DOCUMENT_REVIEWS)
            .where(DOCUMENT_REVIEWS.ID.eq(reviewId))
            .fetchOne();

        assertNotNull(review);
        assertEquals(testDocumentId, review.get(DOCUMENT_REVIEWS.DOCUMENT_ID));
        assertEquals(activityToken, review.get(DOCUMENT_REVIEWS.ACTIVITY_TOKEN));
        assertEquals("jooq-reviewer@example.com", review.get(DOCUMENT_REVIEWS.REVIEWER_ASSIGNED));
        assertEquals("PENDING", review.get(DOCUMENT_REVIEWS.REVIEW_STATUS));

        // Get activity token for document
        Optional<String> token = repository.getActivityTokenForDocument(testDocumentId);
        assertTrue(token.isPresent());
        assertEquals(activityToken, token.get());

        // Complete review
        ReviewDecision decision = new ReviewDecision();
        decision.setApproved(true);
        decision.setComments("Approved via jOOQ test");
        decision.setReviewedBy("jooq-reviewer@example.com");
        decision.setTags(List.of("jooq", "test"));
        decision.setRequiredActions(List.of("archive"));

        repository.completeReview(testDocumentId, decision);

        // Verify review completion
        Record completedReview = dsl.select()
            .from(DOCUMENT_REVIEWS)
            .where(DOCUMENT_REVIEWS.ID.eq(reviewId))
            .fetchOne();

        assertEquals("COMPLETED", completedReview.get(DOCUMENT_REVIEWS.REVIEW_STATUS));
        assertEquals((byte) 1, completedReview.get(DOCUMENT_REVIEWS.REVIEW_DECISION));
        assertEquals("Approved via jOOQ test", completedReview.get(DOCUMENT_REVIEWS.REVIEW_COMMENTS));
        assertNotNull(completedReview.get(DOCUMENT_REVIEWS.COMPLETED_AT));
    }

    @Test
    @Order(5)
    @DisplayName("Test complex jOOQ queries with aggregations")
    public void testComplexJooqQueries() {
        // Create multiple documents for testing
        for (int i = 0; i < 5; i++) {
            String workflowId = "jooq-agg-" + i + "-" + UUID.randomUUID();
            byte[] content = ("Content " + i).getBytes();
            Long docId = repository.saveDocument(workflowId, "doc-" + i + ".pdf", "application/pdf", content);

            // Set different statuses
            String status = i % 2 == 0 ? "COMPLETED" : "PENDING_REVIEW";
            repository.updateDocumentStatus(docId, status);
        }

        // Test aggregation query
        var statusCounts = dsl.select(
                DOCUMENT_METADATA.STATUS,
                count().as("count")
            )
            .from(DOCUMENT_METADATA)
            .groupBy(DOCUMENT_METADATA.STATUS)
            .orderBy(count().desc())
            .fetch();

        assertFalse(statusCounts.isEmpty());

        // Test with having clause
        var largeStatusGroups = dsl.select(
                DOCUMENT_METADATA.STATUS,
                count().as("count")
            )
            .from(DOCUMENT_METADATA)
            .groupBy(DOCUMENT_METADATA.STATUS)
            .having(count().gt(0))
            .fetch();

        assertFalse(largeStatusGroups.isEmpty());
    }

    @Test
    @Order(6)
    @DisplayName("Test jOOQ window functions")
    public void testWindowFunctions() {
        // Create documents with varying sizes
        for (int i = 1; i <= 3; i++) {
            String workflowId = "jooq-window-" + i + "-" + UUID.randomUUID();
            byte[] content = new byte[i * 1000]; // Different sizes
            repository.saveDocument(workflowId, "window-doc-" + i + ".pdf", "application/pdf", content);
        }

        // Test window function query
        List<Map<String, Object>> rankings = repository.getDocumentRankingBySize();

        assertFalse(rankings.isEmpty());

        // Verify ranking is correct (larger documents should have lower rank numbers)
        for (int i = 1; i < rankings.size(); i++) {
            Object prevSizeObj = rankings.get(i - 1).get("size_bytes");
            Object currSizeObj = rankings.get(i).get("size_bytes");

            // Handle column names that might be lowercase
            if (prevSizeObj == null) prevSizeObj = rankings.get(i - 1).get("SIZE_BYTES");
            if (currSizeObj == null) currSizeObj = rankings.get(i).get("SIZE_BYTES");

            Long prevSize = ((Number) prevSizeObj).longValue();
            Long currSize = ((Number) currSizeObj).longValue();
            assertTrue(prevSize >= currSize, "Documents should be ordered by size descending");
        }
    }

    @Test
    @Order(7)
    @DisplayName("Test batch operations with jOOQ")
    public void testBatchOperations() {
        // Create documents for batch update
        Map<Long, String> documentStatuses = new java.util.HashMap<>();

        for (int i = 0; i < 3; i++) {
            String workflowId = "jooq-batch-" + i + "-" + UUID.randomUUID();
            Long docId = repository.saveDocument(workflowId, "batch-" + i + ".pdf", "application/pdf", "batch content".getBytes());
            documentStatuses.put(docId, "BATCH_UPDATED_" + i);
        }

        // Perform batch update
        repository.batchUpdateStatuses(documentStatuses);

        // Verify all updates
        for (Map.Entry<Long, String> entry : documentStatuses.entrySet()) {
            String status = dsl.select(DOCUMENT_METADATA.STATUS)
                .from(DOCUMENT_METADATA)
                .where(DOCUMENT_METADATA.DOCUMENT_ID.eq(entry.getKey()))
                .fetchOne(DOCUMENT_METADATA.STATUS);

            assertEquals(entry.getValue(), status);
        }
    }

    @Test
    @Order(8)
    @DisplayName("Test jOOQ transactions")
    public void testJooqTransactions() {
        String workflowId = "jooq-tx-" + UUID.randomUUID();

        // Test successful transaction
        Long docId = dsl.transactionResult(config -> {
            DSLContext ctx = using(config);

            // Insert document
            Long id = ctx.insertInto(DOCUMENTS)
                .columns(DOCUMENTS.WORKFLOW_ID, DOCUMENTS.NAME, DOCUMENTS.MIME_TYPE, DOCUMENTS.SIZE_BYTES, DOCUMENTS.CONTENT)
                .values(workflowId, "transaction.pdf", "application/pdf", 100L, "tx content".getBytes())
                .returningResult(DOCUMENTS.ID)
                .fetchOne()
                .getValue(DOCUMENTS.ID);

            // Insert metadata
            ctx.insertInto(DOCUMENT_METADATA)
                .columns(DOCUMENT_METADATA.DOCUMENT_ID, DOCUMENT_METADATA.STATUS)
                .values(id, "TX_SUCCESS")
                .execute();

            return id;
        });

        assertNotNull(docId);

        // Verify both records exist
        assertTrue(dsl.fetchExists(DOCUMENTS, DOCUMENTS.ID.eq(docId)));
        assertTrue(dsl.fetchExists(DOCUMENT_METADATA, DOCUMENT_METADATA.DOCUMENT_ID.eq(docId)));

        // Test transaction rollback
        assertThrows(RuntimeException.class, () -> {
            dsl.transaction(config -> {
                DSLContext ctx = using(config);

                // This should succeed
                ctx.insertInto(DOCUMENTS)
                    .columns(DOCUMENTS.WORKFLOW_ID, DOCUMENTS.NAME, DOCUMENTS.MIME_TYPE, DOCUMENTS.SIZE_BYTES, DOCUMENTS.CONTENT)
                    .values("rollback-test", "rollback.pdf", "application/pdf", 50L, "rollback".getBytes())
                    .execute();

                // Force rollback
                throw new RuntimeException("Forced rollback");
            });
        });

        // Verify rollback worked - document should not exist
        assertFalse(dsl.fetchExists(DOCUMENTS, DOCUMENTS.WORKFLOW_ID.eq("rollback-test")));
    }

    @Test
    @Order(9)
    @DisplayName("Test jOOQ with subqueries")
    public void testSubqueries() {
        // Query documents with latest status updates
        Result<Record> recentDocuments = dsl.select()
            .from(DOCUMENTS)
            .where(DOCUMENTS.ID.in(
                select(DOCUMENT_METADATA.DOCUMENT_ID)
                    .from(DOCUMENT_METADATA)
                    .where(DOCUMENT_METADATA.STATUS.in("COMPLETED", "PROCESSING"))
            ))
            .fetch();

        assertNotNull(recentDocuments);

        // Query with correlated subquery
        var documentsWithReviews = dsl.select(
                DOCUMENTS.ID,
                DOCUMENTS.NAME,
                field(
                    select(count())
                        .from(DOCUMENT_REVIEWS)
                        .where(DOCUMENT_REVIEWS.DOCUMENT_ID.eq(DOCUMENTS.ID))
                ).as("review_count")
            )
            .from(DOCUMENTS)
            .fetch();

        assertNotNull(documentsWithReviews);
    }

    @Test
    @Order(10)
    @DisplayName("Test jOOQ optimistic locking")
    public void testOptimisticLocking() {
        // Create a document
        String workflowId = "jooq-lock-" + UUID.randomUUID();
        Long docId = repository.saveDocument(workflowId, "lock-test.pdf", "application/pdf", "lock content".getBytes());

        // Simulate concurrent updates using separate queries
        String status1 = "LOCKED_1";
        String status2 = "LOCKED_2";

        // Both updates should succeed (no optimistic locking in current schema)
        // But we can demonstrate how to add version checking
        int updated1 = dsl.update(DOCUMENT_METADATA)
            .set(DOCUMENT_METADATA.STATUS, status1)
            .where(DOCUMENT_METADATA.DOCUMENT_ID.eq(docId))
            .execute();

        int updated2 = dsl.update(DOCUMENT_METADATA)
            .set(DOCUMENT_METADATA.STATUS, status2)
            .where(DOCUMENT_METADATA.DOCUMENT_ID.eq(docId))
            .execute();

        assertEquals(1, updated1);
        assertEquals(1, updated2);

        // Final status should be from the second update
        String finalStatus = dsl.select(DOCUMENT_METADATA.STATUS)
            .from(DOCUMENT_METADATA)
            .where(DOCUMENT_METADATA.DOCUMENT_ID.eq(docId))
            .fetchOne(DOCUMENT_METADATA.STATUS);

        assertEquals(status2, finalStatus);
    }

    @AfterEach
    public void verifyDatabaseIntegrity() {
        // Verify foreign key constraints
        Result<Record> orphanedMetadata = dsl.select()
            .from(DOCUMENT_METADATA)
            .leftJoin(DOCUMENTS).on(DOCUMENT_METADATA.DOCUMENT_ID.eq(DOCUMENTS.ID))
            .where(DOCUMENTS.ID.isNull())
            .fetch();

        assertTrue(orphanedMetadata.isEmpty(), "No orphaned metadata records should exist");
    }
}