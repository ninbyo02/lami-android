#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
INPUT_DIR="$ROOT_DIR/artifacts/external/edge_gallery_apks"
OUTPUT_DIR="$ROOT_DIR/app/src/galleryStackGpuProbeDebug/jniLibs/arm64-v8a"
MANIFEST_PATH="$ROOT_DIR/artifacts/gallery_stack_gpu_probe/native_lib_manifest.tsv"
STAGE=0

usage() {
  printf 'usage: %s [--input <edge-gallery-apk-dir>] [--output <jniLibs-dir>] [--manifest <tsv>] [--stage]\n' "$0"
  printf 'default input: %s\n' "$INPUT_DIR"
  printf 'default output: %s\n' "$OUTPUT_DIR"
  printf 'default manifest: %s\n' "$MANIFEST_PATH"
  printf '\nDefault mode is report-only. Use --stage to copy .so files into the probe flavor source set.\n'
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --input)
      shift
      INPUT_DIR="${1:-}"
      ;;
    --output)
      shift
      OUTPUT_DIR="${1:-}"
      ;;
    --manifest)
      shift
      MANIFEST_PATH="${1:-}"
      ;;
    --stage)
      STAGE=1
      ;;
    --dry-run|--report-only)
      STAGE=0
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift || true
done

case "$OUTPUT_DIR" in
  "$ROOT_DIR/app/src/main/"*|"$ROOT_DIR/app/src/debug/"*|"$ROOT_DIR/app/src/standard"*|"$ROOT_DIR/app/src/standardDebug/"*)
    printf 'Refusing to stage into a standard/shared source set: %s\n' "$OUTPUT_DIR" >&2
    exit 3
    ;;
esac

if [ ! -d "$INPUT_DIR" ]; then
  printf 'Edge Gallery APK input directory is missing: %s\n' "$INPUT_DIR" >&2
  exit 4
fi

APK_LIST="$(find "$INPUT_DIR" -maxdepth 1 -type f -name '*.apk' 2>/dev/null | sort)"
if [ -z "$APK_LIST" ]; then
  printf 'No APK files found in: %s\n' "$INPUT_DIR" >&2
  exit 5
fi

zip_entries() {
  local apk="$1"
  if command -v zipinfo >/dev/null 2>&1; then
    zipinfo -1 "$apk" 2>/dev/null
  else
    unzip -Z1 "$apk" 2>/dev/null
  fi
}

sha_for() {
  local file="$1"
  if [ -f "$file" ] && command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf 'unavailable'
  fi
}

size_for() {
  local file="$1"
  if [ -f "$file" ]; then
    wc -c <"$file" 2>/dev/null | awk '{print $1}'
  else
    printf 'unavailable'
  fi
}

build_id_for() {
  local file="$1"
  if [ -f "$file" ] && command -v readelf >/dev/null 2>&1; then
    readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
  else
    printf 'unavailable'
  fi
}

mkdir -p "$(dirname "$MANIFEST_PATH")"
if [ "$STAGE" = "1" ]; then
  mkdir -p "$OUTPUT_DIR"
fi

TMP_DIR="${TMPDIR:-/tmp}/lami-gallery-stack-gpu-probe-stage-$$"
mkdir -p "$TMP_DIR"

{
  printf 'library\tapk\tentry\tsize_bytes\tsha256\tbuild_id\tstaged\tstaged_path\n'
  printf '%s\n' "$APK_LIST" |
    while IFS= read -r apk; do
      [ -n "$apk" ] || continue
      zip_entries "$apk" |
        grep '^lib/arm64-v8a/.*\.so$' |
        while IFS= read -r entry; do
          lib="$(basename "$entry")"
          tmp_file="$TMP_DIR/$(basename "$apk").$lib"
          unzip -p "$apk" "$entry" >"$tmp_file" 2>/dev/null || true
          staged="report_only"
          staged_path="$OUTPUT_DIR/$lib"
          if [ "$STAGE" = "1" ]; then
            if [ -f "$staged_path" ]; then
              staged="skipped_existing"
            else
              unzip -p "$apk" "$entry" >"$staged_path" 2>/dev/null || true
              staged="copied"
            fi
          fi
          printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
            "$lib" \
            "$(basename "$apk")" \
            "$entry" \
            "$(size_for "$tmp_file")" \
            "$(sha_for "$tmp_file")" \
            "$(build_id_for "$tmp_file")" \
            "$staged" \
            "$staged_path"
        done
    done
} >"$MANIFEST_PATH"

printf 'mode=%s\n' "$([ "$STAGE" = "1" ] && printf 'stage' || printf 'report_only')"
printf 'input_dir=%s\n' "$INPUT_DIR"
printf 'output_dir=%s\n' "$OUTPUT_DIR"
printf 'manifest=%s\n' "$MANIFEST_PATH"
printf 'native_lib_count=%s\n' "$(awk 'NR > 1 { count += 1 } END { print count + 0 }' "$MANIFEST_PATH")"
if [ "$STAGE" != "1" ]; then
  printf 'No files copied. Re-run with --stage to populate the probe flavor jniLibs directory.\n'
fi
