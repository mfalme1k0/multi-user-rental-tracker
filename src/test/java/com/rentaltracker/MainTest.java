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

import static org.junit.jupiter.api.Assertions.assertTrue;


class MainTest {

    private final InputStream originalIn = System.in;
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @AfterEach
    void restoreStreamsAndProperty() {
        System.setIn(originalIn);
        System.setOut(originalOut);
        System.setErr(originalErr);
        System.clearProperty(Main.DATABASE_PROPERTY);
    }

    @Test
    void mainInitializesDatabaseAndExitsFromAuthMenu(@TempDir Path tempDir) {
        Path db = tempDir.resolve("test.db");
        System.setProperty(Main.DATABASE_PROPERTY, db.toString());

        ByteArrayInputStream in = new ByteArrayInputStream("3\n".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setIn(in);
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

        Main.main(new String[]{});

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Multi-User Rental Tracker") || output.contains("Rental Tracker"));
        assertTrue(output.contains("Goodbye.") || output.contains("Exit") || output.contains("3"));
        assertTrue(db.toFile().exists());
    }
}