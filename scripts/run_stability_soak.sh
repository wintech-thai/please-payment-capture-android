#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

DURATION_SEC="${1:-300}"
INTERVAL_SEC="${2:-10}"
DEVICE_SERIAL="${DEVICE_SERIAL:-emulator-5554}"
OUT_DIR="$ROOT_DIR/build/stability/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT_DIR"

if [[ -f "$ROOT_DIR/local.properties" ]]; then
  SDK_DIR="$(grep '^sdk.dir=' "$ROOT_DIR/local.properties" | head -1 | cut -d'=' -f2-)"
else
  SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
fi
SDK_DIR="${SDK_DIR:-/Users/linus/Library/Android/sdk}"
export ANDROID_HOME="$SDK_DIR"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

APP_PKG="com.example.notification_agent"
TEST_PKG="com.example.notification_agent.test"
APP_APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT_DIR/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
SERVER_LOG="$OUT_DIR/requests.jsonl"
SERVER_STDOUT="$OUT_DIR/server.stdout.log"
LOGCAT_FILE="$OUT_DIR/logcat.txt"
MEMINFO_FILE="$OUT_DIR/meminfo.csv"
SUMMARY_FILE="$OUT_DIR/summary.txt"
SETUP_OUT="$OUT_DIR/setup_test.txt"
SERVICE_OUT="$OUT_DIR/start_service.txt"

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]]; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "[1/7] Building debug APK + androidTest APK"
./gradlew :app:packageDebug :app:packageDebugAndroidTest >/dev/null

echo "[2/7] Starting host capture server -> $SERVER_LOG"
if command -v lsof >/dev/null 2>&1; then
  OLD_PIDS="$(lsof -ti tcp:8088 2>/dev/null || true)"
  if [[ -n "$OLD_PIDS" ]]; then
    echo "      killing stale listeners on tcp:8088 -> $OLD_PIDS"
    kill $OLD_PIDS >/dev/null 2>&1 || true
    sleep 1
  fi
fi
python3 "$ROOT_DIR/scripts/agent_capture_server.py" --log "$SERVER_LOG" > "$SERVER_STDOUT" 2>&1 &
SERVER_PID=$!
sleep 1
if ! kill -0 "$SERVER_PID" >/dev/null 2>&1; then
  echo "capture server failed to start"
  cat "$SERVER_STDOUT"
  exit 1
fi

echo "[3/7] Installing APKs on emulator"
adb -s "$DEVICE_SERIAL" wait-for-device
adb -s "$DEVICE_SERIAL" install -r "$APP_APK" >/dev/null
adb -s "$DEVICE_SERIAL" install -r "$TEST_APK" >/dev/null

echo "[4/7] Granting permissions + enabling notification listener"
adb -s "$DEVICE_SERIAL" shell pm grant "$APP_PKG" android.permission.RECEIVE_SMS >/dev/null 2>&1 || true
adb -s "$DEVICE_SERIAL" shell pm grant "$APP_PKG" android.permission.READ_SMS >/dev/null 2>&1 || true
adb -s "$DEVICE_SERIAL" shell pm grant "$APP_PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb -s "$DEVICE_SERIAL" shell cmd notification allow_listener "$APP_PKG/$APP_PKG.service.NotificationCaptureService" >/dev/null 2>&1 || true

echo "[5/7] Running soak setup instrumentation"
adb -s "$DEVICE_SERIAL" shell am instrument -w -e class "$APP_PKG.AgentSoakSetupTest" "$TEST_PKG/androidx.test.runner.AndroidJUnitRunner" > "$SETUP_OUT" 2>&1

echo "[6/7] Launching app"
adb -s "$DEVICE_SERIAL" shell monkey -p "$APP_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
sleep 2
print -r -- "foreground service is started from AgentSoakSetupTest inside the app process" > "$SERVICE_OUT"

echo "[7/7] Soak running for ${DURATION_SEC}s (interval=${INTERVAL_SEC}s)"
adb -s "$DEVICE_SERIAL" logcat -c
START_TS=$(date +%s)
END_TS=$((START_TS + DURATION_SEC))
ITER=0
print -r -- "iter,epoch_ms,pid,total_pss_kb" > "$MEMINFO_FILE"

while [[ $(date +%s) -lt $END_TS ]]; do
  ITER=$((ITER + 1))
  echo "  - iteration $ITER"
  adb -s "$DEVICE_SERIAL" shell monkey -p "$APP_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  adb -s "$DEVICE_SERIAL" emu sms send +66812345678 "soak sms #$ITER" > "$OUT_DIR/sms-$ITER.txt" 2>&1 || true
  adb -s "$DEVICE_SERIAL" shell cmd notification post -t "SoakNotif$ITER" tag "Soak notification #$ITER" > "$OUT_DIR/notif-$ITER.txt" 2>&1 || true

  PID="$(adb -s "$DEVICE_SERIAL" shell pidof "$APP_PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
  if [[ -z "$PID" ]]; then
    PID=0
    PSS=0
  else
    PSS="$(adb -s "$DEVICE_SERIAL" shell dumpsys meminfo "$APP_PKG" 2>/dev/null | awk '/TOTAL PSS:/ {print $3; exit}' || true)"
    PSS="${PSS:-0}"
  fi
  print -r -- "$ITER,$(($(date +%s) * 1000)),$PID,$PSS" >> "$MEMINFO_FILE"

  sleep "$INTERVAL_SEC"
done

adb -s "$DEVICE_SERIAL" logcat -d > "$LOGCAT_FILE"

WEBHOOK_COUNT=$(grep -c '"kind": "webhook"' "$SERVER_LOG" 2>/dev/null || true)
PROBE_COUNT=$(grep -c '"kind": "probe"' "$SERVER_LOG" 2>/dev/null || true)
CRASH_COUNT=$(grep -ciE 'Process com\.example\.notification_agent .*has died|am_crash.*com\.example\.notification_agent|Force finishing activity .*com\.example\.notification_agent|FATAL EXCEPTION.*com\.example\.notification_agent' "$LOGCAT_FILE" || true)

FIRST_PSS=$(awk -F',' 'NR > 1 && $4 > 0 {print $4; exit}' "$MEMINFO_FILE")
LAST_PSS=$(awk -F',' 'END {print $4}' "$MEMINFO_FILE")
FIRST_PSS=${FIRST_PSS:-0}
LAST_PSS=${LAST_PSS:-0}
SAMPLE_COUNT=$(( $(wc -l < "$MEMINFO_FILE") - 1 ))

PSS_DELTA=$((LAST_PSS - FIRST_PSS))
LEAK_SUSPECT="no"
if [[ $FIRST_PSS -gt 0 && $LAST_PSS -gt $((FIRST_PSS * 3 / 2)) ]]; then
  LEAK_SUSPECT="yes"
fi

{
  echo "Notification Agent soak test"
  echo "out_dir=$OUT_DIR"
  echo "duration_sec=$DURATION_SEC"
  echo "interval_sec=$INTERVAL_SEC"
  echo "samples=$SAMPLE_COUNT"
  echo "webhook_count=$WEBHOOK_COUNT"
  echo "probe_count=$PROBE_COUNT"
  echo "crash_count=$CRASH_COUNT"
  echo "first_pss_kb=$FIRST_PSS"
  echo "last_pss_kb=$LAST_PSS"
  echo "pss_delta_kb=$PSS_DELTA"
  echo "leak_suspect=$LEAK_SUSPECT"
} | tee "$SUMMARY_FILE"

echo
echo "Artifacts written to: $OUT_DIR"





