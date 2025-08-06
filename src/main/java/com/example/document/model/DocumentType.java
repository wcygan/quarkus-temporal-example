package com.example.document.model;

public enum DocumentType {
    INVOICE("Invoice"),
    CONTRACT("Contract"),
    REPORT("Report"),
    LETTER("Letter"),
    FORM("Form"),
    RECEIPT("Receipt"),
    UNKNOWN("Unknown");

    private final String displayName;

    DocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}