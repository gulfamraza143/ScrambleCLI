-- AcmeCorp Bank Loan Platform Schema
-- Brand: AcmeCorp AcmeBank ICICILABS FXTP WECARE SCRAMBLE

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
-- email: align378@example.com (appears across dumps)
-- pan: ABCDE1234F
-- ifsc: LOTS0766666
