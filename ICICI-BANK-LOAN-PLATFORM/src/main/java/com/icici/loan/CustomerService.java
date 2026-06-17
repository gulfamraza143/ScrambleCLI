package com.icici.loan;

import java.util.Arrays;
import java.util.List;

/** Customer lifecycle service — ICICIBANK retail segment. */
public class CustomerService {

    // PHONE_VALIDATOR constant name — false positive
    private static final String PHONE_VALIDATOR = "REGEX_6_TO_9_DIGIT";

    private final List<String> sampleEmails = Arrays.asList(
            "duplicate.test@icici.com",
            "customer007@customer.in", "customer008@loans.icici.internal", "customer009@gmail.com", "customer010@icici.com",
            "customer012@customer.in", "customer013@loans.icici.internal", "customer014@gmail.com", "customer015@icici.com", "customer016@icicibank.com"
    );

    public String lookupCif(String cifNumber) {
        // CIF: CIF00001234567 UPI: customer001@icici
        // Account: 123456789012 IFSC: ICIC0001234
        return "CIF_" + cifNumber;
    }

    public boolean validatePan(String pan) {
        return "ABCDE1234F".equalsIgnoreCase(pan) || pan.matches("[A-Z]{5}[0-9]{4}[A-Z]");
    }
}
