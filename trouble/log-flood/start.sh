#!/bin/bash

#############################################
# Log Flood - 시작
#############################################

RUNNER_URL="${RUNNER_URL:-http://localhost:9090}"

FILE_SIZE_KB=${1:-512}
INTERVAL_MS=${2:-200}
FILES_PER_ITER=${3:-3}

echo "Starting Log Flood scenario..."
echo "  file_size_kb:  ${FILE_SIZE_KB}"
echo "  interval_ms:   ${INTERVAL_MS}"
echo "  files_per_iter: ${FILES_PER_ITER}"
echo ""

RESPONSE=$(curl -sf -X POST "${RUNNER_URL}/api/v1/scenarios/c01_log_flood/start" \
    -H "Content-Type: application/json" \
    -d "{\"params\":{\"file_size_kb\":\"${FILE_SIZE_KB}\",\"interval_ms\":\"${INTERVAL_MS}\",\"files_per_iter\":\"${FILES_PER_ITER}\"}}" 2>&1)

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to connect to runner at ${RUNNER_URL}"
    echo "  Is the runner running? Try: ./start_runner.sh"
    exit 1
fi

echo "$RESPONSE"
echo ""
echo "Monitor: ${RUNNER_URL}/api/v1/scenarios/c01_log_flood/status"
echo "Web UI:  ${RUNNER_URL}/"
