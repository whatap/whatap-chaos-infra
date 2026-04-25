#!/bin/bash
#
# Solo log generator — writes ERROR-level lines into /var/log/no-txid/app.log
# at INTERVAL_SEC ± jitter. Templates mimic real-world background-worker
# errors that classically have no transaction context (Quartz, Kafka
# consumer, app startup, JVM thread, Hikari validation, Spring AOP).
#
# Mix of "ERROR <logger>" prefixed lines AND bare lines (no level prefix)
# to exercise both code paths in LogProcessor / PatternFilter.
#
# Each line embeds variable parts (UUID, counter, bean, class) so Drain3
# extracts a stable template per category, while still letting cooldown
# elapse and re-emit naturally.

set -u

LOG_DIR="${LOG_DIR:-/var/log/no-txid}"
LOG_FILE="${LOG_DIR}/app.log"
INTERVAL_SEC="${INTERVAL_SEC:-5}"
ROTATE_BYTES="${ROTATE_BYTES:-10485760}"   # 10 MB

mkdir -p "$LOG_DIR"

# Templates. Each %TOKEN% is substituted at emit time with a fresh value.
# Mix: 4 templates begin with "ERROR <logger>" (typical Spring/Logback),
# 2 start bare so the LogProcessor's level==ERROR predicate is exercised
# both ways.
templates=(
'ERROR org.quartz.core.JobRunShell - Job DEFAULT.dailyReportJob threw uncaught exception: java.lang.IllegalStateException; jobInstance=%UUID%'
'ERROR org.springframework.kafka.listener.KafkaMessageListenerContainer - Stopping container; partition=order-events-%N% offset=%N% error=org.apache.kafka.common.errors.SerializationException'
'org.springframework.boot.SpringApplication - Application run failed; bean=%BEAN% cause=org.springframework.beans.factory.NoSuchBeanDefinitionException'
'ERROR java.lang.Thread.UncaughtExceptionHandler - Uncaught exception in thread pool-%N%-thread-%N%: java.lang.NullPointerException at com.example.svc.%CLASS%.execute'
'com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Failed to validate connection com.mysql.cj.jdbc.ConnectionImpl@%HEX% (Connection.isValid timed out); conn=%UUID%'
'ERROR o.s.aop.framework.CglibAopProxy - Unable to proxy interface-implementing method [public void com.example.svc.%CLASS%.process()]'
)

beans=("authenticationManager" "dataSource" "redisTemplate" "kafkaTemplate" "transactionManager" "objectMapper")
classes=("OrderService" "PaymentService" "InventoryService" "NotificationService" "AuditService" "ReportService")

rand_int() { echo $((RANDOM % $1)); }

uuid() {
    if command -v uuidgen >/dev/null 2>&1; then
        uuidgen
    else
        cat /proc/sys/kernel/random/uuid
    fi
}

hex8() {
    printf '%08x' $(( (RANDOM << 16) ^ RANDOM ))
}

rotate_if_big() {
    if [ -f "$LOG_FILE" ]; then
        local size
        size=$(stat -c%s "$LOG_FILE" 2>/dev/null || echo 0)
        if [ "$size" -gt "$ROTATE_BYTES" ]; then
            mv -f "$LOG_FILE" "${LOG_FILE}.1"
        fi
    fi
}

emit_one() {
    local idx=$(rand_int ${#templates[@]})
    local line="${templates[$idx]}"
    local ts
    ts=$(date '+%Y-%m-%d %H:%M:%S.%3N')
    line="${line//%UUID%/$(uuid)}"
    line="${line//%N%/$(rand_int 1000)}"
    line="${line//%HEX%/$(hex8)}"
    line="${line//%BEAN%/${beans[$(rand_int ${#beans[@]})]}}"
    line="${line//%CLASS%/${classes[$(rand_int ${#classes[@]})]}}"
    echo "${ts} ${line}" >> "$LOG_FILE"
}

echo " [solo_logs] writing to ${LOG_FILE} every ~${INTERVAL_SEC}s (±jitter)"
emit_count=0
while true; do
    rotate_if_big
    emit_one
    emit_count=$((emit_count + 1))
    if [ $((emit_count % 50)) -eq 0 ]; then
        echo " [solo_logs] emitted ${emit_count} lines so far"
    fi
    # Jitter ±50% of interval so timing isn't suspiciously regular
    JITTER=$(( INTERVAL_SEC + (RANDOM % (INTERVAL_SEC + 1)) - (INTERVAL_SEC / 2) ))
    [ "$JITTER" -lt 1 ] && JITTER=1
    sleep "$JITTER"
done
