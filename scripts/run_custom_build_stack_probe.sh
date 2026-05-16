#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACT_DIR="${1:-artifacts/litert_custom_build/20260516_235244}"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuExperimentProbeActivity"
RUN_ENGINE_DRY_RUN=false
MODEL_PATH=""
RUN_ID="$(date +%s%3N 2>/dev/null || date +%s)"
SNAPSHOT_FILE="files/npu_experiment_probe.txt"
DRY_RUN_FILE="files/npu_engine_initialize_dry_run.txt"
CRASH_MARKER_FILE="files/npu_engine_initialize_crash_marker.txt"
LAST_STAGE_FILE="files/npu_engine_initialize_last_stage.txt"
DEFAULT_MODEL_BASENAME="gemma-4-E2B-it_qualcomm_sm8750.litertlm"

shift $(( $# > 0 ? 1 : 0 ))
while [ "$#" -gt 0 ]; do
  case "$1" in
    --engine-dry-run)
      RUN_ENGINE_DRY_RUN=true
      shift
      ;;
    --model-path)
      MODEL_PATH="${2:-}"
      shift 2
      ;;
    *)
      echo "[custom-build-probe] unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1

echo "[custom-build-probe] staging custom built native stack..."
bash scripts/stage_litert_custom_build_stack_for_experiment.sh "$ARTIFACT_DIR" || exit $?

echo "[custom-build-probe] building customBuildExperimentDebug APK..."
./gradlew :app:assembleCustomBuildExperimentDebug || exit $?

CUSTOM_APK="app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk"
STANDARD_APK="app/build/outputs/apk/standard/debug/app-standard-debug.apk"
NPU_APK="app/build/outputs/apk/npuExperiment/debug/app-npuExperiment-debug.apk"
GALLERY_APK="app/build/outputs/apk/galleryStackExperiment/debug/app-galleryStackExperiment-debug.apk"

echo
echo "[custom-build-probe] customBuildExperiment APK native stack:"
unzip -l "$CUSTOM_APK" 2>/dev/null | grep -E "libLiteRt|liblitertlm|libGemma|libQnn" || true

echo
echo "[custom-build-probe] standardDebug leakage check:"
if unzip -l "$STANDARD_APK" 2>/dev/null | grep -E "libLiteRtDispatch_Qualcomm|libGemmaModelConstraintProvider|libLiteRtCompilerPlugin_Qualcomm|customnpu"; then
  echo "[custom-build-probe] ERROR: standardDebug custom stack leakage detected." >&2
  exit 3
else
  echo "[custom-build-probe] no custom stack leakage detected in standardDebug."
fi

echo
echo "[custom-build-probe] npuExperimentDebug leakage check:"
if unzip -l "$NPU_APK" 2>/dev/null | grep -E "libLiteRtCompilerPlugin_Qualcomm|libGemmaModelConstraintProvider"; then
  echo "[custom-build-probe] ERROR: npuExperimentDebug custom stack leakage detected." >&2
  exit 4
else
  echo "[custom-build-probe] no custom-only stack leakage detected in npuExperimentDebug."
fi

echo
echo "[custom-build-probe] galleryStackExperimentDebug custom-only leakage check:"
if unzip -l "$GALLERY_APK" 2>/dev/null | grep -E "libLiteRtCompilerPlugin_Qualcomm|libGemmaModelConstraintProvider"; then
  echo "[custom-build-probe] ERROR: galleryStackExperimentDebug custom-only leakage detected." >&2
  exit 5
else
  echo "[custom-build-probe] no custom-only leakage detected in galleryStackExperimentDebug."
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "[custom-build-probe] adb not found; skipping install/probe."
  exit 0
fi
DEVICE_COUNT="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$DEVICE_COUNT" -lt 1 ]; then
  echo "[custom-build-probe] no adb device connected; skipping install/probe."
  exit 0
fi

echo
echo "[custom-build-probe] installing customBuildExperimentDebug..."
./update.sh update --flavor customBuildExperiment || exit $?

echo "[custom-build-probe] clearing probe files..."
adb shell run-as "$APP_ID" rm -f "$SNAPSHOT_FILE" "$DRY_RUN_FILE" "$CRASH_MARKER_FILE" "$LAST_STAGE_FILE" >/dev/null 2>&1 || true
adb logcat -c >/dev/null 2>&1 || true

if [ -z "$MODEL_PATH" ]; then
  MODEL_PATH="/data/user/0/$APP_ID/files/local_models/$DEFAULT_MODEL_BASENAME"
fi

if [ "$RUN_ENGINE_DRY_RUN" = "true" ]; then
  echo "[custom-build-probe] verifying model path: $MODEL_PATH"
  MODEL_EXISTS="$(adb shell run-as "$APP_ID" test -f "$MODEL_PATH" '&&' echo yes 2>/dev/null | tr -d '\r' || true)"
  if [ "$MODEL_EXISTS" != "yes" ]; then
    echo "[custom-build-probe] model missing in app files; trying /data/local/tmp/$DEFAULT_MODEL_BASENAME"
    adb shell run-as "$APP_ID" mkdir -p files/local_models >/dev/null 2>&1 || true
    adb shell run-as "$APP_ID" cp "/data/local/tmp/$DEFAULT_MODEL_BASENAME" "files/local_models/$DEFAULT_MODEL_BASENAME" >/dev/null 2>&1 || true
  fi
fi

echo "[custom-build-probe] starting probe Activity. engineDryRun=$RUN_ENGINE_DRY_RUN runId=$RUN_ID"
if [ "$RUN_ENGINE_DRY_RUN" = "true" ]; then
  adb shell am start -W -n "$APP_ID/$ACTIVITY" \
    --ez run_engine_initialize_dry_run true \
    --ez diagnostic_files_cleared_before_run true \
    --es run_id "$RUN_ID" \
    --es model_path "$MODEL_PATH"
else
  adb shell am start -W -n "$APP_ID/$ACTIVITY" \
    --ez run_engine_initialize_dry_run false \
    --ez diagnostic_files_cleared_before_run true \
    --es run_id "$RUN_ID"
fi

sleep 3

echo
echo "[custom-build-probe] pidof $APP_ID:"
PID="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
printf '%s\n' "${PID:-<not-running>}"

echo
echo "[custom-build-probe] nativeLibraryDir probe:"
adb shell run-as "$APP_ID" ls -l "$(adb shell run-as "$APP_ID" sh -c 'dirname $(find /data/app -name libLiteRtDispatch_Qualcomm.so 2>/dev/null | head -n 1)' 2>/dev/null | tr -d '\r')" 2>/dev/null || true

echo
echo "[custom-build-probe] probe snapshot:"
SNAPSHOT="$(adb shell run-as "$APP_ID" cat "$SNAPSHOT_FILE" 2>/dev/null | tr -d '\r' || true)"
if [ -n "$SNAPSHOT" ]; then
  printf '%s\n' "$SNAPSHOT"
else
  echo "<missing>"
fi

if [ "$RUN_ENGINE_DRY_RUN" = "true" ]; then
  echo
  echo "[custom-build-probe] engine dry-run stage file:"
  adb shell run-as "$APP_ID" cat "$DRY_RUN_FILE" || true
fi

echo
echo "[custom-build-probe] related logcat lines:"
adb logcat -b all -d -t 5000 2>/dev/null | grep -Ei "Custom Build Stack|NpuExperimentProbe|AcceleratorProbe|LiteRt|LiteRT|litert|Dispatch|dispatch|QNN|Qnn|HTP|Htp|ADSP|nativeCreateEngine|customnpu|linker|dlopen|CheckRuntimeCompatibility|RuntimeCApi|NPU|lami|FATAL|SIGABRT|SIGSEGV" || true

if [ "$RUN_ENGINE_DRY_RUN" = "true" ] && { [ -z "$PID" ] || [ -z "$SNAPSHOT" ]; }; then
  echo
  echo "[custom-build-probe] crash suspected or snapshot missing; collecting customnpu diagnostics..."
  if bash scripts/collect_npu_tombstone_diagnostics.sh \
    --app-id "$APP_ID" \
    --label customnpu \
    --run-id "$RUN_ID"; then
    echo "[custom-build-probe] collector finished."
  else
    echo "[custom-build-probe] warning: collector failed; continuing." >&2
  fi
fi

echo
if [ "$RUN_ENGINE_DRY_RUN" = "true" ]; then
  echo "[custom-build-probe] Done. Explicit Engine.initialize dry-run was requested; Conversation/generateResponse were not called."
else
  echo "[custom-build-probe] Done. Engine.initialize dry-run was not requested."
fi
