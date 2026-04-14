#!/bin/bash

#############################################
# Chaos Runner - 중지
#############################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Stop log-flood container if running
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^chaos-log-flood$"; then
    echo "Stopping log-flood container..."
    docker rm -f chaos-log-flood > /dev/null 2>&1
fi

echo "Stopping Chaos Runner..."
docker compose down
