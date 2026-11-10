package com.cobre.notification.domain.exception;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String eventId, String clientId) {
        super("Notification event '" + eventId + "' not found for client '" + clientId + "'");
    }
}

