#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
trap 'adb logcat -d > app/build/central-logcat.txt || true' EXIT
chmod +x gradlew
./gradlew :app:connectedDebugAndroidTest --no-daemon
# Gradle's device test runner force-stops and uninstalls the target after instrumentation.
# Install the same built APK for the standalone lifecycle checks.
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Reproduce the app-specific battery permission granted during initial device preparation.
adb shell dumpsys deviceidle whitelist +br.eng.thsouza.winterhold
if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]; then
  adb shell pm grant br.eng.thsouza.winterhold android.permission.POST_NOTIFICATIONS
fi
adb shell am start -W -n br.eng.thsouza.winterhold/.MainActivity
adb shell input keyevent 3
adb forward tcp:18790 tcp:8790
for attempt in $(seq 1 30); do
  if curl --max-time 3 --fail --silent http://127.0.0.1:18790/healthz; then break; fi
  sleep 1
done
web_key=$(adb shell run-as br.eng.thsouza.winterhold cat shared_prefs/web_access.xml | \
  python3 -c 'import sys,json,xml.etree.ElementTree as ET; root=ET.fromstring(sys.stdin.read()); print(json.loads(root.find("./string[@name=\"users\"]").text)[0]["key"])')
curl --max-time 5 --fail --silent -H "X-Winterhold-Key: $web_key" -H 'Content-Type: application/json' \
  --data '{"type":"mode","value":"demo"}' http://127.0.0.1:18790/api/v1/action
curl --max-time 5 --fail --silent -H "X-Winterhold-Key: $web_key" -H 'Content-Type: application/json' \
  --data '{"type":"running","value":false}' http://127.0.0.1:18790/api/v1/action
curl --max-time 5 --fail --silent -H "X-Winterhold-Key: $web_key" -H 'Content-Type: application/json' \
  --data '{"type":"limit","key":"temperature","value":29}' http://127.0.0.1:18790/api/v1/action
check_central() {
  local response
  for attempt in $(seq 1 60); do
    if response=$(curl --max-time 4 --fail --silent -H "X-Winterhold-Key: $web_key" http://127.0.0.1:18790/api/v1/state); then
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
echo "Checking central with activity closed and screen off"
check_central
# The activity is already closed. Kill the process without force-stop so START_STICKY may recover.
# Use the userdebug emulator's shell to simulate a system process kill. Android 11's
# run-as command failed to signal the test PID on Android 11.
adb root
adb wait-for-device
pid=$(adb shell pidof br.eng.thsouza.winterhold | tr -d '\r')
adb shell kill -9 "$pid"
adb forward tcp:18790 tcp:8790
echo "Checking START_STICKY process recovery and saved settings"
check_central
# A real reboot exercises the system BOOT_COMPLETED broadcast, without reopening the app.
adb reboot
adb wait-for-device
timeout 180 bash -c 'until [ "$(adb shell getprop sys.boot_completed | tr -d "\r")" = 1 ]; do sleep 2; done'
adb shell input keyevent 82
adb forward tcp:18790 tcp:8790
echo "Checking BOOT_COMPLETED recovery without opening the app"
check_central
