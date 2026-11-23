#!/usr/bin/env bash
# GitHub's disposable emulator has no personal accounts or app data.
set -euo pipefail
apk=$1
flow=$2
screenshot=$3
serial=${ANDROID_SERIAL:?An explicit CI emulator serial is required}
[[ ${GITHUB_ACTIONS:-} == true && $serial =~ ^emulator-[0-9]+$ ]]
adb_device=(adb -s "$serial")
[[ "$("${adb_device[@]}" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
# Choose the rotation relative to the AVD's natural display dimensions.
size=$("${adb_device[@]}" shell wm size | tr -d '\r' | awk '/size:/ {size=$NF} END {print size}')
[[ $size =~ ^([0-9]+)x([0-9]+)$ ]]
rotation=0
if (( BASH_REMATCH[1] < BASH_REMATCH[2] )); then rotation=1; fi
"${adb_device[@]}" shell settings put system accelerometer_rotation 0
"${adb_device[@]}" shell settings put system user_rotation "$rotation"
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
python3 - "$reports" "$screenshot" <<'PYTHON'
from pathlib import Path
from struct import unpack
import sys

root, name = sys.argv[1:]
images = list(Path(root).rglob(name + ".png"))
if len(images) != 1:
    raise SystemExit(f"Expected one {name} screenshot in {root}, found {len(images)}")
with images[0].open("rb") as image:
    header = image.read(24)
if header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
    raise SystemExit(f"Invalid PNG: {images[0]}")
width, height = unpack(">II", header[16:24])
if width <= height:
    raise SystemExit(f"Expected landscape tablet evidence, got {width}x{height}")
print(f"{images[0]}: {width}x{height}")
PYTHON
