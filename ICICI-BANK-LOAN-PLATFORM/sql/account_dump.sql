-- Account dump
INSERT INTO accounts (account_number, ifsc, customer_email, credit_card, debit_card) VALUES
('123456789012', 'ICIC0001234', 'duplicate.test@icici.com', '4111111111111111', '5500000000000004'),
('987654321098', 'SBIN0100002', 'customer026@icicibank.com', '4222222222222222', '5500000000000005'),
('111122223333', 'AXIS0100003', 'customer027@customer.in', '4333333333333333', '5500000000000006');

-- SWIFT: ICICINBBXXX
-- Policy: POL-ICICI-2024-001
-- Merchant: MERCH001

ifsc=ICIC0001234  # occurrence 1
ifsc=ICIC0001234  # occurrence 2
ifsc=ICIC0001234  # occurrence 3
ifsc=ICIC0001234  # occurrence 4
ifsc=ICIC0001234  # occurrence 5
ifsc=ICIC0001234  # occurrence 6
ifsc=ICIC0001234  # occurrence 7
ifsc=ICIC0001234  # occurrence 8
ifsc=ICIC0001234  # occurrence 9
ifsc=ICIC0001234  # occurrence 10
