#!/bin/sh
# Browse fling benchmark on a real phone (G-ADB1 baseline, G-ADB2 after). Coordinator runs it from the Pi with the phone on ADB.
# Usage: tools/perf/browse_fling.sh OUT_DIR [SECONDS=20]
# Preconditions (manual, same for both runs): staging APK from main installed, Manu's real wardrobe, Browse open,
# Photos-only layout pinched to 4 columns, screen on, charging, airplane mode off, no sync running (wait 1 min after launch).
# Output: OUT_DIR/gfxinfo.txt, meminfo.txt, summary.txt (janky %, p50/p90/p95/p99 frame ms).
set -eu
OUT="$1"; DUR="${2:-20}"; PKG=com.gusanitolabs.robia
mkdir -p "$OUT"
SIZE=$(adb shell wm size | awk -F': ' '/Physical/ {print $2}' | tr -d '\r')
W=${SIZE%x*}; H=${SIZE#*x}
X=$((W / 2)); Y1=$((H * 80 / 100)); Y2=$((H * 25 / 100))
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
end=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$end" ]; do
  adb shell input swipe "$X" "$Y1" "$X" "$Y2" 120
  adb shell input swipe "$X" "$Y2" "$X" "$Y1" 120
done
adb shell dumpsys gfxinfo "$PKG" > "$OUT/gfxinfo.txt"
adb shell dumpsys meminfo "$PKG" > "$OUT/meminfo.txt"
grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" "$OUT/gfxinfo.txt" | head -6 | tee "$OUT/summary.txt"
