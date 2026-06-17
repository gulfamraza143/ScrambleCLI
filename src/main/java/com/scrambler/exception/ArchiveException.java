package com.scrambler.exception;

/**
 * Thrown when archive operations fail, including ZIP extraction, zip-slip detection,
 * corrupt archives, or zip bomb limit violations.
 */
public class ArchiveException extends RuntimeException {

    /**
     * Creates an exception with a descriptive message.
     *
     * @param message detail describing the archive failure
     */
    public ArchiveException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a descriptive message and underlying cause.
     *
     * @param message detail describing the archive failure
     * @param cause   the underlying I/O or parsing failure
     */
    public ArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
