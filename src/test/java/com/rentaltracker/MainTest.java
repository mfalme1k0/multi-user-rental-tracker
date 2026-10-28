package com.rentaltracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class MainTest {

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @AfterEach
    void restoreStreamsAndProperty() {
        System.setIn(originalIn);
        System.setOut(originalOut);
        System.setErr(originalErr);
        System.clearProperty(Main.DATABASE_PROPERTY);
    }

    // -------------------------------------------------------------------------
    // Success path
    // -------------------------------------------------------------------------

    @Test
    void runInitializesDatabaseAndExitsFromAuthMenu(@TempDir Path tempDir) {
        Path db = tempDir.resolve("test.db");
        redirectIo("3\n");

        int code = Main.run(db.toString());

        assertEquals(0, code);
        assertTrue(Files.exists(db), "database file should be created");
        assertTrue(out().contains("Multi-User Rental Tracker"));
        assertTrue(out().contains("Goodbye."));
    }

    @Test
    void runUsesAuthMenuWhenNotLoggedIn(@TempDir Path tempDir) {
        Path db = tempDir.resolve("auth-menu.db");
        redirectIo("3\n");

        int code = Main.run(db.toString());

        assertEquals(0, code);
        assertTrue(out().contains("1. Log in") || out().contains("Log in"));
        assertTrue(out().contains("2. Register") || out().contains("Register"));
        assertTrue(out().contains("3. Exit") || out().contains("Exit"));
    }

    @Test
    void mainDelegatesToRunOnSuccess(@TempDir Path tempDir) {
        Path db = tempDir.resolve("main-success.db");
        System.setProperty(Main.DATABASE_PROPERTY, db.toString());
        redirectIo("3\n");

        // Success: run returns 0, so main must not call System.exit.
        Main.main(new String[]{});

        assertTrue(Files.exists(db));
        assertTrue(out().contains("Multi-User Rental Tracker"));
        assertTrue(out().contains("Goodbye."));
    }

    // -------------------------------------------------------------------------
    // Failure path (database init)
    // -------------------------------------------------------------------------

    @Test
    void runReturnsOneWhenDatabaseCannotBeOpened(@TempDir Path tempDir) throws Exception {
        // Directory path is not a valid SQLite database file → initialize fails.
        Path notADb = tempDir.resolve("not-a-db");
        Files.createDirectories(notADb);

        redirectIo("");

        int code = Main.run(notADb.toString());

        assertEquals(1, code);
        assertTrue(err().contains("Could not open the database"));
        assertFalse(out().contains("Goodbye."));
    }

    @Test
    void runDoesNotStartTransportWhenDatabaseInitFails(@TempDir Path tempDir) throws Exception {
        Path notADb = tempDir.resolve("not-a-db-2");
        Files.createDirectories(notADb);

        redirectIo("1\nalice\nsecret\n"); // would be login if Transport started

        int code = Main.run(notADb.toString());

        assertEquals(1, code);
        assertFalse(out().contains("Welcome back"));
        assertFalse(out().contains("Username:"));
    }

    // -------------------------------------------------------------------------
    // Structure
    // -------------------------------------------------------------------------

    @Test
    void constructorIsPrivate() throws Exception {
        Constructor<Main> ctor = Main.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();
        assertTrue(instance instanceof Main);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void redirectIo(String stdin) {
        stdout.reset();
        stderr.reset();
        System.setIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    private String out() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return stderr.toString(StandardCharsets.UTF_8);
    }
}