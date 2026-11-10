package com.cobre.notification.domain.exception;

public class InvalidReplayStateException extends RuntimeException {

    public InvalidReplayStateException(String eventId, String currentStatus) {
        super("Notification event '" + eventId + "' cannot be replayed because its status is '"
                + currentStatus + "' (must be 'failed')");
    }
}

