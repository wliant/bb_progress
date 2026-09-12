#!/usr/bin/env bash
# Runs the e2e suite against a throwaway stack so it never touches the data in
# your everyday instance. The specs delete records and reset the profile, so they
# must not be pointed at a stack you actually use.
#
#   ./run-isolated.sh                 # whole suite
#   ./run-isolated.sh tests/care-logs.spec.ts
set -uo pipefail

E2E_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$E2E_DIR")"

export COMPOSE_PROJECT_NAME="${E2E_PROJECT_NAME:-bbprogress-e2e}"
export APP_PORT="${E2E_APP_PORT:-8099}"
export DB_NAME=bb
export DB_USER=bb
export DB_PASSWORD=e2e-throwaway
export TZ=Asia/Singapore

cleanup() {
  (cd "$ROOT" && docker compose down -v >/dev/null 2>&1)
}
trap cleanup EXIT

echo "Starting throwaway stack '$COMPOSE_PROJECT_NAME' on port ${APP_PORT}..."
if ! (cd "$ROOT" && docker compose up -d --build --force-recreate); then
  echo "Failed to start the e2e stack" >&2
  exit 1
fi

echo -n "Waiting for the API"
for _ in $(seq 1 60); do
  if curl -sf "http://localhost:$APP_PORT/api/milestones" -o /dev/null; then
    echo " - ready"
    break
  fi
  echo -n "."
  sleep 2
done

cd "$E2E_DIR"
E2E_BASE_URL="http://localhost:$APP_PORT" npx playwright test "$@"
status=$?

echo "Tearing down ${COMPOSE_PROJECT_NAME}..."
exit $status
