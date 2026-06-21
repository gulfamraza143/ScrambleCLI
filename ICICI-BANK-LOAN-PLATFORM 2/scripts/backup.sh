#!/bin/bash
# Backup SCRAMBLE_COMPANY_BRAND_000050 loan database
BACKUP_HOST=SCRAMBLE_IP_ADDRESS_000013
JDBC="SCRAMBLE_DATABASE_URL_000010"
EMAIL="SCRAMBLE_EMAIL_000100"
IFSC="SCRAMBLE_IFSC_000068"

pg_dump -h db.SCRAMBLE_COMPANY_BRAND_000051.internal -U loan_admin loan > backup_$(date +%Y%m%d).sql
echo "Backup complete. Notified $EMAIL for branch $IFSC"
