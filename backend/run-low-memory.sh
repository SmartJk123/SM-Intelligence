#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

IDENTITY_JAR="identity-service/target/identity-service-1.0.0-SNAPSHOT.jar"
ACCOUNTS_JAR="accounts-service/target/accounts-service-1.0.0-SNAPSHOT.jar"
TRANSACTIONS_JAR="transactions-service/target/transactions-service-1.0.0-SNAPSHOT.jar"

# Check if JARs are built; if not, build them once
if [[ ! -f "$IDENTITY_JAR" || ! -f "$ACCOUNTS_JAR" || ! -f "$TRANSACTIONS_JAR" ]]; then
  echo "Building JARs (first time only)..."
  ./mvnw clean package -DskipTests -pl identity-service,accounts-service,transactions-service -am
fi

# Secret for Identity token signing
export JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48)}"

# Low-memory JVM options:
# - C1 JIT only (-XX:TieredStopAtLevel=1): avoids heavy C2 compiler native memory footprint
# - Small heap limits (-Xms64m -Xmx192m): caps heap allocation
# - Reduced stack size (-Xss256k): saves ~75% stack memory per thread
# - Bounded metaspace (-XX:MaxMetaspaceSize=128m)
JVM_OPTS="-Xms64m -Xmx192m -Xss256k -XX:MaxMetaspaceSize=128m -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

echo "================================================================"
echo "Starting microservices in LOW-MEMORY mode (JAR direct execution)"
echo "  Identity:     http://localhost:8081"
echo "  Accounts:     http://localhost:8082"
echo "  Transactions: http://localhost:8083"
echo "Press Ctrl+C to stop all services."
echo "================================================================"

trap 'echo ""; echo "Shutting down all services..."; kill $(jobs -p) 2>/dev/null; exit' INT TERM EXIT

java $JVM_OPTS -jar "$IDENTITY_JAR" &
java $JVM_OPTS -jar "$ACCOUNTS_JAR" &
java $JVM_OPTS -jar "$TRANSACTIONS_JAR" &

wait
