package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.User;
import com.rentaltracker.exception.MappingException;
import com.rentaltracker.exception.NotFoundException;
import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.repository.UserRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

public class SQLiteUserRepository implements UserRepository {

    private final DatabaseManager databaseManager;

    public SQLiteUserRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public User insert(User user) {
        String insertSql = """
                INSERT INTO users (username)
                VALUES (?)
                """;

        String selectSql = """
                SELECT id, username, created_at
                FROM users
                WHERE id = ?
                """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement insertStatement = connection.prepareStatement(
                        insertSql,
                        Statement.RETURN_GENERATED_KEYS)) {

            insertStatement.setString(1, user.username());
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
                                "Inserted user could not be found: " + id
                        );
                    }

                    return mapUser(resultSet);
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
    public Optional<User> findById(long id) {
        String sql = """
            SELECT id, username, created_at
            FROM users
            WHERE id = ?
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapUser(resultSet))
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
    public Optional<User> findByUsername(String username) {
        String sql = """
            SELECT id, username, created_at
            FROM users
            WHERE username = ?
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(mapUser(resultSet))
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
    public Optional<User> findFirst() {
        String sql = """
            SELECT id, username, created_at
            FROM users
            ORDER BY id ASC
            LIMIT 1
            """;
        Connection connection = databaseManager.getConnection();
        try (
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {

            return resultSet.next()
                    ? Optional.of(mapUser(resultSet))
                    : Optional.empty();

        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        }
        finally {
            databaseManager.releaseConnection(connection);
        }
    }

    @Override
    public User getById(long id) {
        return findById(id)
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + id)
                );
    }

    private User mapUser(ResultSet resultSet) {
        try {
            long id = resultSet.getLong("id");
            String username = resultSet.getString("username");
            String createdAtValue = resultSet.getString("created_at");

            LocalDateTime createdAt =
                    OffsetDateTime.parse(createdAtValue).toLocalDateTime();

            return new User(
                    id,
                    username,
                    null,
                    createdAt
            );

        } catch (SQLException | RuntimeException e) {
            throw new MappingException(
                    "Could not map database row to User",
                    e
            );
        }
    }
}

