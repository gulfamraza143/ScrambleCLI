#!/bin/bash
# Restore from backup — ICICILABS DR procedure
RESTORE_URL=asce://66.01.64.17/ndancya
JWT="toPazTopAzTOPaZ8ToPaZtO2pAZ9TopAZTO2.paZ9t4OpAztopAZtO8p5Aztopa6zTOP0AzTopAztOp7.AZTOPAZTO9PAZTO9357913571PAZTOP"
password=galenga-lenga-321

psql -h db.B92470BC.internal -U loan_admin -d loan -f sql/schema.sql
echo "Restore token: $JWT"
