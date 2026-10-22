package com.rentaltracker.repository.sqlite;

import com.rentaltracker.domain.LogEntry;
import com.rentaltracker.exception.MappingException;
import com.rentaltracker.infrastructure.DatabaseManager;
import com.rentaltracker.repository.LogRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class SQLiteLogRepository implements LogRepository {
    private static final DateTimeFormatter UTC_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final DatabaseManager databaseManager;

    public SQLiteLogRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public LogEntry append(LogEntry entry) {
        String insertSql = """
                INSERT INTO application_logs (occurred_at, level, event_name, context_json, user_id)
                VALUES (?, ?, ?, ?, ?)
                """;
        String selectSql = """
                SELECT id, occurred_at, level, event_name, context_json, user_id
                FROM application_logs
                WHERE id = ?
                """;
        Connection connection = databaseManager.getConnection();
        try (PreparedStatement insert = connection.prepareStatement(
                insertSql, Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, formatUtc(entry.occurredAt()));
            insert.setString(2, entry.level());
            insert.setString(3, entry.eventName());
            insert.setString(4, entry.contextJson());
            if (entry.userId() == null) {
                insert.setNull(5, java.sql.Types.INTEGER);
            } else {
                insert.setLong(5, entry.userId());
            }
            insert.executeUpdate();

            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Inserted log event did not return an ID");
                }
                long id = keys.getLong(1);
                try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                    select.setLong(1, id);
                    try (ResultSet rows = select.executeQuery()) {
                        if (!rows.next()) {
                            throw new SQLException("Inserted log event could not be found: " + id);
                        }
                        return mapLog(rows);
                    }
                }
            }
        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        } finally {
            databaseManager.releaseConnection(connection);
        }
    }

    @Override
    public List<LogEntry> findRecent(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Log limit must be at least 1");
        }
        String sql = """
                SELECT id, occurred_at, level, event_name, context_json, user_id
                FROM application_logs
                ORDER BY occurred_at DESC, id DESC
                LIMIT ?
                """;
        Connection connection = databaseManager.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            List<LogEntry> entries = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(mapLog(rows));
                }
            }
            return entries;
        } catch (SQLException e) {
            throw SQLiteExceptionTranslator.translate(e);
        } finally {
            databaseManager.releaseConnection(connection);
        }
    }

    private LogEntry mapLog(ResultSet rows) {
        try {
            long userId = rows.getLong("user_id");
            Long nullableUserId = rows.wasNull() ? null : userId;
            return new LogEntry(
                    rows.getLong("id"),
                    OffsetDateTime.parse(rows.getString("occurred_at")).toLocalDateTime(),
                    rows.getString("level"),
                    rows.getString("event_name"),
                    rows.getString("context_json"),
                    nullableUserId
            );
        } catch (SQLException | RuntimeException e) {
            throw new MappingException("Could not map database row to LogEntry", e);
        }
    }

    private static String formatUtc(LocalDateTime timestamp) {
        return timestamp.atOffset(ZoneOffset.UTC).format(UTC_FORMAT);
    }
}
