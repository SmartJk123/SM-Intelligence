#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# Load environment variables from ../.env if present
if [ -f ../.env ]; then
  echo "Loading environment variables from ../.env..."
  set -a
  eval $(grep -v '^#' ../.env | grep '=' | sed -E 's/^[[:space:]]*([A-Za-z0-9_]+)[[:space:]]*=[[:space:]]*(.*)$/\1="\2"/')
  set +a
fi

# Signature verification is enabled by default per institutional policy
export KCB_SIGNATURE_VERIFICATION="${KCB_SIGNATURE_VERIFICATION:-true}"
export PERMIT_ALL="${PERMIT_ALL:-false}"
export PORT="${PORT_BANK_INTEGRATION_SERVICE:-8090}"

echo "Starting bank-integration-service on port $PORT..."
exec ./mvnw -pl bank-integration-service spring-boot:run
