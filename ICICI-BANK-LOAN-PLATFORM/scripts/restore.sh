#!/bin/bash
# Restore from backup — ICICILABS DR procedure
RESTORE_URL=http://10.20.30.50/restore
JWT="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2k7Iiwicm9sZSI6ImFkbWluIn0.SIGNATURE7TOKEN1234567890ABCDEF"
password=restore-admin-999

psql -h db.icici.internal -U loan_admin -d loan -f sql/schema.sql
echo "Restore token: $JWT"
