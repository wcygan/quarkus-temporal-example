package com.example.document.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ProcessingMetrics implements Serializable {
    private String workflowId;
    private Long documentId;
    private DocumentStatus currentStatus;
    private Priority priority;
    private Instant startTime;
    private Instant lastUpdateTime;
    private Map<String, Long> stageDurations;
    private int totalStages;
    private int completedStages;
    private double progressPercentage;
    private long estimatedRemainingMs;
    private Map<String, Object> additionalMetrics;

    public ProcessingMetrics() {
        this.stageDurations = new HashMap<>();
        this.additionalMetrics = new HashMap<>();
    }

    public ProcessingMetrics(String workflowId, Long documentId) {
        this();
        this.workflowId = workflowId;
        this.documentId = documentId;
    }

    public void recordStageDuration(String stage, long durationMs) {
        stageDurations.put(stage, durationMs);
    }

    public void updateProgress() {
        if (totalStages > 0) {
            this.progressPercentage = (completedStages * 100.0) / totalStages;
        }
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public DocumentStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(DocumentStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Instant lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public Map<String, Long> getStageDurations() {
        return stageDurations;
    }

    public void setStageDurations(Map<String, Long> stageDurations) {
        this.stageDurations = stageDurations;
    }

    public int getTotalStages() {
        return totalStages;
    }

    public void setTotalStages(int totalStages) {
        this.totalStages = totalStages;
    }

    public int getCompletedStages() {
        return completedStages;
    }

    public void setCompletedStages(int completedStages) {
        this.completedStages = completedStages;
    }

    public double getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(double progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public long getEstimatedRemainingMs() {
        return estimatedRemainingMs;
    }

    public void setEstimatedRemainingMs(long estimatedRemainingMs) {
        this.estimatedRemainingMs = estimatedRemainingMs;
    }

    public Map<String, Object> getAdditionalMetrics() {
        return additionalMetrics;
    }

    public void setAdditionalMetrics(Map<String, Object> additionalMetrics) {
        this.additionalMetrics = additionalMetrics;
    }
}