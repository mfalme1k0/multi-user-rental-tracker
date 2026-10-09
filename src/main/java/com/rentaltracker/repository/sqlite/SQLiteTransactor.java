package com.rentaltracker.repository.sqlite;

import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.repository.Transactor;

import java.util.Objects;
import java.util.function.Supplier;

public final class SQLiteTransactor implements Transactor {

    private final DatabaseManager databaseManager;

    public SQLiteTransactor(DatabaseManager databaseManager) {
        this.databaseManager =
                Objects.requireNonNull(databaseManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        Objects.requireNonNull(work);

        if (databaseManager.isTransactionActive()) {
            return work.get();
        }

        databaseManager.beginTransaction();

        T result;

        try {
            result = work.get();
        } catch (RuntimeException | Error failure) {
            rollbackAfter(failure);
            throw failure;
        }

        databaseManager.commitTransaction();

        return result;
    }

    private void rollbackAfter(Throwable failure) {
        try {
            databaseManager.rollbackTransaction();
        } catch (RuntimeException rollbackFailure) {
            // Report the original failure; keep the rollback problem attached to it.
            failure.addSuppressed(rollbackFailure);
        }
    }
}