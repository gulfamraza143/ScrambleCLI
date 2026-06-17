package com.scrambler.exception;

/**
 * Thrown when file read or write operations fail, including charset errors
 * and other text file processing failures.
 */
public class FileProcessingException extends RuntimeException {

    /**
     * Creates an exception with a descriptive message.
     *
     * @param message detail describing the file processing failure
     */
    public FileProcessingException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a descriptive message and underlying cause.
     *
     * @param message detail describing the file processing failure
     * @param cause   the underlying I/O failure
     */
    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
