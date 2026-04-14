#!/bin/bash

#############################################
# Chaos Runner - 시작
#############################################

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Check whatap.conf
if [ ! -f whatap.conf ]; then
    echo "ERROR: whatap.conf not found."
    echo "  cp whatap.conf.template whatap.conf"
    echo "  vi whatap.conf"
    exit 1
fi

if grep -q "YOUR_LICENSE_KEY_HERE\|YOUR_WHATAP_SERVER_IP" whatap.conf 2>/dev/null; then
    echo "ERROR: whatap.conf still has placeholder values."
    echo ""
    echo "  Edit whatap.conf with your WhaTap project credentials:"
    echo "    license=<your-project-license-key>"
    echo "    whatap.server.host=<your-whatap-server-ip>"
    echo ""
    echo "  Get these from: https://service.whatap.io"
    exit 1
fi

# Clean up stale containers
docker rm -f chaos-log-flood chaos-runner 2>/dev/null

echo "Starting Chaos Runner..."
docker compose up -d --build

echo ""
echo "  Web UI: http://localhost:9090/"
echo "  API:    http://localhost:9090/api/v1/health"
echo ""
echo "Start log flood:"
echo "  ./trouble/log-flood/start.sh"
