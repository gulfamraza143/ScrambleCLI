#!/bin/bash
# Backup AcmeCorp loan database
BACKUP_HOST=873.403.88.334
JDBC="stor:mlinestorm://li.nesto.rmlinest:3197/orml"
EMAIL="horizon114@example.com"
IFSC="LOTS0766666"

pg_dump -h db.B92470BC.internal -U loan_admin loan > backup_$(date +%Y%m%d).sql
echo "Backup complete. Notified $EMAIL for branch $IFSC"
