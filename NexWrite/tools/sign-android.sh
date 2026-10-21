#!/usr/bin/env bash
# SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
set -euo pipefail
if [ "$#" -ne 2 ]; then
  echo 'Usage: bash tools/sign-android.sh UNSIGNED_APK OUTPUT_APK' >&2
  exit 2
fi
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
: "${NEXWRITE_SIGNING_KEYSTORE:?Set the private keystore path outside the repository}"
: "${NEXWRITE_SIGNING_PASSWORD_FILE:?Set the private password-file path outside the repository}"
build_tools="$ANDROID_SDK_ROOT/build-tools/36.1.0"
staging_dir=$(mktemp -d)
trap 'rm -rf "$staging_dir"' EXIT
test -f "$1"
test -r "$NEXWRITE_SIGNING_KEYSTORE"
test -r "$NEXWRITE_SIGNING_PASSWORD_FILE"
mkdir -p "$(dirname "$2")"
signing_passwords="$staging_dir/signing-passwords"
# apksigner consumes one line for --ks-pass and another for --key-pass, even
# when both credentials deliberately use the same password. Keep the source
# file single-line and create the two-entry input only in the private staging
# directory that is removed on exit.
cat "$NEXWRITE_SIGNING_PASSWORD_FILE" > "$signing_passwords"
printf '\n' >> "$signing_passwords"
cat "$NEXWRITE_SIGNING_PASSWORD_FILE" >> "$signing_passwords"
printf '\n' >> "$signing_passwords"
chmod 600 "$signing_passwords"
"$build_tools/zipalign" -P 16 -f 4 "$1" "$staging_dir/aligned.apk"
"$build_tools/apksigner" sign --ks "$NEXWRITE_SIGNING_KEYSTORE" \
  --ks-key-alias "${NEXWRITE_SIGNING_ALIAS:-nexwrite-release}" \
  --ks-pass "file:$signing_passwords" \
  --key-pass "file:$signing_passwords" \
  --out "$2" "$staging_dir/aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$2"
"$build_tools/zipalign" -c -P 16 -v 4 "$2" | tail -n 1
python3 - "$2" <<'PY'
import hashlib, pathlib, sys
apk = pathlib.Path(sys.argv[1])
print(hashlib.sha256(apk.read_bytes()).hexdigest(), apk.name)
PY
