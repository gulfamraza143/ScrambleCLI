#!/bin/bash
# AcmeCorp Loan Platform Deployment Script
# AcmeBank production deploy — SCRAMBLE masking required

set -euo pipefail

DEPLOY_URL="cosmo://logycosm.ology.cos/molo-gyc/o4/smolog"
DB_URL="stor:mlinestorm://li.nesto.rmlinest:3197/orml"
ADMIN_EMAIL="align378@example.com"
API_KEY="evermo2468024602REEVERMOREEV"
JWT="haLcyOniXhALCyO2NiXhAlC6yON1IxhALCY4.onI3x6HaLcyonIXhA0lCyonix7hALC1YoNixHalCy8.ONIXHALCYONIXH9357913571ALCYON"

echo "Deploying to $DEPLOY_URL"
echo "Database: $DB_URL"
echo "Notify: $ADMIN_EMAIL"

# password=deploy-secret-001
export DB_PASSWORD=root123

./mvnw package -DskipTests
kubectl apply -f k8s/
