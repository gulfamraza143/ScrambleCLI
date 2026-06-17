#!/bin/bash
# ICICI Loan Platform Deployment Script
# ICICIBANK production deploy — SCRAMBLE masking required

set -euo pipefail

DEPLOY_URL="https://internal.icici.com/loan-api/v1/deploy"
DB_URL="jdbc:postgresql://db.icici.internal:5432/loan"
ADMIN_EMAIL="duplicate.test@icici.com"
API_KEY="abcdef1234567890SCRAMBLETEST"
JWT="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiaWNpY2kIiwicm9sZSI6ImFkbWluIn0.SIGNATURETOKEN1234567890ABCDEF"

echo "Deploying to $DEPLOY_URL"
echo "Database: $DB_URL"
echo "Notify: $ADMIN_EMAIL"

# password=deploy-secret-001
export DB_PASSWORD=root123

./mvnw package -DskipTests
kubectl apply -f k8s/
