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
    void rejectsUuidLikeCreditCardFalsePositives() {
        assertFalse(DetectionValidators.isValidCreditCard("09317399-8980-4718"));
        assertFalse(DetectionValidators.isValidCreditCard("74185988-6536-4834"));
        assertFalse(DetectionValidators.isValidCreditCard("8232-212553142849"));
    }

    @Test
    void acceptsValidPhoneFormats() {
        assertTrue(DetectionValidators.isValidPhone("+91-9876543210"));
        assertTrue(DetectionValidators.isValidPhone("9876543210"));
        assertTrue(DetectionValidators.isValidPhone("555-123-4567"));
        assertTrue(DetectionValidators.isValidPhone("+1 (555) 123-4567"));
    }

    @Test
    void rejectsDecimalAndBareDigitPhoneFalsePositives() {
        assertFalse(DetectionValidators.isValidPhone("0.11000000"));
        assertFalse(DetectionValidators.isValidPhone("616250959298"));
        assertFalse(DetectionValidators.isValidPhone("100000000"));
        assertFalse(DetectionValidators.isValidPhone("918897478998"));
        assertFalse(DetectionValidators.isValidPhone("11.00000000"));
        assertFalse(DetectionValidators.isValidPhone("7004-4915-8475"));
    }

    @Test
    void acceptsHashedPasswordValues() {
        assertTrue(DetectionValidators.isValidPassword(
                "argon2$argon2i$v=19$m=512,t=2,p=2$NURSRFVIY0lGOWI0$x3MyMoJ5B30g4ZEVLmy6uA"));
        assertTrue(DetectionValidators.isValidPassword("admin123"));
    }

    @Test
    void rejectsSourceCodePasswordFalsePositives() {
        assertFalse(DetectionValidators.isValidPassword("forms.CharField("));
        assertFalse(DetectionValidators.isValidPassword("dt.now()"));
        assertFalse(DetectionValidators.isValidPassword("self.cleaned_data["));
        assertFalse(DetectionValidators.isValidPassword("request.POST["));
        assertFalse(DetectionValidators.isValidPassword("settings.LAM_START_DATE"));
    }

    @Test
    void rejectsLoopbackIpAddresses() {
        assertFalse(DetectionValidators.isValidIpAddress("127.0.0.1"));
        assertFalse(DetectionValidators.isValidIpAddress("0.0.0.0"));
        assertTrue(DetectionValidators.isValidIpAddress("192.168.1.10"));
        assertTrue(DetectionValidators.isValidIpAddress("10.0.2.2"));
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

    @Test
    void acceptsValidAwsAccessKeys() {
        assertTrue(DetectionValidators.isValidAwsAccessKey("AKIAIOSFODNN7EXAMPLE"));
        assertTrue(DetectionValidators.isValidAwsAccessKey("ASIAIOSFODNN7EXAMPLE"));
    }

    @Test
    void rejectsInvalidAwsAccessKeys() {
        assertFalse(DetectionValidators.isValidAwsAccessKey("AKIAIOSFODNN7EXAMPL"));
        assertFalse(DetectionValidators.isValidAwsAccessKey("BKIAIOSFODNN7EXAMPLE"));
    }

    @Test
    void acceptsValidGoogleApiKeys() {
        assertTrue(DetectionValidators.isValidGoogleApiKey("AIzaSyDaGmWKa4Js4Z9jR83EIBbn15edIbZht09"));
    }

    @Test
    void rejectsInvalidGoogleApiKeys() {
        assertFalse(DetectionValidators.isValidGoogleApiKey("AIzaSyDaGmWKa4Js4Z9jR83EIBbn15edIbZht0"));
        assertFalse(DetectionValidators.isValidGoogleApiKey("BIzaSyDaGmWKa4Js4Z9jR83EIBbn15edIbZht09"));
    }

    @Test
    void acceptsValidEnterpriseCredentials() {
        assertTrue(DetectionValidators.isValidEnterpriseCredential("ghp_1234567890abcdefghijklmnopqrstuvwxyz"));
        assertTrue(DetectionValidators.isValidEnterpriseCredential("xoxb-1234567890-abcdefghij-abcdefghijklmnopqrstuvwx"));
    }

    @Test
    void rejectsShortEnterpriseCredentials() {
        assertFalse(DetectionValidators.isValidEnterpriseCredential("short"));
    }

    @Test
    void acceptsValidAssignmentValues() {
        assertTrue(DetectionValidators.isValidAssignmentValue("oauth_access_value"));
        assertTrue(DetectionValidators.isValidAssignmentValue("xyz"));
    }

    @Test
    void rejectsShortAssignmentValues() {
        assertFalse(DetectionValidators.isValidAssignmentValue("ab"));
    }

    @Test
    void acceptsValidSshPublicKeys() {
        assertTrue(DetectionValidators.isValidSshPublicKey(
                "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQC7vbqajDhA0Z8Z"));
        assertTrue(DetectionValidators.isValidSshPublicKey(
                "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIG7vbqajDhA0Z8Z"));
        assertTrue(DetectionValidators.isValidSshPublicKey(
                "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA\n-----END PUBLIC KEY-----"));
    }

    @Test
    void rejectsInvalidSshPublicKeys() {
        assertFalse(DetectionValidators.isValidSshPublicKey("ssh-rsa short"));
        assertFalse(DetectionValidators.isValidSshPublicKey("ssh-dss AAAAB3NzaC1yc2E"));
    }
}
