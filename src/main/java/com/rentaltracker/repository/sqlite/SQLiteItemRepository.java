package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.Item;
import com.rentaltracker.domain.ItemStatus;
import com.rentaltracker.exception.NotFoundException;
import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.repository.ItemRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteItemRepository implements ItemRepository {

    private final DatabaseManager databaseManager;

    public SQLiteItemRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Item insert(Item item) {
        String insertSql = """
                INSERT INTO listed_items (
                    owner_id,
                    name,
                    description,
                    cost_per_day,
                    status
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        String selectSql = """
                SELECT
                    id,
                    owner_id,
                    name,
                    description,
                    cost_per_day,
                    status,
                    created_at
                FROM listed_items
                WHERE id = ?
                """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement insertStatement = connection.prepareStatement(
                        insertSql,
                        Statement.RETURN_GENERATED_KEYS)) {

            insertStatement.setLong(1, item.ownerId());
            insertStatement.setString(2, item.name());
            insertStatement.setString(3, item.description());
            insertStatement.setString(4, item.costPerDay().toPlainString());
            insertStatement.setString(5, item.status().dbValue());

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
                                "Inserted item could not be found: " + id
                        );
                    }

                    return mapItem(resultSet);
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
    public Optional<Item> findById(long id) {
        String sql = """
            SELECT
                id,
                owner_id,
                name,
                description,
                cost_per_day,
                status,
                created_at
            FROM listed_items
            WHERE id = ?
            """;

        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapItem(resultSet))
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
    public Item getById(long id) {
        return findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Item not found: " + id)
                );
    }

    @Override
    public List<Item> findByOwnerId(long ownerId) {
        String sql = """
            SELECT
                id,
                owner_id,
                name,
                description,
                cost_per_day,
                status,
                created_at
            FROM listed_items
            WHERE owner_id = ?
            ORDER BY id ASC
            """;

        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, ownerId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Item> items = new java.util.ArrayList<>();

                while (resultSet.next()) {
                    items.add(mapItem(resultSet));
                }

                return items;
            }

        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        }
        finally {
            databaseManager.releaseConnection(connection);
        }
    }

    @Override
    public List<Item> findByOwnerIdAndStatus(long ownerId, ItemStatus status) {
        String sql = """
            SELECT
                id,
                owner_id,
                name,
                description,
                cost_per_day,
                status,
                created_at
            FROM listed_items
            WHERE owner_id = ?
              AND status = ?
            ORDER BY id ASC
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, ownerId);
            statement.setString(2, status.dbValue());

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Item> items = new ArrayList<>();

                while (resultSet.next()) {
                    items.add(mapItem(resultSet));
                }

                return items;
            }

        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        }
        finally {
            databaseManager.releaseConnection(connection);
        }
    }

    @Override
    public void updateStatus(long itemId, ItemStatus status) {
        String sql = """
            UPDATE listed_items
            SET status = ?
            WHERE id = ?
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, status.dbValue());
            statement.setLong(2, itemId);

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated == 0) {
                throw new NotFoundException(
                        "Item not found: " + itemId
                );
            }

        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        }
        finally {
            databaseManager.releaseConnection(connection);
        }
    }

    private Item mapItem(ResultSet resultSet) {
        try {
            long id = resultSet.getLong("id");
            long ownerId = resultSet.getLong("owner_id");
            String name = resultSet.getString("name");
            String description = resultSet.getString("description");
            String costPerDayValue = resultSet.getString("cost_per_day");
            BigDecimal costPerDay = new BigDecimal(costPerDayValue);

            String statusValue = resultSet.getString("status");
            ItemStatus status = ItemStatus.fromDbValue(statusValue);

            String createdAtValue = resultSet.getString("created_at");
            LocalDateTime createdAt =
                    OffsetDateTime.parse(createdAtValue).toLocalDateTime();

            return new Item(
                    id,
                    ownerId,
                    name,
                    description,
                    costPerDay,
                    status,
                    createdAt
            );

        } catch (SQLException | RuntimeException e) {
            throw new com.rentaltracker.exception.MappingException(
                    "Could not map database row to Item",
                    e
            );
        }
    }
}

