#!/usr/bin/env bash
set -euo pipefail
trap 'adb logcat -d -t 2000 > android-qa/logcat.txt || true; adb pull /sdcard/Android/data/com.harsh.vlcglass/files/android-error.png android-qa/ || true' EXIT
adb shell settings put secure immersive_mode_confirmations confirmed
# Like Android CTS, honor test-requested orientations and suppress OS education.
if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 31 ]; then
  adb shell wm fixed-to-user-rotation disabled
  adb shell wm set-ignore-orientation-request false
  adb shell wm set-letterbox-style --isEducationEnabled false
fi
adb install -r publish-0.3.1/release/VLC-Glass-Android-0.3.1-x86_64.apk
adb install -r android-qa/VLC-Glass-QA-0.3.1.apk
adb shell am instrument -w com.harsh.vlcglass.qa/.Smoke | tee android-qa/instrumentation.log
python3 - <<'PY'
from pathlib import Path
import re,json
p=Path('android-qa');text=(p/'instrumentation.log').read_text()
m=re.search(r'(?:INSTRUMENTATION_RESULT: stream=)?(\{[^\n]+\})',text)
if not m:raise SystemExit('No Android instrumentation report returned')
report=json.loads(m.group(1));(p/'android-smoke.json').write_text(json.dumps(report,indent=2))
if not report.get('passed'):raise SystemExit('Android smoke test failed: '+str(report))
PY
adb pull /sdcard/Android/data/com.harsh.vlcglass/files/android-portrait.png android-qa/
adb pull /sdcard/Android/data/com.harsh.vlcglass/files/android-landscape.png android-qa/
