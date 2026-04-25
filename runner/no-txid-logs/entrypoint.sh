#!/bin/bash
set -e

echo "=============================================="
echo " Container Chaos: No-TXID Log Simulator"
echo "=============================================="
echo " Goal: ship ERROR-level log lines through the WhaTap-Infra"
echo " agent's logsink module so they reach the AppLog parquet"
echo " path without any \`@txid\` attached. Tests the AIOps"
echo " ErrorQueue \"solo log admitted\" path (no-txid drop fix)."
echo "=============================================="

echo "=============================================="
echo " Configuring WhaTap Infra Agent..."
echo "=============================================="

WHATAP_CONF_DIR=/usr/whatap/infra/conf
mkdir -p ${WHATAP_CONF_DIR}
cp /tmp/whatap-base.conf ${WHATAP_CONF_DIR}/whatap.conf
if [ -n "${WHATAP_ONAME}" ]; then
    echo "oname=${WHATAP_ONAME}" >> ${WHATAP_CONF_DIR}/whatap.conf
    echo " [OK] oname=${WHATAP_ONAME}"
fi

echo " Starting WhaTap Infra Agent..."
/etc/init.d/whatap-infra start 2>/dev/null || {
    echo "[WARN] whatap-infra service start failed, trying direct execution..."
    /usr/whatap/infra/whatap_infrad &
}

sleep 2
if pgrep -f whatap_infrad > /dev/null 2>&1; then
    echo " [OK] WhaTap Infra Agent is running"
else
    echo " [WARN] WhaTap Infra Agent may not be running"
fi

mkdir -p /var/log/no-txid

echo "=============================================="
echo " Starting solo-log generator (interval=${INTERVAL_SEC:-5}s)..."
echo "=============================================="

exec /opt/solo_logs.sh
