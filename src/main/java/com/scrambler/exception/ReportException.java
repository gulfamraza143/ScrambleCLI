package com.scrambler.exception;

/**
 * Thrown when entity report CSV read or write operations fail, including schema validation errors.
 */
public class ReportException extends RuntimeException {

    /**
     * Creates an exception with a descriptive message.
     *
     * @param message detail describing the report failure
     */
    public ReportException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a descriptive message and underlying cause.
     *
     * @param message detail describing the report failure
     * @param cause   the underlying I/O or parsing failure
     */
    public ReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
