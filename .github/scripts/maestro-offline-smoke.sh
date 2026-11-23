#!/usr/bin/env bash
# GitHub's disposable emulator has no personal accounts or app data.
set -euo pipefail
apk=$1
flow=$2
serial=${ANDROID_SERIAL:?An explicit CI emulator serial is required}
[[ ${GITHUB_ACTIONS:-} == true && $serial =~ ^emulator-[0-9]+$ ]]
adb_device=(adb -s "$serial")
[[ "$("${adb_device[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
"${adb_device[@]}" install -r "$apk"
"${adb_device[@]}" shell cmd connectivity airplane-mode enable
"${adb_device[@]}" shell svc wifi disable

reports=build/maestro
mkdir -p "$reports"
rm -f "$reports/network.txt"
for attempt in {1..15}; do
  if "${adb_device[@]}" shell dumpsys connectivity | tr -d '\r' |
    awk 'tolower($0) ~ /^[[:space:]]*active default network:[[:space:]]*none[[:space:]]*$/ {found=1} END {exit !found}'; then
    printf 'Device %s has no active default network.\n' "$serial" > "$reports/network.txt"
    break
  fi
  sleep 1
done
[[ -s "$reports/network.txt" ]] || { echo "Could not verify offline state on the emulator." >&2; exit 1; }
maestro --device "$serial" test --test-output-dir "$reports" "$flow"
