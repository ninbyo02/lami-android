#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuExperimentProbeActivity"
ARTIFACTS_DIR="$ROOT_DIR/artifacts"

FLAVOR="npuExperiment"
APP_ID="io.github.ninbyo02.lami.npu"
MODEL_PATH=""
PHASE="inventory"
INSTALL=true
ENGINE_INITIALIZE=false
ENGINE_CONFIG_VARIANT="default"
WAIT_SECONDS=8

LOGCAT_PATTERN="AndroidRuntime|DEBUG|DEBUGGERD|libc|crash_dump64|tombstoned|LiteRT|litert|QNN|Qnn|FastRPC|Backend.NPU|NpuExperimentProbe|AcceleratorProbe|Engine.initialize|FATAL|SIGABRT|abort|tombstone|mismatch|UnsatisfiedLinkError"

usage() {
  cat <<'USAGE'
Usage:
  scripts/run_backend_npu_attach_probe.sh [options]

Options:
  --flavor npuExperiment|customBuildExperiment|galleryStackExperiment|galleryAlignedNpuProbe
  --model-path PATH
  --phase inventory|engine_initialize|conversation|one_token_decode
  --engine-config-variant default|cache-files|cache-cache|max128|max32|backend-only|backend-null-modalities|gallery-like-cache|gallery-like-max128|gallery-like-all|gallery-like-data-data-path|gallery-like-canonical-path
  --engine-initialize   Explicitly opt in to Engine.initialize dry-run.
  --no-install          Do not run ./update.sh before probing.
  --wait SECONDS        Seconds to wait before pulling reports. Default: 8.

Default is safe inventory/config dry-run only. It does not connect Backend.NPU
to ChatScreen, does not create Conversation, and does not generate tokens.
USAGE
}

extract_signal() {
  local file="$1"
  grep -Ei "Fatal signal|signal [0-9]+|SIGABRT|SIGSEGV" "$file" 2>/dev/null | tail -n 1 | sed 's/^[[:space:]]*//' || true
}

extract_abort_message() {
  local file="$1"
  grep -Ei "Abort message|abort message|No usable Dispatch runtime|Failed to initialize Dispatch API" "$file" 2>/dev/null | tail -n 1 | sed 's/^[[:space:]]*//' || true
}

extract_backtrace_head() {
  local file="$1"
  awk '
    BEGIN { capture = 0; count = 0 }
    /backtrace:/ { capture = 1; next }
    capture == 1 && count < 8 {
      if ($0 ~ /#[0-9]+|pc /) {
        gsub(/^[[:space:]]+/, "", $0)
        print
        count++
      } else if (count > 0) {
        exit
      }
    }
  ' "$file" 2>/dev/null | tr '\n' '|' | sed 's/|$//' || true
}

extract_latest_dropbox_block_for_app() {
  local file="$1"
  local app_id="$2"
  awk -v app_id="$app_id" '
    function flush_block() {
      if (block != "" && index(block, app_id) > 0) {
        latest = block
      }
      block = ""
    }
    /^=+ / {
      flush_block()
    }
    {
      block = block $0 "\n"
    }
    END {
      flush_block()
      if (latest != "") {
        printf "%s", latest
      } else {
        print "<no current package crash block found>"
      }
    }
  ' "$file" 2>/dev/null || true
}

write_fallback_report() {
  local txt_path="$1"
  local md_path="$2"
  local native_crash_suspected="$3"
  local signal_line="$4"
  local abort_line="$5"
  local backtrace_head="$6"
  local pid_state="$7"
  local isolated_flavor="false"
  local gallery_aligned_stack="false"
  local engine_config_max_num_images="null"

  if [ "$FLAVOR" = "galleryAlignedNpuProbe" ]; then
    isolated_flavor="true"
    gallery_aligned_stack="true"
  fi
  if [ "$ENGINE_CONFIG_VARIANT" = "gallery-like-all" ]; then
    engine_config_max_num_images="1"
  fi

  {
    printf 'backend_npu_attach_probe_v1\n'
    printf 'run_id=%s\n' "$RUN_ID"
    printf 'phase_requested=%s\n' "$PHASE"
    printf 'explicit_engine_initialize_opt_in=%s\n' "$ENGINE_INITIALIZE"
    printf 'engine_config_variant=%s\n' "$ENGINE_CONFIG_VARIANT"
    printf 'backend_npu_attach_status=report-missing-after-probe\n'
    printf 'application_id=%s\n' "$APP_ID"
    printf 'current_flavor=%s\n' "$FLAVOR"
    printf 'isolated_flavor=%s\n' "$isolated_flavor"
    printf 'gallery_aligned_stack=%s\n' "$gallery_aligned_stack"
    printf 'lib_inventory_summary=unknown-report-missing\n'
    printf 'model_path=%s\n' "${MODEL_PATH:--}"
    printf 'model_canonical_path=unknown-report-missing\n'
    printf 'model_path_variant=unknown-report-missing\n'
    printf 'native_library_dir_variant=unknown-report-missing\n'
    printf 'application_info_native_library_dir=unknown-report-missing\n'
    printf 'context_application_info_native_library_dir=unknown-report-missing\n'
    printf 'hard_resolved_native_library_dir=unknown-report-missing\n'
    printf 'engineconfig_constructor_args_summary=unknown-report-missing\n'
    printf 'engineconfig_cache_dir=unknown-report-missing\n'
    printf 'engineconfig_max_num_tokens=unknown-report-missing\n'
    printf 'engineconfig_max_num_images=%s\n' "$engine_config_max_num_images"
    printf 'engine_initialize_invoked=unknown-report-missing\n'
    printf 'engine_initialize_returned=unknown-report-missing\n'
    printf 'engine_initialize_result=unknown-report-missing\n'
    printf 'process_alive_after_probe=%s\n' "$pid_state"
    printf 'native_crash_suspected=%s\n' "$native_crash_suspected"
    printf 'signal=%s\n' "${signal_line:--}"
    printf 'abort_message=%s\n' "${abort_line:--}"
    printf 'backtrace_head=%s\n' "${backtrace_head:--}"
    printf 'logcat_file=%s\n' "$LOCAL_LOGCAT"
    printf 'tombstone_summary_file=%s\n' "$LOCAL_TOMBSTONE"
    printf 'dropbox_full_file=%s\n' "$LOCAL_DROPBOX"
    printf 'safety_policy=dev-only explicit opt-in; no production ChatScreen wiring; no fallback change; no QAIRT/QNN setting change; no always-on System.loadLibrary\n'
  } > "$txt_path"

  {
    printf '# Backend.NPU Attach Probe\n\n'
    printf 'Fallback report generated by script because the app-side report artifact was missing after probe execution.\n\n'
    printf '| key | value |\n'
    printf '| --- | --- |\n'
    while IFS= read -r line; do
      case "$line" in
        backend_npu_attach_probe_v1|'') continue ;;
      esac
      printf '| %s | %s |\n' "${line%%=*}" "${line#*=}"
    done < "$txt_path"
  } > "$md_path"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --flavor)
      FLAVOR="${2:-}"
      shift 2
      ;;
    --model-path)
      MODEL_PATH="${2:-}"
      shift 2
      ;;
    --phase)
      PHASE="${2:-}"
      shift 2
      ;;
    --engine-config-variant)
      ENGINE_CONFIG_VARIANT="${2:-default}"
      shift 2
      ;;
    --engine-initialize)
      ENGINE_INITIALIZE=true
      shift
      ;;
    --no-install)
      INSTALL=false
      shift
      ;;
    --wait)
      WAIT_SECONDS="${2:-8}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[backend-npu-attach-probe] unknown option: $1"
      usage
      exit 2
      ;;
  esac
done

case "$FLAVOR" in
  npuExperiment)
    APP_ID="io.github.ninbyo02.lami.npu"
    ;;
  customBuildExperiment)
    APP_ID="io.github.ninbyo02.lami.customnpu"
    ;;
  galleryStackExperiment)
    APP_ID="io.github.ninbyo02.lami.gallerynpu"
    ;;
  galleryAlignedNpuProbe)
    APP_ID="io.github.ninbyo02.lami.galleryprobe"
    ;;
  *)
    echo "[backend-npu-attach-probe] unsupported flavor: $FLAVOR"
    exit 2
    ;;
esac

case "$ENGINE_CONFIG_VARIANT" in
  default|cache-files|cache-cache|max128|max32|backend-only|backend-null-modalities|gallery-like-cache|gallery-like-max128|gallery-like-all|gallery-like-data-data-path|gallery-like-canonical-path)
    ;;
  *)
    echo "[backend-npu-attach-probe] unsupported engine config variant: $ENGINE_CONFIG_VARIANT"
    exit 2
    ;;
esac

case "$PHASE" in
  inventory|engine_initialize|conversation|one_token_decode)
    ;;
  initialize|engine-init|engine_init)
    PHASE="engine_initialize"
    ;;
  decode|one-token|one_token)
    PHASE="one_token_decode"
    ;;
  *)
    echo "[backend-npu-attach-probe] unsupported phase: $PHASE"
    exit 2
    ;;
esac

if [ "$PHASE" != "inventory" ] && [ "$ENGINE_INITIALIZE" != "true" ]; then
  echo "[backend-npu-attach-probe] phase=$PHASE requires --engine-initialize explicit opt-in."
  exit 2
fi

cd "$ROOT_DIR" || exit 1
mkdir -p "$ARTIFACTS_DIR"

if ! command -v adb >/dev/null 2>&1; then
  echo "[backend-npu-attach-probe] adb not found."
  exit 1
fi

if [ "$INSTALL" = "true" ]; then
  echo "[backend-npu-attach-probe] Installing $FLAVOR debug APK..."
  ./update.sh update --flavor "$FLAVOR" || exit $?
fi

DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$DEVICE_COUNT" -lt 1 ]; then
  echo "[backend-npu-attach-probe] no adb device connected."
  exit 1
fi

RUN_ID="$(date +%Y%m%d_%H%M%S)"
REMOTE_TXT="files/backend_npu_attach_probe_${RUN_ID}.txt"
REMOTE_MD="files/backend_npu_attach_probe_${RUN_ID}.md"
LOCAL_TXT="$ARTIFACTS_DIR/backend_npu_attach_probe_${RUN_ID}.txt"
LOCAL_MD="$ARTIFACTS_DIR/backend_npu_attach_probe_${RUN_ID}.md"
LOCAL_LOGCAT="$ARTIFACTS_DIR/backend_npu_attach_probe_${RUN_ID}.logcat.txt"
LOCAL_TOMBSTONE="$ARTIFACTS_DIR/backend_npu_attach_probe_${RUN_ID}.tombstone.txt"
LOCAL_DROPBOX="$ARTIFACTS_DIR/backend_npu_attach_probe_${RUN_ID}.dropbox.txt"

echo "[backend-npu-attach-probe] Clearing stale reports for $APP_ID..."
adb shell run-as "$APP_ID" rm -f \
  "files/backend_npu_attach_probe_latest.txt" \
  "files/backend_npu_attach_probe_latest.md" \
  "$REMOTE_TXT" \
  "$REMOTE_MD" \
  "files/npu_engine_initialize_dry_run.txt" \
  "files/npu_engine_initialize_last_stage.txt" \
  "files/npu_engine_initialize_crash_marker.txt" >/dev/null 2>&1 || true

echo "[backend-npu-attach-probe] Clearing logcat when available..."
adb logcat -c >/dev/null 2>&1 || true

echo "[backend-npu-attach-probe] Starting background logcat capture: $LOCAL_LOGCAT"
adb logcat -b all -v threadtime > "$LOCAL_LOGCAT" 2>&1 &
LOGCAT_PID=$!

echo "[backend-npu-attach-probe] Starting probe. runId=$RUN_ID flavor=$FLAVOR phase=$PHASE engineInitialize=$ENGINE_INITIALIZE engineConfigVariant=$ENGINE_CONFIG_VARIANT"
if [ -n "$MODEL_PATH" ]; then
  adb shell am start -W \
    -n "$APP_ID/$ACTIVITY" \
    --ez run_backend_npu_attach_probe true \
    --ez run_engine_initialize_dry_run "$ENGINE_INITIALIZE" \
    --ez diagnostic_files_cleared_before_run true \
    --es run_id "$RUN_ID" \
    --es phase "$PHASE" \
    --es engine_config_variant "$ENGINE_CONFIG_VARIANT" \
    --es model_path "$MODEL_PATH"
else
  adb shell am start -W \
    -n "$APP_ID/$ACTIVITY" \
    --ez run_backend_npu_attach_probe true \
    --ez run_engine_initialize_dry_run "$ENGINE_INITIALIZE" \
    --ez diagnostic_files_cleared_before_run true \
    --es run_id "$RUN_ID" \
    --es phase "$PHASE" \
    --es engine_config_variant "$ENGINE_CONFIG_VARIANT"
fi

echo "[backend-npu-attach-probe] Waiting up to ${WAIT_SECONDS}s for report files..."
elapsed=0
while [ "$elapsed" -lt "$WAIT_SECONDS" ]; do
  if adb shell run-as "$APP_ID" test -f "$REMOTE_TXT" >/dev/null 2>&1; then
    break
  fi
  sleep 1
  elapsed=$((elapsed + 1))
done

if kill "$LOGCAT_PID" >/dev/null 2>&1; then
  wait "$LOGCAT_PID" 2>/dev/null || true
else
  wait "$LOGCAT_PID" 2>/dev/null || true
fi

echo "[backend-npu-attach-probe] Appending post-run adb logcat -d dump..."
{
  echo
  echo "===== adb logcat -d post-run dump ====="
  adb logcat -b all -d -v threadtime 2>/dev/null || true
} >> "$LOCAL_LOGCAT"

PID="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
echo "[backend-npu-attach-probe] pidof $APP_ID: ${PID:-<not-running>}"

ACTIVITY_STATE="$(adb shell dumpsys activity processes 2>/dev/null | grep -F "$APP_ID" | head -n 5 || true)"
echo "[backend-npu-attach-probe] dumpsys activity process lines:"
if [ -n "$ACTIVITY_STATE" ]; then
  printf '%s\n' "$ACTIVITY_STATE"
else
  echo "<none>"
fi

echo "[backend-npu-attach-probe] Collecting tombstone summary: $LOCAL_TOMBSTONE"
adb shell dumpsys dropbox --print > "$LOCAL_DROPBOX" 2>/dev/null || true
{
  echo "===== tombstoned command ====="
  adb shell timeout 2 tombstoned 2>&1 || true
  echo
  echo "===== /data/tombstones listing ====="
  adb shell ls -lt /data/tombstones 2>&1 || true
  echo
  echo "===== latest dropbox crash block for $APP_ID ====="
  extract_latest_dropbox_block_for_app "$LOCAL_DROPBOX" "$APP_ID"
  echo
  echo "===== current package logcat crash snippets ====="
  grep -Ei "$APP_ID|AndroidRuntime|DEBUG|DEBUGGERD|libc|crash_dump64|tombstoned|LiteRT|litert|QNN|Qnn|FastRPC|Backend.NPU|Engine.initialize|FATAL|SIGABRT|abort|tombstone" "$LOCAL_LOGCAT" | tail -n 200 || true
} > "$LOCAL_TOMBSTONE"

if adb shell run-as "$APP_ID" cat "$REMOTE_TXT" > "$LOCAL_TXT"; then
  echo "[backend-npu-attach-probe] wrote $LOCAL_TXT"
else
  echo "[backend-npu-attach-probe] failed to pull txt report from app files."
fi

if adb shell run-as "$APP_ID" cat "$REMOTE_MD" > "$LOCAL_MD"; then
  echo "[backend-npu-attach-probe] wrote $LOCAL_MD"
else
  echo "[backend-npu-attach-probe] failed to pull md report from app files."
fi

SIGNAL_LINE="$(extract_signal "$LOCAL_LOGCAT")"
ABORT_LINE="$(extract_abort_message "$LOCAL_LOGCAT")"
BACKTRACE_HEAD="$(extract_backtrace_head "$LOCAL_LOGCAT")"
if [ -z "$PID" ] || [ -n "$SIGNAL_LINE" ] || [ -n "$ABORT_LINE" ] || [ -n "$BACKTRACE_HEAD" ]; then
  NATIVE_CRASH_SUSPECTED=true
else
  NATIVE_CRASH_SUSPECTED=false
fi
PROCESS_ALIVE_STATE="$([ -n "$PID" ] && printf 'alive' || printf 'not-running')"

if [ -s "$LOCAL_TXT" ]; then
  {
    printf 'native_crash_suspected=%s\n' "$NATIVE_CRASH_SUSPECTED"
    printf 'signal=%s\n' "${SIGNAL_LINE:--}"
    printf 'abort_message=%s\n' "${ABORT_LINE:--}"
    printf 'backtrace_head=%s\n' "${BACKTRACE_HEAD:--}"
    printf 'logcat_file=%s\n' "$LOCAL_LOGCAT"
    printf 'tombstone_summary_file=%s\n' "$LOCAL_TOMBSTONE"
    printf 'dropbox_full_file=%s\n' "$LOCAL_DROPBOX"
  } >> "$LOCAL_TXT"
fi

if [ -s "$LOCAL_MD" ]; then
  {
    printf '\n## Native Crash Collection\n\n'
    printf '| key | value |\n'
    printf '| --- | --- |\n'
    printf '| native_crash_suspected | %s |\n' "$NATIVE_CRASH_SUSPECTED"
    printf '| signal | %s |\n' "${SIGNAL_LINE:--}"
    printf '| abort_message | %s |\n' "${ABORT_LINE:--}"
    printf '| backtrace_head | %s |\n' "${BACKTRACE_HEAD:--}"
    printf '| logcat_file | %s |\n' "$LOCAL_LOGCAT"
    printf '| tombstone_summary_file | %s |\n' "$LOCAL_TOMBSTONE"
    printf '| dropbox_full_file | %s |\n' "$LOCAL_DROPBOX"
  } >> "$LOCAL_MD"
fi

if [ ! -s "$LOCAL_TXT" ] || [ ! -s "$LOCAL_MD" ]; then
  echo "[backend-npu-attach-probe] app report missing; writing script fallback report."
  write_fallback_report "$LOCAL_TXT" "$LOCAL_MD" "$NATIVE_CRASH_SUSPECTED" "$SIGNAL_LINE" "$ABORT_LINE" "$BACKTRACE_HEAD" "$PROCESS_ALIVE_STATE"
fi

echo
echo "[backend-npu-attach-probe] report paths:"
echo "$LOCAL_TXT"
echo "$LOCAL_MD"
echo "$LOCAL_LOGCAT"
echo "$LOCAL_TOMBSTONE"
echo "$LOCAL_DROPBOX"

echo
echo "[backend-npu-attach-probe] Related logcat lines:"
grep -Ei "$LOGCAT_PATTERN" "$LOCAL_LOGCAT" | tail -n 300 || true

if [ ! -s "$LOCAL_TXT" ] || [ ! -s "$LOCAL_MD" ]; then
  echo "[backend-npu-attach-probe] missing report artifact."
  exit 1
fi

echo "[backend-npu-attach-probe] Done. Production ChatScreen and S1-S5 route are not connected by this probe."
