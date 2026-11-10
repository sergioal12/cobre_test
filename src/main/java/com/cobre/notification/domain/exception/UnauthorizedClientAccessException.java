package com.cobre.notification.domain.exception;

public class UnauthorizedClientAccessException extends RuntimeException {

    public UnauthorizedClientAccessException(String message) {
        super(message);
    }
}

