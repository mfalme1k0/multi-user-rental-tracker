
package com.rentaltracker.service.importing;

import java.util.ArrayList;
import java.util.List;

public final class ImportSummary {

    private int inserted;
    private int matched;

    private final List<RejectedRecord> rejected = new ArrayList<>();
    private final List<ImportRecordResult> results = new ArrayList<>();

    public void incrementInserted() {
        inserted++;
    }

    public void incrementMatched() {
        matched++;
    }

    public void recordInserted(String fileName, int lineNumber) {
        incrementInserted();
        results.add(new ImportRecordResult(
                fileName,
                lineNumber,
                ImportRecordResult.Outcome.INSERTED,
                null
        ));
    }

    public void recordMatched(String fileName, int lineNumber) {
        incrementMatched();
        results.add(new ImportRecordResult(
                fileName,
                lineNumber,
                ImportRecordResult.Outcome.MATCHED,
                null
        ));
    }

    public void reject(RejectedRecord record) {
        rejected.add(record);
        results.add(new ImportRecordResult(
                record.fileName(),
                record.lineNumber(),
                ImportRecordResult.Outcome.REJECTED,
                record.reason()
        ));
    }

    public int inserted() {
        return inserted;
    }

    public int matched() {
        return matched;
    }

    public int rejectedCount() {
        return rejected.size();
    }

    public List<RejectedRecord> rejected() {
        return List.copyOf(rejected);
    }

    public List<ImportRecordResult> results() {
        return List.copyOf(results);
    }

    @Override
    public String toString() {
        return "inserted=" + inserted
                + " matched=" + matched
                + " rejected=" + rejected.size();
    }
}
