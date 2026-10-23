#!/usr/bin/env bash
# Verifies the offline / privacy guarantees of a built APK:
#  1. the APK requests no INTERNET / network-state permission,
#  2. only the expected permissions are requested,
#  3. production sources contain no networking or logging code (also enforced by PrivacyGuardTest).
#
# Usage: scripts/verify-offline.sh [path/to/app.apk]   (default: release APK)
set -euo pipefail
cd "$(dirname "$0")/.."

APK="${1:-app/build/outputs/apk/release/app-release.apk}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AAPT2="$(ls -d "$SDK"/build-tools/*/aapt2 | sort -V | tail -1)"

[[ -f "$APK" ]] || { echo "APK not found: $APK (run ./gradlew :app:assembleRelease)"; exit 1; }

echo "== Permissions requested by $APK"
perms=$("$AAPT2" dump permissions "$APK" | sed -n "s/^uses-permission: name='\(.*\)'.*/\1/p")
echo "$perms"

fail=0
if echo "$perms" | grep -E -q "INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE"; then
  echo "FAIL: network permission present"; fail=1
fi
unexpected=$(echo "$perms" | grep -v -E "^(android.permission.READ_CALL_LOG|android.permission.READ_CONTACTS|com.lastcalled.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)$" || true)
if [[ -n "$unexpected" ]]; then
  echo "FAIL: unexpected permissions:"; echo "$unexpected"; fail=1
fi

echo "== Scanning production sources for network / logging code"
if grep -R -n -E "java\.net\.|javax\.net\.|HttpURLConnection|okhttp|retrofit|Socket\(|android\.util\.Log|println\(" app/src/main domain/src/main; then
  echo "FAIL: network or logging code found"; fail=1
fi

[[ $fail -eq 0 ]] && echo "OK: no network permission, no network or logging code."
exit $fail
