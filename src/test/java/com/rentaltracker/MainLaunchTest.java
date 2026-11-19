package com.rentaltracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The exit decision and the demo-seed switch, which MainTest cannot reach through the environment.
class MainLaunchTest {

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @AfterEach
    void restoreStreams() {
        System.setIn(originalIn);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void successDoesNotRequestExit() {
        List<Integer> exits = new ArrayList<>();

        Main.exitOnFailure(0, exits::add);

        assertTrue(exits.isEmpty());
    }

    @Test
    void failureRequestsExitWithTheSameCode() {
        List<Integer> exits = new ArrayList<>();

        Main.exitOnFailure(1, exits::add);
        Main.exitOnFailure(2, exits::add);

        assertEquals(List.of(1, 2), exits);
    }

    @Test
    void runSkipsDemoDataWhenSeedingIsOff(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("no-seed.db");
        redirectIo("3\n");

        assertEquals(0, Main.run(db.toString(), false));

        assertEquals(0, countUsers(db));
    }

    @Test
    void runSeedsDemoDataWhenSeedingIsOn(@TempDir Path tempDir) throws Exception {
        Path db = tempDir.resolve("seed.db");
        redirectIo("3\n");

        assertEquals(0, Main.run(db.toString(), true));

        assertEquals(2, countUsers(db));
    }

    private void redirectIo(String stdin) {
        System.setIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private static int countUsers(Path db) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM users")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }
}
