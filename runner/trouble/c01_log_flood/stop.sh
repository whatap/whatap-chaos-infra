#!/bin/bash
# Container Log Flood - Stop
# Kills the batch process and cleans up log files. Container stays running.

CONTAINER_NAME="chaos-log-flood"

if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${CONTAINER_NAME}$"; then
    echo "Container ${CONTAINER_NAME} is not running"
    exit 0
fi

# Collect stats before stopping
FILE_COUNT=$(docker exec ${CONTAINER_NAME} sh -c 'ls /var/log/batch/ 2>/dev/null | wc -l' 2>/dev/null || echo "0")
DISK_USAGE=$(docker exec ${CONTAINER_NAME} sh -c 'du -sh /var/log/batch/ 2>/dev/null | cut -f1' 2>/dev/null || echo "0")
echo "Stats before stop: ${FILE_COUNT} files, ${DISK_USAGE}"

# Kill the batch process
docker exec ${CONTAINER_NAME} pkill -f LogFloodBatch 2>/dev/null
sleep 1

# Clean up log files (reset disk to baseline)
docker exec ${CONTAINER_NAME} sh -c 'rm -f /var/log/batch/*.log'
echo "Log flood stopped. Log files cleaned."
echo "Container is still running (baseline monitoring continues)."
