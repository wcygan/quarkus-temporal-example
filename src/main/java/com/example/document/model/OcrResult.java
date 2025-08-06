package com.example.document.model;

import java.io.Serializable;
import java.util.List;

public class OcrResult implements Serializable {
    private String text;
    private String extractedText;
    private double confidence;
    private int pageCount;
    private List<PageOcrResult> pages;
    private long processingTimeMs;

    public OcrResult() {
    }

    public OcrResult(String text, double confidence) {
        this.text = text;
        this.confidence = confidence;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public List<PageOcrResult> getPages() {
        return pages;
    }

    public void setPages(List<PageOcrResult> pages) {
        this.pages = pages;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }

    public static class PageOcrResult implements Serializable {
        private int pageNumber;
        private String text;
        private double confidence;

        public PageOcrResult() {
        }

        public PageOcrResult(int pageNumber, String text, double confidence) {
            this.pageNumber = pageNumber;
            this.text = text;
            this.confidence = confidence;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
    }
}