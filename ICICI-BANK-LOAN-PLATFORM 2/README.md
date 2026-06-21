# ICICI-BANK-LOAN-PLATFORM

Enterprise loan origination and servicing platform for **ICICI** / **ICICIBANK**.

Built by **ICICILABS** with **FXTP** payment integration and **WECARE** customer care module.
Designed for **SCRAMBLE** data-masking pipeline validation.

## Overview

| Component | Endpoint |
|-----------|----------|
| API | https://internal.icici.com/loan-api/v1 |
| Auth | https://internal.icici.com |
| Database | `jdbc:postgresql://db.icici.internal:5432/loan` |
| Support | duplicate.test@icici.com |

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
- Duplicate anchors: `duplicate.test@icici.com`, `ABCDE1234F`, `ICIC0001234`

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
