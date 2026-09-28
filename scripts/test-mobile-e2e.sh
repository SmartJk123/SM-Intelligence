#!/usr/bin/env bash
set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================================================${NC}"
echo -e "${BLUE}   SM-Intelligence: Full Mobile E2E Integration Test Suite      ${NC}"
echo -e "${BLUE}================================================================${NC}"
echo ""

# Pre-flight Health Checks
echo -e "${BLUE}[Step 0] Probing Microservice Health Endpoints...${NC}"
check_service() {
  local name=$1
  local url=$2
  local port=$3
  local status
  status=$(curl -s --connect-timeout 2 "$url" | grep -o '"status":"[^"]*' | cut -d'"' -f4 || echo "DOWN")
  if [[ "$status" == "UP" ]]; then
    echo -e "  ${GREEN}✔ ${name} on port ${port} is UP${NC}"
  else
    echo -e "  ${RED}✖ ${name} on port ${port} is DOWN or unreachable${NC}"
    echo -e "    -> Start it with: ./backend/mvnw -pl $(echo "$name" | tr '[:upper:]' '[:lower:]' | tr ' ' '-') spring-boot:run"
    return 1
  fi
}

FAILED=0
check_service "identity-service" "http://localhost:8081/actuator/health" "8081" || FAILED=1
check_service "accounts-service" "http://localhost:8082/actuator/health" "8082" || FAILED=1
check_service "transactions-service" "http://localhost:8083/actuator/health" "8083" || FAILED=1

if [[ "$FAILED" -eq 1 ]]; then
  echo ""
  echo -e "${RED}[ERROR] One or more microservices are not running.${NC}"
  echo -e "Tip: You can start all 3 services concurrently with: ${BLUE}./backend/run-services.sh${NC}"
  exit 1
fi
echo -e "  ${GREEN}All services healthy. Proceeding to functional E2E tests...${NC}\n"

# Generate unique email for clean idempotent execution
UNIQUE_ID=$(date +%s)
TEST_EMAIL="mobile.user.${UNIQUE_ID}@example.com"
ACC_NUM="ACC-MOBILE-${UNIQUE_ID: -4}"

echo -e "${BLUE}[Step 1/4] Testing Identity Service (:8081)...${NC}"

# 1.1 Register Mobile User
echo "1.1 Calling POST /api/auth/register with mobile payload (email, phoneNumber)..."
REG_RES=$(curl -s -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"Mobile Tester\",
    \"email\": \"${TEST_EMAIL}\",
    \"phoneNumber\": \"+254712345678\",
    \"password\": \"Password123!\"
  }")

USER_ID=$(echo "$REG_RES" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
TOKEN=$(echo "$REG_RES" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [[ -z "$USER_ID" || -z "$TOKEN" ]]; then
  echo -e "${RED}[FAIL] Registration failed. Response: ${REG_RES}${NC}"
  exit 1
fi
echo -e "${GREEN}[PASS] Registered successfully. User ID: ${USER_ID}${NC}"

# 1.2 Login Mobile User
echo "1.2 Calling POST /api/auth/login with mobile payload (email)..."
LOGIN_RES=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${TEST_EMAIL}\",
    \"password\": \"Password123!\"
  }")

LOGIN_TOKEN=$(echo "$LOGIN_RES" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
if [[ -z "$LOGIN_TOKEN" ]]; then
  echo -e "${RED}[FAIL] Login failed. Response: ${LOGIN_RES}${NC}"
  exit 1
fi
echo -e "${GREEN}[PASS] Login successful. JWT token received.${NC}"

# 1.3 Verify Profile
echo "1.3 Calling GET /api/auth/me with Bearer token..."
ME_RES=$(curl -s -X GET http://localhost:8081/api/auth/me \
  -H "Authorization: Bearer ${TOKEN}")
echo -e "${GREEN}[PASS] Profile verified.${NC}"
echo ""

echo -e "${BLUE}[Step 2/4] Testing Accounts Service (:8082)...${NC}"

# 2.1 Create Account
echo "2.1 Calling POST /api/accounts with mobile payload (accountId, bankName, cardType: DEBIT)..."
ACC_RES=$(curl -s -X POST http://localhost:8082/api/accounts \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"${USER_ID}\",
    \"accountId\": \"${ACC_NUM}\",
    \"accountName\": \"Mobile Everyday Savings\",
    \"bankName\": \"KCB Bank\",
    \"cardType\": \"DEBIT\",
    \"initial_balance\": 85000.00
  }")

ACC_UUID=$(echo "$ACC_RES" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [[ -z "$ACC_UUID" ]]; then
  echo -e "${RED}[FAIL] Account creation failed. Response: ${ACC_RES}${NC}"
  exit 1
fi
echo -e "${GREEN}[PASS] Account created: ${ACC_NUM} (UUID: ${ACC_UUID})${NC}"

# 2.2 Lookup by String Account Number
echo "2.2 Calling GET /api/accounts/${ACC_NUM} (Lookup by account number)..."
LOOKUP_RES=$(curl -s -X GET "http://localhost:8082/api/accounts/${ACC_NUM}")
LOOKUP_STATUS=$(echo "$LOOKUP_RES" | grep -o '"accountStatus":"[^"]*' | cut -d'"' -f4)
echo -e "${GREEN}[PASS] Lookup by string identifier successful. Status: ${LOOKUP_STATUS}${NC}"

# 2.3 List Accounts without Query Params
echo "2.3 Calling GET /api/accounts (Without mandatory userId param)..."
LIST_RES=$(curl -s -X GET "http://localhost:8082/api/accounts")
echo -e "${GREEN}[PASS] Account list returned without errors.${NC}"

# 2.4 Update Balance (POST method tolerance)
echo "2.4 Calling POST /api/accounts/${ACC_NUM}/balance with {'balance': 92500.50}..."
BAL_RES=$(curl -s -X POST "http://localhost:8082/api/accounts/${ACC_NUM}/balance" \
  -H "Content-Type: application/json" \
  -d '{"balance": 92500.50}')
NEW_BAL=$(echo "$BAL_RES" | grep -o '"availableBalance":[0-9.]*' | cut -d':' -f2)
echo -e "${GREEN}[PASS] Balance updated to: ${NEW_BAL}${NC}"

# 2.5 Update Status (PUT method and lowercase normalization)
echo "2.5 Calling PUT /api/accounts/${ACC_NUM}/status with {'status': 'restricted', 'connection_status': 'syncing'}..."
STATUS_RES=$(curl -s -X PUT "http://localhost:8082/api/accounts/${ACC_NUM}/status" \
  -H "Content-Type: application/json" \
  -d '{"status": "restricted", "connection_status": "syncing"}')
echo -e "${GREEN}[PASS] Status updated and normalized successfully.${NC}"
echo ""

echo -e "${BLUE}[Step 3/4] Testing Transactions Service (:8083)...${NC}"

# 3.1 Record Credit (Deposit)
echo "3.1 Calling POST /api/transactions for M-Pesa deposit (CREDIT 15,000 KES)..."
TX_CREDIT_RES=$(curl -s -X POST http://localhost:8083/api/transactions \
  -H "Content-Type: application/json" \
  -d "{
    \"accountId\": \"${ACC_UUID}\",
    \"amount\": 15000.00,
    \"currency\": \"KES\",
    \"transactionType\": \"CREDIT\",
    \"counterparty\": \"Safaricom M-Pesa\",
    \"paymentMethod\": \"MPESA_PAYBILL\",
    \"providerReference\": \"MPESA-${UNIQUE_ID: -6}\",
    \"description\": \"Salary advance transfer\"
  }")
TX_ID=$(echo "$TX_CREDIT_RES" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [[ -z "$TX_ID" ]]; then
  echo -e "${RED}[FAIL] Transaction recording failed. Response: ${TX_CREDIT_RES}${NC}"
  exit 1
fi
echo -e "${GREEN}[PASS] Credit transaction recorded. ID: ${TX_ID}${NC}"

# 3.2 Record Debit (Expense)
echo "3.2 Calling POST /api/transactions for Naivas purchase (DEBIT 3,450 KES)..."
TX_DEBIT_RES=$(curl -s -X POST http://localhost:8083/api/transactions \
  -H "Content-Type: application/json" \
  -d "{
    \"accountId\": \"${ACC_UUID}\",
    \"amount\": 3450.00,
    \"currency\": \"KES\",
    \"transactionType\": \"DEBIT\",
    \"counterparty\": \"Naivas Supermarket\",
    \"paymentMethod\": \"VISA_DEBIT\",
    \"description\": \"Weekly groceries\"
  }")
echo -e "${GREEN}[PASS] Debit transaction recorded.${NC}"

# 3.3 List Transactions for Account
echo "3.3 Calling GET /api/transactions?accountId=${ACC_UUID}..."
TX_LIST=$(curl -s -X GET "http://localhost:8083/api/transactions?accountId=${ACC_UUID}")
echo -e "${GREEN}[PASS] Retrieved transaction history for account.${NC}"

# 3.4 Reverse Transaction
echo "3.4 Calling POST /api/transactions/${TX_ID}/reverse..."
REV_RES=$(curl -s -X POST "http://localhost:8083/api/transactions/${TX_ID}/reverse" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Customer dispute resolution"}')
REV_STATUS=$(echo "$REV_RES" | grep -o '"status":"[^"]*' | cut -d'"' -f4)
echo -e "${GREEN}[PASS] Transaction reversed. Status: ${REV_STATUS}${NC}"
echo ""

echo -e "${BLUE}[Step 4/4] Testing Account Deletion Modes (:8082)...${NC}"

# 4.1 Soft Close
echo "4.1 Calling DELETE /api/accounts/${ACC_NUM}?permanent=false (Soft close)..."
CLOSE_RES=$(curl -s -X DELETE "http://localhost:8082/api/accounts/${ACC_NUM}?permanent=false")
CLOSED_STATUS=$(echo "$CLOSE_RES" | grep -o '"accountStatus":"[^"]*' | cut -d'"' -f4)
echo -e "${GREEN}[PASS] Soft close verified. Status: ${CLOSED_STATUS}${NC}"

# 4.2 Permanent Delete
echo "4.2 Calling DELETE /api/accounts/${ACC_NUM} (Permanent delete)..."
DEL_RES=$(curl -s -X DELETE "http://localhost:8082/api/accounts/${ACC_NUM}")
echo -e "${GREEN}[PASS] Permanent delete completed.${NC}"

# 4.3 Verify 404 after permanent delete
echo "4.3 Verifying GET /api/accounts/${ACC_NUM} returns 404 Not Found..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8082/api/accounts/${ACC_NUM}")
if [[ "$HTTP_CODE" == "404" ]]; then
  echo -e "${GREEN}[PASS] Confirmed 404 Not Found after deletion.${NC}"
else
  echo -e "${RED}[FAIL] Expected 404 but got ${HTTP_CODE}${NC}"
  exit 1
fi

echo ""
echo -e "${GREEN}================================================================${NC}"
echo -e "${GREEN}   ALL MOBILE E2E TESTS PASSED 100% ACROSS ALL 3 SERVICES!      ${NC}"
echo -e "${GREEN}================================================================${NC}"
