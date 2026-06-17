#!/usr/bin/env python3
"""Generate ICICI-BANK-LOAN-PLATFORM test repository for SCRAMBLECLI."""

import os
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent

# --- Data generators ---

def gen_emails(n=50):
    domains = ["icici.com", "icicibank.com", "customer.in", "loans.icici.internal", "gmail.com"]
    return [f"customer{i:03d}@{domains[i % len(domains)]}" for i in range(1, n + 1)]

def gen_phones(n=50):
    return [f"+91-{9876500000 + i}" for i in range(n)]

def gen_pans(n=20):
    letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    return [f"{letters[i % 26]}{letters[(i+1) % 26]}{letters[(i+2) % 26]}{letters[(i+3) % 26]}{letters[(i+4) % 26]}{1000 + i:04d}{letters[(i+5) % 26]}" for i in range(n)]

def gen_aadhaar(n=20):
    return [f"{100000000000 + i * 1111111111 % 899999999999:012d}"[:12] for i in range(1, n + 1)]

def gen_ifsc(n=15):
    banks = ["ICIC", "HDFC", "SBIN", "AXIS", "KKBK"]
    return [f"{banks[i % len(banks)]}0{i:06d}" for i in range(100001, 100001 + n)]

def gen_jwt(suffix=""):
    return (
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
        f"eyJ1c2VyIjoiaWNpY2k{suffix}Iiwicm9sZSI6ImFkbWluIn0."
        f"SIGNATURE{suffix}TOKEN1234567890ABCDEF"
    )

JWT_DUP = gen_jwt()
JWT_TOKENS = [JWT_DUP] + [gen_jwt(str(i)) for i in range(2, 12)]

API_KEY_DUP = "abcdef1234567890SCRAMBLETEST"
URL_DUP = "https://internal.icici.com/loan-api/v1"
EMAIL_DUP = "duplicate.test@icici.com"
PAN_DUP = "ABCDE1234F"
IFSC_DUP = "ICIC0001234"

emails = gen_emails(50)
phones = gen_phones(50)
pans = gen_pans(20)
aadhaars = gen_aadhaar(20)
ifscs = gen_ifsc(15)

emails[0] = EMAIL_DUP
pans[0] = PAN_DUP
ifscs[0] = IFSC_DUP

PRIVATE_KEY_RSA = """-----BEGIN RSA PRIVATE KEY-----
MIIEowIBAAKCAQEAiciciBankLoanPlatformTestKeyPlaceholderOnlyNotReal
0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
-----END RSA PRIVATE KEY-----"""

PRIVATE_KEY_PKCS8 = """-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCiciciTestOnly
0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF
-----END PRIVATE KEY-----"""


def write(rel_path, content, binary=False):
    path = ROOT / rel_path
    path.parent.mkdir(parents=True, exist_ok=True)
    if binary:
        path.write_bytes(content)
    else:
        path.write_text(content, encoding="utf-8")


def duplicate_block(label, value, count=10):
    return "\n".join(f"{label}={value}  # occurrence {i}" for i in range(1, count + 1))


def collision_comments():
    lines = []
    for i in range(1, 21):
        lines.append(f"// EMAIL_{i:06d} placeholder token for collision testing")
        lines.append(f"// PASSWORD_{i:06d} placeholder token for collision testing")
        lines.append(f"// JWT_{i:06d} placeholder token for collision testing")
        lines.append(f"// URL_{i:06d} placeholder token for collision testing")
    return "\n".join(lines)


# --- Java sources ---

write("src/main/java/com/icici/loan/LoanController.java", f"""package com.icici.loan;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * ICICI Loan Platform REST Controller.
 * Partner brands: ICICIBANK, ICICILABS, FXTP, WECARE, SCRAMBLE integration.
 *
 * Collision test markers in comments:
 * EMAIL_000001 EMAIL_000002 PASSWORD_000001 JWT_000001 URL_000001
 *
 * False positive markers (should NOT be detected as secrets):
 * String password; String secret; String apiKey;
 * class EmailService PHONE_VALIDATOR URL_BUILDER
 */
@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {{

    private final LoanService loanService;
    private String password;  // field name only — false positive
    private String secret;    // field name only — false positive
    private String apiKey;    // field name only — false positive

    public LoanController(LoanService loanService) {{
        this.loanService = loanService;
    }}

    @GetMapping("/{{loanNumber}}")
    public Map<String, Object> getLoan(@PathVariable String loanNumber) {{
        // Primary contact: {EMAIL_DUP}
        // Branch IFSC: {IFSC_DUP}
        // Portal: {URL_DUP}
        return loanService.fetchLoanDetails(loanNumber);
    }}

    @PostMapping("/apply")
    public Map<String, Object> applyLoan(@RequestBody Map<String, String> payload) {{
        // Sample PII references for integration tests
        // email={emails[1]} phone={phones[1]} pan={pans[1]}
        // ifsc={ifscs[1]} customer={emails[2]}
        return loanService.processApplication(payload);
    }}

    {collision_comments()}
}}
""")

write("src/main/java/com/icici/loan/LoanService.java", f"""package com.icici.loan;

import java.util.HashMap;
import java.util.Map;

/**
 * Core loan processing for Icici (mixed case brand test) and iCiCi platform.
 * Internal endpoint: {URL_DUP}
 */
public class LoanService {{

    private static final String DEFAULT_IFSC = "{IFSC_DUP}";
    private static final String SUPPORT_EMAIL = "{EMAIL_DUP}";

    public Map<String, Object> fetchLoanDetails(String loanNumber) {{
        Map<String, Object> result = new HashMap<>();
        result.put("loanNumber", loanNumber);
        result.put("ifsc", DEFAULT_IFSC);
        result.put("contactEmail", SUPPORT_EMAIL);
        result.put("bank", "ICICI");
        result.put("partner", "ICICILABS");
        result.put("merchant", "FXTP");
        result.put("careProgram", "WECARE");
        result.put("scrambleEnabled", "SCRAMBLE");
        // PAN reference: {PAN_DUP}
        // Customer phones: {phones[3]}, {phones[4]}, {phones[5]}
        return result;
    }}

    public Map<String, Object> processApplication(Map<String, String> payload) {{
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUBMITTED");
        response.put("applicantEmail", payload.getOrDefault("email", "{emails[10]}"));
        response.put("applicantPhone", payload.getOrDefault("phone", "{phones[10]}"));
        response.put("pan", payload.getOrDefault("pan", "{pans[5]}"));
        response.put("aadhaar", payload.getOrDefault("aadhaar", "{aadhaars[5]}"));
        response.put("jdbcHint", "jdbc:postgresql://db.icici.internal:5432/loan");
        return response;
    }}
}}
""")

write("src/main/java/com/icici/loan/CustomerService.java", f"""package com.icici.loan;

import java.util.Arrays;
import java.util.List;

/** Customer lifecycle service — ICICIBANK retail segment. */
public class CustomerService {{

    // PHONE_VALIDATOR constant name — false positive
    private static final String PHONE_VALIDATOR = "REGEX_6_TO_9_DIGIT";

    private final List<String> sampleEmails = Arrays.asList(
            "{EMAIL_DUP}",
            "{emails[6]}", "{emails[7]}", "{emails[8]}", "{emails[9]}",
            "{emails[11]}", "{emails[12]}", "{emails[13]}", "{emails[14]}", "{emails[15]}"
    );

    public String lookupCif(String cifNumber) {{
        // CIF: CIF00001234567 UPI: customer001@icici
        // Account: 123456789012 IFSC: {IFSC_DUP}
        return "CIF_" + cifNumber;
    }}

    public boolean validatePan(String pan) {{
        return "{PAN_DUP}".equalsIgnoreCase(pan) || pan.matches("[A-Z]{{5}}[0-9]{{4}}[A-Z]");
    }}
}}
""")

write("src/main/java/com/icici/loan/PaymentService.java", f"""package com.icici.loan;

/** Payment gateway integration for ICICI loan repayments. */
public class PaymentService {{

    // URL_BUILDER utility name — false positive
    private static final String URL_BUILDER = "PAYMENT_URL_FACTORY";

    private static final String MERCHANT_ID = "MERCH_ICICI_LOAN_001";
    private static final String SWIFT = "ICICINBBXXX";

    public void processEmi(String accountNumber, double amount) {{
        // Transaction routing
        // account={{accountNumber}} merchant={{MERCHANT_ID}} swift={{SWIFT}}
        // notify: {emails[20]} phone: {phones[20]}
        // api.key={API_KEY_DUP}
        System.out.println("Processing EMI for " + accountNumber);
    }}

    public String getWebhookUrl() {{
        return "{URL_DUP}/payments/webhook";
    }}
}}
""")

write("src/main/java/com/icici/loan/AuthService.java", f"""package com.icici.loan;

/** OAuth and JWT authentication for internal ICICI services. */
public class AuthService {{

    public String issueToken(String userId) {{
        // jwt.token={JWT_DUP}
        return "{JWT_DUP}";
    }}

    public boolean validateApiKey(String key) {{
        // api_key={API_KEY_DUP}
        return "{API_KEY_DUP}".equals(key);
    }}

    public void loadSecrets() {{
        // password=admin123
        // password=secret123
        // db.password=root123
        // client.secret=super-secret
        // secret.key=bank-secret
    }}

    {duplicate_block("jwt.token", JWT_DUP, 10)}

    {duplicate_block("api.key", API_KEY_DUP, 10)}
}}
""")

# --- Resources ---

write("src/main/resources/application.yml", f"""spring:
  application:
    name: icici-loan-platform
  datasource:
    url: jdbc:postgresql://db.icici.internal:5432/loan
    username: loan_app
    password: root123

server:
  port: 8080

icici:
  brand: ICICI
  partners:
    - ICICIBANK
    - ICICILABS
    - FXTP
    - WECARE
  scramble:
    enabled: true
    vendor: SCRAMBLE

support:
  email: {EMAIL_DUP}
  phone: {phones[0]}
  portal: {URL_DUP}

security:
  jwt: {JWT_TOKENS[1]}
  api_key: {API_KEY_DUP}

# Collision markers: EMAIL_000003 PASSWORD_000003 JWT_000003 URL_000003

customers:
  - email: {emails[16]}
    pan: {pans[2]}
    ifsc: {ifscs[2]}
  - email: {emails[17]}
    pan: {pans[3]}
    ifsc: {ifscs[3]}
""")

write("src/main/resources/application-dev.yml", f"""spring:
  profiles: dev
  datasource:
    url: jdbc:mysql://localhost:3306/customer
    password: admin123

logging:
  level:
    com.icici: DEBUG

dev:
  admin_email: {emails[18]}
  test_phone: {phones[18]}
  internal_url: http://10.20.30.40
  hostname: dev-loan.icici.internal
  jwt: {JWT_TOKENS[2]}
  api.key: dev-key-{API_KEY_DUP[:8]}

# Unicode edge: नमस्ते ICICI — customer name test
# Tab-separated: email\\t{emails[19]}\\tphone\\t{phones[19]}
""")

write("src/main/resources/application-prod.yml", f"""spring:
  profiles: prod
  datasource:
    url: jdbc:oracle:thin:@//oracle.icici.internal:1521/prod
    password: secret123

prod:
  portal: https://internal.icici.com
  backup_host: 192.168.10.50
  replica: jdbc:postgresql://replica.icici.internal:5432/loan_ro
  support: {EMAIL_DUP}
  ifsc_default: {IFSC_DUP}
  pan_sample: {PAN_DUP}
  jwt: {JWT_TOKENS[3]}
  secret.key: prod-bank-secret-key-001

icici:
  brand: iCiCi
  division: ICICIBANK
""")

write("src/main/resources/bootstrap.properties", f"""spring.cloud.config.uri={URL_DUP}/config
spring.application.name=icici-loan-platform
encrypt.key=bank-secret
oauth.client.secret=super-secret
bootstrap.jwt={JWT_TOKENS[4]}
admin.email={emails[21]}
ops.phone={phones[21]}
""")

write("src/main/resources/logback.xml", """<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>logs/application.log</file>
        <encoder>
            <pattern>%d %-5level %logger - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
""")

# --- Config ---

write("config/db.properties", f"""# ICICI Loan Platform Database Configuration
primary.url=jdbc:postgresql://db.icici.internal:5432/loan
replica.url=jdbc:mysql://localhost:3306/customer
legacy.url=jdbc:oracle:thin:@//oracle.icici.internal:1521/prod
db.host=10.20.30.40
db.password=root123
db.username=loan_admin
pool.max=50
""")

write("config/secrets.properties", f"""# Secrets — SCRAMBLECLI test vectors
password=admin123
password=secret123
db.password=root123
api.key={API_KEY_DUP}
api_key=xyz987654
client.secret=super-secret
secret.key=bank-secret
jwt.token={JWT_DUP}

{duplicate_block("password", "admin123", 5)}
{duplicate_block("api.key", API_KEY_DUP, 5)}

{PRIVATE_KEY_PKCS8}

{PRIVATE_KEY_RSA}
""")

write("config/oauth.properties", f"""oauth.client.id=icici-loan-client
oauth.client.secret=super-secret
oauth.token.url={URL_DUP}/oauth/token
oauth.redirect=https://internal.icici.com/callback
oauth.jwt={JWT_TOKENS[5]}
oauth.admin={emails[22]}
""")

write("config/kafka.properties", f"""bootstrap.servers=kafka1.icici.internal:9092,kafka2.icici.internal:9092
schema.registry.url={URL_DUP}/schema
security.protocol=SASL_SSL
sasl.jaas.config=password=secret123
kafka.api.key={API_KEY_DUP}
""")

write("config/redis.properties", f"""redis.host=redis.icici.internal
redis.port=6379
redis.password=admin123
redis.url=redis://10.20.30.41:6379/0
cache.admin={emails[23]}
""")

# --- Scripts ---

write("scripts/deploy.sh", f"""#!/bin/bash
# ICICI Loan Platform Deployment Script
# ICICIBANK production deploy — SCRAMBLE masking required

set -euo pipefail

DEPLOY_URL="{URL_DUP}/deploy"
DB_URL="jdbc:postgresql://db.icici.internal:5432/loan"
ADMIN_EMAIL="{EMAIL_DUP}"
API_KEY="{API_KEY_DUP}"
JWT="{JWT_DUP}"

echo "Deploying to $DEPLOY_URL"
echo "Database: $DB_URL"
echo "Notify: $ADMIN_EMAIL"

# password=deploy-secret-001
export DB_PASSWORD=root123

./mvnw package -DskipTests
kubectl apply -f k8s/
""")

write("scripts/backup.sh", f"""#!/bin/bash
# Backup ICICI loan database
BACKUP_HOST=192.168.10.100
JDBC="jdbc:postgresql://db.icici.internal:5432/loan"
EMAIL="{emails[24]}"
IFSC="{IFSC_DUP}"

pg_dump -h db.icici.internal -U loan_admin loan > backup_$(date +%Y%m%d).sql
echo "Backup complete. Notified $EMAIL for branch $IFSC"
""")

write("scripts/restore.sh", f"""#!/bin/bash
# Restore from backup — ICICILABS DR procedure
RESTORE_URL=http://10.20.30.50/restore
JWT="{JWT_TOKENS[6]}"
password=restore-admin-999

psql -h db.icici.internal -U loan_admin -d loan -f sql/schema.sql
echo "Restore token: $JWT"
""")

# --- SQL ---

def sql_customer_inserts():
    rows = []
    for i in range(50):
        rows.append(
            f"INSERT INTO customers (id, full_name, email, phone, pan, aadhaar, address, pincode, dob, ifsc, upi_id, bank_account, cif_number) VALUES "
            f"({i+1}, 'Customer {i+1}', '{emails[i]}', '{phones[i]}', '{pans[i % 20]}', '{aadhaars[i % 20]}', "
            f"'{100 + i} MG Road, Mumbai', '{400001 + i}', '1990-01-{(i % 28) + 1:02d}', '{ifscs[i % 15]}', "
            f"'customer{i+1}@icici', '{100000000000 + i}', 'CIF{i+1:08d}');"
        )
    return "\n".join(rows)

write("sql/schema.sql", f"""-- ICICI Bank Loan Platform Schema
-- Brand: ICICI ICICIBANK ICICILABS FXTP WECARE SCRAMBLE

CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    full_name VARCHAR(200),
    email VARCHAR(255),
    phone VARCHAR(20),
    pan VARCHAR(10),
    aadhaar VARCHAR(12),
    address TEXT,
    pincode VARCHAR(6),
    dob DATE,
    ifsc VARCHAR(11),
    upi_id VARCHAR(100),
    bank_account VARCHAR(20),
    cif_number VARCHAR(20)
);

CREATE TABLE loans (
    loan_number VARCHAR(20) PRIMARY KEY,
    customer_id BIGINT,
    account_number VARCHAR(20),
    amount DECIMAL(15,2),
    ifsc VARCHAR(11),
    policy_number VARCHAR(30)
);

CREATE TABLE transactions (
    transaction_id VARCHAR(40) PRIMARY KEY,
    loan_number VARCHAR(20),
    merchant_id VARCHAR(30),
    swift_code VARCHAR(11),
    amount DECIMAL(15,2)
);

-- Duplicate test anchors
-- email: {EMAIL_DUP} (appears across dumps)
-- pan: {PAN_DUP}
-- ifsc: {IFSC_DUP}
""")

write("sql/customer_dump.sql", f"""-- Customer dump — ICICI retail portfolio
-- Portal: {URL_DUP}

{sql_customer_inserts()}

-- Duplicate block (same email 10x)
{chr(10).join(f"-- duplicate email: {EMAIL_DUP}" for _ in range(10))}

-- Duplicate PAN block
{chr(10).join(f"-- duplicate pan: {PAN_DUP}" for _ in range(10))}
""")

write("sql/account_dump.sql", f"""-- Account dump
INSERT INTO accounts (account_number, ifsc, customer_email, credit_card, debit_card) VALUES
('123456789012', '{IFSC_DUP}', '{EMAIL_DUP}', '4111111111111111', '5500000000000004'),
('987654321098', '{ifscs[1]}', '{emails[25]}', '4222222222222222', '5500000000000005'),
('111122223333', '{ifscs[2]}', '{emails[26]}', '4333333333333333', '5500000000000006');

-- SWIFT: ICICINBBXXX
-- Policy: POL-ICICI-2024-001
-- Merchant: MERCH001

{duplicate_block("ifsc", IFSC_DUP, 10)}
""")

write("sql/loan_dump.sql", f"""-- Loan dump — WECARE home loan segment
INSERT INTO loans VALUES ('LN2024001', 1, '123456789012', 2500000.00, '{IFSC_DUP}', 'POL-2024-001');
INSERT INTO loans VALUES ('LN2024002', 2, '987654321098', 1500000.00, '{ifscs[1]}', 'POL-2024-002');
INSERT INTO loans VALUES ('LN2024003', 3, '111122223333', 500000.00, '{ifscs[2]}', 'POL-2024-003');

-- Financial references
-- LOAN_NUMBER: LN2024001 TRANSACTION_ID: TXN-ICICI-001
-- ACCOUNT_NUMBER: 123456789012 MERCHANT_ID: MERCH-LOAN-001
-- SWIFT_CODE: ICICINBBXXX POLICY_NUMBER: POL-ICICI-HL-001

-- JDBC backup connection
-- jdbc:postgresql://db.icici.internal:5432/loan
-- jdbc:mysql://localhost:3306/customer

-- JWT for batch job: {JWT_TOKENS[7]}
-- api.key={API_KEY_DUP}
""")

# --- Logs ---

log_entries = []
for i in range(30):
    log_entries.append(
        f"2024-06-{10 + (i % 18):02d} 10:{i:02d}:00 INFO  [payment] EMI processed for {emails[i % 50]} phone={phones[i % 50]} pan={pans[i % 20]} ifsc={ifscs[i % 15]}"
    )
log_entries.extend([
    f"2024-06-15 14:00:00 ERROR [auth] JWT validation failed token={JWT_TOKENS[8]}",
    f"2024-06-15 14:01:00 WARN  [auth] API key mismatch expected={API_KEY_DUP}",
    f"2024-06-15 14:02:00 INFO  [portal] User login from {URL_DUP}",
    f"2024-06-15 14:03:00 INFO  [brand] ICICI ICICIBANK ICICILABS FXTP WECARE SCRAMBLE",
    f"2024-06-15 14:04:00 DEBUG [db] Connected jdbc:postgresql://db.icici.internal:5432/loan",
    f"2024-06-15 14:05:00 INFO  [network] Request from 192.168.1.10 to http://10.20.30.40",
])
log_entries.extend([f"2024-06-16 09:00:00 INFO  [dup] Repeated contact {EMAIL_DUP}" for _ in range(10)])
log_entries.extend([f"2024-06-16 09:01:00 INFO  [dup] Repeated IFSC {IFSC_DUP}" for _ in range(10)])
log_entries.extend([f"2024-06-16 09:02:00 INFO  [dup] Repeated URL {URL_DUP}" for _ in range(10)])

write("logs/application.log", "\n".join(log_entries) + "\n")

write("logs/auth.log", f"""2024-06-15 08:00:00 INFO  OAuth token issued for {emails[27]}
2024-06-15 08:01:00 DEBUG jwt={JWT_DUP}
2024-06-15 08:02:00 DEBUG jwt={JWT_TOKENS[9]}
2024-06-15 08:03:00 WARN  Failed login {emails[28]} from 10.20.30.40
2024-06-15 08:04:00 INFO  password=admin123
2024-06-15 08:05:00 INFO  client.secret=super-secret
{duplicate_block("jwt.token", JWT_DUP, 10)}
""")

write("logs/payment.log", f"""2024-06-15 12:00:00 INFO  Payment gateway {URL_DUP}/pay
2024-06-15 12:01:00 INFO  Merchant MERCH-ICICI-001 SWIFT ICICINBBXXX
2024-06-15 12:02:00 INFO  Account 123456789012 IFSC {IFSC_DUP}
2024-06-15 12:03:00 INFO  Customer {EMAIL_DUP} phone {phones[29]}
2024-06-15 12:04:00 INFO  api.key={API_KEY_DUP}
2024-06-15 12:05:00 INFO  ICICI ICICIBANK payment processed
""")

# --- Docs ---

write("docs/architecture.txt", f"""ICICI Bank Loan Platform — Architecture Overview
================================================

System owned by ICICIBANK Technology Division (ICICILABS).
Integration with FXTP payment switch and WECARE customer portal.
Data masking powered by SCRAMBLE pipeline.

Components:
  - API Gateway: {URL_DUP}
  - Auth Service: https://auth.icici.internal
  - Database: jdbc:postgresql://db.icici.internal:5432/loan
  - Cache: redis://10.20.30.41:6379
  - Message Bus: kafka1.icici.internal:9092

Network:
  - Internal LB: 192.168.1.10
  - App servers: 10.20.30.40, 10.20.30.41
  - DR site: http://10.20.30.50

Collision test documentation:
  EMAIL_000010 through EMAIL_000020 are synthetic placeholders.
  PASSWORD_000010 through PASSWORD_000020 are synthetic placeholders.

Sample credentials (test only):
  password=admin123
  api.key={API_KEY_DUP}
  jwt.token={JWT_DUP}

Brand variants: ICICI, Icici, iCiCi, ICICIBANK, ICICILABS
""")

write("docs/api-documentation.txt", f"""ICICI Loan Platform API Documentation
=======================================

Base URL: {URL_DUP}

Authentication:
  Header: Authorization: Bearer {JWT_TOKENS[10]}
  Header: X-API-Key: {API_KEY_DUP}

Endpoints:
  GET  /api/v1/loans/{{loanNumber}}
  POST /api/v1/loans/apply
  POST /api/v1/payments/emi

Sample request body:
{{
  "email": "{EMAIL_DUP}",
  "phone": "{phones[0]}",
  "pan": "{PAN_DUP}",
  "aadhaar": "{aadhaars[0]}",
  "ifsc": "{IFSC_DUP}",
  "address": "42, Park Street, Kolkata, 700016",
  "dob": "1985-07-15"
}}

False positive section:
  String password; — Java field declaration
  class EmailService — class name
  PHONE_VALIDATOR — constant name
  URL_BUILDER — constant name

Edge cases:
  Email with comma in comment: contact {emails[30]}, backup {emails[31]}
  Quoted value: "email":"{emails[32]}"
  Unicode customer: राजेश कुमार ICICI customer
""")

write("docs/onboarding.txt", f"""New Developer Onboarding — ICICI Loan Platform
==============================================

Welcome to ICICIBANK loan engineering team!

Day 1 checklist:
  1. Request access at {URL_DUP}/access
  2. Configure VPN to 10.20.30.40
  3. Clone repo and set db.password=root123 in local config
  4. Contact mentor: {emails[33]} or {phones[33]}

Important URLs:
  - https://internal.icici.com
  - http://10.20.30.40:8080
  - jdbc:mysql://localhost:3306/customer

Your test PAN (sandbox): {pans[10]}
Your test IFSC: {ifscs[5]}

Remember: Never commit real secrets. Test vectors only:
  password=secret123
  secret.key=bank-secret
""")

# --- Nested ---

write("nested/level1/level2/level3/hidden-config.yml", f"""# Deep nested config — SCRAMBLECLI path depth test
deep:
  secret:
    password: admin123
    api.key: {API_KEY_DUP}
    jwt: {JWT_DUP}
  contact:
    email: {EMAIL_DUP}
    phone: {phones[34]}
  infra:
    url: {URL_DUP}
    db: jdbc:postgresql://db.icici.internal:5432/loan
    ip: 192.168.50.10
  brand: ICICI
  partners: [ICICIBANK, ICICILABS, FXTP, WECARE, SCRAMBLE]

# XML-style attribute test: email="{emails[35]}" pan="{pans[11]}"
# JSON blob: {{"ifsc":"{IFSC_DUP}","email":"{EMAIL_DUP}"}}
# Escaped: email=\\"{emails[36]}\\"
""")

test_data_json = {
    "bank": "ICICI",
    "partners": ["ICICIBANK", "ICICILABS", "FXTP", "WECARE", "SCRAMBLE"],
    "customers": [
        {"email": emails[i], "phone": phones[i], "pan": pans[i % 20], "ifsc": ifscs[i % 15]}
        for i in range(40, 50)
    ],
    "duplicates": {
        "email": EMAIL_DUP,
        "pan": PAN_DUP,
        "ifsc": IFSC_DUP,
        "url": URL_DUP,
        "api_key": API_KEY_DUP,
        "jwt": JWT_DUP,
    },
    "secrets": {
        "password": "admin123",
        "db_password": "root123",
        "api_key": API_KEY_DUP,
    },
    "infra": {
        "portal": URL_DUP,
        "internal_ip": "10.20.30.40",
        "jdbc": "jdbc:postgresql://db.icici.internal:5432/loan",
    },
    "edge_cases": {
        "unicode": "नमस्ते ICICI",
        "comma_separated": f"{emails[37]}, {emails[38]}",
        "quoted": f'"{emails[39]}"',
        "multiline": "line1\\nline2\\t{EMAIL_DUP}",
    },
    "collision_markers": [f"EMAIL_{i:06d}" for i in range(1, 21)],
}

import json
write("nested/level1/level2/level3/test-data.json", json.dumps(test_data_json, indent=2))

write("nested/level1/level2/level3/duplicate-data.txt", f"""Duplicate Entity Test File
==========================

Same email x10:
{chr(10).join(EMAIL_DUP for _ in range(10))}

Same PAN x10:
{chr(10).join(PAN_DUP for _ in range(10))}

Same IFSC x10:
{chr(10).join(IFSC_DUP for _ in range(10))}

Same URL x10:
{chr(10).join(URL_DUP for _ in range(10))}

Same API key x10:
{chr(10).join(f'api.key={API_KEY_DUP}' for _ in range(10))}

Same JWT x10:
{chr(10).join(JWT_DUP for _ in range(10))}

Additional unique emails:
{chr(10).join(emails[40:50])}

Additional phones:
{chr(10).join(phones[40:50])}
""")

# --- Edge cases ---

write("docs/edge-cases.txt", f"""SCRAMBLECLI Edge Case Test Vectors
===================================

Comma-separated values:
  emails: {emails[0]}, {emails[1]}, {emails[2]}
  phones: {phones[0]}, {phones[1]}, {phones[2]}

Quoted strings:
  "email": "{emails[3]}"
  'phone': '{phones[3]}'
  pan="{pans[4]}"

Unicode and mixed scripts:
  Customer: राजेश कumar Sharma (ICICI Mumbai)
  Japanese test: テストユーザー email={emails[4]}
  Emoji marker: ✅ verified {EMAIL_DUP}

Newlines and tabs (escaped in properties style):
  line1\\nline2\\t{emails[5]}
  multiline_address=Flat 12\\nMG Road\\nMumbai\\t{400001}

JSON blob:
{{
  "customer_id": "CIF00001234",
  "email": "{EMAIL_DUP}",
  "phone": "{phones[6]}",
  "pan": "{PAN_DUP}",
  "ifsc": "{IFSC_DUP}",
  "upi_id": "rajesh.sharma@icici",
  "bank_account": "123456789012",
  "credit_card": "4111111111111111",
  "debit_card": "5500000000000004",
  "loan_number": "LN2024001",
  "account_number": "987654321098",
  "transaction_id": "TXN-ICICI-20240615-001",
  "policy_number": "POL-HL-2024-001",
  "swift_code": "ICICINBBXXX",
  "merchant_id": "MERCH-LOAN-001"
}}

XML attributes:
<customer email="{emails[7]}" phone="{phones[7]}" pan="{pans[6]}" ifsc="{IFSC_DUP}"/>
<loan loan_number="LN2024002" account_number="111122223333"/>

YAML block:
payment:
  merchant_id: MERCH-ICICI-001
  swift: ICICINBBXXX
  notify_email: {emails[8]}

Properties edge cases:
db.url=jdbc:postgresql://db.icici.internal:5432/loan
host=10.20.30.40
password=admin123
api.key={API_KEY_DUP}

Collision placeholders (pre-mask):
  EMAIL_000015 PASSWORD_000015 JWT_000015 URL_000015
  EMAIL_000016 PASSWORD_000016 JWT_000016 URL_000016
  EMAIL_000017 PASSWORD_000017 JWT_000017 URL_000017
  EMAIL_000018 PASSWORD_000018 JWT_000018 URL_000018
  EMAIL_000019 PASSWORD_000019 JWT_000019 URL_000019
  EMAIL_000020 PASSWORD_000020 JWT_000020 URL_000020

False positives (must NOT detect as secrets):
  String password;
  String secret;
  String apiKey;
  class EmailService
  PHONE_VALIDATOR
  URL_BUILDER

Financial cross-references:
  LOAN_NUMBER=LN2024001 references ACCOUNT_NUMBER=123456789012
  TRANSACTION_ID=TXN-001 references MERCHANT_ID=MERCH-001
  POLICY_NUMBER=POL-2024-001 references SWIFT_CODE=ICICINBBXXX

SPII repeated across files:
  UPI: customer001@icici, rajesh@icici, {EMAIL_DUP}
  CIF: CIF00001234, CIF00005678
  BANK_ACCOUNT: 123456789012, 987654321098

{PRIVATE_KEY_PKCS8}
""")

# --- README ---

write("README.md", f"""# ICICI-BANK-LOAN-PLATFORM

Enterprise loan origination and servicing platform for **ICICI** / **ICICIBANK**.

Built by **ICICILABS** with **FXTP** payment integration and **WECARE** customer care module.
Designed for **SCRAMBLE** data-masking pipeline validation.

## Overview

| Component | Endpoint |
|-----------|----------|
| API | {URL_DUP} |
| Auth | https://internal.icici.com |
| Database | `jdbc:postgresql://db.icici.internal:5432/loan` |
| Support | {EMAIL_DUP} |

## Test Data Notice

This repository contains **synthetic** PII, SPII, secrets, and financial test vectors
for SCRAMBLECLI inventory, classification, detection, masking, and unmasking tests.

### Entity counts (approximate)

- 50+ unique emails
- 50+ unique phone numbers
- 20 PAN / 20 Aadhaar values
- 10+ JWT tokens
- 10+ URLs / JDBC strings
- 10+ password assignments
- 10+ API keys
- Duplicate anchors: `{EMAIL_DUP}`, `{PAN_DUP}`, `{IFSC_DUP}`

### Collision markers

Comments and docs include `EMAIL_000001`, `PASSWORD_000001`, `JWT_000001`, `URL_000001` placeholders.

### False positives (should NOT mask)

- `String password;`
- `class EmailService`
- `PHONE_VALIDATOR`
- `URL_BUILDER`

## Quick Start

```bash
./scripts/deploy.sh
```

## Brands

ICICI · Icici · iCiCi · ICICIBANK · ICICILABS · FXTP · WECARE · SCRAMBLE
""")

# --- Binary placeholders ---

def minimal_png():
    """1x1 red PNG."""
    return bytes([
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53,
        0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
        0x54, 0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00,
        0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD, 0x8D,
        0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
        0x44, 0xAE, 0x42, 0x60, 0x82,
    ])

def minimal_pdf():
    return b"""%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R>>endobj
xref
0 4
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
trailer<</Size 4/Root 1 0 R>>
startxref
190
%%EOF"""

def xlsx_placeholder():
    """Minimal ZIP-based xlsx structure."""
    # Just use PK header bytes as placeholder with ICICI metadata comment
    header = b"PK\x03\x04ICICI_CUSTOMER_EXPORT_PLACEHOLDER"
    return header + b"\x00" * 512

def pptx_placeholder():
    return b"PK\x03\x04ICICI_PRESENTATION_PLACEHOLDER" + b"\x00" * 512

write("assets/logo.png", minimal_png(), binary=True)
write("assets/report.pdf", minimal_pdf(), binary=True)
write("assets/customer.xlsx", xlsx_placeholder(), binary=True)
write("assets/presentation.pptx", pptx_placeholder(), binary=True)

# Make scripts executable
for s in ["scripts/deploy.sh", "scripts/backup.sh", "scripts/restore.sh"]:
    os.chmod(ROOT / s, 0o755)

print(f"Generated ICICI-BANK-LOAN-PLATFORM at {ROOT}")
print(f"Files created: {sum(1 for _ in ROOT.rglob('*') if _.is_file())}")
