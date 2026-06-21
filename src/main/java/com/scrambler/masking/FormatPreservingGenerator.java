package com.scrambler.masking;

import com.scrambler.detection.EntityType;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates format-preserving masked values that maintain length, character classes, separators, and overall shape.
 */
public final class FormatPreservingGenerator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^(.+)@(.+)$");
    private static final Pattern UPI_PATTERN = Pattern.compile("^(.+)@(.+)$", Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_EMAIL_DOMAIN = "example.com";
    private static final String DEFAULT_UPI_PROVIDER = "demo";

    private final NounDictionary nounDictionary;
    private final BrandReplacementDictionary brandDictionary;

    /**
     * Creates a generator with default noun and brand dictionaries.
     */
    public FormatPreservingGenerator() {
        this(NounDictionary.defaults(), BrandReplacementDictionary.defaults());
    }

    /**
     * Creates a generator with supplied dictionaries.
     *
     * @param nounDictionary  noun seed dictionary
     * @param brandDictionary brand replacement dictionary
     */
    public FormatPreservingGenerator(NounDictionary nounDictionary, BrandReplacementDictionary brandDictionary) {
        this.nounDictionary = Objects.requireNonNull(nounDictionary, "nounDictionary must not be null");
        this.brandDictionary = Objects.requireNonNull(brandDictionary, "brandDictionary must not be null");
    }

    /**
     * Generates a format-preserving masked value for the given entity type and original text.
     *
     * @param entityType entity type being masked
     * @param original   original matched text
     * @param attempt    collision-resolution attempt (0 for first candidate)
     * @return synthetic masked value
     */
    public String generate(EntityType entityType, String original, int attempt) {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(original, "original must not be null");
        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }

        return switch (entityType) {
            case EMAIL -> maskEmail(original, attempt);
            case PHONE -> maskPhone(original, attempt);
            case PAN -> maskPan(original, attempt);
            case AADHAAR -> maskAadhaar(original, attempt);
            case GSTIN -> maskGstin(original, attempt);
            case IFSC -> maskIfsc(original, attempt);
            case UPI_ID -> maskUpi(original, attempt);
            case COMPANY_BRAND -> brandDictionary.replace(original);
            case CREDIT_CARD -> maskCreditCard(original, attempt);
            case TAN -> maskTan(original, attempt);
            case CIN -> maskCin(original, attempt);
            default -> maskGeneric(original, attempt);
        };
    }

    private String maskEmail(String original, int attempt) {
        Matcher matcher = EMAIL_PATTERN.matcher(original);
        if (!matcher.matches()) {
            return maskGeneric(original, attempt);
        }
        String noun = nounDictionary.selectNoun(original, attempt);
        int sequence = Math.floorMod(stableHash(original, attempt), 999) + 1;
        return noun + String.format("%03d", sequence) + "@" + DEFAULT_EMAIL_DOMAIN;
    }

    private String maskPhone(String original, int attempt) {
        StringBuilder masked = new StringBuilder(original.length());
        int digitIndex = 0;
        for (int index = 0; index < original.length(); index++) {
            char current = original.charAt(index);
            if (Character.isDigit(current)) {
                int mapped = mapDigit(current - '0', original, attempt, digitIndex);
                if (original.chars().filter(Character::isDigit).count() == 10 && digitIndex == 0 && mapped < 6) {
                    mapped = 6 + Math.floorMod(mapped, 4);
                }
                masked.append((char) ('0' + mapped));
                digitIndex++;
            } else {
                masked.append(current);
            }
        }
        return masked.toString();
    }

    private String maskPan(String original, int attempt) {
        String normalized = original.toUpperCase(Locale.ROOT);
        String noun = nounDictionary.selectNoun(original, attempt).toUpperCase(Locale.ROOT);
        String letters = fitAlpha(noun, 5);
        String digits = fitDigits(stableHash(original, attempt), 4);
        char suffix = (char) ('A' + Math.floorMod(stableHash(original + "suffix", attempt), 26));
        return letters + digits + suffix;
    }

    private String maskAadhaar(String original, int attempt) {
        StringBuilder masked = new StringBuilder(original.length());
        int digitIndex = 0;
        for (int index = 0; index < original.length(); index++) {
            char current = original.charAt(index);
            if (Character.isDigit(current)) {
                masked.append(mapDigit(current - '0', original, attempt, digitIndex));
                digitIndex++;
            } else {
                masked.append(current);
            }
        }
        return masked.toString();
    }

    private String maskGstin(String original, int attempt) {
        String normalized = original.toUpperCase(Locale.ROOT);
        String stateCode = normalized.substring(0, 2);
        String panPart = maskPan(normalized.substring(2, 12), attempt);
        char entityCode = (char) ('1' + Math.floorMod(stableHash(original + "entity", attempt), 9));
        char checksum = (char) ('A' + Math.floorMod(stableHash(original + "gst", attempt), 26));
        return stateCode + panPart + entityCode + "Z" + checksum;
    }

    private String maskIfsc(String original, int attempt) {
        String normalized = original.toUpperCase(Locale.ROOT);
        String bankCode = fitAlpha("LOTS", 4);
        StringBuilder branch = new StringBuilder();
        for (int index = 5; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (Character.isDigit(current)) {
                branch.append(mapDigit(current - '0', original, attempt, index));
            } else {
                branch.append((char) ('A' + Math.floorMod(stableHash(original + index, attempt), 26)));
            }
        }
        while (branch.length() < 6) {
            branch.append(mapDigit(0, original, attempt, branch.length()));
        }
        return bankCode + "0" + branch.substring(0, 6);
    }

    private String maskUpi(String original, int attempt) {
        Matcher matcher = UPI_PATTERN.matcher(original);
        if (!matcher.matches()) {
            return maskGeneric(original, attempt);
        }
        String noun = nounDictionary.selectNoun(original, attempt);
        int sequence = Math.floorMod(stableHash(original, attempt), 999) + 1;
        return noun + String.format("%03d", sequence) + "@" + DEFAULT_UPI_PROVIDER;
    }

    private String maskCreditCard(String original, int attempt) {
        StringBuilder masked = new StringBuilder(original.length());
        int digitIndex = 0;
        for (int index = 0; index < original.length(); index++) {
            char current = original.charAt(index);
            if (Character.isDigit(current)) {
                masked.append(mapDigit(current - '0', original, attempt, digitIndex));
                digitIndex++;
            } else {
                masked.append(current);
            }
        }
        return masked.toString();
    }

    private String maskTan(String original, int attempt) {
        String normalized = original.toUpperCase(Locale.ROOT);
        String letters = fitAlpha(nounDictionary.selectNoun(original, attempt), 4);
        String digits = fitDigits(stableHash(original, attempt), 5);
        char suffix = (char) ('A' + Math.floorMod(stableHash(original + "tan", attempt), 26));
        return letters + digits + suffix;
    }

    private String maskCin(String original, int attempt) {
        String normalized = original.toUpperCase(Locale.ROOT);
        char prefix = normalized.charAt(0);
        String digits = fitDigits(stableHash(original, attempt), 5);
        String letters = fitAlpha(nounDictionary.selectNoun(original, attempt), 2);
        String year = fitDigits(stableHash(original + "year", attempt), 4);
        String category = fitAlpha(nounDictionary.selectNoun(original + "cat", attempt), 3);
        String sequence = fitDigits(stableHash(original + "seq", attempt), 6);
        return "" + prefix + digits + letters + year + category + sequence;
    }

    private String maskGeneric(String original, int attempt) {
        String noun = nounDictionary.selectNoun(original, attempt);
        StringBuilder masked = new StringBuilder(original.length());
        int nounIndex = 0;
        for (int index = 0; index < original.length(); index++) {
            char current = original.charAt(index);
            if (Character.isLetter(current)) {
                char replacement = noun.charAt(nounIndex % noun.length());
                masked.append(Character.isUpperCase(current)
                        ? Character.toUpperCase(replacement)
                        : Character.toLowerCase(replacement));
                nounIndex++;
            } else if (Character.isDigit(current)) {
                masked.append(mapDigit(current - '0', original, attempt, index));
            } else {
                masked.append(current);
            }
        }
        return masked.toString();
    }

    private static char mapDigit(int digit, String original, int attempt, int digitIndex) {
        int mapped = digit + 1 + Math.floorMod(stableHash(original + "#" + attempt, digitIndex), 8);
        return (char) ('0' + Math.floorMod(mapped, 10));
    }

    private static String fitAlpha(String source, int length) {
        String normalized = source.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "X");
        if (normalized.length() >= length) {
            return normalized.substring(0, length);
        }
        StringBuilder builder = new StringBuilder(normalized);
        while (builder.length() < length) {
            builder.append('X');
        }
        return builder.toString();
    }

    private static String fitDigits(int seed, int length) {
        StringBuilder builder = new StringBuilder();
        int value = Math.abs(seed);
        for (int index = 0; index < length; index++) {
            builder.append(Math.floorMod(value + index * 7, 10));
        }
        return builder.toString();
    }

    private static String truncateOrPad(String value, int length) {
        if (value.length() >= length) {
            return value.substring(0, length);
        }
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < length) {
            builder.append('0');
        }
        return builder.toString();
    }

    private static String fitLength(String value, int length) {
        if (value.length() == length) {
            return value;
        }
        if (value.length() > length) {
            return value.substring(0, length);
        }
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < length) {
            builder.append('x');
        }
        return builder.toString();
    }

    private static int stableHash(String value, int attempt) {
        int hash = attempt;
        for (int index = 0; index < value.length(); index++) {
            hash = 31 * hash + value.charAt(index);
        }
        return hash;
    }
}
