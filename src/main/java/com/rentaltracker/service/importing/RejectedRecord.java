package com.rentaltracker.service.importing;

//rejected import  with the why
public record RejectedRecord(String fileName, int lineNumber, String reason) {

    public RejectedRecord {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be >= 1");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    @Override
    public String toString() {
        return fileName + " line " + lineNumber + ": " + reason;
    }
}