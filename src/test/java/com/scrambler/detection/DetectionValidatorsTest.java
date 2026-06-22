package com.scrambler.detection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectionValidatorsTest {

    private static final String VALID_AADHAAR = "123412341232";

    @Test
    void acceptsValidAadhaarAfterNormalization() {
        assertTrue(DetectionValidators.isValidAadhaar(VALID_AADHAAR));
        assertTrue(DetectionValidators.isValidAadhaar("1234 1234 1232"));
        assertTrue(DetectionValidators.isValidAadhaar("1234-1234-1232"));
    }

    @Test
    void rejectsInvalidAadhaarChecksum() {
        assertFalse(DetectionValidators.isValidAadhaar("123412341234"));
        assertFalse(DetectionValidators.isValidAadhaar("999999999999"));
        assertFalse(DetectionValidators.isValidAadhaar("123456789012"));
    }

    @Test
    void rejectsShortAndLongValues() {
        assertFalse(DetectionValidators.isValidAadhaar("12345678901"));
        assertFalse(DetectionValidators.isValidAadhaar("1234567890123"));
        assertFalse(DetectionValidators.isValidAadhaar("1234 1234 123"));
    }

    @Test
    void rejectsRandomTwelveDigitNumbers() {
        assertFalse(DetectionValidators.isValidAadhaar("111111111111"));
        assertFalse(DetectionValidators.isValidAadhaar("987654321098"));
    }

    @Test
    void creditCardValidatorRegression() {
        assertTrue(DetectionValidators.isValidCreditCard("4111111111111111"));
        assertFalse(DetectionValidators.isValidCreditCard("4012-8888-8888-1882"));
    }

    @Test
    void acceptsValidGstinWithChecksum() {
        assertTrue(DetectionValidators.isValidGstin("27AAPFU0939F1ZV"));
        assertTrue(DetectionValidators.isValidGstin("09AAAUP8175A1ZG"));
        assertTrue(DetectionValidators.isValidGstin("29AAACB1234C1ZB"));
        assertTrue(DetectionValidators.isValidGstin("27aapfu0939f1zv"));
    }

    @Test
    void rejectsInvalidGstinChecksum() {
        assertFalse(DetectionValidators.isValidGstin("29ABCDE1234F1Z5"));
        assertFalse(DetectionValidators.isValidGstin("27AAPFU0939F1ZA"));
    }

    @Test
    void rejectsInvalidGstinStructure() {
        assertFalse(DetectionValidators.isValidGstin("991234567890123"));
        assertFalse(DetectionValidators.isValidGstin("27AAPFU0939F1Z"));
        assertFalse(DetectionValidators.isValidGstin("27AAPFU0939F1ZY"));
    }

    @Test
    void acceptsValidPanHolderTypes() {
        assertTrue(DetectionValidators.isValidPan("ABCPA1234F"));
        assertTrue(DetectionValidators.isValidPan("AAACB5678D"));
        assertTrue(DetectionValidators.isValidPan("abcpa1234f"));
    }

    @Test
    void rejectsInvalidPanHolderTypes() {
        assertFalse(DetectionValidators.isValidPan("ABCDE1234F"));
        assertFalse(DetectionValidators.isValidPan("ABCDX1234F"));
        assertFalse(DetectionValidators.isValidPan("ABCD1234F"));
    }

    @Test
    void acceptsValidTanStructure() {
        assertTrue(DetectionValidators.isValidTan("DELM12345L"));
        assertTrue(DetectionValidators.isValidTan("MUMD12345F"));
        assertTrue(DetectionValidators.isValidTan("delm12345l"));
    }

    @Test
    void rejectsInvalidTanStructure() {
        assertFalse(DetectionValidators.isValidTan("ABCD00000E"));
        assertFalse(DetectionValidators.isValidTan("ABCD1234E"));
        assertFalse(DetectionValidators.isValidTan("ABCD123456E"));
    }

    @Test
    void acceptsValidCinStructure() {
        assertTrue(DetectionValidators.isValidCin("L17110MH1973PLC019786"));
        assertTrue(DetectionValidators.isValidCin("U72200KA2021PTC123456"));
    }

    @Test
    void rejectsInvalidCinStructure() {
        assertFalse(DetectionValidators.isValidCin("X17110MH1973PLC019786"));
        assertFalse(DetectionValidators.isValidCin("L17110XX1973PLC019786"));
        assertFalse(DetectionValidators.isValidCin("L17110MH1973ZZZ019786"));
        assertFalse(DetectionValidators.isValidCin("L17110MH1973PLC01978"));
    }

    @Test
    void acceptsValidInternalIdentifierValues() {
        assertTrue(DetectionValidators.isValidInternalIdentifier("E123456"));
        assertTrue(DetectionValidators.isValidInternalIdentifier("EMP000987"));
        assertTrue(DetectionValidators.isValidInternalIdentifier("778899"));
        assertTrue(DetectionValidators.isValidInternalIdentifier("rajesh.singh"));
        assertTrue(DetectionValidators.isValidInternalIdentifier("employee01"));
    }

    @Test
    void rejectsInvalidInternalIdentifierValues() {
        assertFalse(DetectionValidators.isValidInternalIdentifier("12"));
        assertFalse(DetectionValidators.isValidInternalIdentifier("1234"));
        assertFalse(DetectionValidators.isValidInternalIdentifier(""));
        assertFalse(DetectionValidators.isValidInternalIdentifier("ab"));
    }
}
