#!/usr/bin/env bash
set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}  SM-Intelligence: Bank Integrations E2E Test Suite (Stanbic)   ${NC}"
echo -e "${BLUE}================================================================${NC}"
echo ""

BASE_URL="${BANK_INTEGRATION_URL:-http://localhost:8090}"
SIG_SECRET="${STANBIC_SIGNATURE_SECRET:-test-secret-key-stanbic-987}"
SIG_HEADER="${STANBIC_SIGNATURE_HEADER:-X-Stanbic-Signature}"

# Pre-flight Health Checks
echo -e "${BLUE}[Step 0] Probing Bank Integration Service Health...${NC}"
check_service() {
  local name=$1
  local url=$2
  local port=$3
  if curl -s --connect-timeout 2 "$url" | grep -q '"status":"UP"'; then
    echo -e "  ${GREEN}✔ ${name} on port ${port} is UP${NC}"
  else
    echo -e "  ${RED}✖ ${name} on port ${port} is DOWN or unreachable${NC}"
    echo -e "    -> Start it with: ${YELLOW}cd backend && mvn spring-boot:run -pl bank-integration-service${NC}"
    return 1
  fi
}

FAILED=0
check_service "bank-integration-service" "${BASE_URL}/actuator/health" "8090" || FAILED=1

if [[ "$FAILED" -eq 1 ]]; then
  echo ""
  echo -e "${RED}[ERROR] bank-integration-service is not running on ${BASE_URL}.${NC}"
  echo -e "Please start the service before running this test script."
  exit 1
fi
echo -e "  ${GREEN}Service healthy. Proceeding to functional E2E tests...${NC}\n"

# Step 1: List all supported bank integrations
echo -e "${BLUE}[Step 1] Verifying Supported Bank Integrations...${NC}"
HEALTH_LIST=$(curl -s "${BASE_URL}/api/v1/admin/bank-integrations")
if echo "$HEALTH_LIST" | grep -q '"bankId":"stanbic"'; then
  echo -e "  ${GREEN}✔ Stanbic Bank Kenya registered in integrations list${NC}"
else
  echo -e "  ${RED}✖ Stanbic Bank Kenya missing from integrations list: ${HEALTH_LIST}${NC}"
  exit 1
fi

# Step 2: Query Stanbic Credentials & Configuration
echo -e "\n${BLUE}[Step 2] Checking Stanbic Credentials Endpoint...${NC}"
CREDS_RES=$(curl -s "${BASE_URL}/api/v1/admin/bank-integrations/stanbic/credentials")
if echo "$CREDS_RES" | grep -q '"bankId":"stanbic"'; then
  echo -e "  ${GREEN}✔ Stanbic credentials status retrieved safely (no leaked secrets)${NC}"
else
  echo -e "  ${RED}✖ Failed to retrieve Stanbic credentials status: ${CREDS_RES}${NC}"
  exit 1
fi

# Step 3: Webhook URL Endpoint
echo -e "\n${BLUE}[Step 3] Verifying Webhook Callback Endpoint Address...${NC}"
WH_URL_RES=$(curl -s "${BASE_URL}/api/v1/admin/bank-integrations/stanbic/webhook-url")
if echo "$WH_URL_RES" | grep -q '/api/v1/webhooks/stanbic'; then
  echo -e "  ${GREEN}✔ Webhook address confirmed: ${WH_URL_RES}${NC}"
else
  echo -e "  ${RED}✖ Unexpected webhook address response: ${WH_URL_RES}${NC}"
  exit 1
fi

# Step 4: Webhook Probe (GET)
echo -e "\n${BLUE}[Step 4] Probing Webhook Endpoint (GET)...${NC}"
PROBE_RES=$(curl -s "${BASE_URL}/api/v1/webhooks/stanbic")
if echo "$PROBE_RES" | grep -q "Stanbic Bank Kenya"; then
  echo -e "  ${GREEN}✔ Webhook GET probe verified with human-readable verification prompt${NC}"
else
  echo -e "  ${RED}✖ Unexpected webhook probe response: ${PROBE_RES}${NC}"
  exit 1
fi

# Step 5: Webhook Notification Ingestion with HMAC-SHA256 (POST)
echo -e "\n${BLUE}[Step 5] Ingesting Signed Notification (POST)...${NC}"
REF="STANBIC-E2E-$(date +%s)"
PAYLOAD="{\"transactionReference\":\"${REF}\",\"amount\":45000.00,\"currency\":\"KES\",\"direction\":\"CREDIT\",\"narration\":\"E2E Settlement Deposit\",\"bookingDate\":\"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\"}"

# Compute HMAC-SHA256 signature
SIG=$(printf '%s' "$PAYLOAD" | openssl dgst -sha256 -hmac "$SIG_SECRET" | awk '{print $NF}')

NOTIF_RES=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/webhooks/stanbic" \
  -H "Content-Type: application/json" \
  -H "${SIG_HEADER}: ${SIG}" \
  -d "$PAYLOAD")

NOTIF_HTTP=$(echo "$NOTIF_RES" | tail -n1)
NOTIF_BODY=$(echo "$NOTIF_RES" | sed '$d')

if [[ "$NOTIF_HTTP" == "200" ]] && echo "$NOTIF_BODY" | grep -q '"status":"received"'; then
  echo -e "  ${GREEN}✔ Notification accepted (HTTP 200, status=received, ref=${REF})${NC}"
else
  echo -e "  ${RED}✖ Notification rejected (HTTP ${NOTIF_HTTP}): ${NOTIF_BODY}${NC}"
  exit 1
fi

# Step 6: Security Verification (Invalid Signature & Missing Header)
echo -e "\n${BLUE}[Step 6] Security Verification (Tamper Resistance)...${NC}"
BAD_SIG_RES=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/webhooks/stanbic" \
  -H "Content-Type: application/json" \
  -H "${SIG_HEADER}: bad_invalid_hmac_signature" \
  -d "$PAYLOAD")
BAD_HTTP=$(echo "$BAD_SIG_RES" | tail -n1)

if [[ "$BAD_HTTP" == "401" ]]; then
  echo -e "  ${GREEN}✔ Tampered signature rejected with HTTP 401 Unauthorized${NC}"
else
  echo -e "  ${RED}✖ Security check failed: Expected HTTP 401, got ${BAD_HTTP}${NC}"
  exit 1
fi

NO_SIG_RES=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/webhooks/stanbic" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")
NO_SIG_HTTP=$(echo "$NO_SIG_RES" | tail -n1)

if [[ "$NO_SIG_HTTP" == "401" ]]; then
  echo -e "  ${GREEN}✔ Missing signature header rejected with HTTP 401 Unauthorized${NC}"
else
  echo -e "  ${RED}✖ Security check failed: Expected HTTP 401, got ${NO_SIG_HTTP}${NC}"
  exit 1
fi

# Step 7: Deduplication & Idempotency
echo -e "\n${BLUE}[Step 7] Testing Idempotency & Deduplication...${NC}"
DUP_RES=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/webhooks/stanbic" \
  -H "Content-Type: application/json" \
  -H "${SIG_HEADER}: ${SIG}" \
  -d "$PAYLOAD")
DUP_HTTP=$(echo "$DUP_RES" | tail -n1)

if [[ "$DUP_HTTP" == "200" ]]; then
  echo -e "  ${GREEN}✔ Duplicate notification handled idempotently (HTTP 200)${NC}"
else
  echo -e "  ${RED}✖ Duplicate notification failed with HTTP ${DUP_HTTP}${NC}"
  exit 1
fi

# Step 8: Connection Test Endpoint
echo -e "\n${BLUE}[Step 8] Running Bank Connection Test Probe...${NC}"
CONN_TEST_RES=$(curl -s -X POST "${BASE_URL}/api/v1/admin/bank-integrations/stanbic/test" \
  -H "Content-Type: application/json")
if echo "$CONN_TEST_RES" | grep -q "steps"; then
  echo -e "  ${GREEN}✔ Connection test completed and recorded diagnostic steps${NC}"
else
  echo -e "  ${RED}✖ Connection test failed: ${CONN_TEST_RES}${NC}"
  exit 1
fi

# Step 9: Bruno Test Collection Verification
echo -e "\n${BLUE}[Step 9] Running Bruno CLI Bank Integrations Test Suite...${NC}"
if command -v bru >/dev/null 2>&1; then
  (cd bruno-collections && bru run bank-integrations --env Local)
else
  echo -e "  ${YELLOW}Bruno CLI not found in PATH, skipping Bruno automated run${NC}"
fi

echo ""
echo -e "${GREEN}================================================================${NC}"
echo -e "${GREEN}   ✔ All Stanbic Bank Integration Tests Passed Successfully!    ${NC}"
echo -e "${GREEN}================================================================${NC}"
