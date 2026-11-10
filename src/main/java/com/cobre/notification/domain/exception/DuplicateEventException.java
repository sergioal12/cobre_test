package com.cobre.notification.domain.exception;

/**
 * Raised when a client tries to create a notification event whose identifier already exists.
 * Event identifiers are globally unique, so re-using one must never overwrite an existing event.
 */
public class DuplicateEventException extends RuntimeException {

    public DuplicateEventException(String eventId) {
        super("Notification event '" + eventId + "' already exists");
    }
}

