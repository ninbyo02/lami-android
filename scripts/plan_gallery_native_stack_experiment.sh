#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-artifacts/gallery_native_stack_plan/$TIMESTAMP}"
APK_PATH="${1:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
STANDARD_APK="${STANDARD_APK:-app/build/outputs/apk/standard/debug/app-standard-debug.apk}"
NPU_APK="${NPU_APK:-app/build/outputs/apk/npuExperiment/debug/app-npuExperiment-debug.apk}"

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR/extracted"

log() {
  printf '[gallery-stack-plan] %s\n' "$*"
}

sha_for() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1 && [ -f "$file" ]; then
    sha256sum "$file" | awk '{print $1}'
  fi
}

build_id_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
  fi
}

needed_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | paste -sd ',' -
  fi
}

soname_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Library soname: \[\(.*\)\].*/\1/p' | head -n 1
  fi
}

export_count_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -Ws "$file" 2>/dev/null |
      awk '$4 == "FUNC" && ($5 == "GLOBAL" || $5 == "WEAK") && $7 != "UND" {count++} END {print count+0}'
  fi
}

focus_symbols_for() {
  local file="$1"
  if command -v nm >/dev/null 2>&1 && [ -f "$file" ]; then
    nm -D "$file" 2>/dev/null |
      awk '{print $NF}' |
      grep -E 'LiteRtDispatchGetApi|LiteRtQualcommOptionsGet|LiteRtDispatchGetCapabilities|LiteRtDispatchGetApiVersion|Qnn|QNN|Htp|HTP|dispatch|Dispatch|compiler|Compiler' |
      sort -u |
      paste -sd ',' -
  fi
}

elf_file_for() {
  local file="$1"
  if command -v file >/dev/null 2>&1 && [ -f "$file" ]; then
    file -b "$file" | sed 's/[[:space:]]\+/ /g'
  fi
}

classify_lib() {
  local name="$1"
  local lower
  lower="$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')"

  case "$name" in
    liblitertlm_jni.so|libLiteRt.so|libLiteRtDispatch_Qualcomm.so|libLiteRtDispatch.so|libLiteRtRuntimeCApi.so)
      printf 'required-candidate'
      return
      ;;
  esac

  case "$name" in
    libQnnSystem.so|libQnnHtp.so|libQnnHtpPrepare.so|libQnnGpu.so|libQnnDsp.so)
      printf 'qnn-runtime-candidate'
      return
      ;;
    libQnnHtpV79Skel.so|libQnnHtpV79Stub.so|libQnnHtpV75Skel.so|libQnnHtpV75Stub.so|libQnnHtpV73Skel.so|libQnnHtpV73Stub.so|libQnnHtpV69Skel.so|libQnnHtpV69Stub.so|libQnnHtpV68Skel.so|libQnnHtpV68Stub.so|libQnnDspV66Skel.so|libQnnDspV66Stub.so)
      printf 'htp-skel-stub-candidate'
      return
      ;;
  esac

  if printf '%s' "$lower" | grep -Eq 'compiler|plugin'; then
    printf 'compiler-plugin-candidate'
  elif printf '%s' "$lower" | grep -Eq 'qualcomm|qnn|dispatch|litert'; then
    printf 'vendor-runtime-candidate'
  else
    printf 'unrelated-or-unknown'
  fi
}

risk_for() {
  local category="$1"
  local name="$2"
  case "$category" in
    required-candidate)
      case "$name" in
        libLiteRtDispatch_Qualcomm.so) printf 'medium: already staged elsewhere, but must stay isolated' ;;
        *) printf 'high: replaces LiteRT-LM runtime generation and can affect JNI/native ABI' ;;
      esac
      ;;
    qnn-runtime-candidate)
      printf 'high: QNN runtime generation/path mismatch can change dispatch capabilities'
      ;;
    htp-skel-stub-candidate)
      printf 'medium-high: required for HTP but path/version sensitive'
      ;;
    compiler-plugin-candidate|vendor-runtime-candidate)
      printf 'high: vendor plugin/runtime role must be verified before packaging'
      ;;
    *)
      printf 'unknown: do not package unless proven necessary'
      ;;
  esac
}

apk_lib_names() {
  local apk="$1"
  if [ -f "$apk" ]; then
    unzip -l "$apk" 'lib/arm64-v8a/*.so' 2>/dev/null | awk '{print $4}' | sed -n 's#lib/arm64-v8a/##p' | sort -u
  fi
}

log "apk: $APK_PATH"
if [ ! -f "$APK_PATH" ]; then
  {
    printf '# Gallery native stack plan\n\n'
    printf 'apk: `%s`\n\n' "$APK_PATH"
    printf 'status: missing\n\n'
    printf 'No files were copied. Provide the Gallery SM8750 APK and rerun this script.\n'
  } >"$OUT_DIR/summary.md"
  printf 'status=missing\n' >"$OUT_DIR/summary.txt"
  log "missing APK; wrote $OUT_DIR"
  exit 0
fi

unzip -q -o "$APK_PATH" 'lib/arm64-v8a/*' -d "$OUT_DIR/extracted" 2>/dev/null || true
LIB_DIR="$OUT_DIR/extracted/lib/arm64-v8a"

{
  printf 'apk=%s\n' "$APK_PATH"
  printf 'apk_sha256=%s\n' "$(sha_for "$APK_PATH")"
  printf 'artifact_dir=%s\n' "$OUT_DIR"
  printf 'gallery_arm64_lib_dir=%s\n' "$LIB_DIR"
} >"$OUT_DIR/summary.txt"

{
  printf 'apk_path\tlibrary\tcategory\trisk\tsize\tsha256\tbuild_id\tsoname\tneeded\texported_symbols\tfocus_symbols\telf\n'
  if [ -d "$LIB_DIR" ]; then
    find "$LIB_DIR" -maxdepth 1 -type f -name '*.so' | sort | while IFS= read -r file; do
      name="$(basename "$file")"
      category="$(classify_lib "$name")"
      risk="$(risk_for "$category" "$name")"
      printf 'lib/arm64-v8a/%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$name" \
        "$name" \
        "$category" \
        "$risk" \
        "$(wc -c <"$file" 2>/dev/null | tr -d ' ')" \
        "$(sha_for "$file")" \
        "$(build_id_for "$file")" \
        "$(soname_for "$file")" \
        "$(needed_for "$file")" \
        "$(export_count_for "$file")" \
        "$(focus_symbols_for "$file")" \
        "$(elf_file_for "$file")"
    done
  fi
} >"$OUT_DIR/gallery_arm64_libs.tsv"

awk -F '\t' 'NR > 1 {count[$3]++} END {for (c in count) print c "\t" count[c]}' "$OUT_DIR/gallery_arm64_libs.tsv" | sort >"$OUT_DIR/category_counts.tsv"
awk -F '\t' 'NR > 1 && $3 == "required-candidate" {print $2}' "$OUT_DIR/gallery_arm64_libs.tsv" >"$OUT_DIR/required_candidates.txt"
awk -F '\t' 'NR > 1 && $3 == "qnn-runtime-candidate" {print $2}' "$OUT_DIR/gallery_arm64_libs.tsv" >"$OUT_DIR/qnn_runtime_candidates.txt"
awk -F '\t' 'NR > 1 && $3 == "htp-skel-stub-candidate" {print $2}' "$OUT_DIR/gallery_arm64_libs.tsv" >"$OUT_DIR/htp_skel_stub_candidates.txt"
awk -F '\t' 'NR > 1 && ($3 == "compiler-plugin-candidate" || $3 == "vendor-runtime-candidate") {print $2}' "$OUT_DIR/gallery_arm64_libs.tsv" >"$OUT_DIR/plugin_vendor_candidates.txt"

{
  printf 'library\tstandard_debug_collision\tnpu_experiment_debug_collision\tplanned_action\n'
  if [ -d "$LIB_DIR" ]; then
    standard_names="$(apk_lib_names "$STANDARD_APK" | tr '\n' ' ')"
    npu_names="$(apk_lib_names "$NPU_APK" | tr '\n' ' ')"
    find "$LIB_DIR" -maxdepth 1 -type f -name '*.so' | sed 's#.*/##' | sort | while IFS= read -r name; do
      standard_collision="no"
      npu_collision="no"
      if printf ' %s ' "$standard_names" | grep -q " $name "; then
        standard_collision="yes"
      fi
      if printf ' %s ' "$npu_names" | grep -q " $name "; then
        npu_collision="yes"
      fi
      printf '%s\t%s\t%s\t%s\n' "$name" "$standard_collision" "$npu_collision" "do-not-copy-in-this-phase"
    done
  fi
} >"$OUT_DIR/collision_plan.tsv"

{
  printf '# Gallery SM8750 native stack plan\n\n'
  printf '%s\n' "- APK: \`$APK_PATH\`"
  printf '%s\n' "- APK SHA-256: \`$(sha_for "$APK_PATH")\`"
  printf '%s\n' "- Artifact dir: \`$OUT_DIR\`"
  printf '%s\n\n' "- Action: classification only; no files copied into the app."
  printf '## Category counts\n\n'
  printf '| Category | Count |\n| --- | ---: |\n'
  awk -F '\t' '{printf "| `%s` | %s |\n", $1, $2}' "$OUT_DIR/category_counts.tsv"
  printf '\n## Required candidates\n\n'
  sed 's/^/- `/' "$OUT_DIR/required_candidates.txt" | sed 's/$/`/' || true
  printf '\n## QNN runtime candidates\n\n'
  sed 's/^/- `/' "$OUT_DIR/qnn_runtime_candidates.txt" | sed 's/$/`/' || true
  printf '\n## HTP skel/stub candidates\n\n'
  sed 's/^/- `/' "$OUT_DIR/htp_skel_stub_candidates.txt" | sed 's/$/`/' || true
  printf '\n## Vendor/compiler/plugin candidates\n\n'
  sed 's/^/- `/' "$OUT_DIR/plugin_vendor_candidates.txt" | sed 's/$/`/' || true
  printf '\n## libLiteRtRuntimeCApi.so\n\n'
  if grep -q '^lib/arm64-v8a/libLiteRtRuntimeCApi\.so' "$OUT_DIR/gallery_arm64_libs.tsv"; then
    printf 'present\n'
  else
    printf 'not present in `lib/arm64-v8a`\n'
  fi
  printf '\n## Collision plan\n\n'
  printf 'See `%s/collision_plan.tsv`. Same-name libraries must be staged only under a future isolated `galleryStackExperimentDebug` source set.\n' "$OUT_DIR"
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR"
log "summary: $OUT_DIR/summary.md"
exit 0
