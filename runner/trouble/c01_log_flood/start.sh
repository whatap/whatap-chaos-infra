#!/bin/bash
# Container Log Flood - Start
# Starts the Java batch flood process inside the already-running container

CONTAINER_NAME="chaos-log-flood"

FILE_SIZE_KB=${1:-512}
INTERVAL_MS=${2:-200}
FILES_PER_ITER=${3:-3}

# Check container is running
if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${CONTAINER_NAME}$"; then
    echo "ERROR: Container ${CONTAINER_NAME} is not running"
    echo "  Start it with: docker compose up -d"
    exit 1
fi

# Check if batch process is already running
if docker exec ${CONTAINER_NAME} pgrep -f LogFloodBatch > /dev/null 2>&1; then
    echo "ERROR: Batch process is already running"
    exit 1
fi

# Start batch process inside the container
echo "Starting log flood..."
docker exec -d ${CONTAINER_NAME} \
    env LOG_FILE_SIZE_KB=${FILE_SIZE_KB} \
        LOG_INTERVAL_MS=${INTERVAL_MS} \
        LOG_FILES_PER_ITERATION=${FILES_PER_ITER} \
    java -Xmx128m -XX:+UseG1GC -cp /opt/batch LogFloodBatch

sleep 1

# Verify it started
if docker exec ${CONTAINER_NAME} pgrep -f LogFloodBatch > /dev/null 2>&1; then
    echo "Log flood started"
    echo "  File size: ${FILE_SIZE_KB} KB"
    echo "  Interval: ${INTERVAL_MS} ms"
    echo "  Files/iter: ${FILES_PER_ITER}"
else
    echo "ERROR: Failed to start batch process"
    exit 1
fi
