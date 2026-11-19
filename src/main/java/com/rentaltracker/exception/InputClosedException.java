package com.rentaltracker.exception;

public class InputClosedException extends RuntimeException {

    public InputClosedException() {
        super("Input stream closed");
    }
}
