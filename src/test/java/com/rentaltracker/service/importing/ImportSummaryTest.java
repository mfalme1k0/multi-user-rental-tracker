package com.rentaltracker.service.importing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportSummaryTest {

    @Test
    void newSummaryStartsWithZeroCounts() {
        ImportSummary summary = new ImportSummary();

        assertAll(
                () -> assertEquals(0, summary.inserted()),
                () -> assertEquals(0, summary.matched()),
                () -> assertEquals(0, summary.rejectedCount()),
                () -> assertTrue(summary.rejected().isEmpty())
        );
    }

    @Test
    void incrementInsertedIncreasesInsertedCount() {
        ImportSummary summary = new ImportSummary();

        summary.incrementInserted();
        summary.incrementInserted();

        assertEquals(2, summary.inserted());
        assertEquals(0, summary.matched());
    }

    @Test
    void incrementMatchedIncreasesMatchedCount() {
        ImportSummary summary = new ImportSummary();

        summary.incrementMatched();
        summary.incrementMatched();
        summary.incrementMatched();

        assertEquals(3, summary.matched());
        assertEquals(0, summary.inserted());
    }

    @Test
    void rejectAddsRecordAndIncreasesRejectedCount() {
        ImportSummary summary = new ImportSummary();
        RejectedRecord rejected = new RejectedRecord(
                "users.csv", 2, "username must not be blank"
        );

        summary.reject(rejected);

        assertEquals(1, summary.rejectedCount());
        assertEquals(List.of(rejected), summary.rejected());
    }

    @Test
    void rejectedReturnsImmutableCopy() {
        ImportSummary summary = new ImportSummary();
        RejectedRecord rejected = new RejectedRecord(
                "users.csv", 2, "invalid username"
        );

        summary.reject(rejected);

        List<RejectedRecord> records = summary.rejected();

        assertThrows(
                UnsupportedOperationException.class,
                () -> records.add(new RejectedRecord(
                        "items.csv", 3, "invalid item"
                ))
        );

        assertEquals(1, summary.rejectedCount());
    }

    @Test
    void toStringIncludesAllCounts() {
        ImportSummary summary = new ImportSummary();

        summary.incrementInserted();
        summary.incrementInserted();
        summary.incrementMatched();
        summary.reject(new RejectedRecord(
                "users.csv", 4, "invalid username"
        ));

        assertEquals(
                "inserted=2 matched=1 rejected=1",
                summary.toString()
        );
    }

    @Test
    void recordsInsertedAndMatchedResults() {
        ImportSummary summary = new ImportSummary();

        summary.recordInserted("users.csv", 3);
        summary.recordMatched("listed_items.csv", 2);

        assertAll(
                () -> assertEquals(1, summary.inserted()),
                () -> assertEquals(1, summary.matched()),
                () -> assertEquals(2, summary.results().size()),
                () -> assertEquals(
                        ImportRecordResult.Outcome.INSERTED,
                        summary.results().get(0).outcome()
                ),
                () -> assertEquals("users.csv",
                        summary.results().get(0).fileName()),
                () -> assertEquals(3,
                        summary.results().get(0).lineNumber()),
                () -> assertEquals(
                        ImportRecordResult.Outcome.MATCHED,
                        summary.results().get(1).outcome()
                )
        );
    }

    @Test
    void rejectionAlsoCreatesDetailedResult() {
        ImportSummary summary = new ImportSummary();

        summary.reject(new RejectedRecord(
                "users.csv", 4, "username must not be blank"
        ));

        ImportRecordResult result = summary.results().getFirst();

        assertAll(
                () -> assertEquals(1, summary.rejectedCount()),
                () -> assertEquals(
                        ImportRecordResult.Outcome.REJECTED,
                        result.outcome()
                ),
                () -> assertEquals("users.csv", result.fileName()),
                () -> assertEquals(4, result.lineNumber()),
                () -> assertEquals(
                        "username must not be blank",
                        result.reason()
                )
        );
    }

    @Test
    void resultsReturnsImmutableCopy() {
        ImportSummary summary = new ImportSummary();
        summary.recordInserted("users.csv", 1);

        List<ImportRecordResult> results = summary.results();

        assertThrows(
                UnsupportedOperationException.class,
                () -> results.add(new ImportRecordResult(
                        "users.csv",
                        2,
                        ImportRecordResult.Outcome.MATCHED,
                        null
                ))
        );

        assertEquals(1, summary.results().size());
    }
}
