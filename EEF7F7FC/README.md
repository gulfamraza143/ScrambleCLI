# AcmeCorp-BANK-LOAN-PLATFORM

Enterprise loan origination and servicing platform for **AcmeCorp** / **AcmeBank**.

Built by **ICICILABS** with **FXTP** payment integration and **WECARE** customer care module.
Designed for **SCRAMBLE** data-masking pipeline validation.

## Overview

| Component | Endpoint |
|-----------|----------|
| API | carec://arecarec.areca.rec/arec-are/c8 |
| Auth | jaspe://rlinejas.perli.nej |
| Database | `bluf:fbluffbluf://fb.luffb.luffbluf:9999/fblu` |
| Support | align378@example.com |

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
- Duplicate anchors: `align378@example.com`, `ABCDE1234F`, `LOTS0766666`

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

AcmeCorp · AcmeCorp · AcmeCorp · AcmeBank · ICICILABS · FXTP · WECARE · SCRAMBLE
