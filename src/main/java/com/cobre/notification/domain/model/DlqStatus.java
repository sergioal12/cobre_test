package com.cobre.notification.domain.model;

/**
 * Status of an event stored in the Dead Letter Queue (DLQ) table for manual reprocessing.
 */
public enum DlqStatus {
    PENDING_MANUAL_REPROCESS("pending_manual_reprocess"),
    REPROCESSED("reprocessed"),
    DISCARDED("discarded");

    private final String value;

    DlqStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

