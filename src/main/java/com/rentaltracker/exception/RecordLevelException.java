package com.rentaltracker.exception;

public class RecordLevelException extends RuntimeException {
    public RecordLevelException(String message) {
        super(message);
    }
}