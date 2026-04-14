#!/bin/bash
set -e

DISK_LIMIT_MB=${DISK_LIMIT_MB:-1024}

echo "=============================================="
echo " Container Chaos: Log Flood"
echo "=============================================="

# Create loopback ext4 filesystem for /var/log/batch
echo " Creating ${DISK_LIMIT_MB}MB ext4 volume at /var/log/batch..."
truncate -s ${DISK_LIMIT_MB}M /tmp/logdisk.img
mkfs.ext4 -q -F /tmp/logdisk.img
mount -o loop /tmp/logdisk.img /var/log/batch
echo " [OK] /var/log/batch mounted (${DISK_LIMIT_MB}MB ext4)"
df -h /var/log/batch

echo "=============================================="
echo " Configuring WhaTap Infra Agent..."
echo "=============================================="

# Build whatap.conf from base template + oname
WHATAP_CONF_DIR=/usr/whatap/infra/conf
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

echo "=============================================="
echo " Ready. Waiting for scenario trigger..."
echo " (Batch process is NOT running yet)"
echo "=============================================="

# Keep container alive (WhaTap agent collects baseline metrics)
exec tail -f /dev/null
