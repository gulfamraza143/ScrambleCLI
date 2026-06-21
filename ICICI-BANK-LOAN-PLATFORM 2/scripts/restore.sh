#!/bin/bash
# Restore from backup — SCRAMBLE_COMPANY_BRAND_000055 DR procedure
RESTORE_URL=SCRAMBLE_URL_000038
JWT="SCRAMBLE_JWT_000031"
password=SCRAMBLE_PASSWORD_000015

psql -h db.SCRAMBLE_COMPANY_BRAND_000056.internal -U loan_admin -d loan -f sql/schema.sql
echo "Restore token: $JWT"
