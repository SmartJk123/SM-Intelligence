#!/usr/bin/env bash
set -e

# Automatically resolve the Git repository root
ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

echo "=========================================================="
echo "SM-Intelligence Fleet Builder & K3s Importer"
echo "Root Directory: ${ROOT_DIR}"
echo "=========================================================="

declare -A SERVICES=(
  ["smi-api-gateway"]="backend/Dockerfile.gateway"
  ["smi-identity-service"]="backend/Dockerfile.identity"
  ["smi-accounts-service"]="backend/Dockerfile.accounts"
  ["smi-transactions-service"]="backend/Dockerfile.transactions"
  ["smi-categories-service"]="backend/Dockerfile.categories"
  ["smi-budgets-service"]="backend/Dockerfile.budgets"
  ["smi-investments-service"]="backend/Dockerfile.investments"
  ["smi-forecasts-service"]="backend/Dockerfile.forecasts"
  ["smi-notifications-service"]="backend/Dockerfile.notifications"
  ["smi-audit-service"]="backend/Dockerfile.audit"
  ["smi-bank-integration-service"]="backend/Dockerfile.bank-integration"
  ["smi-assistant-service"]="backend/Dockerfile.assistant"
)

COUNT=1
TOTAL=${#SERVICES[@]}

for IMAGE_NAME in "${!SERVICES[@]}"; do
  DOCKERFILE="${SERVICES[$IMAGE_NAME]}"
  echo ""
  echo "----------------------------------------------------------"
  echo "[$COUNT/$TOTAL] Building: ${IMAGE_NAME} via ${DOCKERFILE}"
  echo "----------------------------------------------------------"
  
  # Build Docker image natively (ARM64 or x86_64)
  docker build -t "${IMAGE_NAME}:latest" -f "${DOCKERFILE}" backend/
  
  # If k3s is available, import directly into containerd
  if command -v k3s &>/dev/null; then
    echo "--> Importing ${IMAGE_NAME} into K3s containerd..."
    docker save "${IMAGE_NAME}:latest" | sudo k3s ctr images import -
    echo "--> SUCCESS: ${IMAGE_NAME} imported into K3s!"
  elif command -v kind &>/dev/null && kind get clusters | grep -q "^smi-cluster$"; then
    echo "--> Loading ${IMAGE_NAME} into Kind cluster smi-cluster..."
    kind load docker-image "${IMAGE_NAME}:latest" --name smi-cluster
    echo "--> SUCCESS: ${IMAGE_NAME} loaded into Kind!"
  else
    echo "--> SUCCESS: ${IMAGE_NAME} built in Docker!"
  fi
  
  COUNT=$((COUNT + 1))
done

echo ""
echo "=========================================================="
echo "🎉 ALL 12 MICROSERVICES BUILT & READY!"
echo "=========================================================="
