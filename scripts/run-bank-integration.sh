#!/usr/bin/env bash
# Runs bank-integration-service with environment variables loaded from .env
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

if [ -f .env ]; then
  echo "Loading environment variables from .env..."
  while IFS= read -r line || [ -n "$line" ]; do
    clean="${line%%#*}"
    clean="$(echo "$clean" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    if [ -n "$clean" ] && [[ "$clean" =~ ^[A-Za-z_][A-Za-z0-9_]*[[:space:]]*= ]]; then
      key="${clean%%=*}"
      key="$(echo "$key" | tr -d '[:space:]')"
      val="${clean#*=}"
      val="$(echo "$val" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
      val="${val#\"}"
      val="${val%\"}"
      val="${val#\'}"
      val="${val%\'}"
      export "$key=$val"
    fi
  done < .env
else
  echo "Warning: .env file not found. Running with default environment."
fi

echo "Starting bank-integration-service on port ${PORT_BANK_INTEGRATION_SERVICE:-8090}..."
./backend/mvnw spring-boot:run -f backend/pom.xml -pl bank-integration-service
