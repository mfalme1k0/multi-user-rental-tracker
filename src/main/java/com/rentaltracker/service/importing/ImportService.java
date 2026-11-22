
package com.rentaltracker.service.importing;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.RentalStatus;
import com.rentaltracker.domain.User;
import com.rentaltracker.exception.RecordLevelException;
import com.rentaltracker.repository.ItemRepository;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.repository.Transactor;
import com.rentaltracker.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ImportService {

    static final String USERS_FILE = "users.csv";
    static final String ITEMS_FILE = "listed_items.csv";
    static final String RENTALS_FILE = "rentals.csv";

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final RentalRepository rentalRepository;
    private final Transactor transactor;

    public ImportService(
            UserRepository userRepository,
            ItemRepository itemRepository,
            RentalRepository rentalRepository,
            Transactor transactor) {

        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.rentalRepository = rentalRepository;
        this.transactor = transactor;
    }

    public ImportSummary importData(ImportBatch batch) {
        return transactor.inTransaction(() -> {
            ImportSummary summary = new ImportSummary();

            // External ID -> local ID
            Map<Long, Long> userIdMap = new HashMap<>();
            Map<Long, Long> itemIdMap = new HashMap<>();

            for (CsvUserRow row : batch.users()) {
                processUser(row, userIdMap, summary);
            }

            for (CsvItemRow row : batch.items()) {
                processItem(row, userIdMap, itemIdMap, summary);
            }

            for (CsvRentalRow row : batch.rentals()) {
                processRental(row, userIdMap, itemIdMap, summary);
            }

            return summary;
        });
    }

    // ------------------------------------------------------------------
    // Users
    // ------------------------------------------------------------------

    private void processUser(
            CsvUserRow row,
            Map<Long, Long> userIdMap,
            ImportSummary summary) {

        ImportState state = ImportState.READ;

        try {
            long externalId = parseLong(row.id(), "id");
            state = state.transitionTo(ImportState.RESOLVED);

            String username = requireNonBlank(row.username(), "username");
            String password = requireNonBlank(row.password(), "password");

            // Imported password hashes are carried across verbatim.
            state = state.transitionTo(ImportState.VALIDATED);

            Optional<User> existing =
                    userRepository.findByUsername(username);

            if (existing.isPresent()) {
                Long localId = requirePersistedId(
                        existing.get().id(), "existing user");

                userIdMap.put(externalId, localId);

                state = state.transitionTo(ImportState.MATCHED);
                summary.recordMatched(USERS_FILE, row.lineNumber());

            } else {
                User inserted = userRepository.insert(
                        User.newUser(username, password));

                Long localId = requirePersistedId(
                        inserted.id(), "inserted user");

                userIdMap.put(externalId, localId);

                state = state.transitionTo(ImportState.INSERTED);
                summary.recordInserted(USERS_FILE, row.lineNumber());
            }

        } catch (RuntimeException e) {
            handleFailure(e, state, USERS_FILE,
                    row.lineNumber(), summary);
        }
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    private void processItem(
            CsvItemRow row,
            Map<Long, Long> userIdMap,
            Map<Long, Long> itemIdMap,
            ImportSummary summary) {

        ImportState state = ImportState.READ;

        try {
            long externalId = parseLong(row.id(), "id");

            long externalOwnerId =
                    parseLong(row.ownerId(), "owner_id");

            Long localOwnerId = userIdMap.get(externalOwnerId);

            if (localOwnerId == null) {
                throw new RecordLevelException(
                        "owner external id " + externalOwnerId
                                + " could not be resolved");
            }

            state = state.transitionTo(ImportState.RESOLVED);

            String name = requireNonBlank(row.name(), "name");
            BigDecimal cost = parseDecimal(
                    row.costPerDay(), "cost_per_day");

            if (cost.signum() <= 0) {
                throw new RecordLevelException(
                        "cost_per_day must be positive");
            }

            ItemStatus status = parseItemStatus(row.status());

            String description =
                    normaliseNullableText(row.description());

            state = state.transitionTo(ImportState.VALIDATED);

            List<Item> matches = itemRepository.findMatches(
                    localOwnerId, name, cost, description);

            if (matches.size() > 1) {
                throw new RecordLevelException(
                        "ambiguous item match (multiple rows)");
            }

            if (matches.size() == 1) {
                Long localId = requirePersistedId(
                        matches.get(0).id(), "matched item");

                itemIdMap.put(externalId, localId);

                state = state.transitionTo(ImportState.MATCHED);
                summary.recordMatched(ITEMS_FILE, row.lineNumber());

            } else {
                Item inserted = itemRepository.insert(
                        new Item(
                                null,
                                localOwnerId,
                                name,
                                description,
                                cost,
                                status,
                                null
                        ));

                Long localId = requirePersistedId(
                        inserted.id(), "inserted item");

                itemIdMap.put(externalId, localId);

                state = state.transitionTo(ImportState.INSERTED);
                summary.recordInserted(ITEMS_FILE, row.lineNumber());
            }

        } catch (RuntimeException e) {
            handleFailure(e, state, ITEMS_FILE,
                    row.lineNumber(), summary);
        }
    }

    // ------------------------------------------------------------------
    // Rentals
    // ------------------------------------------------------------------

    private void processRental(
            CsvRentalRow row,
            Map<Long, Long> userIdMap,
            Map<Long, Long> itemIdMap,
            ImportSummary summary) {

        ImportState state = ImportState.READ;

        try {
            long externalItemId =
                    parseLong(row.itemId(), "item_id");

            long externalRenterId =
                    parseLong(row.renterId(), "renter_id");

            Long localItemId = itemIdMap.get(externalItemId);
            Long localRenterId = userIdMap.get(externalRenterId);

            if (localItemId == null) {
                throw new RecordLevelException(
                        "item external id " + externalItemId
                                + " could not be resolved");
            }

            if (localRenterId == null) {
                throw new RecordLevelException(
                        "renter external id " + externalRenterId
                                + " could not be resolved");
            }

            state = state.transitionTo(ImportState.RESOLVED);

            LocalDateTime startTime =
                    parseDateTime(row.startTime(), "start_time");

            LocalDateTime endTime =
                    parseDateTime(row.endTime(), "end_time");

            if (!endTime.isAfter(startTime)) {
                throw new RecordLevelException(
                        "end_time must be after start_time");
            }

            RentalStatus status = parseRentalStatus(row.status());

            LocalDateTime returnedAt =
                    parseNullableDateTime(
                            row.returnedAt(), "returned_at");

            if (status == RentalStatus.ACTIVE && returnedAt != null) {
                throw new RecordLevelException(
                        "active rental must not have returned_at");
            }

            if (status == RentalStatus.CLOSED && returnedAt == null) {
                throw new RecordLevelException(
                        "closed rental must have returned_at");
            }

            state = state.transitionTo(ImportState.VALIDATED);

            List<Rental> matches = rentalRepository.findMatches(
                    localItemId, localRenterId, startTime);

            if (matches.size() > 1) {
                throw new RecordLevelException(
                        "ambiguous rental match (multiple rows)");
            }

            if (matches.size() == 1) {
                state = state.transitionTo(ImportState.MATCHED);
                summary.recordMatched(RENTALS_FILE, row.lineNumber());

            } else {
                Rental inserted = rentalRepository.insert(
                        new Rental(
                                null,
                                localItemId,
                                localRenterId,
                                startTime,
                                endTime,
                                returnedAt,
                                status
                        ));

                requirePersistedId(inserted.id(), "inserted rental");

                state = state.transitionTo(ImportState.INSERTED);
                summary.recordInserted(RENTALS_FILE, row.lineNumber());
            }


        } catch (RuntimeException e) {
            handleFailure(e, state, RENTALS_FILE,
                    row.lineNumber(), summary);
        }
    }

    // ------------------------------------------------------------------
    // Persisted ID validation
    // ------------------------------------------------------------------

    private static Long requirePersistedId(Long id, String entity) {
        if (id == null) {
            throw new RecordLevelException(
                    entity + " was not assigned a database ID");
        }

        return id;
    }

    // ------------------------------------------------------------------
    // Failure classification
    // ------------------------------------------------------------------

    private void handleFailure(
            RuntimeException e,
            ImportState state,
            String fileName,
            int lineNumber,
            ImportSummary summary) {

        if (!isRecordLevel(e)) {
            // Infrastructure failure: allow transaction rollback.
            throw e;
        }

        state.transitionTo(ImportState.REJECTED);

        summary.reject(
                new RejectedRecord(
                        fileName,
                        lineNumber,
                        e.getMessage()
                ));
    }

    private static boolean isRecordLevel(Throwable e) {
        if (e instanceof RecordLevelException) {
            return true;
        }

        String name = e.getClass().getSimpleName();

        return name.contains("Constraint")
                || name.contains("Validation")
                || name.contains("Business")
                || name.contains("Mapping");
    }

    // ------------------------------------------------------------------
    // Parsing and validation helpers
    // ------------------------------------------------------------------

    private static long parseLong(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new RecordLevelException(
                    field + " must not be blank");
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new RecordLevelException(
                    field + " is not a valid integer: " + value);
        }
    }

    private static BigDecimal parseDecimal(
            String value, String field) {

        if (value == null || value.isBlank()) {
            throw new RecordLevelException(
                    field + " must not be blank");
        }

        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new RecordLevelException(
                    field + " is not a valid number: " + value);
        }
    }

    private static String requireNonBlank(
            String value, String field) {

        if (value == null || value.isBlank()) {
            throw new RecordLevelException(
                    field + " must not be blank");
        }

        return value.trim();
    }

    private static String normaliseNullableText(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static LocalDateTime parseDateTime(
            String value, String field) {

        if (value == null || value.isBlank()) {
            throw new RecordLevelException(
                    field + " must not be blank");
        }

        LocalDateTime parsed = tryParseDateTime(value.trim());

        if (parsed == null) {
            throw new RecordLevelException(
                    field + " is not a valid timestamp: " + value);
        }

        return parsed;
    }

    private static LocalDateTime parseNullableDateTime(
            String value, String field) {

        if (value == null || value.isBlank()) {
            return null;
        }

        LocalDateTime parsed = tryParseDateTime(value.trim());

        if (parsed == null) {
            throw new RecordLevelException(
                    field + " is not a valid timestamp: " + value);
        }

        return parsed;
    }

    private static LocalDateTime tryParseDateTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // Try offset timestamp.
        }

        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static ItemStatus parseItemStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new RecordLevelException(
                    "status must not be blank");
        }

        try {
            return ItemStatus.fromDbValue(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new RecordLevelException(
                    "unknown item status: " + value);
        }
    }

    private static RentalStatus parseRentalStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new RecordLevelException(
                    "status must not be blank");
        }

        try {
            return RentalStatus.fromDbValue(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new RecordLevelException(
                    "unknown rental status: " + value);
        }
    }
}
