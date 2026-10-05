#!/usr/bin/env bash
set -euo pipefail

# emulator-runner executes individual script lines in separate shells.
# Keep installation, instrumentation and evidence collection in one process.
apk=$(find newtube/smarttubetv/build/outputs/apk -name '*universal*debug*.apk' -print -quit)
test_apk=$(find newtube/smarttubetv/build/outputs/apk/androidTest -name '*.apk' -print -quit)
test -f "$apk"
test -f "$test_apk"

collect_evidence() {
    mkdir -p ui-evidence
    adb pull /sdcard/Android/data/io.github.harshsinghal10h.newtubeglass.debug/files/glass-screenshots ui-evidence/ || true
    adb logcat -d > ui-evidence/logcat.txt || true
}
trap collect_evidence EXIT

adb install -r "$apk"
adb install -r "$test_apk"
adb shell pm grant io.github.harshsinghal10h.newtubeglass.debug android.permission.POST_NOTIFICATIONS
adb shell am instrument -w -e class com.newtube.mobile.ui.glass.GlassDeviceTest io.github.harshsinghal10h.newtubeglass.debug.test/androidx.test.runner.AndroidJUnitRunner | tee device-test-result.txt
! grep -E 'FAILURES|INSTRUMENTATION_FAILED|Process crashed|AssertionFailed' device-test-result.txt
grep -q 'OK (1 test)' device-test-result.txt
