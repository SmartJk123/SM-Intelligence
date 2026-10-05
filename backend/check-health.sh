#!/usr/bin/env bash
services=(
  "identity-service:8081"
  "accounts-service:8082"
  "transactions-service:8083"
  "categories-service:8084"
  "budgets-service:8085"
  "investments-service:8086"
  "forecasts-service:8087"
  "notifications-service:8088"
  "audit-service:8089"
  "bank-integration-service:8090"
)

for item in "${services[@]}"; do
  name="${item%%:*}"
  port="${item##*:}"
  echo -n "Checking $name on port $port: "
  curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:$port/actuator/health" || echo "DOWN"
done