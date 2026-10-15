#!/usr/bin/env bash
# Load test (M3). Builds the jar, starts loadtest/compose.yaml (2 vCPU / 2 GB for the
# application, 1 vCPU / 1 GB for PostgreSQL), seeds 10,000 links and runs the Gatling
# scenarios one after the other. Reports land in build/reports/gatling.
#
# Run from backend/:  loadtest/run.sh
# Options (environment): RATES="250 500 1000 2000"  DURATION=60  VIRAL_RATE=2000  KEEP=1
set -euo pipefail

RATES="${RATES:-250 500 1000 2000}"
DURATION="${DURATION:-60}"
VIRAL_RATE="${VIRAL_RATE:-2000}"
compose="docker compose -f loadtest/compose.yaml"

./gradlew bootJar -q
VURLI_ADMIN_TOTP_KEY="$(openssl rand -base64 32)"
export VURLI_ADMIN_TOTP_KEY
$compose up -d --wait

printf 'Waiting for the application'
for _ in $(seq 1 90); do
  if curl -sf http://127.0.0.1:18080/actuator/health >/dev/null; then break; fi
  printf '.'
  sleep 1
done
echo
$compose exec -T postgres psql -q -U vurli -d vurli -v ON_ERROR_STOP=1 < loadtest/seed.sql

sql() {
  $compose exec -T postgres psql -U vurli -d vurli -tA -c "$1"
}

run() {
  echo "== $1 at $2/s for ${3}s"
  sql "truncate click_events" >/dev/null
  # A failed assertion is a result too; keep going with the next scenario.
  SCENARIO="$1" RATE="$2" DURATION="$3" ./gradlew gatlingRun --simulation io.vurli.loadtest.RedirectSimulation \
    --console=plain -q 2>&1 | sed -n '/---- Global Information/,/^=====/p;/---- Errors/,/^=====/p;/percentage of failed/p' \
    || echo "   (assertions failed)"
  # Clicks are written once a second; compare with the OK count above.
  sleep 3
  echo "   clicks written: $(sql "select count(*) from click_events")"
}

for rate in $RATES; do
  run browse "$rate" "$DURATION"
done
run viral "$VIRAL_RATE" "$DURATION"
run limit 20 60

if [ "${KEEP:-0}" != "1" ]; then
  $compose down -v
fi
