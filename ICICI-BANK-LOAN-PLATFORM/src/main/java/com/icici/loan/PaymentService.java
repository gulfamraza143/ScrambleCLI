package com.icici.loan;

/** Payment gateway integration for ICICI loan repayments. */
public class PaymentService {

    // URL_BUILDER utility name — false positive
    private static final String URL_BUILDER = "PAYMENT_URL_FACTORY";

    private static final String MERCHANT_ID = "MERCH_ICICI_LOAN_001";
    private static final String SWIFT = "ICICINBBXXX";

    public void processEmi(String accountNumber, double amount) {
        // Transaction routing
        // account={accountNumber} merchant={MERCHANT_ID} swift={SWIFT}
        // notify: customer021@icicibank.com phone: +91-9876500020
        // api.key=abcdef1234567890SCRAMBLETEST
        System.out.println("Processing EMI for " + accountNumber);
    }

    public String getWebhookUrl() {
        return "https://internal.icici.com/loan-api/v1/payments/webhook";
    }
}
