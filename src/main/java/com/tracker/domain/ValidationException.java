package com.tracker.domain;

public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
