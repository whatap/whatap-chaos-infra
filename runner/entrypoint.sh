#!/bin/bash

echo "=============================================="
echo " Chaos Scenario Runner"
echo "=============================================="

# Build whatap.conf from base template + oname
export WHATAP_HOME=/app

if [ -f /tmp/whatap-base.conf ]; then
    cp /tmp/whatap-base.conf ${WHATAP_HOME}/whatap.conf
    if [ -n "${WHATAP_ONAME}" ]; then
        echo "oname=${WHATAP_ONAME}" >> ${WHATAP_HOME}/whatap.conf
        echo " [OK] oname=${WHATAP_ONAME}"
    fi
fi

# Start WhaTap Infra Agent
echo " Starting WhaTap Infra Agent (WHATAP_HOME=${WHATAP_HOME})..."

if [ -f "${WHATAP_HOME}/whatap.conf" ]; then
    /usr/whatap/infra/whatap_infrad &
    sleep 2
    if pgrep -f whatap_infrad > /dev/null 2>&1; then
        echo " [OK] WhaTap Infra Agent is running"
    else
        echo " [WARN] WhaTap Infra Agent may not be running"
    fi
else
    echo " [WARN] ${WHATAP_HOME}/whatap.conf not found, skipping WhaTap agent"
fi

echo "=============================================="
echo " Starting Scenario Runner..."
echo "=============================================="

# Start Java scenario runner (foreground)
exec java -cp out ChaosServer 9090 /app/trouble
