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
NATIVE_DIAG_FILE="files/qairt244_native_diag.txt"
LINKER_DEBUG_PROP="debug.ld.app.$APP_ID"
LINKER_DEBUG_VALUE="dlerror,dlopen,dlsym"
DEFAULT_MODEL_BASENAME="gemma-4-E2B-it_qualcomm_sm8750.litertlm"
EXPECTED_QAIRT244_LITERT_BUILD_ID="a03032ad1eeefda446478aea308c2ed0"
EXPECTED_QAIRT244_DISPATCH_BUILD_ID="a8006da3bd9b4fdf5b7131f8d864b6ee"
EXPECTED_QAIRT244_LITERTLM_JNI_BUILD_ID="b78167f717866bbc1d9a981f01fb0334"
EXPECTED_QAIRT244_COMPILER_PLUGIN_BUILD_ID="443391d4c4348191230b67a3ab8a6037"
EXPECTED_QAIRT244_GEMMA_PROVIDER_BUILD_ID="f9e5e73e668032550042319e43012011"
EXPECTED_QAIRT244_LOGGING_LITERT_BUILD_ID="731b74da505bef341a184b3778d0412d"
EXPECTED_QAIRT244_LOGGING_DISPATCH_BUILD_ID="042452227c659a546d4008455d231580"
EXPECTED_QAIRT244_LOGGING_LITERTLM_JNI_BUILD_ID="8554bcd057031088ad9bb2100f1f8f94"
EXPECTED_QAIRT244_LOGGING_COMPILER_PLUGIN_BUILD_ID="e566cda2e3179428c73cdd5e33c5d702"
EXPECTED_QAIRT244_LITERT_SHA256="84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553"
EXPECTED_QAIRT244_DISPATCH_SHA256="00c26484621ab42bea6e3bee0d7e908451a428cf19cbd1ebfecf4ccee79e1739"
EXPECTED_QAIRT244_LITERTLM_JNI_SHA256="310e37ff7cf770c24d636bbb0f9647a0d59dd893ba0c2530acdfc06569704230"
EXPECTED_QAIRT244_COMPILER_PLUGIN_SHA256="c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c"
EXPECTED_QAIRT244_GEMMA_PROVIDER_SHA256="45ca57e55d52976e5d2dadfc0e874499fc0671c169a28077772c25264f9d81f6"
EXPECTED_QAIRT244_LOGGING_LITERT_SHA256="3ba100245ed79d45abf3c34230aee77d6aabd0b6c302a1ce8dd060b95575e7ec"
EXPECTED_QAIRT244_LOGGING_DISPATCH_SHA256="1491a945fff9858861c5c75fa071a111dcd9870a82d92b9801c59dc7b2e9ebe8"
EXPECTED_QAIRT244_LOGGING_LITERTLM_JNI_SHA256="462d69fbb71a7bb5e2aa74562959885e7d4f647fc92f4725e726039bbae57474"
EXPECTED_QAIRT244_LOGGING_COMPILER_PLUGIN_SHA256="3b25d9739c998b294fb92e7406edcf49ec0cc7f148fb5d67f2e9da32ab2f6583"

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

clear_linker_debug_prop() {
  if [ "${LINKER_DEBUG_PROP_SET:-false}" = "true" ]; then
    adb shell "setprop '$LINKER_DEBUG_PROP' ''" >/dev/null 2>&1 || true
  fi
}

trap clear_linker_debug_prop EXIT

print_expected_qairt244_stack() {
  cat <<EOF
[custom-build-probe] expected QAIRT 2.44 custom stack:
  libLiteRt.so build_id=$EXPECTED_QAIRT244_LITERT_BUILD_ID sha256=$EXPECTED_QAIRT244_LITERT_SHA256
  libLiteRtDispatch_Qualcomm.so build_id=$EXPECTED_QAIRT244_DISPATCH_BUILD_ID sha256=$EXPECTED_QAIRT244_DISPATCH_SHA256
  liblitertlm_jni.so build_id=$EXPECTED_QAIRT244_LITERTLM_JNI_BUILD_ID sha256=$EXPECTED_QAIRT244_LITERTLM_JNI_SHA256
  libLiteRtCompilerPlugin_Qualcomm.so build_id=$EXPECTED_QAIRT244_COMPILER_PLUGIN_BUILD_ID sha256=$EXPECTED_QAIRT244_COMPILER_PLUGIN_SHA256
  libGemmaModelConstraintProvider.so build_id=$EXPECTED_QAIRT244_GEMMA_PROVIDER_BUILD_ID sha256=$EXPECTED_QAIRT244_GEMMA_PROVIDER_SHA256
  accepted logging diagnostic stack:
  libLiteRt.so build_id=$EXPECTED_QAIRT244_LOGGING_LITERT_BUILD_ID sha256=$EXPECTED_QAIRT244_LOGGING_LITERT_SHA256
  libLiteRtDispatch_Qualcomm.so build_id=$EXPECTED_QAIRT244_LOGGING_DISPATCH_BUILD_ID sha256=$EXPECTED_QAIRT244_LOGGING_DISPATCH_SHA256
  liblitertlm_jni.so build_id=$EXPECTED_QAIRT244_LOGGING_LITERTLM_JNI_BUILD_ID sha256=$EXPECTED_QAIRT244_LOGGING_LITERTLM_JNI_SHA256
  libLiteRtCompilerPlugin_Qualcomm.so build_id=$EXPECTED_QAIRT244_LOGGING_COMPILER_PLUGIN_BUILD_ID sha256=$EXPECTED_QAIRT244_LOGGING_COMPILER_PLUGIN_SHA256
  libGemmaModelConstraintProvider.so build_id=$EXPECTED_QAIRT244_GEMMA_PROVIDER_BUILD_ID sha256=$EXPECTED_QAIRT244_GEMMA_PROVIDER_SHA256
EOF
}

print_actual_custom_stack_from_snapshot() {
  if [ -n "${SNAPSHOT:-}" ]; then
    echo "[custom-build-probe] actual custom stack line from probe snapshot:"
    printf '%s\n' "$SNAPSHOT" | grep -F "Custom Build Stack Compatibility:" || true
  else
    echo "[custom-build-probe] actual custom stack snapshot is missing."
  fi
}

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
./gradlew :app:installCustomBuildExperimentDebug || exit $?

echo "[custom-build-probe] clearing probe files..."
adb shell run-as "$APP_ID" rm -f "$SNAPSHOT_FILE" "$DRY_RUN_FILE" "$CRASH_MARKER_FILE" "$LAST_STAGE_FILE" "$NATIVE_DIAG_FILE" >/dev/null 2>&1 || true
adb logcat -c >/dev/null 2>&1 || true

if [ "$RUN_ENGINE_DRY_RUN" = "true" ]; then
  echo "[custom-build-probe] enabling linker debug property: $LINKER_DEBUG_PROP=$LINKER_DEBUG_VALUE"
  adb shell setprop "$LINKER_DEBUG_PROP" "$LINKER_DEBUG_VALUE" >/dev/null 2>&1 || true
  LINKER_DEBUG_PROP_SET=true
  echo "[custom-build-probe] linker debug property now:"
  adb shell getprop "$LINKER_DEBUG_PROP" 2>/dev/null | tr -d '\r' || true
fi

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
  DRY_RUN_STAGE="$(adb shell run-as "$APP_ID" cat "$DRY_RUN_FILE" 2>/dev/null | tr -d '\r' || true)"
  if [ -n "$DRY_RUN_STAGE" ]; then
    printf '%s\n' "$DRY_RUN_STAGE"
  else
    echo "<missing>"
  fi
  echo
  echo "[custom-build-probe] native file logger:"
  NATIVE_DIAG="$(adb shell run-as "$APP_ID" cat "$NATIVE_DIAG_FILE" 2>/dev/null | tr -d '\r' || true)"
  if [ -n "$NATIVE_DIAG" ]; then
    printf '%s\n' "$NATIVE_DIAG"
  else
    echo "<missing>"
  fi
  if printf '%s\n%s\n' "$SNAPSHOT" "$DRY_RUN_STAGE" | grep -q "custom-stack-build-id-mismatch"; then
    echo
    echo "[custom-build-probe] ERROR: Engine.initialize dry-run skipped because packaged custom stack Build IDs did not match expected values." >&2
    print_expected_qairt244_stack >&2
    print_actual_custom_stack_from_snapshot >&2
    CUSTOM_STACK_BUILD_ID_MISMATCH=true
  else
    CUSTOM_STACK_BUILD_ID_MISMATCH=false
  fi
fi

echo
echo "[custom-build-probe] related logcat lines:"
adb logcat -b all -d -t 5000 2>/dev/null | grep -Ei "Custom Build Stack|NpuExperimentProbe|AcceleratorProbe|LiteRt|LiteRT|litert|Dispatch|dispatch|QNN|Qnn|HTP|Htp|ADSP|nativeCreateEngine|customnpu|linker|linker64|dlopen|dlerror|dlsym|cannot locate|library .* not found|needed by|namespace|qairt244_dlopen_trace_v1|CheckRuntimeCompatibility|RuntimeCApi|NPU|lami|FATAL|SIGABRT|SIGSEGV" || true

if [ "$RUN_ENGINE_DRY_RUN" = "true" ]; then
  echo
  echo "[custom-build-probe] linker debug property after run:"
  adb shell getprop "$LINKER_DEBUG_PROP" 2>/dev/null | tr -d '\r' || true
fi

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

if [ "${CUSTOM_STACK_BUILD_ID_MISMATCH:-false}" = "true" ]; then
  exit 6
fi
