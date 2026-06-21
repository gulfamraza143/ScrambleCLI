package com.SCRAMBLE_COMPANY_BRAND_000131.loan;

import java.util.HashMap;
import java.util.Map;

/**
 * Core loan processing for SCRAMBLE_COMPANY_BRAND_000132 (mixed case brand test) and SCRAMBLE_COMPANY_BRAND_000133 platform.
 * Internal endpoint: SCRAMBLE_URL_000041
 */
public class LoanService {

    private static final String DEFAULT_IFSC = "SCRAMBLE_IFSC_000139";
    private static final String SUPPORT_EMAIL = "SCRAMBLE_EMAIL_000179";

    public Map<String, Object> fetchLoanDetails(String loanNumber) {
        Map<String, Object> result = new HashMap<>();
        result.put("loanNumber", loanNumber);
        result.put("ifsc", DEFAULT_IFSC);
        result.put("contactEmail", SUPPORT_EMAIL);
        result.put("bank", "SCRAMBLE_COMPANY_BRAND_000134");
        result.put("partner", "SCRAMBLE_COMPANY_BRAND_000135");
        result.put("merchant", "SCRAMBLE_COMPANY_BRAND_000136");
        result.put("careProgram", "SCRAMBLE_COMPANY_BRAND_000137");
        result.put("scrambleEnabled", "SCRAMBLE_COMPANY_BRAND_000138");
        // PAN reference: ABCDE1234F
        // Customer phones: +SCRAMBLE_PHONE_000185, +SCRAMBLE_PHONE_000186, +SCRAMBLE_PHONE_000187
        return result;
    }

    public Map<String, Object> processApplication(Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUBMITTED");
        response.put("applicantEmail", payload.getOrDefault("email", "SCRAMBLE_EMAIL_000180"));
        response.put("applicantPhone", payload.getOrDefault("phone", "+SCRAMBLE_PHONE_000188"));
        response.put("pan", payload.getOrDefault("pan", "FGHIJ1005K"));
        response.put("aadhaar", payload.getOrDefault("aadhaar", "SCRAMBLE_PHONE_000189"));
        response.put("jdbcHint", "SCRAMBLE_DATABASE_URL_000014");
        return response;
    }
}
