package com.scrambler.masking;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Generates random opaque uppercase hex tokens for path-level masking.
 * Each invocation produces unpredictable values; no sequential counters or deterministic mappings.
 */
public final class OpaqueTokenGenerator {

    private static final int TOKEN_BYTES = 4;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generates a random opaque token (8 uppercase hex characters).
     *
     * @param attempt collision-resolution attempt (unused; retained for mapper retry contract)
     * @return opaque token
     */
    public String generate(int attempt) {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
