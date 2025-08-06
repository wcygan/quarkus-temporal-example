package com.example.document.activity;

import com.example.document.model.ReviewDecision;
import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface ReviewActivities {
    
    void requestReview(Long documentId);
    
    void completeReview(String activityToken, ReviewDecision decision);
}