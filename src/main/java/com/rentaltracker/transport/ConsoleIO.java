package com.rentaltracker.transport;

import com.rentaltracker.exception.InputClosedException;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;

public final class ConsoleIO {

    private final BufferedReader in;
    private final PrintStream out;

    private final Function<String, char[]> passwordReader;

    public ConsoleIO(InputStream in, PrintStream out) {
        this(in, out, (Function<String, char[]>) null);
    }

    private ConsoleIO(InputStream in, PrintStream out, Function<String, char[]> passwordReader) {
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
        this.passwordReader = passwordReader;
    }

    /** Real stdin/stdout. Passwords are not echoed when a terminal is attached. */
    public static ConsoleIO system() {
        return create(System.console(), System.in, System.out);
    }

   //for tests to pass a mock io
    static ConsoleIO create(Console console, InputStream in, PrintStream out) {
        Function<String, char[]> reader = null;
        if (console != null) {
            reader = prompt -> console.readPassword("%s", prompt);
        }
        return new ConsoleIO(in, out, reader);
    }


    static ConsoleIO withPasswordReader(
            InputStream in,
            PrintStream out,
            Function<String, char[]> passwordReader) {
        return new ConsoleIO(in, out, passwordReader);
    }

    public void println() {
        out.println();
    }

    public void println(String text) {
        out.println(text);
    }

    public void print(String text) {
        out.print(text);
        out.flush();
    }

    public void printf(String format, Object... args) {
        out.printf(format, args);
    }

    public String readLine(String prompt) {
        print(prompt);
        return readRaw().strip();
    }

    public String readPassword(String prompt) {
        if (passwordReader != null) {
            char[] chars = passwordReader.apply(prompt);
            if (chars == null) {
                throw new InputClosedException();
            }
            String password = new String(chars);
            Arrays.fill(chars, ' ');
            return password;
        }
        print(prompt);
        return readRaw();
    }

    private String readRaw() {
        try {
            String line = in.readLine();
            if (line == null) {
                throw new InputClosedException();
            }
            return line;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}