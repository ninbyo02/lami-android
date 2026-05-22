#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ -z "${LAMI_LEGACY_NPU_TOMBSTONE_COLLECTOR:-}" ]; then
  exec "$ROOT_DIR/scripts/collect_npu_tombstone_diagnostics_v2.sh" "$@"
fi

APP_ID="${APP_ID:-io.github.ninbyo02.lami.npu}"
APK_PATH="${APK_PATH:-app/build/outputs/apk/npuExperiment/debug/app-npuExperiment-debug.apk}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-artifacts/npu_diagnostics/$TIMESTAMP}"
KEYWORDS="FATAL|SIGABRT|Abort message|DEBUG|tombstone|litert|LiteRt|LiteRT|LiteRtDispatch|Dispatch|dispatch|QNN|Qnn|HTP|Htp|NPU|nativeCreateEngine|liblitertlm_jni|libLiteRt|libLiteRtDispatch_Qualcomm|No usable Dispatch runtime found|Failed to initialize Dispatch API|insufficient capabilities|version mismatch|symbol mismatch|LiteRtQualcommOptionsGet|LiteRtDispatchGetApi|QAIRT244_SMOKE|QAIRT244_SENTINEL|QAIRT244_DIAG|qairt244_app_jni_smoke_v1|qairt244_jni_entry_v1|qairt244_android_log_v1|qairt244_qnn_provider_trace_v1|qairt244_htp_backend_trace_v1"
LIBS=(
  "libLiteRtDispatch_Qualcomm.so"
  "libLiteRt.so"
  "liblitertlm_jni.so"
  "libQnnHtp.so"
  "libQnnSystem.so"
  "libQnnHtpPrepare.so"
  "libQnnHtpV79Skel.so"
  "libQnnHtpV79Stub.so"
)

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() {
  printf '[npu-tombstone-collect] %s\n' "$*"
}

save_cmd() {
  local output="$1"
  shift
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n\n'
    "$@" 2>&1
  } >"$output"
}

if ! command -v adb >/dev/null 2>&1; then
  log "adb not found."
  printf 'adb not found\n' >"$OUT_DIR/error.txt"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$DEVICE_COUNT" -lt 1 ]; then
  log "no adb device connected."
  printf 'no adb device connected\n' >"$OUT_DIR/error.txt"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

log "saving diagnostics to $OUT_DIR"

{
  for prop in \
    ro.product.model \
    ro.soc.model \
    ro.soc.manufacturer \
    ro.hardware \
    ro.build.version.sdk \
    ro.build.fingerprint; do
    printf '%s=' "$prop"
    adb shell getprop "$prop" 2>/dev/null | tr -d '\r'
  done
} >"$OUT_DIR/device_props.txt"

for file in \
  files/npu_engine_initialize_dry_run.txt \
  files/npu_engine_initialize_last_stage.txt \
  files/npu_engine_initialize_crash_marker.txt \
  files/npu_experiment_probe.txt; do
  name="$(basename "$file")"
  adb shell run-as "$APP_ID" cat "$file" >"$OUT_DIR/$name" 2>"$OUT_DIR/$name.err" || true
done
cp "$OUT_DIR/npu_engine_initialize_dry_run.txt" "$OUT_DIR/stage_file.txt" 2>/dev/null || true
cp "$OUT_DIR/npu_engine_initialize_crash_marker.txt" "$OUT_DIR/crash_marker.txt" 2>/dev/null || true
cp "$OUT_DIR/npu_experiment_probe.txt" "$OUT_DIR/probe_snapshot.txt" 2>/dev/null || true

save_cmd "$OUT_DIR/package_dump.txt" adb shell dumpsys package "$APP_ID"
NATIVE_DIR="$(
  grep -m1 -E 'nativeLibraryDir=|primaryCpuAbi=' "$OUT_DIR/package_dump.txt" >/dev/null 2>&1
  grep -m1 'nativeLibraryDir=' "$OUT_DIR/package_dump.txt" 2>/dev/null | sed 's/.*nativeLibraryDir=//' | tr -d '\r'
)"

log "collecting logcat"
adb logcat -b all -d -t 2000 >"$OUT_DIR/logcat_all_tail.txt" 2>"$OUT_DIR/logcat_all_tail.err" || true
adb logcat -b crash -d -t 500 >"$OUT_DIR/logcat_crash_tail.txt" 2>"$OUT_DIR/logcat_crash_tail.err" || true
grep -Ei "$KEYWORDS" "$OUT_DIR/logcat_all_tail.txt" >"$OUT_DIR/logcat_litert_qnn_extract.txt" 2>/dev/null || true
grep -Ei "$KEYWORDS" "$OUT_DIR/logcat_crash_tail.txt" >>"$OUT_DIR/logcat_litert_qnn_extract.txt" 2>/dev/null || true

log "collecting dropbox"
adb shell dumpsys dropbox --print >"$OUT_DIR/dropbox_all.txt" 2>"$OUT_DIR/dropbox_all.err" || true
grep -Ei "$KEYWORDS|$APP_ID|system_app_crash|data_app_crash" "$OUT_DIR/dropbox_all.txt" >"$OUT_DIR/dropbox_crash_extract.txt" 2>/dev/null || true

log "collecting latest tombstone"
TOMBSTONE_PATH="$(adb shell 'ls -t /data/tombstones/tombstone_[0-9][0-9] 2>/dev/null | head -n 1' 2>/dev/null | tr -d '\r')"
if [ -n "$TOMBSTONE_PATH" ]; then
  printf '%s\n' "$TOMBSTONE_PATH" >"$OUT_DIR/tombstone_path.txt"
  adb shell cat "$TOMBSTONE_PATH" >"$OUT_DIR/tombstone_latest.txt" 2>"$OUT_DIR/tombstone_latest.err" || true
else
  printf 'latest tombstone not found or not readable\n' >"$OUT_DIR/tombstone_latest.txt"
fi
adb shell ls -lt /data/tombstones >"$OUT_DIR/tombstone_listing.txt" 2>"$OUT_DIR/tombstone_listing.err" || true

if [ -s "$OUT_DIR/tombstone_latest.txt" ]; then
  grep -E '^(Cmdline:|pid:|signal |Abort message:|ABI:|Timestamp:|Build fingerprint:|tagged_addr_ctrl:|backtrace:|      #[0-9]+ pc|Cause:)' "$OUT_DIR/tombstone_latest.txt" >"$OUT_DIR/tombstone_key_extract.txt" 2>/dev/null || true
  grep -E 'BuildId:|Build ID:' "$OUT_DIR/tombstone_latest.txt" | sort -u >"$OUT_DIR/tombstone_build_ids.txt" 2>/dev/null || true
  if [ -z "${NATIVE_DIR:-}" ]; then
    NATIVE_DIR="$(
      grep -m1 -oE '/data/app/[^ ]+/lib/arm64/libLiteRt\.so' "$OUT_DIR/tombstone_latest.txt" 2>/dev/null |
        sed 's#/libLiteRt\.so$##'
    )"
  fi
fi

log "collecting native library metadata"
{
  printf 'appId=%s\n' "$APP_ID"
  printf 'nativeLibraryDir=%s\n' "${NATIVE_DIR:-unknown}"
  printf '\n[device native libraries]\n'
  if [ -n "${NATIVE_DIR:-}" ]; then
    for lib in "${LIBS[@]}"; do
      path="$NATIVE_DIR/$lib"
      printf '\n## %s\n' "$lib"
      adb shell ls -l "$path" 2>&1 | tr -d '\r'
      adb shell sha256sum "$path" 2>&1 | tr -d '\r'
    done
  else
    printf 'nativeLibraryDir not found from dumpsys package\n'
  fi
  printf '\n[apk build ids]\n'
} >"$OUT_DIR/native_lib_build_ids.txt"

APK_LIB_DIR="$OUT_DIR/apk_libs"
mkdir -p "$APK_LIB_DIR"
if [ -f "$APK_PATH" ]; then
  for lib in "${LIBS[@]}"; do
    if unzip -p "$APK_PATH" "lib/arm64-v8a/$lib" >"$APK_LIB_DIR/$lib" 2>/dev/null; then
      {
        printf '\n## %s\n' "$lib"
        ls -l "$APK_LIB_DIR/$lib"
        sha256sum "$APK_LIB_DIR/$lib" 2>/dev/null || true
        file "$APK_LIB_DIR/$lib" 2>/dev/null || true
        readelf -n "$APK_LIB_DIR/$lib" 2>/dev/null | grep -A3 -E 'Build ID|NT_GNU_BUILD_ID' || true
      } >>"$OUT_DIR/native_lib_build_ids.txt"
    fi
  done
else
  printf 'APK not found: %s\n' "$APK_PATH" >>"$OUT_DIR/native_lib_build_ids.txt"
fi

log "done"
printf '%s\n' "$OUT_DIR"
