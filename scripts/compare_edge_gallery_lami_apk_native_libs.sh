#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
EDGE_GALLERY_APK="${1:-}"
LAMI_APK="${2:-$ROOT_DIR/app/build/outputs/apk/standard/debug/app-standard-debug.apk}"

print_header() {
  printf '\n== %s ==\n' "$1"
}

usage() {
  printf 'usage: %s <edge-gallery.apk> [lami-standard-debug.apk]\n' "$0"
  printf 'default LAMI APK: %s\n' "$LAMI_APK"
}

if [ -z "$EDGE_GALLERY_APK" ]; then
  usage
  exit 1
fi

if [ ! -f "$EDGE_GALLERY_APK" ]; then
  printf 'missing Edge Gallery APK: %s\n' "$EDGE_GALLERY_APK"
  exit 1
fi

if [ ! -f "$LAMI_APK" ]; then
  printf 'missing LAMI APK: %s\n' "$LAMI_APK"
  printf 'hint: run ./gradlew :app:assembleStandardDebug first, or pass the LAMI APK path as the second argument\n'
  exit 1
fi

print_header "Edge Gallery vs LAMI native library comparison"
printf 'edge_gallery_apk=%s\n' "$EDGE_GALLERY_APK"
printf 'lami_apk=%s\n' "$LAMI_APK"

"$ROOT_DIR/scripts/compare_native_libs.sh" "$EDGE_GALLERY_APK" "$LAMI_APK"

print_header "LAMI standardDebug target native lib origin"
"$ROOT_DIR/scripts/dump_standard_debug_apk_native_libs.sh" "$LAMI_APK"

exit 0
