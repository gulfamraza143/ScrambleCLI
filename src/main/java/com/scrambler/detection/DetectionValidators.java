package com.scrambler.detection;

import com.scrambler.config.CompanyDictionary;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

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
            { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 },
            { 1, 2, 3, 4, 0, 6, 7, 8, 9, 5 },
            { 2, 3, 4, 0, 1, 7, 8, 9, 5, 6 },
            { 3, 4, 0, 1, 2, 8, 9, 5, 6, 7 },
            { 4, 0, 1, 2, 3, 9, 5, 6, 7, 8 },
            { 5, 9, 8, 7, 6, 0, 4, 3, 2, 1 },
            { 6, 5, 9, 8, 7, 1, 0, 4, 3, 2 },
            { 7, 6, 5, 9, 8, 7, 2, 1, 0, 4 },
            { 8, 7, 6, 5, 9, 8, 3, 2, 1, 0 },
            { 9, 8, 7, 6, 5, 4, 3, 2, 1, 0 }
    };

    private static final int[][] VERHOEFF_PERMUTATION = {
            { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 },
            { 1, 5, 7, 6, 2, 8, 3, 0, 9, 4 },
            { 5, 8, 0, 3, 7, 9, 6, 1, 4, 2 },
            { 8, 9, 1, 6, 0, 4, 3, 5, 2, 7 },
            { 9, 4, 5, 3, 1, 2, 6, 8, 7, 0 },
            { 4, 2, 8, 6, 5, 7, 3, 9, 0, 1 },
            { 2, 7, 9, 3, 8, 0, 6, 4, 1, 5 },
            { 7, 0, 4, 6, 9, 1, 3, 2, 5, 8 }
    };

    private static final List<String> INTERNAL_HOST_SUFFIXES = List.of(".internal", ".local", ".corp");

    private DetectionValidators() {
    }

    static Predicate<String> createSensitiveUrlValidator(CompanyDictionary companyDictionary) {
        List<String> normalizedBrands = companyDictionary.getTerms().stream()
                .map(term -> term.replaceAll("\\s+", "").toLowerCase(Locale.ROOT))
                .filter(term -> !term.isEmpty())
                .toList();
        return url -> isSensitiveUrl(url, normalizedBrands);
    }

    static boolean isSensitiveUrl(String url, List<String> normalizedBrandTerms) {
        String host = extractUrlHost(url);
        if (host.isEmpty()) {
            return false;
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(hostLower)) {
            return true;
        }
        if (isPrivateOrLoopbackIpv4(hostLower)) {
            return true;
        }
        for (String suffix : INTERNAL_HOST_SUFFIXES) {
            if (hostLower.endsWith(suffix)) {
                return true;
            }
        }
        for (String brand : normalizedBrandTerms) {
            if (hostLower.contains(brand)) {
                return true;
            }
        }
        return false;
    }

    static String extractUrlHost(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return "";
        }
        String remainder = url.substring(schemeEnd + 3);
        int at = remainder.lastIndexOf('@');
        if (at >= 0) {
            remainder = remainder.substring(at + 1);
        }
        int end = remainder.length();
        for (char delimiter : new char[] { ':', '/', '?', '#' }) {
            int index = remainder.indexOf(delimiter);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        String host = remainder.substring(0, end);
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            host = host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isPrivateOrLoopbackIpv4(String host) {
        if (!host.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
            return false;
        }
        String[] octets = host.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        int[] values = new int[4];
        for (int index = 0; index < 4; index++) {
            int value = Integer.parseInt(octets[index]);
            if (value < 0 || value > 255) {
                return false;
            }
            values[index] = value;
        }
        if (values[0] == 127) {
            return true;
        }
        if (values[0] == 10) {
            return true;
        }
        if (values[0] == 192 && values[1] == 168) {
            return true;
        }
        return values[0] == 172 && values[1] >= 16 && values[1] <= 31;
    }

    static boolean isValidAadhaar(String value) {
        String normalized = stripSeparators(value);
        if (!normalized.matches("\\d{12}")) {
            return false;
        }
        return passesVerhoeffCheck(normalized);
    }

    static boolean isValidCreditCard(String value) {
        if (value.matches("\\d{8}[\\s-]\\d{4}[\\s-]\\d{4}(?:[\\s-]\\d{4})?")) {
            return false;
        }
        String stripped = stripSeparators(value);
        if (!stripped.matches("\\d{13,19}")) {
            return false;
        }
        if (value.matches(".*[\\s-].*")) {
            String[] groups = value.split("[\\s-]");
            if (groups.length == 3 && groups[0].length() == 8) {
                return false;
            }
            for (String group : groups) {
                if (group.length() > 6) {
                    return false;
                }
                if (groups.length == 4 && group.length() != 4) {
                    return false;
                }
            }
        }
        return passesLuhnCheck(stripped);
    }

    static boolean isValidPhone(String value) {
        String trimmed = value.trim();
        if (trimmed.matches("\\d+\\.\\d+")) {
            return false;
        }
        if (trimmed.matches("\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}")) {
            return false;
        }
        String digitsOnly = trimmed.replaceAll("\\D", "");
        if (digitsOnly.matches("[6-9]\\d{9}") && digitsOnly.length() == 10) {
            return true;
        }
        if (digitsOnly.matches("91[6-9]\\d{9}") && digitsOnly.length() == 12) {
            return trimmed.contains("+") || trimmed.matches(".*91[\\s-].*");
        }
        if (trimmed.startsWith("+") && digitsOnly.length() >= 10 && digitsOnly.length() <= 15) {
            return true;
        }
        if (trimmed.contains("(") && trimmed.contains(")")
                && digitsOnly.length() >= 10 && digitsOnly.length() <= 15) {
            return true;
        }
        if (trimmed.matches(".*\\d+[\\s-]\\d+[\\s-]\\d+.*")
                && digitsOnly.length() >= 10 && digitsOnly.length() <= 15) {
            return true;
        }
        return false;
    }

    static boolean isValidPassword(String value) {
        String trimmed = value.trim();
        if (trimmed.length() < 4) {
            return false;
        }
        if (trimmed.startsWith("argon2")
                || trimmed.startsWith("pbkdf2")
                || trimmed.startsWith("$2a$")
                || trimmed.startsWith("$2b$")) {
            return true;
        }
        if (trimmed.indexOf('(') >= 0 || trimmed.indexOf('[') >= 0) {
            return false;
        }
        if (trimmed.matches("(?i)password")) {
            return false;
        }
        if (trimmed.contains(".") && trimmed.matches(".*\\.[A-Za-z_][A-Za-z0-9_]*.*")) {
            return false;
        }
        return true;
    }

    static boolean isValidIpAddress(String value) {
        return !value.equals("0.0.0.0") && !value.startsWith("127.");
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

    static boolean isValidAwsAccessKey(String value) {
        String trimmed = value.trim();
        return trimmed.length() == 20
                && trimmed.matches("(?:AKIA|ASIA)[0-9A-Z]{16}");
    }

    static boolean isValidGoogleApiKey(String value) {
        String trimmed = value.trim();
        return trimmed.length() == 39
                && trimmed.matches("AIza[0-9A-Za-z_-]{35}");
    }

    static boolean isValidEnterpriseCredential(String value) {
        return value.trim().length() >= 8;
    }

    static boolean isValidAssignmentValue(String value) {
        return value.trim().length() >= 3;
    }

    static boolean isValidSshPublicKey(String value) {
        String trimmed = value.trim();
        if (trimmed.contains("BEGIN PUBLIC KEY")) {
            return trimmed.length() > 40;
        }
        if (trimmed.startsWith("ssh-rsa ") || trimmed.startsWith("ssh-ed25519 ")) {
            return trimmed.length() > 30;
        }
        return false;
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
            checksum = VERHOEFF_MULTIPLICATION[checksum][VERHOEFF_PERMUTATION[(digits.length() - index - 1)
                    % 8][digit]];
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
