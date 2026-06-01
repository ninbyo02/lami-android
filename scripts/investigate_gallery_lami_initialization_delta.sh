#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/gallery_lami_initialization_delta/$TIMESTAMP"

GALLERY_APK="${GALLERY_APK:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
GALLERY_LIB_DIR="${GALLERY_LIB_DIR:-}"
LAMI_APK="${LAMI_APK:-$ROOT_DIR/app/build/outputs/apk/galleryAlignedNpuProbe/debug/app-galleryAlignedNpuProbe-debug.apk}"
AAPT="${AAPT:-/home/sato/Android/Sdk/build-tools/36.1.0/aapt}"

LIBS=(
  liblitertlm_jni.so
  libLiteRt.so
  libLiteRtDispatch_Qualcomm.so
  libLiteRtCompilerPlugin_Qualcomm.so
  libQnnHtp.so
  libQnnSystem.so
  libQnnHtpPrepare.so
  libQnnHtpV79Skel.so
  libQnnHtpV79Stub.so
  libGemmaModelConstraintProvider.so
  libllm_inference_engine_jni.so
)

usage() {
  cat <<'USAGE'
Usage:
  scripts/investigate_gallery_lami_initialization_delta.sh [options]

Options:
  --gallery-apk PATH     Google AI Edge Gallery SM8750 APK.
  --gallery-lib-dir DIR  Extracted Gallery arm64-v8a stack dir.
  --lami-apk PATH        galleryAlignedNpuProbe APK.
  --out-dir DIR          Output directory.

This is static investigation only. It does not install, launch, initialize
Engine, replace libraries, change QAIRT/QNN/fallback settings, or add
System.loadLibrary.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --gallery-apk)
      GALLERY_APK="${2:-}"
      shift 2
      ;;
    --gallery-lib-dir)
      GALLERY_LIB_DIR="${2:-}"
      shift 2
      ;;
    --lami-apk)
      LAMI_APK="${2:-}"
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
      echo "[gallery-lami-delta] unknown option: $1"
      usage
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"/{gallery,lami,diff}

latest_gallery_stack_dir() {
  find "$ROOT_DIR/artifacts/gallery_dispatch_requirements" -type d -path '*/gallery_stack' 2>/dev/null | sort | tail -n 1
}

if [ -z "$GALLERY_LIB_DIR" ]; then
  GALLERY_LIB_DIR="$(latest_gallery_stack_dir)"
fi

apk_list() {
  local apk="$1"
  local out="$2"
  if [ -f "$apk" ] && command -v zipinfo >/dev/null 2>&1; then
    zipinfo -1 "$apk" >"$out" 2>/dev/null || true
  else
    printf '<apk-missing>\n' >"$out"
  fi
}

apk_badging() {
  local apk="$1"
  local out="$2"
  if [ -f "$apk" ] && [ -x "$AAPT" ]; then
    "$AAPT" dump badging "$apk" >"$out" 2>&1 || true
  else
    printf '<apk-or-aapt-missing>\n' >"$out"
  fi
}

apk_manifest_tree() {
  local apk="$1"
  local out="$2"
  if [ -f "$apk" ] && [ -x "$AAPT" ]; then
    "$AAPT" dump xmltree "$apk" AndroidManifest.xml >"$out" 2>&1 || true
  else
    printf '<apk-or-aapt-missing>\n' >"$out"
  fi
}

sha_for() {
  local file="$1"
  if [ -f "$file" ] && command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
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

extract_lib() {
  local apk="$1"
  local lib="$2"
  local out="$3"
  if [ -f "$apk" ] && command -v unzip >/dev/null 2>&1; then
    unzip -p "$apk" "lib/arm64-v8a/$lib" >"$out" 2>/dev/null && return 0
  fi
  rm -f "$out"
  return 1
}

copy_or_extract_gallery_lib() {
  local lib="$1"
  local out="$OUT_DIR/gallery/$lib"
  if extract_lib "$GALLERY_APK" "$lib" "$out"; then
    printf 'gallery-apk'
    return 0
  fi
  if [ -n "$GALLERY_LIB_DIR" ] && [ -f "$GALLERY_LIB_DIR/$lib" ]; then
    cp "$GALLERY_LIB_DIR/$lib" "$out"
    printf 'gallery-lib-dir'
    return 0
  fi
  rm -f "$out"
  printf 'missing'
  return 1
}

copy_or_extract_lami_lib() {
  local lib="$1"
  local out="$OUT_DIR/lami/$lib"
  if extract_lib "$LAMI_APK" "$lib" "$out"; then
    printf 'lami-apk'
    return 0
  fi
  rm -f "$out"
  printf 'missing'
  return 1
}

write_lib_inventory() {
  printf 'library\tgallery_exists\tgallery_source\tgallery_build_id\tgallery_sha256\tlami_exists\tlami_source\tlami_build_id\tlami_sha256\tsha256_match\n' >"$OUT_DIR/native_lib_delta.tsv"
  for lib in "${LIBS[@]}"; do
    local gallery_source lami_source gallery_file lami_file gallery_exists lami_exists gallery_sha lami_sha
    gallery_source="$(copy_or_extract_gallery_lib "$lib")"
    lami_source="$(copy_or_extract_lami_lib "$lib")"
    gallery_file="$OUT_DIR/gallery/$lib"
    lami_file="$OUT_DIR/lami/$lib"
    [ -f "$gallery_file" ] && gallery_exists=true || gallery_exists=false
    [ -f "$lami_file" ] && lami_exists=true || lami_exists=false
    gallery_sha="$(sha_for "$gallery_file")"
    lami_sha="$(sha_for "$lami_file")"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$lib" \
      "$gallery_exists" \
      "$gallery_source" \
      "$(build_id_for "$gallery_file")" \
      "$gallery_sha" \
      "$lami_exists" \
      "$lami_source" \
      "$(build_id_for "$lami_file")" \
      "$lami_sha" \
      "$([ "$gallery_sha" != "-" ] && [ "$gallery_sha" = "$lami_sha" ] && printf true || printf false)" >>"$OUT_DIR/native_lib_delta.tsv"
  done
}

write_apk_metadata() {
  apk_list "$GALLERY_APK" "$OUT_DIR/gallery/apk_files.txt"
  apk_list "$LAMI_APK" "$OUT_DIR/lami/apk_files.txt"
  apk_badging "$GALLERY_APK" "$OUT_DIR/gallery/badging.txt"
  apk_badging "$LAMI_APK" "$OUT_DIR/lami/badging.txt"
  apk_manifest_tree "$GALLERY_APK" "$OUT_DIR/gallery/AndroidManifest.xmltree.txt"
  apk_manifest_tree "$LAMI_APK" "$OUT_DIR/lami/AndroidManifest.xmltree.txt"

  grep -E 'uses-permission|uses-library|meta-data|extractNativeLibs|debuggable|application|package=' "$OUT_DIR/gallery/AndroidManifest.xmltree.txt" >"$OUT_DIR/gallery/manifest_key_lines.txt" 2>/dev/null || true
  grep -E 'uses-permission|uses-library|meta-data|extractNativeLibs|debuggable|application|package=' "$OUT_DIR/lami/AndroidManifest.xmltree.txt" >"$OUT_DIR/lami/manifest_key_lines.txt" 2>/dev/null || true
  grep '^assets/' "$OUT_DIR/gallery/apk_files.txt" >"$OUT_DIR/gallery/assets.txt" 2>/dev/null || true
  grep '^assets/' "$OUT_DIR/lami/apk_files.txt" >"$OUT_DIR/lami/assets.txt" 2>/dev/null || true
  grep '^lib/' "$OUT_DIR/gallery/apk_files.txt" >"$OUT_DIR/gallery/native_lib_paths.txt" 2>/dev/null || true
  grep '^lib/' "$OUT_DIR/lami/apk_files.txt" >"$OUT_DIR/lami/native_lib_paths.txt" 2>/dev/null || true
}

write_summary() {
  local optional_compiler optional_gemma
  optional_compiler="$(awk -F '\t' '$1 == "libLiteRtCompilerPlugin_Qualcomm.so" {print "gallery=" $2 ",lami=" $6}' "$OUT_DIR/native_lib_delta.tsv")"
  optional_gemma="$(awk -F '\t' '$1 == "libGemmaModelConstraintProvider.so" {print "gallery=" $2 ",lami=" $6}' "$OUT_DIR/native_lib_delta.tsv")"
  {
    printf '# Gallery vs Lami Backend.NPU initialization delta\n\n'
    printf '%s\n' "- generated_at: \`$TIMESTAMP\`"
    printf '%s\n' "- gallery_apk: \`$GALLERY_APK\`"
    printf '%s\n' "- gallery_lib_dir: \`${GALLERY_LIB_DIR:-}\`"
    printf '%s\n' "- lami_apk: \`$LAMI_APK\`"
    printf '%s\n\n' "- scope: static investigation only; no install, no Engine.initialize, no library replacement"
    printf '## Difference table\n\n'
    printf '| Area | Gallery evidence | Lami galleryAlignedNpuProbe evidence | Next risk to test |\n'
    printf '| --- | --- | --- | --- |\n'
    printf '| AndroidManifest.xml | `%s` | `%s` | compare `extractNativeLibs`, package namespace, app attributes |\n' "$OUT_DIR/gallery/manifest_key_lines.txt" "$OUT_DIR/lami/manifest_key_lines.txt"
    printf '| permissions/meta-data/uses-library | `%s` | `%s` | detect Gallery-only manifest contract or service/provider setup |\n' "$OUT_DIR/gallery/manifest_key_lines.txt" "$OUT_DIR/lami/manifest_key_lines.txt"
    printf '| assets/config files | `%s` | `%s` | detect Gallery-only runtime config or model metadata assets |\n' "$OUT_DIR/gallery/assets.txt" "$OUT_DIR/lami/assets.txt"
    printf '| native libs | `%s` | `%s` | verify all runtime libs are present as a stack, not partial swaps |\n' "$OUT_DIR/gallery/native_lib_paths.txt" "$OUT_DIR/lami/native_lib_paths.txt"
    printf '| Engine.initialize call shape | `Backend.NPU(String nativeLibraryDir); EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String); Engine.initialize()` from dex surface artifacts | same API shape through reflection probe | vary cacheDir, maxNumTokens, maxNumImages, model path spelling |\n'
    printf '| optional libs | `%s`; `%s` | see `native_lib_delta.tsv` | if Gallery APK is unavailable this is based on extracted artifact dir only |\n' "$optional_compiler" "$optional_gemma"
    printf '\n## Native library delta\n\n```text\n'
    cat "$OUT_DIR/native_lib_delta.tsv"
    printf '```\n\n'
    printf '## Added probe variants\n\n'
    printf '| Variant | cacheDir | maxNumTokens | maxNumImages | modelPath handling |\n'
    printf '| --- | --- | --- | --- | --- |\n'
    printf '| `gallery-like-cache` | `context.cacheDir` | `null` | `null` | unchanged |\n'
    printf '| `gallery-like-max128` | `null` | `128` | `null` | unchanged |\n'
    printf '| `gallery-like-all` | `context.cacheDir` | `128` | `1` | unchanged |\n'
    printf '| `gallery-like-data-data-path` | `null` | `null` | `null` | `/data/user/0/<pkg>/...` rewritten to `/data/data/<pkg>/...` inside app |\n'
    printf '| `gallery-like-canonical-path` | `null` | `null` | `null` | app uses `File(modelPath).canonicalPath` |\n'
    printf '\n## Files\n\n'
    printf '%s\n' "- \`$OUT_DIR/native_lib_delta.tsv\`"
    printf '%s\n' "- \`$OUT_DIR/gallery/AndroidManifest.xmltree.txt\`"
    printf '%s\n' "- \`$OUT_DIR/lami/AndroidManifest.xmltree.txt\`"
    printf '%s\n' "- \`$OUT_DIR/gallery/apk_files.txt\`"
    printf '%s\n' "- \`$OUT_DIR/lami/apk_files.txt\`"
  } >"$OUT_DIR/summary.md"
}

write_apk_metadata
write_lib_inventory
write_summary

mkdir -p "$ROOT_DIR/docs"
cp "$OUT_DIR/summary.md" "$ROOT_DIR/docs/backend_npu_gallery_lami_initialization_delta.md"

echo "[gallery-lami-delta] wrote $OUT_DIR/summary.md"
echo "[gallery-lami-delta] updated docs/backend_npu_gallery_lami_initialization_delta.md"
printf '%s\n' "$OUT_DIR"
