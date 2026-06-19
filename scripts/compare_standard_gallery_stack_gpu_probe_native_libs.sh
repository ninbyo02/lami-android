#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
STANDARD_APK="$ROOT_DIR/app/build/outputs/apk/standard/debug/app-standard-debug.apk"
GALLERY_APK="$ROOT_DIR/app/build/outputs/apk/galleryStackGpuProbe/debug/app-galleryStackGpuProbe-debug.apk"
OUT_DIR="$ROOT_DIR/artifacts/gpu_runtime_stack_compare"

usage() {
  cat <<USAGE
usage: $0 [--standard-apk PATH] [--gallery-apk PATH] [--out-dir PATH]

Compares final APK arm64-v8a native libraries between standardDebug and
galleryStackGpuProbeDebug. Outputs TSV/Markdown diagnostics only; it never
stages or modifies native libraries.

defaults:
  --standard-apk $STANDARD_APK
  --gallery-apk  $GALLERY_APK
  --out-dir      $OUT_DIR
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --standard-apk)
      STANDARD_APK="${2:-}"
      shift 2
      ;;
    --gallery-apk)
      GALLERY_APK="${2:-}"
      shift 2
      ;;
    --out-dir)
      OUT_DIR="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

sha_for() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1 && [ -f "$file" ]; then
    sha256sum "$file" | awk '{print $1}'
  fi
}

size_for() {
  local file="$1"
  if [ -f "$file" ]; then
    wc -c <"$file" 2>/dev/null | awk '{print $1}'
  fi
}

build_id_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
  fi
}

soname_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Library soname: \[\(.*\)\].*/\1/p' | head -1
  fi
}

needed_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | paste -sd ',' -
  fi
}

category_for() {
  local lib="$1"
  case "$lib" in
    libLiteRt.so|liblitertlm_jni.so)
      printf 'core_litert_lm_gpu_runtime'
      ;;
    libLiteRtDispatch_Qualcomm.so|libLiteRtCompilerPlugin_Qualcomm.so)
      printf 'litert_dispatch_compiler_plugin'
      ;;
    libGemmaModelConstraintProvider.so)
      printf 'model_constraint_provider'
      ;;
    libQnn*.so|libQnnHtp*.so|libQnnSystem.so|libQnnTFLiteDelegate.so)
      printf 'qnn_qualcomm_npu_or_delegate_stack'
      ;;
    libLiteRtClGlAccelerator.so|*Gpu*|*GPU*|*OpenCL*|*ClGl*|*Vulkan*|*WebGpu*|*WebGPU*)
      printf 'gpu_named_auxiliary'
      ;;
    *)
      printf 'support_or_unclassified'
      ;;
  esac
}

classification_for() {
  local lib="$1"
  local standard_present="$2"
  local gallery_present="$3"
  local same_sha="$4"
  local same_build_id="$5"

  case "$lib" in
    libLiteRt.so|liblitertlm_jni.so)
      if [ "$standard_present" = "yes" ] && [ "$gallery_present" = "yes" ] && [ "$same_sha" = "true" ]; then
        printf 'core_runtime_aligned'
      elif [ "$standard_present" = "yes" ] && [ "$gallery_present" = "yes" ]; then
        printf 'highest_priority_full_stack_alignment_candidate'
      else
        printf 'highest_priority_missing_core_runtime_candidate'
      fi
      ;;
    libLiteRtDispatch_Qualcomm.so|libLiteRtCompilerPlugin_Qualcomm.so|libGemmaModelConstraintProvider.so)
      if [ "$standard_present" = "yes" ] && [ "$gallery_present" = "yes" ] && { [ "$same_sha" = "true" ] || [ "$same_build_id" = "true" ]; }; then
        printf 'aligned_runtime_stack_member'
      else
        printf 'high_priority_runtime_stack_member_review'
      fi
      ;;
    libQnn*.so|libQnnHtp*.so|libQnnSystem.so|libQnnTFLiteDelegate.so)
      printf 'not_primary_generic_gpu_candidate_qnn_or_npu_stack'
      ;;
    libLiteRtClGlAccelerator.so|*Gpu*|*GPU*|*OpenCL*|*ClGl*|*Vulkan*|*WebGpu*|*WebGPU*)
      printf 'gpu_auxiliary_stack_review'
      ;;
    *)
      if [ "$standard_present" = "$gallery_present" ] && [ "$same_sha" = "true" ]; then
        printf 'likely_unrelated_aligned_support_lib'
      elif [ "$standard_present" = "$gallery_present" ]; then
        printf 'support_lib_diff_review'
      else
        printf 'support_lib_presence_diff_review'
      fi
      ;;
  esac
}

notes_for() {
  local lib="$1"
  local classification="$2"
  case "$classification" in
    highest_priority_full_stack_alignment_candidate)
      printf 'core LiteRT/LiteRT-LM lib differs; compare as a matched runtime stack, not as a single-so swap'
      ;;
    highest_priority_missing_core_runtime_candidate)
      printf 'core LiteRT/LiteRT-LM lib presence differs; full-stack staging required before behavior claims'
      ;;
    high_priority_runtime_stack_member_review)
      printf 'dispatch/compiler/model-constraint member differs or is missing; review with the core runtime pair'
      ;;
    not_primary_generic_gpu_candidate_qnn_or_npu_stack)
      printf 'QNN/NPU-related; keep visible but do not treat as primary generic GPU root without evidence'
      ;;
    gpu_auxiliary_stack_review)
      printf 'GPU-named auxiliary library; review if present in only one APK or if build id differs'
      ;;
    *)
      case "$lib" in
        libc++_shared.so)
          printf 'C++ runtime support library; mismatch can matter indirectly but is not the first GPU-specific target'
          ;;
        *)
          printf 'support or unclassified library'
          ;;
      esac
      ;;
  esac
}

extract_apk_libs() {
  local apk="$1"
  local label="$2"
  local dest="$3"
  mkdir -p "$dest"
  if [ ! -f "$apk" ]; then
    printf 'missing %s APK: %s\n' "$label" "$apk" >&2
    return 1
  fi
  unzip -q -o "$apk" 'lib/arm64-v8a/*.so' -d "$dest" 2>/dev/null || true
  if [ ! -d "$dest/lib/arm64-v8a" ]; then
    printf '%s APK has no lib/arm64-v8a/*.so entries: %s\n' "$label" "$apk" >&2
    return 1
  fi
}

write_inventory() {
  local label="$1"
  local dir="$2"
  local out="$3"
  {
    printf 'apk_label\tlibrary\tpresent\tpath\tsize_bytes\tsha256\tbuild_id\tsoname\tneeded\tcategory\n'
    find "$dir" -maxdepth 1 -type f -name '*.so' 2>/dev/null | sort | while IFS= read -r file; do
      lib="${file##*/}"
      printf '%s\t%s\tyes\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$label" \
        "$lib" \
        "$file" \
        "$(size_for "$file")" \
        "$(sha_for "$file")" \
        "$(build_id_for "$file")" \
        "$(soname_for "$file")" \
        "$(needed_for "$file")" \
        "$(category_for "$lib")"
    done
  } >"$out"
}

write_needed_edges() {
  local label="$1"
  local dir="$2"
  local out="$3"
  find "$dir" -maxdepth 1 -type f -name '*.so' 2>/dev/null | sort | while IFS= read -r file; do
    lib="${file##*/}"
    needed="$(needed_for "$file")"
    if [ -z "${needed:-}" ]; then
      printf '%s\t%s\t%s\n' "$label" "$lib" "-"
      continue
    fi
    printf '%s' "$needed" | tr ',' '\n' | while IFS= read -r dep; do
      [ -n "$dep" ] && printf '%s\t%s\t%s\n' "$label" "$lib" "$dep"
    done
  done >>"$out"
}

field_or_none() {
  local value="$1"
  if [ -n "${value:-}" ]; then
    printf '%s' "$value"
  else
    printf 'none'
  fi
}

if [ ! -f "$STANDARD_APK" ]; then
  printf 'missing standardDebug APK: %s\n' "$STANDARD_APK" >&2
  printf 'hint: run ./gradlew :app:assembleStandardDebug first\n' >&2
  exit 1
fi

if [ ! -f "$GALLERY_APK" ]; then
  printf 'missing galleryStackGpuProbeDebug APK: %s\n' "$GALLERY_APK" >&2
  printf 'hint: run ./gradlew :app:assembleGalleryStackGpuProbeDebug first\n' >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
WORK_DIR="${TMPDIR:-/tmp}/lami-gpu-runtime-stack-compare-$$"
trap 'rm -rf "$WORK_DIR"' EXIT

STANDARD_EXTRACT="$WORK_DIR/standard"
GALLERY_EXTRACT="$WORK_DIR/gallery"
extract_apk_libs "$STANDARD_APK" "standardDebug" "$STANDARD_EXTRACT" || exit 1
extract_apk_libs "$GALLERY_APK" "galleryStackGpuProbeDebug" "$GALLERY_EXTRACT" || exit 1
STANDARD_DIR="$STANDARD_EXTRACT/lib/arm64-v8a"
GALLERY_DIR="$GALLERY_EXTRACT/lib/arm64-v8a"

STANDARD_INVENTORY="$OUT_DIR/standard_debug_native_libs.tsv"
GALLERY_INVENTORY="$OUT_DIR/gallery_stack_gpu_probe_native_libs.tsv"
DIFF_OUT="$OUT_DIR/native_lib_diff.tsv"
NEEDED_OUT="$OUT_DIR/needed_dependency_edges.tsv"
SUMMARY_OUT="$OUT_DIR/gpu_runtime_stack_classification.md"

write_inventory "standardDebug" "$STANDARD_DIR" "$STANDARD_INVENTORY"
write_inventory "galleryStackGpuProbeDebug" "$GALLERY_DIR" "$GALLERY_INVENTORY"

printf 'apk_label\tlibrary\tneeded_library\n' >"$NEEDED_OUT"
write_needed_edges "standardDebug" "$STANDARD_DIR" "$NEEDED_OUT"
write_needed_edges "galleryStackGpuProbeDebug" "$GALLERY_DIR" "$NEEDED_OUT"

{
  find "$STANDARD_DIR" "$GALLERY_DIR" -maxdepth 1 -type f -name '*.so' 2>/dev/null |
    sed 's#.*/##' |
    sort -u
} >"$OUT_DIR/all_native_lib_names.txt"

printf 'library\tstandard_present\tgallery_present\tsame_sha256\tsame_build_id\tstandard_size_bytes\tgallery_size_bytes\tstandard_sha256\tgallery_sha256\tstandard_build_id\tgallery_build_id\tstandard_needed\tgallery_needed\tcategory\tclassification\tnotes\n' >"$DIFF_OUT"

while IFS= read -r lib; do
  [ -n "$lib" ] || continue
  standard_file="$STANDARD_DIR/$lib"
  gallery_file="$GALLERY_DIR/$lib"
  standard_present="no"
  gallery_present="no"
  [ -f "$standard_file" ] && standard_present="yes"
  [ -f "$gallery_file" ] && gallery_present="yes"
  standard_size=""
  gallery_size=""
  standard_sha=""
  gallery_sha=""
  standard_build=""
  gallery_build=""
  standard_needed=""
  gallery_needed=""
  if [ "$standard_present" = "yes" ]; then
    standard_size="$(size_for "$standard_file")"
    standard_sha="$(sha_for "$standard_file")"
    standard_build="$(build_id_for "$standard_file")"
    standard_needed="$(needed_for "$standard_file")"
  fi
  if [ "$gallery_present" = "yes" ]; then
    gallery_size="$(size_for "$gallery_file")"
    gallery_sha="$(sha_for "$gallery_file")"
    gallery_build="$(build_id_for "$gallery_file")"
    gallery_needed="$(needed_for "$gallery_file")"
  fi
  same_sha="false"
  same_build="false"
  if [ "$standard_present" = "yes" ] && [ "$gallery_present" = "yes" ] && [ -n "$standard_sha" ] && [ "$standard_sha" = "$gallery_sha" ]; then
    same_sha="true"
  fi
  if [ "$standard_present" = "yes" ] && [ "$gallery_present" = "yes" ] && [ -n "$standard_build" ] && [ "$standard_build" = "$gallery_build" ]; then
    same_build="true"
  fi
  category="$(category_for "$lib")"
  classification="$(classification_for "$lib" "$standard_present" "$gallery_present" "$same_sha" "$same_build")"
  notes="$(notes_for "$lib" "$classification")"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$lib" \
    "$standard_present" \
    "$gallery_present" \
    "$same_sha" \
    "$same_build" \
    "$(field_or_none "$standard_size")" \
    "$(field_or_none "$gallery_size")" \
    "$(field_or_none "$standard_sha")" \
    "$(field_or_none "$gallery_sha")" \
    "$(field_or_none "$standard_build")" \
    "$(field_or_none "$gallery_build")" \
    "$(field_or_none "$standard_needed")" \
    "$(field_or_none "$gallery_needed")" \
    "$category" \
    "$classification" \
    "$notes"
done <"$OUT_DIR/all_native_lib_names.txt" >>"$DIFF_OUT"

total_libs="$(wc -l <"$OUT_DIR/all_native_lib_names.txt" | awk '{print $1}')"
sha_diff_count="$(awk -F '\t' 'NR > 1 && $2 == "yes" && $3 == "yes" && $4 == "false" {count++} END {print count+0}' "$DIFF_OUT")"
presence_diff_count="$(awk -F '\t' 'NR > 1 && $2 != $3 {count++} END {print count+0}' "$DIFF_OUT")"
high_priority_count="$(awk -F '\t' 'NR > 1 && $15 ~ /highest_priority|high_priority/ {count++} END {print count+0}' "$DIFF_OUT")"

{
  printf '# GPU runtime stack classification\n\n'
  printf '%s\n' "- standard_apk: \`$STANDARD_APK\`"
  printf '%s\n' "- gallery_stack_gpu_probe_apk: \`$GALLERY_APK\`"
  printf '%s\n' "- total_arm64_libs_compared: \`$total_libs\`"
  printf '%s\n' "- same-name_sha256_differences: \`$sha_diff_count\`"
  printf '%s\n' "- presence_differences: \`$presence_diff_count\`"
  printf '%s\n\n' "- high_priority_runtime_candidates: \`$high_priority_count\`"
  printf '## Interpretation\n\n'
  printf 'The current model-separation probe showed the same Edge Gallery E2B model succeeds in `galleryStackGpuProbe` and fails in `standardDebug`. Treat remaining deltas as runtime/native stack candidates, not model identity candidates.\n\n'
  printf 'Do not promote any single `.so` into `standardDebug`. `libLiteRt.so` and `liblitertlm_jni.so` are especially treated as a matched core runtime pair; dispatch, compiler plugin, and model constraint provider entries must be reviewed with that pair.\n\n'
  printf '## Focus libraries\n\n'
  printf '| Library | Category | Classification | standardDebug | galleryStackGpuProbeDebug | Notes |\n'
  printf '| --- | --- | --- | --- | --- | --- |\n'
  for focus in \
    libLiteRt.so \
    liblitertlm_jni.so \
    libLiteRtDispatch_Qualcomm.so \
    libLiteRtCompilerPlugin_Qualcomm.so \
    libGemmaModelConstraintProvider.so \
    libQnnSystem.so \
    libQnnGpu.so \
    libQnnHtp.so \
    libQnnHtpPrepare.so \
    libQnnHtpV79Stub.so \
    libQnnHtpV79Skel.so \
    libQnnDsp.so; do
    awk -F '\t' -v lib="$focus" '
      NR > 1 && $1 == lib {
        std = $2 ", size=" $6 ", build_id=" $10
        gal = $3 ", size=" $7 ", build_id=" $11
        printf("| `%s` | `%s` | `%s` | `%s` | `%s` | %s |\n", $1, $14, $15, std, gal, $16)
      }
    ' "$DIFF_OUT"
  done
  printf '\n## Outputs\n\n'
  printf '%s\n' '- `standard_debug_native_libs.tsv`'
  printf '%s\n' '- `gallery_stack_gpu_probe_native_libs.tsv`'
  printf '%s\n' '- `native_lib_diff.tsv`'
  printf '%s\n' '- `needed_dependency_edges.tsv`'
  printf '%s\n' '- `all_native_lib_names.txt`'
} >"$SUMMARY_OUT"

printf 'wrote %s\n' "$STANDARD_INVENTORY"
printf 'wrote %s\n' "$GALLERY_INVENTORY"
printf 'wrote %s\n' "$DIFF_OUT"
printf 'wrote %s\n' "$NEEDED_OUT"
printf 'wrote %s\n' "$SUMMARY_OUT"

exit 0
