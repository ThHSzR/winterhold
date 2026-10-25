#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
trap 'adb logcat -d > app/build/central-logcat.txt || true' EXIT
chmod +x gradlew
./gradlew :app:connectedDebugAndroidTest --no-daemon
python3 test-lifecycle.py
