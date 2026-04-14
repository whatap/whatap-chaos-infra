#!/bin/bash

#############################################
# Log Flood - 중지
#############################################

RUNNER_URL="${RUNNER_URL:-http://localhost:9090}"

echo "Stopping Log Flood scenario..."

RESPONSE=$(curl -sf -X POST "${RUNNER_URL}/api/v1/scenarios/c01_log_flood/stop" 2>&1)

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to connect to runner at ${RUNNER_URL}"
    exit 1
fi

echo "$RESPONSE"
