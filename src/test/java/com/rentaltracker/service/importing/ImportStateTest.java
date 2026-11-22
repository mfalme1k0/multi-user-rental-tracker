package com.rentaltracker.service.importing;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ImportStateTest {

    @Test
    void readAllowsResolvedAndRejected() {
        assertEquals(
                Set.of(ImportState.RESOLVED, ImportState.REJECTED),
                ImportState.READ.allowedTransitions()
        );
    }

    @Test
    void resolvedAllowsValidatedAndRejected() {
        assertEquals(
                Set.of(ImportState.VALIDATED, ImportState.REJECTED),
                ImportState.RESOLVED.allowedTransitions()
        );
    }

    @Test
    void validatedAllowsInsertedMatchedAndRejected() {
        assertEquals(
                Set.of(
                        ImportState.INSERTED,
                        ImportState.MATCHED,
                        ImportState.REJECTED
                ),
                ImportState.VALIDATED.allowedTransitions()
        );
    }

    @Test
    void terminalStatesHaveNoAllowedTransitions() {
        assertAll(
                () -> assertTrue(ImportState.INSERTED.allowedTransitions().isEmpty()),
                () -> assertTrue(ImportState.MATCHED.allowedTransitions().isEmpty()),
                () -> assertTrue(ImportState.REJECTED.allowedTransitions().isEmpty())
        );
    }

    @Test
    void validTransitionsReturnTargetState() {
        assertAll(
                () -> assertEquals(
                        ImportState.RESOLVED,
                        ImportState.READ.transitionTo(ImportState.RESOLVED)
                ),
                () -> assertEquals(
                        ImportState.REJECTED,
                        ImportState.READ.transitionTo(ImportState.REJECTED)
                ),
                () -> assertEquals(
                        ImportState.VALIDATED,
                        ImportState.RESOLVED.transitionTo(ImportState.VALIDATED)
                ),
                () -> assertEquals(
                        ImportState.REJECTED,
                        ImportState.RESOLVED.transitionTo(ImportState.REJECTED)
                ),
                () -> assertEquals(
                        ImportState.INSERTED,
                        ImportState.VALIDATED.transitionTo(ImportState.INSERTED)
                ),
                () -> assertEquals(
                        ImportState.MATCHED,
                        ImportState.VALIDATED.transitionTo(ImportState.MATCHED)
                ),
                () -> assertEquals(
                        ImportState.REJECTED,
                        ImportState.VALIDATED.transitionTo(ImportState.REJECTED)
                )
        );
    }

    @Test
    void invalidTransitionThrowsIllegalStateException() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ImportState.READ.transitionTo(ImportState.INSERTED)
        );

        assertEquals(
                "import record cannot move from READ to INSERTED",
                exception.getMessage()
        );
    }

    @Test
    void terminalStatesCannotTransitionToAnotherState() {
        for (ImportState terminal : Set.of(
                ImportState.INSERTED,
                ImportState.MATCHED,
                ImportState.REJECTED
        )) {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> terminal.transitionTo(ImportState.READ)
            );

            assertEquals(
                    "import record cannot move from " + terminal + " to READ",
                    exception.getMessage()
            );
        }
    }

    @Test
    void nullTargetThrowsIllegalStateException() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> ImportState.READ.transitionTo(null)
        );

        assertEquals(
                "import record cannot move from READ to null",
                exception.getMessage()
        );
    }
}
