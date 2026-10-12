package com.rentaltracker;

import com.rentaltracker.exception.DatabaseConnectionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @TempDir
    Path tempDir;

    private Path database;
    private InputStream originalIn;
    private PrintStream originalOut;
    private String originalProperty;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void setUp() {
        database = tempDir.resolve("main-test.db");
        originalIn = System.in;
        originalOut = System.out;
        originalProperty = System.getProperty(Main.DATABASE_PROPERTY);

        System.setProperty(Main.DATABASE_PROPERTY, database.toString());
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreProcessState() {
        System.setIn(originalIn);
        System.setOut(originalOut);

        if (originalProperty == null) {
            System.clearProperty(Main.DATABASE_PROPERTY);
        } else {
            System.setProperty(Main.DATABASE_PROPERTY, originalProperty);
        }
    }

    /** Runs the whole application with these lines typed on stdin and returns everything it printed. */
    private String runWithKeyboardInput(String... lines) {
        String typed = String.join("\n", lines) + "\n";
        System.setIn(new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8)));
        capturedOut.reset();

        Main.main(new String[0]);

        return capturedOut.toString(StandardCharsets.UTF_8);
    }

    @Test
    void firstLaunchRegistersTheOwnerAndExitsFromTheMenu() {
        String output = runWithKeyboardInput("Alice", "5");

        assertTrue(output.contains("Rental Tracker"), output);
        assertTrue(output.contains("Enter your username"), output);
        assertTrue(output.contains("Welcome, alice!"), output);
        assertTrue(output.contains("=== Rental tracker ==="), output);
        assertTrue(output.contains("Goodbye."), output);
    }

    @Test
    void createsTheDatabaseAtTheConfiguredPath() {
        assertFalse(Files.exists(database));

        runWithKeyboardInput("alice", "5");

        assertTrue(Files.exists(database), "the database must be created where the property points");
    }

    @Test
    void secondLaunchRecognisesTheStoredOwner() {
        runWithKeyboardInput("alice", "5");

        String output = runWithKeyboardInput("5");      // no username typed: it is already stored

        assertTrue(output.contains("Welcome back, alice!"), output);
        assertFalse(output.contains("Enter your username"), output);
        assertTrue(output.contains("Goodbye."), output);
    }

    @Test
    void failsFastWhenTheDatabaseCannotBeOpened() {
        System.setProperty(Main.DATABASE_PROPERTY, tempDir.toString());   // a directory, not a file

        assertThrows(DatabaseConnectionException.class, () -> runWithKeyboardInput("alice", "5"));
    }
}
