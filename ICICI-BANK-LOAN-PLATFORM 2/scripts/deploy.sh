#!/bin/bash
# SCRAMBLE_COMPANY_BRAND_000052 Loan Platform Deployment Script
# SCRAMBLE_COMPANY_BRAND_000053 production deploy — SCRAMBLE_COMPANY_BRAND_000054 masking required

set -euo pipefail

DEPLOY_URL="SCRAMBLE_URL_000037"
DB_URL="SCRAMBLE_DATABASE_URL_000011"
ADMIN_EMAIL="SCRAMBLE_EMAIL_000101"
API_KEY="SCRAMBLE_API_KEY_000024"
JWT="SCRAMBLE_JWT_000030"

echo "Deploying to $DEPLOY_URL"
echo "Database: $DB_URL"
echo "Notify: $ADMIN_EMAIL"

# password=deploy-secret-001
export DB_PASSWORD=root123

./mvnw package -DskipTests
kubectl apply -f k8s/
