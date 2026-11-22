package com.rentaltracker.service.importing;

public record ImportRecordResult(
        String fileName,
        int lineNumber,
        Outcome outcome,
        String reason
) {

    public enum Outcome {
        INSERTED,
        MATCHED,
        REJECTED
    }

    public ImportRecordResult {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName must not be blank");
        }

        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be >= 1");
        }

        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }

        if (outcome == Outcome.REJECTED
                && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(
                    "rejected record reason must not be blank");
        }

        if (outcome != Outcome.REJECTED && reason != null) {
            throw new IllegalArgumentException(
                    "successful record must not have a rejection reason");
        }
    }
}