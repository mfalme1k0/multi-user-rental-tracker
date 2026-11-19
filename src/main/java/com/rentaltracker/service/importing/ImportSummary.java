package com.rentaltracker.service.importing;

import java.util.ArrayList;
import java.util.List;
//tally of imports for the transport layer
public final class ImportSummary {

    private int inserted;
    private int matched;
    private final List<RejectedRecord> rejected = new ArrayList<>();

    public void incrementInserted() { inserted++; }
    public void incrementMatched()  { matched++; }
    public void reject(RejectedRecord record) { rejected.add(record); }

    public int inserted() { return inserted; }
    public int matched()  { return matched;  }
    public int rejectedCount() { return rejected.size(); }
    public List<RejectedRecord> rejected() { return List.copyOf(rejected); }

    @Override
    public String toString() {
        return "inserted=" + inserted + " matched=" + matched + " rejected=" + rejected.size();
    }
}