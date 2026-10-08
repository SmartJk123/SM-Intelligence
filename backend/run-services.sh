#!/usr/bin/env bash
set -e

# Change to backend directory
cd "$(dirname "$0")"

# Load environment variables from ../.env if present
if [ -f ../.env ]; then
  echo "Loading environment variables from ../.env..."
  set -a
  eval $(grep -v '^#' ../.env | grep '=' | sed -E 's/^[[:space:]]*([A-Za-z0-9_]+)[[:space:]]*=[[:space:]]*(.*)$/\1="\2"/')
  set +a
fi

# Generate JWT secret if not set
export JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48)}"

echo "========================================================"
echo "Starting Identity, Accounts, Transactions, Bank Integration, and Assistant services"
echo "Identity:         http://localhost:8081"
echo "Accounts:         http://localhost:8082"
echo "Transactions:     http://localhost:8083"
echo "Bank Integration: http://localhost:8090"
echo "Assistant (Raha): http://localhost:8091"
echo "Press Ctrl+C to stop all services."
echo "========================================================"

# Gracefully terminate background processes on Ctrl+C
trap 'echo ""; echo "Stopping all services..."; kill $(jobs -p) 2>/dev/null; exit' INT TERM EXIT

./mvnw -pl identity-service spring-boot:run &
./mvnw -pl accounts-service spring-boot:run &
./mvnw -pl transactions-service spring-boot:run &
./mvnw -pl bank-integration-service spring-boot:run &
./mvnw -pl assistant-service spring-boot:run &

wait
