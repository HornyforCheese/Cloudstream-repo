#!/usr/bin/env bash
set -uo pipefail
adb install -r test-fixtures/game-app/build/outputs/apk/debug/MutinyGameFixture-debug.apk || exit 1
adb logcat -c
./gradlew :app:connectedDebugAndroidTest --stacktrace --console=plain
runtime_test_exit=$?
adb logcat -d > device-logcat.txt
mkdir -p runtime-screenshots
adb pull /sdcard/Android/data/com.mutinymedia.android/files/Pictures/. runtime-screenshots/ || true
exit "$runtime_test_exit"
