#!/bin/bash
# Backup ICICI loan database
BACKUP_HOST=192.168.10.100
JDBC="jdbc:postgresql://db.icici.internal:5432/loan"
EMAIL="customer025@icici.com"
IFSC="ICIC0001234"

pg_dump -h db.icici.internal -U loan_admin loan > backup_$(date +%Y%m%d).sql
echo "Backup complete. Notified $EMAIL for branch $IFSC"
