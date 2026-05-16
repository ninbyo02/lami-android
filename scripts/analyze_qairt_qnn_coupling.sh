#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILT_STACK="${BUILT_STACK:-$ROOT_DIR/artifacts/litert_custom_build/20260516_235244}"
CUSTOM_CRASH="${CUSTOM_CRASH:-$ROOT_DIR/artifacts/npu_diagnostics/20260517_005032_customnpu}"
QAIRT_HOME="${QAIRT_HOME:-/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424}"
QAIRT_OVERLAY="${QAIRT_OVERLAY:-/home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225}"
GALLERY_APK="${GALLERY_APK:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
CUSTOM_APK="${CUSTOM_APK:-$ROOT_DIR/app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk}"
MODEL_PATH="${MODEL_PATH:-/data/user/0/io.github.ninbyo02.lami.customnpu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm}"
LOCAL_MODEL="${LOCAL_MODEL:-}"
APP_ID="${APP_ID:-io.github.ninbyo02.lami.customnpu}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt_qnn_coupling/$TIMESTAMP"
EXTRACT_DIR="$OUT_DIR/extracted"

KEYWORDS='QNN|Qnn|HTP|Htp|V79|V75|V73|V69|SM8750|Hexagon|capabilit|backend|skeleton|skel|stub|ADSP|LD_LIBRARY_PATH|DSP_LIBRARY|QNN manager|QnnInterface|QnnSystem|QnnHtp|QnnDevice|QnnBackend|QnnGraph|QnnContext|QnnProperty|QnnLog|unsupported|insufficient|version|api|schema|model|dispatch|LiteRtDispatch|No usable Dispatch runtime found|Failed to create a dispatch delegate kernel|Failed to initialize Dispatch API|LiteRtDispatchCheckRuntimeCompatibility'

BUILT_LIBS=(
  libLiteRt.so
  libLiteRtDispatch_Qualcomm.so
  liblitertlm_jni.so
  libLiteRtCompilerPlugin_Qualcomm.so
  libGemmaModelConstraintProvider.so
)

GALLERY_LIBS=(
  libLiteRt.so
  libLiteRtDispatch_Qualcomm.so
  liblitertlm_jni.so
  libQnnSystem.so
  libQnnHtp.so
  libQnnHtpPrepare.so
  libQnnHtpV79Stub.so
  libQnnHtpV79Skel.so
)

QNN_LIBS=(
  libQnnSystem.so
  libQnnHtp.so
  libQnnHtpPrepare.so
  libQnnHtpV79Stub.so
  libQnnHtpV79Skel.so
  libQnnDsp.so
  libQnnGpu.so
  libQnnTFLiteDelegate.so
)

CUSTOM_APK_LIBS=(
  libLiteRt.so
  libLiteRtDispatch_Qualcomm.so
  liblitertlm_jni.so
  libLiteRtCompilerPlugin_Qualcomm.so
  libGemmaModelConstraintProvider.so
  libQnnSystem.so
  libQnnHtp.so
  libQnnHtpPrepare.so
  libQnnHtpV79Stub.so
  libQnnHtpV79Skel.so
  libQnnDsp.so
  libQnnGpu.so
  libQnnTFLiteDelegate.so
  libllm_inference_engine_jni.so
)

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR" "$EXTRACT_DIR"

log() {
  printf '[qairt-qnn-coupling] %s\n' "$*"
}

sha_for() {
  local file="$1"
  if [ -f "$file" ] && command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf -- '-'
  fi
}

size_for() {
  local file="$1"
  if [ -f "$file" ]; then
    wc -c <"$file" 2>/dev/null | tr -d ' '
  else
    printf -- '-'
  fi
}

build_id_for() {
  local file="$1"
  if [ -f "$file" ] && command -v readelf >/dev/null 2>&1; then
    readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
  else
    printf -- '-'
  fi
}

soname_for() {
  local file="$1"
  if [ -f "$file" ] && command -v readelf >/dev/null 2>&1; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Library soname: \[\(.*\)\].*/\1/p' | head -n 1
  else
    printf -- '-'
  fi
}

needed_for() {
  local file="$1"
  if [ -f "$file" ] && command -v readelf >/dev/null 2>&1; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | paste -sd ',' -
  else
    printf -- '-'
  fi
}

exports_for() {
  local file="$1"
  local out="$2"
  if [ -f "$file" ] && command -v nm >/dev/null 2>&1; then
    nm -D --defined-only "$file" >"$out" 2>/dev/null || true
  else
    printf '<unavailable>\n' >"$out"
  fi
}

undefined_for() {
  local file="$1"
  local out="$2"
  if [ -f "$file" ] && command -v nm >/dev/null 2>&1; then
    nm -D -u "$file" >"$out" 2>/dev/null || true
  else
    printf '<unavailable>\n' >"$out"
  fi
}

strings_for() {
  local file="$1"
  local out="$2"
  if [ -f "$file" ] && command -v strings >/dev/null 2>&1; then
    strings "$file" 2>/dev/null | grep -E "$KEYWORDS" | sort -u >"$out" || true
  else
    printf '<missing>\n' >"$out"
  fi
}

extract_apk_lib() {
  local apk="$1"
  local lib="$2"
  local out="$3"
  if [ -f "$apk" ]; then
    if unzip -p "$apk" "lib/arm64-v8a/$lib" >"$out" 2>/dev/null; then
      return 0
    fi
    rm -f "$out"
    return 1
  else
    return 1
  fi
}

qairt_path_for() {
  local lib="$1"
  case "$lib" in
    libQnnHtpV79Skel.so)
      printf '%s\n' "$QAIRT_HOME/lib/hexagon-v79/unsigned/$lib"
      ;;
    *)
      printf '%s\n' "$QAIRT_HOME/lib/aarch64-android/$lib"
      ;;
  esac
}

record_lib() {
  local label="$1"
  local lib="$2"
  local file="$3"
  local safe_label="${label//[^A-Za-z0-9_.-]/_}"
  local safe_lib="${lib//[^A-Za-z0-9_.-]/_}"
  local strings_out="$OUT_DIR/${safe_label}_${safe_lib}.strings.txt"
  local exports_out="$OUT_DIR/${safe_label}_${safe_lib}.exports.txt"
  local undefined_out="$OUT_DIR/${safe_label}_${safe_lib}.undefined.txt"

  if [ -f "$file" ]; then
    strings_for "$file" "$strings_out"
    exports_for "$file" "$exports_out"
    undefined_for "$file" "$undefined_out"
    printf '%s\t%s\tpresent\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$label" \
      "$lib" \
      "$file" \
      "$(size_for "$file")" \
      "$(sha_for "$file")" \
      "$(build_id_for "$file")" \
      "$(soname_for "$file")" \
      "$(needed_for "$file")" \
      "$(wc -l <"$strings_out" 2>/dev/null | tr -d ' ')" >>"$OUT_DIR/qnn_lib_matrix.tsv"
    if [ "$(needed_for "$file")" != "-" ]; then
      needed_for "$file" | tr ',' '\n' | while IFS= read -r needed; do
        [ -n "$needed" ] && printf '%s\t%s\t%s\n' "$label" "$lib" "$needed" >>"$OUT_DIR/needed_matrix.tsv"
      done
    fi
  else
    printf '%s\t%s\tmissing\t%s\t-\t-\t-\t-\t-\t0\n' "$label" "$lib" "$file" >>"$OUT_DIR/qnn_lib_matrix.tsv"
  fi
}

write_context() {
  {
    printf 'timestamp=%s\n' "$TIMESTAMP"
    printf 'builtStack=%s\n' "$BUILT_STACK"
    printf 'customCrash=%s\n' "$CUSTOM_CRASH"
    printf 'QAIRT_HOME=%s\n' "$QAIRT_HOME"
    printf 'QAIRT_OVERLAY=%s\n' "$QAIRT_OVERLAY"
    printf 'galleryApk=%s\n' "$GALLERY_APK"
    printf 'customApk=%s\n' "$CUSTOM_APK"
    printf 'modelPath=%s\n' "$MODEL_PATH"
    printf 'appId=%s\n' "$APP_ID"
    printf 'localModel=%s\n' "${LOCAL_MODEL:-<unset>}"
    printf 'scope=%s\n' 'static-analysis-only; no build; no install; no app launch; no Engine.initialize'
  } >"$OUT_DIR/context.env"
}

write_context

printf 'label\tlibrary\tstatus\tpath\tsize\tsha256\tbuild_id\tsoname\tneeded\tfiltered_string_hits\n' >"$OUT_DIR/qnn_lib_matrix.tsv"
printf 'label\tlibrary\tneeded_library\n' >"$OUT_DIR/needed_matrix.tsv"

for lib in "${BUILT_LIBS[@]}"; do
  file="$BUILT_STACK/built_libs/$lib"
  if [ "$lib" = "libGemmaModelConstraintProvider.so" ] && [ ! -f "$file" ]; then
    file="/home/sato/project/litert-custom-build/LiteRT-LM/prebuilt/android_arm64/$lib"
  fi
  record_lib "built" "$lib" "$file"
done

for lib in "${GALLERY_LIBS[@]}"; do
  out="$EXTRACT_DIR/gallery_$lib"
  if extract_apk_lib "$GALLERY_APK" "$lib" "$out"; then
    record_lib "gallery-sm8750" "$lib" "$out"
  else
    record_lib "gallery-sm8750" "$lib" "$out"
  fi
done

for lib in "${QNN_LIBS[@]}"; do
  record_lib "qairt-2.46" "$lib" "$(qairt_path_for "$lib")"
done

for lib in "${CUSTOM_APK_LIBS[@]}"; do
  out="$EXTRACT_DIR/custom_apk_$lib"
  if extract_apk_lib "$CUSTOM_APK" "$lib" "$out"; then
    record_lib "customBuildExperiment-apk" "$lib" "$out"
  else
    record_lib "customBuildExperiment-apk" "$lib" "$out"
  fi
done

{
  printf 'library\tpackaged_in_custom_apk\n'
  for lib in "${CUSTOM_APK_LIBS[@]}"; do
    if [ -f "$EXTRACT_DIR/custom_apk_$lib" ]; then
      printf '%s\ttrue\n' "$lib"
    else
      printf '%s\tfalse\n' "$lib"
    fi
  done
} >"$OUT_DIR/custom_apk_packaged_libs.tsv"

{
  printf 'library\tmapped_in_tombstone\tbuild_id\tmatching_lines\n'
  for lib in "${CUSTOM_APK_LIBS[@]}"; do
    if [ -f "$CUSTOM_CRASH/tombstone_app_extract.txt" ] && grep -q "$lib" "$CUSTOM_CRASH/tombstone_app_extract.txt"; then
      ids="$(grep "$lib" "$CUSTOM_CRASH/tombstone_app_extract.txt" | sed -n 's/.*BuildId: \([0-9a-f]*\).*/\1/p' | sort -u | paste -sd ',' -)"
      lines="$(grep -c "$lib" "$CUSTOM_CRASH/tombstone_app_extract.txt" 2>/dev/null || true)"
      printf '%s\ttrue\t%s\t%s\n' "$lib" "${ids:-unknown}" "$lines"
    else
      printf '%s\tfalse\t-\t0\n' "$lib"
    fi
  done
} >"$OUT_DIR/loaded_libs_matrix.tsv"

{
  printf '# QAIRT/QNN version summary\n\n'
  printf '%s\n' "- QAIRT_HOME: \`$QAIRT_HOME\`"
  printf '%s\n' "- QAIRT overlay: \`$QAIRT_OVERLAY\`"
  if [ -f "$QAIRT_HOME/version.txt" ]; then
    printf '\n## QAIRT version.txt\n\n```text\n'
    sed -n '1,80p' "$QAIRT_HOME/version.txt"
    printf '```\n'
  fi
  if [ -f "$QAIRT_HOME/include/QNN/QnnCommon.h" ]; then
    printf '\n## QNN header version hints\n\n```text\n'
    grep -R "QNN.*VERSION\\|API_VERSION\\|QNN_VERSION" "$QAIRT_HOME/include" 2>/dev/null | head -n 80 || true
    printf '```\n'
  fi
  printf '\n## Matrix files\n\n'
  printf '%s\n' '- `qnn_lib_matrix.tsv`'
  printf '%s\n' '- `needed_matrix.tsv`'
  printf '%s\n' '- `loaded_libs_matrix.tsv`'
  printf '%s\n' '- `custom_apk_packaged_libs.tsv`'
} >"$OUT_DIR/qairt_version_summary.md"

{
  printf '# Model metadata probe\n\n'
  printf '%s\n' "- Requested model path: \`$MODEL_PATH\`"
  if [ -n "$LOCAL_MODEL" ] && [ -f "$LOCAL_MODEL" ]; then
    printf '%s\n' "- Local model: \`$LOCAL_MODEL\`"
    printf '%s\n' "- Size: \`$(size_for "$LOCAL_MODEL")\`"
    printf '%s\n' "- SHA-256: \`$(sha_for "$LOCAL_MODEL")\`"
    printf '\n## file\n\n```text\n'
    file "$LOCAL_MODEL" 2>/dev/null || true
    printf '```\n\n## filtered strings\n\n```text\n'
    strings "$LOCAL_MODEL" 2>/dev/null | grep -E "$KEYWORDS" | head -n 200 || true
    printf '```\n'
  elif command -v adb >/dev/null 2>&1 && adb devices 2>/dev/null | awk 'NR > 1 && $2 == "device" {found=1} END {exit !found}'; then
    printf '%s\n' '- Local model not set. Device is connected; collecting non-invasive file metadata only.'
    model_base="$(basename "$MODEL_PATH")"
    printf '\n```text\n'
    adb shell run-as "$APP_ID" sh -c "pwd; ls -l files/local_models 2>/dev/null || true; ls -l files/local_models/$model_base 2>/dev/null || true" 2>&1 || true
    printf '```\n'
  else
    printf '%s\n' '- Local model not set and no adb device available. Metadata limited to prior dry-run: size 3016294400 bytes, exists/canRead true.'
  fi
} >"$OUT_DIR/model_metadata_probe.txt"

if command -v adb >/dev/null 2>&1 && adb devices 2>/dev/null | awk 'NR > 1 && $2 == "device" {found=1} END {exit !found}'; then
  {
    printf '# Device staged QAIRT probe\n\n'
    printf 'Static shell inspection only. No app install or launch.\n\n'
    printf '## /data/local/tmp candidates\n\n```text\n'
    adb shell "find /data/local/tmp -maxdepth 4 -type f \\( -name 'libQnn*.so' -o -name 'qnn-*' -o -name 'qnn_*' \\) 2>/dev/null | sort" 2>&1 || true
    printf '```\n'
  } >"$OUT_DIR/device_staged_qairt_probe.md"
else
  printf '# Device staged QAIRT probe\n\nNo adb device connected.\n' >"$OUT_DIR/device_staged_qairt_probe.md"
fi

{
  printf '# QAIRT/QNN coupling static analysis summary\n\n'
  printf '%s\n' "- Output: \`$OUT_DIR\`"
  printf '%s\n' "- Scope: static analysis only; no build, no install, no app launch, no Engine.initialize."
  printf '%s\n' "- Custom crash source: \`$CUSTOM_CRASH\`"
  printf '\n## Key generated files\n\n'
  printf '%s\n' '- `qnn_lib_matrix.tsv`'
  printf '%s\n' '- `needed_matrix.tsv`'
  printf '%s\n' '- `loaded_libs_matrix.tsv`'
  printf '%s\n' '- `custom_apk_packaged_libs.tsv`'
  printf '%s\n' '- `qairt_version_summary.md`'
  printf '%s\n' '- `model_metadata_probe.txt`'
  printf '%s\n' '- per-library `*.strings.txt`, `*.exports.txt`, and `*.undefined.txt`'
  printf '\n## Initial reading\n\n'
  printf '%s\n' '- The custom APK packages QNN/HTP libraries from variant dependencies/staged sources; this must be evaluated separately from same-source LiteRT build outputs.'
  printf '%s\n' '- Tombstone mapping should be read from `loaded_libs_matrix.tsv`, not from older collector nativeLibraryDir metadata.'
  printf '%s\n' '- If QNN prepare/stub/skel libraries are packaged but not mapped in the crash tombstone, the failure may occur before those components are loaded, or lazy loading may not have reached them.'
  printf '%s\n' '- Absence of `libLiteRtRuntimeCApi.so` remains weak evidence unless a NEEDED edge, string, or loader error appears in the generated matrices.'
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
log "done; no build, no install, no app launch, no Engine.initialize"
