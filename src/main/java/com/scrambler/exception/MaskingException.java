package com.scrambler.exception;

/**
 * Thrown when mask or unmask substitution operations fail.
 */
public class MaskingException extends RuntimeException {

    /**
     * Creates an exception with a descriptive message.
     *
     * @param message detail describing the masking failure
     */
    public MaskingException(String message) {
        super(message);
    }
}
