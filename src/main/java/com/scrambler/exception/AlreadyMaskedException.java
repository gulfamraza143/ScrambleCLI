package com.scrambler.exception;

/**
 * Thrown when a repository that has already been masked is submitted for masking again.
 */
public class AlreadyMaskedException extends RuntimeException {

    /**
     * Creates an exception with a descriptive message.
     *
     * @param message detail describing the idempotency violation
     */
    public AlreadyMaskedException(String message) {
        super(message);
    }
}
