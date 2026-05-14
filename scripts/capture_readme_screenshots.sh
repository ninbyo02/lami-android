#!/usr/bin/env bash
set -euo pipefail

pkg="${LAMI_PACKAGE:-io.github.ninbyo02.lami}"
activity="${LAMI_ACTIVITY:-.MainActivity}"
out_dir="${LAMI_SCREENSHOT_DIR:-assets/screenshots}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/capture_readme_screenshots.sh <name>

Examples:
  scripts/capture_readme_screenshots.sh hero-chat
  scripts/capture_readme_screenshots.sh local-inference-stats

Environment:
  LAMI_PACKAGE          Android package name. Default: io.github.ninbyo02.lami
  LAMI_ACTIVITY         Launch activity. Default: .MainActivity
  LAMI_SCREENSHOT_DIR   Output directory. Default: assets/screenshots

Notes:
  This captures the currently visible Android screen after launching LAMI.
  If you run this from Termux on the same device, Android may foreground Termux
  and the screenshot may capture the terminal instead of LAMI. In that case,
  use the device screenshot shortcut and move the file into assets/screenshots.
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

name="${1:-}"
if [[ -z "$name" ]]; then
  usage >&2
  exit 2
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required" >&2
  exit 1
fi

mkdir -p "$out_dir"

adb shell am start -n "$pkg/$activity" >/dev/null
sleep "${LAMI_SCREENSHOT_DELAY:-2}"

out="$out_dir/$name.png"
adb exec-out screencap -p > "$out"

echo "saved: $out"
