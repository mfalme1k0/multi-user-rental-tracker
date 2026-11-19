package com.rentaltracker.service.importing;

import java.util.EnumSet;
import java.util.Set;

public enum ImportState {
    READ,
    RESOLVED,
    VALIDATED,
    INSERTED,
    MATCHED,
    REJECTED;

    public Set<ImportState> allowedTransitions() {
        return switch (this) {
            case READ      -> EnumSet.of(RESOLVED, REJECTED);
            case RESOLVED  -> EnumSet.of(VALIDATED, REJECTED);
            case VALIDATED -> EnumSet.of(INSERTED, MATCHED, REJECTED);
            case INSERTED, MATCHED, REJECTED -> EnumSet.noneOf(ImportState.class);
        };
    }

    public ImportState transitionTo(ImportState target) {
        if (!allowedTransitions().contains(target)) {
            throw new IllegalStateException(
                "import record cannot move from " + this + " to " + target);
        }
        return target;
    }
}