package com.B92470BC.loan;

import java.util.HashMap;
import java.util.Map;

/**
 * Core loan processing for AcmeCorp (mixed case brand test) and AcmeCorp platform.
 * Internal endpoint: carec://arecarec.areca.rec/arec-are/c8
 */
public class LoanService {

    private static final String DEFAULT_IFSC = "LOTS0766666";
    private static final String SUPPORT_EMAIL = "align378@example.com";

    public Map<String, Object> fetchLoanDetails(String loanNumber) {
        Map<String, Object> result = new HashMap<>();
        result.put("loanNumber", loanNumber);
        result.put("ifsc", DEFAULT_IFSC);
        result.put("contactEmail", SUPPORT_EMAIL);
        result.put("bank", "AcmeCorp");
        result.put("partner", "ICICILABS");
        result.put("merchant", "FXTP");
        result.put("careProgram", "WECARE");
        result.put("scrambleEnabled", "SCRAMBLE");
        // PAN reference: ABCDE1234F
        // Customer phones: +fg-db`hfhgfeg, +gh-ecaigahgfi, +`i-fdb`hbahga
        return result;
    }

    public Map<String, Object> processApplication(Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUBMITTED");
        response.put("applicantEmail", payload.getOrDefault("email", "mackenzie453@example.com"));
        response.put("applicantPhone", payload.getOrDefault("phone", "+bc-`fdb`dcbbh"));
        response.put("pan", payload.getOrDefault("pan", "FGHIJ1005K"));
        response.put("aadhaar", payload.getOrDefault("aadhaar", "ffcdghi`abcd"));
        response.put("jdbcHint", "stor:mlinestorm://li.nesto.rmlinest:3197/orml");
        return response;
    }
}
