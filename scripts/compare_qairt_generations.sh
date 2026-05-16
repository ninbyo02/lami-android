#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/artifacts/qairt_generation_compare/$TIMESTAMP}"

QAIRT_ROOTS=()
GALLERY_APK="${GALLERY_APK:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"

usage() {
  cat <<'EOF'
Usage:
  bash scripts/compare_qairt_generations.sh [--qairt-root <path> ...] [--gallery-apk <path>]

Static-only QAIRT/QNN generation comparison. The script does not build, does
not install an app, does not run Engine.initialize, and does not modify app
jniLibs.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --qairt-root)
      [ $# -ge 2 ] || { echo "ERROR: --qairt-root requires a path" >&2; exit 2; }
      QAIRT_ROOTS+=("$2")
      shift 2
      ;;
    --gallery-apk)
      [ $# -ge 2 ] || { echo "ERROR: --gallery-apk requires a path" >&2; exit 2; }
      GALLERY_APK="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ "${#QAIRT_ROOTS[@]}" -eq 0 ]; then
  for candidate in \
    "$HOME/compose/qairt/workspace/sdk/qairt/2.42.0.251225" \
    "$HOME/compose/qairt/workspace/sdk/qairt/2.44.0.260225" \
    "$HOME/compose/qairt/workspace/sdk/qairt/2.46.0.260424" \
    "$HOME/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225"; do
    QAIRT_ROOTS+=("$candidate")
  done
fi

mkdir -p "$OUT_DIR/metadata" "$OUT_DIR/strings" "$OUT_DIR/extracted_gallery"

KEYWORDS='QNN|QAIRT|HTP|V79|V75|V73|V68|SM8750|QCS9075|QCS6490|Hexagon|ADSP|LD_LIBRARY_PATH|DSP_LIBRARY|fastrpc|version|capability|schema|model'

build_id_for() {
  local file="$1"
  readelf -n "$file" 2>/dev/null | awk '/Build ID:/ {print $3; exit}'
}

needed_for() {
  local file="$1"
  readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | paste -sd ',' -
}

metadata_for() {
  local label="$1"
  local file="$2"
  local out="$3"
  local name
  name="$(basename "$file")"
  if [ ! -f "$file" ]; then
    printf '%s\t%s\tmissing\t\t\t\t%s\n' "$label" "$name" "$file" >>"$OUT_DIR/qnn_generation_matrix.tsv"
    return
  fi
  local size sha build_id needed
  size="$(wc -c <"$file" 2>/dev/null | tr -d ' ' || true)"
  sha="$(sha256sum "$file" 2>/dev/null | awk '{print $1}' || true)"
  build_id="$(build_id_for "$file")"
  needed="$(needed_for "$file")"
  printf '%s\t%s\tpresent\t%s\t%s\t%s\t%s\t%s\n' "$label" "$name" "$size" "$sha" "$build_id" "$needed" "$file" >>"$OUT_DIR/qnn_generation_matrix.tsv"
  {
    printf 'label=%s\n' "$label"
    printf 'path=%s\n' "$file"
    printf 'size=%s\n' "$size"
    printf 'sha256=%s\n' "$sha"
    printf 'build_id=%s\n' "$build_id"
    printf 'needed=%s\n' "$needed"
    file "$file" 2>/dev/null || true
  } >"$out"
  strings "$file" 2>/dev/null | grep -Ei "$KEYWORDS" | sort -u >"$OUT_DIR/strings/${label}_${name}.txt" || true
}

{
  printf 'timestamp=%s\n' "$TIMESTAMP"
  printf 'gallery_apk=%s\n' "$GALLERY_APK"
  printf 'safety=no build, no app install, no Engine.initialize, no NPU inference\n'
} >"$OUT_DIR/context.env"

printf 'label\tlibrary\tpresent\tsize\tsha256\tbuild_id\tneeded\tpath\n' >"$OUT_DIR/qnn_generation_matrix.tsv"
printf 'label\troot\trealpath\texists\tlooks_like_version\n' >"$OUT_DIR/qairt_roots.tsv"

LIBS=(
  libQnnSystem.so
  libQnnHtp.so
  libQnnHtpPrepare.so
  libQnnHtpV79Stub.so
  libQnnHtpV79Skel.so
  libQnnDsp.so
  libQnnGpu.so
  libQnnTFLiteDelegate.so
)

for root in "${QAIRT_ROOTS[@]}"; do
  real="$(readlink -f "$root" 2>/dev/null || printf '%s' "$root")"
  exists=false
  [ -d "$root" ] && exists=true
  version="unknown"
  case "$real" in
    *2.42.0.251225*) version="2.42.0.251225" ;;
    *2.44.0.260225*) version="2.44.0.260225" ;;
    *2.46.0.260424*) version="2.46.0.260424" ;;
  esac
  label="qairt_${version//./_}"
  printf '%s\t%s\t%s\t%s\t%s\n' "$label" "$root" "$real" "$exists" "$version" >>"$OUT_DIR/qairt_roots.tsv"
  [ "$exists" = true ] || continue
  for lib in "${LIBS[@]}"; do
    metadata_for "$label" "$root/lib/aarch64-android/$lib" "$OUT_DIR/metadata/${label}_${lib}.txt"
    metadata_for "${label}_hexagon_v79" "$root/lib/hexagon-v79/unsigned/$lib" "$OUT_DIR/metadata/${label}_hexagon_v79_${lib}.txt"
  done
done

if [ -f "$GALLERY_APK" ]; then
  for lib in libLiteRt.so libLiteRtDispatch_Qualcomm.so liblitertlm_jni.so "${LIBS[@]}"; do
    out="$OUT_DIR/extracted_gallery/$lib"
    unzip -p "$GALLERY_APK" "lib/arm64-v8a/$lib" >"$out" 2>/dev/null || rm -f "$out"
    metadata_for "gallery_sm8750" "$out" "$OUT_DIR/metadata/gallery_sm8750_${lib}.txt"
  done
else
  printf 'missing gallery apk: %s\n' "$GALLERY_APK" >"$OUT_DIR/gallery_missing.txt"
fi

cat >"$OUT_DIR/summary.md" <<EOF
# QAIRT Generation Compare Summary

- Output: \`$OUT_DIR\`
- Gallery APK: \`$GALLERY_APK\`
- Safety: no build, no app install, no Engine.initialize, no NPU inference

Key files:

- \`qairt_roots.tsv\`
- \`qnn_generation_matrix.tsv\`
- \`metadata/\`
- \`strings/\`

This script is static-only and is intended to support generation comparison
between Radxa-public QAIRT 2.42, LiteRT-public QAIRT 2.44 metadata, local QAIRT
2.46, and Gallery SM8750 payloads.
EOF

printf '%s\n' "$OUT_DIR"
