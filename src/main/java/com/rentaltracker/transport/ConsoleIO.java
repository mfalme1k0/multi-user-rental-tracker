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


public final class ConsoleIO {

    private final BufferedReader in;
    private final PrintStream out;
    private final Console console; // null unless a real terminal is attached

    public ConsoleIO(InputStream in, PrintStream out) {
        this(in, out, null);
    }

    private ConsoleIO(InputStream in, PrintStream out, Console console) {
        this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
        this.console = console;
    }

//  Real stdin/stdout. Passwords are not echoed when a terminal is attached.
    public static ConsoleIO system() {
        return new ConsoleIO(System.in, System.out, System.console());
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

    // Prints the prompt and returns one line with surrounding whitespace removed.
    public String readLine(String prompt) {
        print(prompt);
        return readRaw().strip();
    }
    //hide password input if a terminal is attached
    public String readPassword(String prompt) {
        if (console != null) {
            char[] chars = console.readPassword("%s", prompt);
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
