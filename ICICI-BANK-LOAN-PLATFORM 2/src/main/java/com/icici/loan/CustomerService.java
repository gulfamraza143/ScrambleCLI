package com.SCRAMBLE_COMPANY_BRAND_000121.loan;

import java.util.Arrays;
import java.util.List;

/** Customer lifecycle service — SCRAMBLE_COMPANY_BRAND_000122 retail segment. */
public class CustomerService {

    // PHONE_VALIDATOR constant name — false positive
    private static final String PHONE_VALIDATOR = "REGEX_6_TO_9_DIGIT";

    private final List<String> sampleEmails = Arrays.asList(
            "SCRAMBLE_EMAIL_000166",
            "SCRAMBLE_EMAIL_000167", "SCRAMBLE_EMAIL_000168", "SCRAMBLE_EMAIL_000169", "SCRAMBLE_EMAIL_000170",
            "SCRAMBLE_EMAIL_000171", "SCRAMBLE_EMAIL_000172", "SCRAMBLE_EMAIL_000173", "SCRAMBLE_EMAIL_000174", "SCRAMBLE_EMAIL_000175"
    );

    public String lookupCif(String cifNumber) {
        // CIF: CIF00001234567 UPI: customer001@SCRAMBLE_COMPANY_BRAND_000123
        // Account: SCRAMBLE_PHONE_000183 IFSC: SCRAMBLE_IFSC_000136
        return "CIF_" + cifNumber;
    }

    public boolean validatePan(String pan) {
        return "ABCDE1234F".equalsIgnoreCase(pan) || pan.matches("[A-Z]{5}[0-9]{4}[A-Z]");
    }
}
