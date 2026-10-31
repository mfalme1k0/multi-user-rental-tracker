package com.rentaltracker.transport;

import com.rentaltracker.exception.InputClosedException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsoleIOTest {

    @Test
    void printlnPrintAndPrintfWriteToStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleIO io = new ConsoleIO(
                InputStream.nullInputStream(),
                new PrintStream(out, true, StandardCharsets.UTF_8));

        io.println("hello");
        io.print("world");
        io.println();
        io.printf("%s-%d", "x", 1);

        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("hello"));
        assertTrue(text.contains("world"));
        assertTrue(text.contains("x-1"));
    }

    @Test
    void readLineStripsWhitespace() {
        assertEquals("alice", io("  alice  \n").readLine("Name: "));
    }

    @Test
    void readPasswordWithoutReaderReadsVisibleLine() {
        assertEquals("s3cret", io("s3cret\n").readPassword("Password: "));
    }

    @Test
    void readPasswordWithReaderReturnsPasswordAndClearsBuffer() {
        char[] source = {'s', 'e', 'c', 'r', 'e', 't'};
        ConsoleIO consoleIO = ConsoleIO.withPasswordReader(
                InputStream.nullInputStream(),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                prompt -> {
                    assertEquals("Password: ", prompt);
                    return source;
                });

        assertEquals("secret", consoleIO.readPassword("Password: "));
        // buffer wiped after use
        assertEquals("      ", new String(source));
    }

    @Test
    void readPasswordWithReaderNullThrowsInputClosed() {
        ConsoleIO consoleIO = ConsoleIO.withPasswordReader(
                InputStream.nullInputStream(),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                prompt -> null);

        assertThrows(InputClosedException.class, () -> consoleIO.readPassword("Password: "));
    }

    @Test
    void createWithNullConsoleFallsBackToLineRead() {
        ConsoleIO consoleIO = ConsoleIO.create(
                null,
                new ByteArrayInputStream("visible\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        assertEquals("visible", consoleIO.readPassword("Password: "));
    }

    @Test
    void createWithConsoleUsesConsoleReadPassword() {
        Console console = mock(Console.class);
        when(console.readPassword(eq("%s"), eq("Password: ")))
                .thenReturn("secret".toCharArray());

        ConsoleIO consoleIO = ConsoleIO.create(
                console,
                InputStream.nullInputStream(),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        assertEquals("secret", consoleIO.readPassword("Password: "));
        verify(console).readPassword("%s", "Password: ");
    }

    @Test
    void createWithConsoleNullCharsThrowsInputClosed() {
        Console console = mock(Console.class);
        when(console.readPassword(eq("%s"), eq("Password: "))).thenReturn(null);

        ConsoleIO consoleIO = ConsoleIO.create(
                console,
                InputStream.nullInputStream(),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        assertThrows(InputClosedException.class, () -> consoleIO.readPassword("Password: "));
    }

    @Test
    void readLineOnEofThrowsInputClosed() {
        assertThrows(InputClosedException.class, () -> io("").readLine("> "));
    }

    @Test
    void readPasswordOnEofThrowsInputClosed() {
        assertThrows(InputClosedException.class, () -> io("").readPassword("Password: "));
    }

    @Test
    void readRawIoExceptionBecomesUnchecked() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        ConsoleIO consoleIO = new ConsoleIO(
                broken,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        assertThrows(UncheckedIOException.class, () -> consoleIO.readLine("> "));
    }

    @Test
    void systemFactoryReturnsNonNull() {
        assertNotNull(ConsoleIO.system());
    }

    private static ConsoleIO io(String scripted) {
        return new ConsoleIO(
                new ByteArrayInputStream(scripted.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
}