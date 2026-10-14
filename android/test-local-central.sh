#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
trap 'adb logcat -d > app/build/central-logcat.txt || true' EXIT
chmod +x gradlew
./gradlew :app:connectedDebugAndroidTest --no-daemon
adb forward tcp:18790 tcp:8790
check_central() {
  local response
  for attempt in $(seq 1 60); do
    if response=$(curl --max-time 4 --fail --silent http://127.0.0.1:18790/api/v1/state); then
      python3 -c 'import json,sys; s=json.loads(sys.argv[1]); assert s["limits"]["temperature"] == 29; assert s["running"] is False' "$response"
      return
    fi
    sleep 2
  done
  echo "Central did not recover" >&2
  return 1
}
adb shell input keyevent 223
sleep 10
check_central
# The activity is already closed. Kill the process without force-stop so START_STICKY may recover.
pid=$(adb shell pidof br.eng.thsouza.winterhold | tr -d '\r')
adb shell run-as br.eng.thsouza.winterhold kill -9 "$pid"
check_central
# A real reboot exercises the system BOOT_COMPLETED broadcast, without reopening the app.
adb reboot
adb wait-for-device
timeout 180 bash -c 'until [ "$(adb shell getprop sys.boot_completed | tr -d "\r")" = 1 ]; do sleep 2; done'
adb shell input keyevent 82
adb forward tcp:18790 tcp:8790
check_central
