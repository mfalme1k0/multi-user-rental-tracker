package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.Rental;
import com.rentaltracker.domain.RentalDetails;
import com.rentaltracker.domain.RentalStatus;
import com.rentaltracker.exception.MappingException;
import com.rentaltracker.exception.NotFoundException;
import com.rentaltracker.repository.RentalRepository;
import com.rentaltracker.infrastructure.DatabaseManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteRentalRepository implements RentalRepository {

    private final DatabaseManager databaseManager;

    public SQLiteRentalRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }


    @Override
    public Rental insert(Rental rental) {
        String insertSql = """
            INSERT INTO rentals (
                item_id,
                renter_id,
                start_time,
                end_time,
                returned_at,
                status
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        String selectSql = """
            SELECT
                id,
                item_id,
                renter_id,
                start_time,
                end_time,
                returned_at,
                status
            FROM rentals
            WHERE id = ?
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement insertStatement = connection.prepareStatement(
                        insertSql,
                        Statement.RETURN_GENERATED_KEYS)) {

            insertStatement.setLong(1, rental.itemId());
            insertStatement.setLong(2, rental.renterId());

            insertStatement.setString(
                    3,
                    rental.startTime()
                            .atOffset(ZoneOffset.UTC)
                            .toString()
            );

            insertStatement.setString(
                    4,
                    rental.endTime()
                            .atOffset(ZoneOffset.UTC)
                            .toString()
            );

            if (rental.returnedAt() == null) {
                insertStatement.setNull(5, java.sql.Types.VARCHAR);
            } else {
                insertStatement.setString(
                        5,
                        rental.returnedAt()
                                .atOffset(ZoneOffset.UTC)
                                .toString()
                );
            }

            insertStatement.setString(
                    6,
                    rental.status().dbValue()
            );

            insertStatement.executeUpdate();

            long id;

            try (ResultSet generatedKeys = insertStatement.getGeneratedKeys()) {
                // sqlite-jdbc always returns one row here (id 0 when nothing was inserted);
                // the read-back below is what detects an insert that stored nothing.
                generatedKeys.next();
                id = generatedKeys.getLong(1);
            }

            try (PreparedStatement selectStatement =
                         connection.prepareStatement(selectSql)) {

                selectStatement.setLong(1, id);

                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException(
                                "Inserted rental could not be found: " + id
                        );
                    }

                    return mapRental(resultSet);
                }
            }

        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        }
        finally {
            databaseManager.releaseConnection(connection);
        }
    }


    @Override
    public Optional<Rental> findById(long id) {
        String sql = """
            SELECT
                id,
                item_id,
                renter_id,
                start_time,
                end_time,
                returned_at,
                status
            FROM rentals
            WHERE id = ?
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapRental(resultSet))
                        : Optional.empty();
            }

        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        }
        finally {
            databaseManager.releaseConnection(connection);
        }
    }
    @Override
    public Rental getById(long id) {
        return findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Rental not found: " + id)
                );
    }

    @Override
    public List<Rental> findByItemId(long itemId) {
        String sql = """
            SELECT
                id,
                item_id,
                renter_id,
                start_time,
                end_time,
                returned_at,
                status
            FROM rentals
            WHERE item_id = ?
            ORDER BY start_time ASC
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, itemId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Rental> rentals = new ArrayList<>();

                while (resultSet.next()) {
                    rentals.add(mapRental(resultSet));
                }

                return rentals;
            }

        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        }
        finally {
            databaseManager.releaseConnection(connection);
        }
    }

    @Override
    public Optional<Rental> findActiveByItemId(long itemId) {
        String sql = """
            SELECT
                id,
                item_id,
                renter_id,
                start_time,
                end_time,
                returned_at,
                status
            FROM rentals
            WHERE item_id = ?
              AND status = 'active'
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, itemId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapRental(resultSet))
                        : Optional.empty();
            }

        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        }
        finally {
            databaseManager.releaseConnection(connection);
        }
    }
    @Override
    public List<RentalDetails> findActiveDetailsByOwnerId(long ownerId) {
        String sql = """
            SELECT
                r.id,
                r.item_id,
                r.renter_id,
                r.start_time,
                r.end_time,
                r.returned_at,
                r.status,
                i.name AS item_name,
                renter.username AS renter_username
            FROM rentals r
            JOIN listed_items i
                ON r.item_id = i.id
            JOIN users owner
                ON i.owner_id = owner.id
            JOIN users renter
                ON r.renter_id = renter.id
            WHERE owner.id = ?
              AND r.status = 'active'
            ORDER BY r.end_time ASC
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, ownerId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<RentalDetails> rentals = new ArrayList<>();

                while (resultSet.next()) {
                    Rental rental = mapRental(resultSet);

                    String itemName =
                            resultSet.getString("item_name");

                    String renterUsername =
                            resultSet.getString("renter_username");

                    rentals.add(
                            new RentalDetails(
                                    rental,
                                    itemName,
                                    renterUsername
                            )
                    );
                }

                return rentals;
            }

        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        }
        finally {
            databaseManager.releaseConnection(connection);
        }
    }

    @Override
    public void updateStatus(
            long rentalId,
            RentalStatus status,
            LocalDateTime returnedAt) {

        String sql = """
            UPDATE rentals
            SET status = ?,
                returned_at = ?
            WHERE id = ?
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement =
                        connection.prepareStatement(sql)) {

            statement.setString(1, status.dbValue());

            if (returnedAt == null) {
                statement.setNull(2, java.sql.Types.VARCHAR);
            } else {
                statement.setString(
                        2,
                        returnedAt
                                .atOffset(ZoneOffset.UTC)
                                .toString()
                );
            }

            statement.setLong(3, rentalId);

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated == 0) {
                throw new NotFoundException(
                        "Rental not found: " + rentalId
                );
            }

        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        }
        finally {
            databaseManager.releaseConnection(connection);
        }
    }
    private Rental mapRental(ResultSet resultSet) {
        try {
            long id = resultSet.getLong("id");
            long itemId = resultSet.getLong("item_id");
            long renterId = resultSet.getLong("renter_id");

            String startTimeValue = resultSet.getString("start_time");
            LocalDateTime startTime =
                    OffsetDateTime.parse(startTimeValue).toLocalDateTime();

            String endTimeValue = resultSet.getString("end_time");
            LocalDateTime endTime =
                    OffsetDateTime.parse(endTimeValue).toLocalDateTime();

            String returnedAtValue = resultSet.getString("returned_at");
            LocalDateTime returnedAt = returnedAtValue == null
                    ? null
                    : OffsetDateTime.parse(returnedAtValue).toLocalDateTime();

            String statusValue = resultSet.getString("status");
            RentalStatus status = RentalStatus.fromDbValue(statusValue);

            return new Rental(
                    id,
                    itemId,
                    renterId,
                    startTime,
                    endTime,
                    returnedAt,
                    status
            );

        } catch (SQLException | RuntimeException e) {
            throw new MappingException(
                    "Could not map database row to Rental",
                    e
            );
        }
    }
}
