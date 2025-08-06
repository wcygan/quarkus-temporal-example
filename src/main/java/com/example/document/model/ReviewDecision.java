package com.example.document.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public class ReviewDecision implements Serializable {
    private boolean approved;
    private String comments;
    private List<String> tags;
    private List<String> requiredActions;
    private String reviewerId;
    private String reviewerName;
    private String reviewedBy;
    private Instant reviewedAt;

    public ReviewDecision() {
    }

    public ReviewDecision(boolean approved, String comments) {
        this.approved = approved;
        this.comments = comments;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getRequiredActions() {
        return requiredActions;
    }

    public void setRequiredActions(List<String> requiredActions) {
        this.requiredActions = requiredActions;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(String reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public void setReviewedAt(long epochMilli) {
        this.reviewedAt = Instant.ofEpochMilli(epochMilli);
    }
}