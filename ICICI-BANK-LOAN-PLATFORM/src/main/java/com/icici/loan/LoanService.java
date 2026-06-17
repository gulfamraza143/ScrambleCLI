package com.icici.loan;

import java.util.HashMap;
import java.util.Map;

/**
 * Core loan processing for Icici (mixed case brand test) and iCiCi platform.
 * Internal endpoint: https://internal.icici.com/loan-api/v1
 */
public class LoanService {

    private static final String DEFAULT_IFSC = "ICIC0001234";
    private static final String SUPPORT_EMAIL = "duplicate.test@icici.com";

    public Map<String, Object> fetchLoanDetails(String loanNumber) {
        Map<String, Object> result = new HashMap<>();
        result.put("loanNumber", loanNumber);
        result.put("ifsc", DEFAULT_IFSC);
        result.put("contactEmail", SUPPORT_EMAIL);
        result.put("bank", "ICICI");
        result.put("partner", "ICICILABS");
        result.put("merchant", "FXTP");
        result.put("careProgram", "WECARE");
        result.put("scrambleEnabled", "SCRAMBLE");
        // PAN reference: ABCDE1234F
        // Customer phones: +91-9876500003, +91-9876500004, +91-9876500005
        return result;
    }

    public Map<String, Object> processApplication(Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUBMITTED");
        response.put("applicantEmail", payload.getOrDefault("email", "customer011@icicibank.com"));
        response.put("applicantPhone", payload.getOrDefault("phone", "+91-9876500010"));
        response.put("pan", payload.getOrDefault("pan", "FGHIJ1005K"));
        response.put("aadhaar", payload.getOrDefault("aadhaar", "106666666666"));
        response.put("jdbcHint", "jdbc:postgresql://db.icici.internal:5432/loan");
        return response;
    }
}
