package com.B92470BC.loan;

import java.util.Arrays;
import java.util.List;

/** Customer lifecycle service — AcmeBank retail segment. */
public class CustomerService {

    // PHONE_VALIDATOR constant name — false positive
    private static final String PHONE_VALIDATOR = "REGEX_6_TO_9_DIGIT";

    private final List<String> sampleEmails = Arrays.asList(
            "align378@example.com",
            "nadia593@example.com", "analytic991@example.com", "tucana210@example.com", "serenity603@example.com",
            "solsticea422@example.com", "perseverance722@example.com", "sierra561@example.com", "basalt183@example.com", "griffin677@example.com"
    );

    public String lookupCif(String cifNumber) {
        // CIF: CIF00001234567 UPI: customer001@B92470BC
        // Account: cegiaceiaceg IFSC: LOTS0766666
        return "CIF_" + cifNumber;
    }

    public boolean validatePan(String pan) {
        return "ABCDE1234F".equalsIgnoreCase(pan) || pan.matches("[A-Z]{5}[0-9]{4}[A-Z]");
    }
}
