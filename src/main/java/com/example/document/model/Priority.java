package com.example.document.model;

public enum Priority {
    HIGH(1),
    MEDIUM(2),
    LOW(3);

    private final int level;

    Priority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}