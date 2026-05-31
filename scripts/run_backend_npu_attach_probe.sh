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
WAIT_SECONDS=8

usage() {
  cat <<'USAGE'
Usage:
  scripts/run_backend_npu_attach_probe.sh [options]

Options:
  --flavor npuExperiment|customBuildExperiment|galleryStackExperiment
  --model-path PATH
  --phase inventory|engine_initialize|conversation|one_token_decode
  --engine-initialize   Explicitly opt in to Engine.initialize dry-run.
  --no-install          Do not run ./update.sh before probing.
  --wait SECONDS        Seconds to wait before pulling reports. Default: 8.

Default is safe inventory/config dry-run only. It does not connect Backend.NPU
to ChatScreen, does not create Conversation, and does not generate tokens.
USAGE
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
  *)
    echo "[backend-npu-attach-probe] unsupported flavor: $FLAVOR"
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

echo "[backend-npu-attach-probe] Starting probe. runId=$RUN_ID flavor=$FLAVOR phase=$PHASE engineInitialize=$ENGINE_INITIALIZE"
if [ -n "$MODEL_PATH" ]; then
  adb shell am start -W \
    -n "$APP_ID/$ACTIVITY" \
    --ez run_backend_npu_attach_probe true \
    --ez run_engine_initialize_dry_run "$ENGINE_INITIALIZE" \
    --ez diagnostic_files_cleared_before_run true \
    --es run_id "$RUN_ID" \
    --es phase "$PHASE" \
    --es model_path "$MODEL_PATH"
else
  adb shell am start -W \
    -n "$APP_ID/$ACTIVITY" \
    --ez run_backend_npu_attach_probe true \
    --ez run_engine_initialize_dry_run "$ENGINE_INITIALIZE" \
    --ez diagnostic_files_cleared_before_run true \
    --es run_id "$RUN_ID" \
    --es phase "$PHASE"
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

PID="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
echo "[backend-npu-attach-probe] pidof $APP_ID: ${PID:-<not-running>}"

ACTIVITY_STATE="$(adb shell dumpsys activity processes 2>/dev/null | grep -F "$APP_ID" | head -n 5 || true)"
echo "[backend-npu-attach-probe] dumpsys activity process lines:"
if [ -n "$ACTIVITY_STATE" ]; then
  printf '%s\n' "$ACTIVITY_STATE"
else
  echo "<none>"
fi

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

echo
echo "[backend-npu-attach-probe] report paths:"
echo "$LOCAL_TXT"
echo "$LOCAL_MD"

echo
echo "[backend-npu-attach-probe] Related logcat lines:"
adb logcat -d -t 500 2>/dev/null | grep -Ei "Backend.NPU|NpuExperimentProbe|AcceleratorProbe|Engine.initialize|Conversation|generate|LiteRt|Dispatch|QNN|NPU|FATAL|SIGABRT|mismatch|UnsatisfiedLinkError" || true

if [ ! -s "$LOCAL_TXT" ] || [ ! -s "$LOCAL_MD" ]; then
  echo "[backend-npu-attach-probe] missing report artifact."
  exit 1
fi

echo "[backend-npu-attach-probe] Done. Production ChatScreen and S1-S5 route are not connected by this probe."
