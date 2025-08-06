package com.example.document.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class Classification implements Serializable {
    private DocumentType documentType;
    private double confidence;
    private Map<DocumentType, Double> scores;
    private List<String> extractedKeywords;
    private List<String> tags;
    private List<String> entities;
    private String sentiment;
    private double riskScore;
    private Map<String, Object> additionalProperties;

    public Classification() {
    }

    public Classification(DocumentType documentType, double confidence) {
        this.documentType = documentType;
        this.confidence = confidence;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Map<DocumentType, Double> getScores() {
        return scores;
    }

    public void setScores(Map<DocumentType, Double> scores) {
        this.scores = scores;
    }

    public List<String> getExtractedKeywords() {
        return extractedKeywords;
    }

    public void setExtractedKeywords(List<String> extractedKeywords) {
        this.extractedKeywords = extractedKeywords;
    }

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String sentiment) {
        this.sentiment = sentiment;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities;
    }
}