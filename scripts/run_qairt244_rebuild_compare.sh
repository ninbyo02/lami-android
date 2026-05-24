#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/artifacts/qairt244_rebuild_compare/$TIMESTAMP}"

LITERT_LM_DIR="${LITERT_LM_DIR:-$HOME/project/litert-custom-build/LiteRT-LM}"
QAIRT_ROOT="${QAIRT_ROOT:-$HOME/compose/qairt/workspace/sdk/qairt/2.44.0.260225}"
PREVIOUS_BUILD="${PREVIOUS_BUILD:-$ROOT_DIR/artifacts/litert_custom_build/20260516_235244}"
GALLERY_APK="${GALLERY_APK:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
CUSTOM_APK="${CUSTOM_APK:-$ROOT_DIR/app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk}"
LOCAL_QAIRT_246="${LOCAL_QAIRT_246:-$HOME/compose/qairt/workspace/sdk/qairt/2.46.0.260424}"

log() {
  printf '[qairt244-rebuild-compare] %s\n' "$*"
}

die() {
  log "ERROR: $*"
  printf 'ERROR: %s\n' "$*" >>"$OUT_DIR/ERROR.txt"
  exit 1
}

metadata_for() {
  local file="$1"
  local out="$2"
  {
    printf 'path=%s\n' "$file"
    if [ ! -f "$file" ]; then
      printf 'present=false\n'
      return
    fi
    printf 'present=true\n'
    printf 'size='
    wc -c <"$file" 2>/dev/null || true
    printf 'sha256='
    sha256sum "$file" 2>/dev/null | awk '{print $1}' || true
    printf 'file='
    file "$file" 2>/dev/null || true
    printf '\nBuild ID:\n'
    readelf -n "$file" 2>/dev/null | sed -n '/Build ID/p' || true
    printf '\nSONAME/NEEDED:\n'
    readelf -d "$file" 2>/dev/null | grep -E 'SONAME|NEEDED' || true
  } >"$out"
}

append_lib_matrix() {
  local label="$1"
  local file="$2"
  local matrix="$3"
  local name
  name="$(basename "$file")"
  if [ ! -f "$file" ]; then
    printf '%s\t%s\tmissing\t\t\t\n' "$label" "$name" >>"$matrix"
    return
  fi
  local size sha build_id needed
  size="$(wc -c <"$file" 2>/dev/null || true)"
  sha="$(sha256sum "$file" 2>/dev/null | awk '{print $1}' || true)"
  build_id="$(readelf -n "$file" 2>/dev/null | sed -n 's/.*Build ID: //p' | head -1 || true)"
  needed="$(readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | tr '\n' ',' | sed 's/,$//' || true)"
  printf '%s\t%s\tpresent\t%s\t%s\t%s\t%s\n' "$label" "$name" "$size" "$sha" "$build_id" "$needed" >>"$matrix"
}

mkdir -p "$OUT_DIR"
: >"$OUT_DIR/ERROR.txt"

log "output: $OUT_DIR"

{
  printf 'OUT_DIR=%s\n' "$OUT_DIR"
  printf 'LITERT_LM_DIR=%s\n' "$LITERT_LM_DIR"
  printf 'QAIRT_ROOT=%s\n' "$QAIRT_ROOT"
  printf 'PREVIOUS_BUILD=%s\n' "$PREVIOUS_BUILD"
  printf 'GALLERY_APK=%s\n' "$GALLERY_APK"
  printf 'CUSTOM_APK=%s\n' "$CUSTOM_APK"
  printf 'LOCAL_QAIRT_246=%s\n' "$LOCAL_QAIRT_246"
  printf 'Build allowed only after exact QAIRT 2.44 root exists and is not the 2.46 overlay.\n'
} >"$OUT_DIR/environment.txt"

if [ ! -d "$QAIRT_ROOT" ]; then
  die "QAIRT 2.44 root not found: $QAIRT_ROOT"
fi

QAIRT_REALPATH="$(readlink -f "$QAIRT_ROOT" 2>/dev/null || printf '%s' "$QAIRT_ROOT")"
{
  printf 'QAIRT_ROOT=%s\n' "$QAIRT_ROOT"
  printf 'QAIRT_REALPATH=%s\n' "$QAIRT_REALPATH"
  printf 'is_symlink=%s\n' "$([ -L "$QAIRT_ROOT" ] && printf true || printf false)"
} >"$OUT_DIR/qairt_root_check.txt"

case "$QAIRT_REALPATH" in
  *2.46.0.260424*)
    die "QAIRT 2.44 root resolves to the known 2.46 SDK overlay: $QAIRT_REALPATH"
    ;;
esac

case "$QAIRT_REALPATH" in
  *2.44.0.260225*) ;;
  *)
    die "QAIRT root does not look like exact 2.44.0.260225: $QAIRT_REALPATH"
    ;;
esac

REQUIRED_QAIRT_FILES=(
  "bin/envsetup.sh"
  "bin/x86_64-linux-clang/qnn-net-run"
  "bin/x86_64-linux-clang/qnn-platform-validator"
  "lib/aarch64-android/libQnnSystem.so"
  "lib/aarch64-android/libQnnHtp.so"
  "lib/aarch64-android/libQnnHtpPrepare.so"
  "lib/aarch64-android/libQnnHtpV79Stub.so"
  "lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so"
)

QNN_MATRIX="$OUT_DIR/qairt244_qnn_libs.tsv"
printf 'label\tlibrary\tpresent\tsize\tsha256\tbuild_id\tneeded\n' >"$QNN_MATRIX"

for rel in "${REQUIRED_QAIRT_FILES[@]}"; do
  path="$QAIRT_ROOT/$rel"
  if [ ! -e "$path" ]; then
    printf 'missing\t%s\n' "$rel" >>"$OUT_DIR/qairt_missing_files.txt"
  fi
  case "$rel" in
    *.so)
      mkdir -p "$OUT_DIR/qairt_metadata"
      metadata_for "$path" "$OUT_DIR/qairt_metadata/$(basename "$path").txt"
      append_lib_matrix "qairt244" "$path" "$QNN_MATRIX"
      ;;
  esac
done

if [ -s "$OUT_DIR/qairt_missing_files.txt" ]; then
  die "QAIRT 2.44 required files are missing; see $OUT_DIR/qairt_missing_files.txt"
fi

BUILD_LOG="$OUT_DIR/build_litert_custom_artifacts_qairt244.log"
log "running qairt244 build into artifacts only"
set +e
bash "$ROOT_DIR/scripts/build_litert_custom_artifacts.sh" \
  "$LITERT_LM_DIR" \
  --qairt-root "$QAIRT_ROOT" \
  --label qairt244 2>&1 | tee "$BUILD_LOG"
BUILD_CODE="${PIPESTATUS[0]}"
set -e
printf 'build_exit_code=%s\n' "$BUILD_CODE" >"$OUT_DIR/build_status.txt"

BUILD_ARTIFACT_DIR="$(grep -Eo "$ROOT_DIR/artifacts/litert_custom_build/[0-9]{8}_[0-9]{6}_qairt244" "$BUILD_LOG" | tail -1 || true)"
if [ -z "$BUILD_ARTIFACT_DIR" ]; then
  BUILD_ARTIFACT_DIR="$(find "$ROOT_DIR/artifacts/litert_custom_build" -maxdepth 1 -type d -name '*_qairt244' -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -1 | awk '{print $2}' || true)"
fi
printf 'build_artifact_dir=%s\n' "$BUILD_ARTIFACT_DIR" >>"$OUT_DIR/build_status.txt"

if [ "$BUILD_CODE" -ne 0 ]; then
  log "build failed; compare summary will record failure"
fi

LIB_MATRIX="$OUT_DIR/native_stack_compare.tsv"
printf 'label\tlibrary\tpresent\tsize\tsha256\tbuild_id\tneeded\n' >"$LIB_MATRIX"

for lib in libLiteRt.so libLiteRtDispatch_Qualcomm.so liblitertlm_jni.so libLiteRtCompilerPlugin_Qualcomm.so libGemmaModelConstraintProvider.so; do
  append_lib_matrix "previous_246_overlay" "$PREVIOUS_BUILD/built_libs/$lib" "$LIB_MATRIX"
  if [ -n "$BUILD_ARTIFACT_DIR" ]; then
    append_lib_matrix "qairt244_build" "$BUILD_ARTIFACT_DIR/built_libs/$lib" "$LIB_MATRIX"
  fi
done

for lib in libLiteRt.so libLiteRtDispatch_Qualcomm.so liblitertlm_jni.so libQnnSystem.so libQnnHtp.so libQnnHtpPrepare.so libQnnHtpV79Stub.so libQnnHtpV79Skel.so; do
  append_lib_matrix "gallery_reference" "$PREVIOUS_BUILD/reference_libs/gallery_stack/$lib" "$LIB_MATRIX"
done

for lib in libQnnSystem.so libQnnHtp.so libQnnHtpPrepare.so libQnnHtpV79Stub.so libQnnHtpV79Skel.so; do
  append_lib_matrix "local_qairt246" "$LOCAL_QAIRT_246/lib/aarch64-android/$lib" "$LIB_MATRIX"
  append_lib_matrix "local_qairt246_skel" "$LOCAL_QAIRT_246/lib/hexagon-v79/unsigned/$lib" "$LIB_MATRIX"
done

{
  printf '# QAIRT 2.44 Rebuild Compare Summary\n\n'
  printf -- '- Output: `%s`\n' "$OUT_DIR"
  printf -- '- QAIRT root: `%s`\n' "$QAIRT_ROOT"
  printf -- '- QAIRT realpath: `%s`\n' "$QAIRT_REALPATH"
  printf -- '- Build exit code: `%s`\n' "$BUILD_CODE"
  printf -- '- Build artifact dir: `%s`\n' "${BUILD_ARTIFACT_DIR:-<unknown>}"
  printf -- '- App insertion: `no`\n'
  printf -- '- Engine.initialize: `not run`\n'
  printf -- '- NPU inference: `not run`\n\n'
  printf '## Files\n\n'
  printf -- '- `qairt244_qnn_libs.tsv`\n'
  printf -- '- `native_stack_compare.tsv`\n'
  printf -- '- `build_litert_custom_artifacts_qairt244.log`\n'
  printf -- '- `build_status.txt`\n'
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
printf '%s\n' "$OUT_DIR"

exit 0
