package com.scrambler.detection;

import java.util.Set;

/**
 * Value validators used by {@link DetectionRule} after regex matching.
 */
final class DetectionValidators {

    private static final String GSTIN_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Set<Character> PAN_HOLDER_TYPES = Set.of(
            'P', 'C', 'H', 'F', 'A', 'T', 'B', 'L', 'J', 'G');
    private static final Set<String> CIN_STATE_CODES = Set.of(
            "AN", "AP", "AR", "AS", "BR", "CG", "CH", "DD", "DL", "DN", "GA", "GJ", "HP", "HR",
            "JH", "JK", "KA", "KL", "LA", "LD", "MH", "ML", "MN", "MP", "MZ", "NL", "OR", "PB",
            "PY", "RJ", "SK", "TN", "TR", "TS", "UK", "UP", "WB");
    private static final Set<String> CIN_COMPANY_TYPES = Set.of(
            "PLC", "PTC", "OPC", "NPL", "GOI", "SGC", "ULL", "ULT", "GAP", "FTC", "SSL", "GAT",
            "PLG", "LLP", "FLL", "FLC");

    private static final int[][] VERHOEFF_MULTIPLICATION = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
            {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
            {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
            {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
            {7, 6, 5, 9, 8, 7, 2, 1, 0, 4},
            {8, 7, 6, 5, 9, 8, 3, 2, 1, 0},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };

    private static final int[][] VERHOEFF_PERMUTATION = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
            {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
            {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
            {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
            {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };

    private DetectionValidators() {
    }

    static boolean isValidAadhaar(String value) {
        String normalized = stripSeparators(value);
        if (!normalized.matches("\\d{12}")) {
            return false;
        }
        return passesVerhoeffCheck(normalized);
    }

    static boolean isValidCreditCard(String value) {
        return passesLuhnCheck(stripSeparators(value));
    }

    static boolean isValidGstin(String value) {
        String normalized = value.trim().toUpperCase();
        if (!normalized.matches("\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]")) {
            return false;
        }
        if (!isValidGstinStateCode(normalized.substring(0, 2))) {
            return false;
        }
        return passesGstinChecksum(normalized);
    }

    static boolean isValidPan(String value) {
        String normalized = value.trim().toUpperCase();
        if (!normalized.matches("[A-Z]{5}\\d{4}[A-Z]")) {
            return false;
        }
        return PAN_HOLDER_TYPES.contains(normalized.charAt(3));
    }

    static boolean isValidTan(String value) {
        String normalized = value.trim().toUpperCase();
        if (!normalized.matches("[A-Z]{4}\\d{5}[A-Z]")) {
            return false;
        }
        String sequence = normalized.substring(4, 9);
        return !sequence.equals("00000");
    }

    static boolean isValidInternalIdentifier(String value) {
        String trimmed = value.trim();
        if (trimmed.length() < 3) {
            return false;
        }
        if (trimmed.matches("\\d{5,}")) {
            return true;
        }
        if (trimmed.matches("[a-zA-Z][a-zA-Z0-9]*\\.[a-zA-Z][a-zA-Z0-9]*")) {
            return true;
        }
        return trimmed.matches("[A-Za-z]+\\d{2,}");
    }

    static boolean isValidCin(String value) {
        String normalized = value.trim().toUpperCase();
        if (!normalized.matches("[UL]\\d{5}[A-Z]{2}\\d{4}[A-Z]{3}\\d{6}")) {
            return false;
        }
        String stateCode = normalized.substring(6, 8);
        if (!CIN_STATE_CODES.contains(stateCode)) {
            return false;
        }
        String companyType = normalized.substring(12, 15);
        if (!CIN_COMPANY_TYPES.contains(companyType)) {
            return false;
        }
        int year = Integer.parseInt(normalized.substring(8, 12));
        return year >= 1800 && year <= 2099;
    }

    private static boolean isValidGstinStateCode(String stateCode) {
        int code = Integer.parseInt(stateCode);
        return (code >= 1 && code <= 38) || code == 96 || code == 97 || code == 99;
    }

    private static boolean passesGstinChecksum(String gstin) {
        int factor = 2;
        int sum = 0;
        int mod = GSTIN_CHARSET.length();
        char[] chars = gstin.substring(0, 14).toCharArray();
        for (int index = chars.length - 1; index >= 0; index--) {
            int codePoint = GSTIN_CHARSET.indexOf(chars[index]);
            if (codePoint < 0) {
                return false;
            }
            int digit = factor * codePoint;
            factor = (factor == 2) ? 1 : 2;
            sum += (digit / mod) + (digit % mod);
        }
        int checkCodePoint = (mod - (sum % mod)) % mod;
        return gstin.charAt(14) == GSTIN_CHARSET.charAt(checkCodePoint);
    }

    private static String stripSeparators(String value) {
        return value.replaceAll("[\\s-]", "");
    }

    private static boolean passesVerhoeffCheck(String digits) {
        int checksum = 0;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int digit = digits.charAt(index) - '0';
            checksum = VERHOEFF_MULTIPLICATION[checksum][VERHOEFF_PERMUTATION[(digits.length() - index - 1) % 8][digit]];
        }
        return checksum == 0;
    }

    private static boolean passesLuhnCheck(String digits) {
        if (!digits.matches("\\d{13,19}")) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int digit = digits.charAt(index) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
