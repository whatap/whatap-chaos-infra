#!/bin/bash
# Container Log Flood - Status

CONTAINER_NAME="chaos-log-flood"

if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${CONTAINER_NAME}$"; then
    echo '{"running":false,"container":false}'
    exit 0
fi

# Check if batch process is running inside the container
FLOODING=$(docker exec ${CONTAINER_NAME} pgrep -f LogFloodBatch > /dev/null 2>&1 && echo "true" || echo "false")
FILE_COUNT=$(docker exec ${CONTAINER_NAME} sh -c 'ls /var/log/batch/ 2>/dev/null | wc -l' 2>/dev/null || echo "0")
DISK_USAGE=$(docker exec ${CONTAINER_NAME} sh -c 'du -sh /var/log/batch/ 2>/dev/null | cut -f1' 2>/dev/null || echo "0")
DISK_PCT=$(docker exec ${CONTAINER_NAME} sh -c "df /var/log/batch 2>/dev/null | tail -1 | awk '{print \$5}'" 2>/dev/null || echo "0%")

echo "{\"running\":${FLOODING},\"container\":true,\"files\":${FILE_COUNT},\"diskUsage\":\"${DISK_USAGE}\",\"diskPct\":\"${DISK_PCT}\"}"
