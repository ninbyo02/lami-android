#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$ROOT_DIR/app/src/standardGpuMinimalRuntimeCandidateDebug/jniLibs/arm64-v8a"
ARTIFACT_DIR="$ROOT_DIR/artifacts/standard_gpu_minimal_runtime_candidate"
MANIFEST_FILE="$ARTIFACT_DIR/native_lib_manifest.tsv"
SUMMARY_FILE="$ARTIFACT_DIR/stage_summary.txt"

EXPECTED_LITERT_SHA="31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24"
EXPECTED_LITERTLM_JNI_SHA="ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f"

DRY_RUN=false
INPUT_DIR=""

usage() {
  cat <<'USAGE'
Usage: scripts/stage_standard_gpu_minimal_runtime_candidate_libs.sh [--dry-run] [--input DIR] [--output DIR]

Stages only the proven minimal GPU runtime pair for the
standardGpuMinimalRuntimeCandidateDebug flavor:

  - libLiteRt.so
  - liblitertlm_jni.so

No standardDebug source set is modified.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --input)
      INPUT_DIR="${2:-}"
      shift 2
      ;;
    --output)
      OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

sha256_of() {
  sha256sum "$1" | awk '{print $1}'
}

size_of() {
  wc -c < "$1" | tr -d '[:space:]'
}

declare -a SOURCE_CANDIDATES=()
if [[ -n "$INPUT_DIR" ]]; then
  SOURCE_CANDIDATES+=("$INPUT_DIR")
fi

SOURCE_CANDIDATES+=(
  "$ROOT_DIR/artifacts/standard_gpu_minimal_runtime_candidate/input"
  "$ROOT_DIR/artifacts/standard_gpu_minimal_runtime_candidate"
  "$ROOT_DIR/app/src/standardGpuRuntimeMinimalProbeDebug/jniLibs/arm64-v8a"
  "$ROOT_DIR/app/src/gpuRuntimeAlignmentProbeDebug/jniLibs/arm64-v8a"
  "$ROOT_DIR/app/build/intermediates/stripped_native_libs/standardGpuRuntimeMinimalProbeDebug/stripStandardGpuRuntimeMinimalProbeDebugDebugSymbols/out/lib/arm64-v8a"
  "$ROOT_DIR/app/build/intermediates/merged_native_libs/standardGpuRuntimeMinimalProbeDebug/mergeStandardGpuRuntimeMinimalProbeDebugNativeLibs/out/lib/arm64-v8a"
  "$ROOT_DIR/app/build/intermediates/stripped_native_libs/gpuRuntimeAlignmentProbeDebug/stripGpuRuntimeAlignmentProbeDebugDebugSymbols/out/lib/arm64-v8a"
  "$ROOT_DIR/app/build/intermediates/merged_native_libs/gpuRuntimeAlignmentProbeDebug/mergeGpuRuntimeAlignmentProbeDebugNativeLibs/out/lib/arm64-v8a"
)

if [[ -d "$ROOT_DIR/artifacts" ]]; then
  while IFS= read -r dir; do
    SOURCE_CANDIDATES+=("$dir")
  done < <(
    find "$ROOT_DIR/artifacts" -type f \( -name 'libLiteRt.so' -o -name 'liblitertlm_jni.so' \) \
      -printf '%h\n' 2>/dev/null | sort -u
  )
fi

find_source_dir() {
  local dir
  for dir in "${SOURCE_CANDIDATES[@]}"; do
    [[ -n "$dir" ]] || continue
    if [[ -f "$dir/libLiteRt.so" && -f "$dir/liblitertlm_jni.so" ]]; then
      printf '%s\n' "$dir"
      return 0
    fi
  done
  return 1
}

SOURCE_DIR="$(find_source_dir || true)"
mkdir -p "$ARTIFACT_DIR"

{
  printf 'standardGpuMinimalRuntimeCandidate staging summary\n'
  printf 'dry_run=%s\n' "$DRY_RUN"
  printf 'output_dir=%s\n' "$OUTPUT_DIR"
  if [[ -n "$SOURCE_DIR" ]]; then
    printf 'source_dir=%s\n' "$SOURCE_DIR"
  else
    printf 'source_dir=missing\n'
    printf 'searched_dirs=\n'
    printf '  %s\n' "${SOURCE_CANDIDATES[@]}"
  fi
} | tee "$SUMMARY_FILE"

if [[ -z "$SOURCE_DIR" ]]; then
  printf 'required libs not found: libLiteRt.so and liblitertlm_jni.so\n' >&2
  if [[ "$DRY_RUN" == "true" ]]; then
    exit 0
  fi
  exit 1
fi

LITERT_FILE="$SOURCE_DIR/libLiteRt.so"
LITERTLM_JNI_FILE="$SOURCE_DIR/liblitertlm_jni.so"
LITERT_SHA="$(sha256_of "$LITERT_FILE")"
LITERTLM_JNI_SHA="$(sha256_of "$LITERTLM_JNI_FILE")"
LITERT_SIZE="$(size_of "$LITERT_FILE")"
LITERTLM_JNI_SIZE="$(size_of "$LITERTLM_JNI_FILE")"

LITERT_MATCH=false
LITERTLM_JNI_MATCH=false
[[ "$LITERT_SHA" == "$EXPECTED_LITERT_SHA" ]] && LITERT_MATCH=true
[[ "$LITERTLM_JNI_SHA" == "$EXPECTED_LITERTLM_JNI_SHA" ]] && LITERTLM_JNI_MATCH=true

{
  printf 'lib\tname\tsource_path\toutput_path\tsize_bytes\tsha256\texpected_sha256\tsha256_matches\n'
  printf 'required\tlibLiteRt.so\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$LITERT_FILE" "$OUTPUT_DIR/libLiteRt.so" "$LITERT_SIZE" "$LITERT_SHA" "$EXPECTED_LITERT_SHA" "$LITERT_MATCH"
  printf 'required\tliblitertlm_jni.so\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$LITERTLM_JNI_FILE" "$OUTPUT_DIR/liblitertlm_jni.so" "$LITERTLM_JNI_SIZE" "$LITERTLM_JNI_SHA" "$EXPECTED_LITERTLM_JNI_SHA" "$LITERTLM_JNI_MATCH"
} > "$MANIFEST_FILE"

printf 'manifest=%s\n' "$MANIFEST_FILE" | tee -a "$SUMMARY_FILE"
printf 'libLiteRt.so sha256=%s expected=%s match=%s\n' "$LITERT_SHA" "$EXPECTED_LITERT_SHA" "$LITERT_MATCH" | tee -a "$SUMMARY_FILE"
printf 'liblitertlm_jni.so sha256=%s expected=%s match=%s\n' "$LITERTLM_JNI_SHA" "$EXPECTED_LITERTLM_JNI_SHA" "$LITERTLM_JNI_MATCH" | tee -a "$SUMMARY_FILE"

if [[ "$LITERT_MATCH" != "true" || "$LITERTLM_JNI_MATCH" != "true" ]]; then
  printf 'refusing to stage: source SHA does not match the proven minimal runtime pair\n' >&2
  if [[ "$DRY_RUN" == "true" ]]; then
    exit 0
  fi
  exit 1
fi

if [[ "$DRY_RUN" == "true" ]]; then
  printf 'dry-run complete: no files copied\n' | tee -a "$SUMMARY_FILE"
  exit 0
fi

mkdir -p "$OUTPUT_DIR"
cp "$LITERT_FILE" "$OUTPUT_DIR/libLiteRt.so"
cp "$LITERTLM_JNI_FILE" "$OUTPUT_DIR/liblitertlm_jni.so"
printf 'staged=true\n' | tee -a "$SUMMARY_FILE"
printf 'staged libLiteRt.so and liblitertlm_jni.so to %s\n' "$OUTPUT_DIR"
