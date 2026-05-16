#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-artifacts/litert_runtime_compatibility/$TIMESTAMP}"
GRADLE_CACHE="${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}"
GALLERY_APK="${GALLERY_APK:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
LAMI_APK="${LAMI_APK:-app/build/outputs/apk/npuExperiment/debug/app-npuExperiment-debug.apk}"
STANDARD_APK="${STANDARD_APK:-app/build/outputs/apk/standard/debug/app-standard-debug.apk}"
LATEST_NPU_ARTIFACT="${LATEST_NPU_ARTIFACT:-}"
APP_ID="${APP_ID:-io.github.ninbyo02.lami.npu}"

KEYWORDS='LiteRtDispatchGetApi|LiteRtDispatchApiVersion|DispatchApi|dispatch_api|dispatch_delegate|capability|capabilities|vendor|Qualcomm|QNN|Qnn|HTP|Htp|SM8750|libQnnSystem|libQnnHtp|libQnnHtpPrepare|QNN_SYSTEM|QNN_HTP|graph|context|backend|No usable|Failed to initialize|LiteRtQualcommOptionsGet|layout|version|mismatch'
FOCUS_LIB_REGEX='^(libLiteRt\.so|liblitertlm_jni\.so|libLiteRtDispatch_Qualcomm\.so|libLiteRtDispatch\.so|libQnn.*\.so|lib.*[Dd]ispatch.*\.so|lib.*[Cc]ompiler.*\.so)$'
FOCUS_AARS=(
  "com.google.ai.edge.litertlm:litertlm-android:0.11.0"
  "com.google.ai.edge.litertlm:litertlm-android:0.10.0"
  "com.google.mediapipe:tasks-genai:0.10.33"
  "com.qualcomm.qti:qnn-runtime:2.34.0"
  "com.qualcomm.qti:qnn-litert-delegate:2.34.0"
)

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() {
  printf '[litert-runtime-compat] %s\n' "$*"
}

safe_name() {
  printf '%s' "$1" | tr ':/ ' '___'
}

find_aar() {
  local gav="$1"
  local group artifact version
  group="$(printf '%s' "$gav" | cut -d: -f1)"
  artifact="$(printf '%s' "$gav" | cut -d: -f2)"
  version="$(printf '%s' "$gav" | cut -d: -f3)"
  find "$GRADLE_CACHE" -path "*/$group/$artifact/$version/*/$artifact-$version.aar" -type f 2>/dev/null | head -n 1
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

has_symbol() {
  local file="$1"
  local symbol="$2"
  if command -v nm >/dev/null 2>&1 && [ -f "$file" ]; then
    if nm -D "$file" 2>/dev/null | grep -q "$symbol"; then
      printf 'yes'
    else
      printf 'no'
    fi
  else
    printf 'unknown'
  fi
}

elf_class_for() {
  local file="$1"
  if command -v file >/dev/null 2>&1 && [ -f "$file" ]; then
    file -b "$file" | sed 's/[[:space:]]\+/ /g'
  fi
}

extract_apk_libs() {
  local apk="$1"
  local out="$2"
  mkdir -p "$out"
  if [ -f "$apk" ]; then
    unzip -q -o "$apk" 'lib/arm64-v8a/*' -d "$out" 2>/dev/null || true
    printf '%s\n' "$out/lib/arm64-v8a"
  else
    printf '%s\n' "$out/missing"
  fi
}

extract_aar_libs() {
  local aar="$1"
  local out="$2"
  mkdir -p "$out"
  if [ -f "$aar" ]; then
    unzip -q -o "$aar" 'jni/arm64-v8a/*' 'lib/arm64-v8a/*' 'META-INF/*' 'AndroidManifest.xml' -d "$out" 2>/dev/null || true
    if [ -d "$out/jni/arm64-v8a" ]; then
      printf '%s\n' "$out/jni/arm64-v8a"
    elif [ -d "$out/lib/arm64-v8a" ]; then
      printf '%s\n' "$out/lib/arm64-v8a"
    else
      printf '%s\n' "$out/no-arm64-libs"
    fi
  else
    printf '%s\n' "$out/missing"
  fi
}

lib_row() {
  local source="$1"
  local file="$2"
  local lib
  lib="$(basename "$file")"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$source" \
    "$lib" \
    "$(sha_for "$file")" \
    "$(build_id_for "$file")" \
    "$(soname_for "$file")" \
    "$(needed_for "$file")" \
    "$(export_count_for "$file")" \
    "$(has_symbol "$file" 'LiteRtDispatchGetApi')" \
    "$(has_symbol "$file" 'LiteRtQualcommOptionsGet')" \
    "$(elf_class_for "$file")" \
    "$file"
}

collect_source_matrix() {
  local label="$1"
  local dir="$2"
  if [ -d "$dir" ]; then
    find "$dir" -maxdepth 1 -type f -name '*.so' 2>/dev/null |
      sed 's#.*/##' |
      grep -E "$FOCUS_LIB_REGEX" |
      sort |
      while IFS= read -r lib; do
        lib_row "$label" "$dir/$lib"
      done
  fi
}

strings_probe() {
  local label="$1"
  local file="$2"
  local out="$3"
  {
    printf 'source=%s\n' "$label"
    printf 'file=%s\n' "$file"
    printf 'sha256=%s\n' "$(sha_for "$file")"
    printf 'build_id=%s\n' "$(build_id_for "$file")"
    printf 'needed=%s\n' "$(needed_for "$file")"
    printf '\n[nm symbols]\n'
    nm -D "$file" 2>/dev/null | grep -E 'LiteRtDispatchGetApi|LiteRtQualcommOptionsGet|Dispatch|dispatch|Qnn|QNN|Htp|HTP' || true
    printf '\n[string hints]\n'
    strings "$file" 2>/dev/null | grep -Ei "$KEYWORDS" | sort -u | head -n 300 || true
  } >"$out"
}

comparison_state() {
  local gallery_dir="$1"
  local lami_dir="$2"
  local out="$3"
  {
    printf 'library\tstate\tgallery_build_id\tlami_build_id\tgallery_sha256\tlami_sha256\tnotes\n'
    {
      find "$gallery_dir" "$lami_dir" -maxdepth 1 -type f -name '*.so' 2>/dev/null |
        sed 's#.*/##' |
        grep -E "$FOCUS_LIB_REGEX" |
        sort -u
    } | while IFS= read -r lib; do
      [ -n "$lib" ] || continue
      local g="$gallery_dir/$lib"
      local l="$lami_dir/$lib"
      local state notes
      if [ -f "$g" ] && [ -f "$l" ]; then
        if [ "$(sha_for "$g")" = "$(sha_for "$l")" ]; then
          state="same"
          notes="same hash"
        else
          state="different"
          notes="hash/build id differs"
        fi
      elif [ -f "$g" ]; then
        state="only-in-gallery"
        notes="missing from Lami payload"
      elif [ -f "$l" ]; then
        state="only-in-lami"
        notes="missing from Gallery payload"
      else
        state="missing"
        notes="not found"
      fi
      printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$lib" "$state" "$(build_id_for "$g")" "$(build_id_for "$l")" "$(sha_for "$g")" "$(sha_for "$l")" "$notes"
    done
  } >"$out"
}

analyze_offsets() {
  local artifact_dir="$1"
  local lami_dir="$2"
  local out="$3"
  local tombstone=""
  if [ -n "$artifact_dir" ] && [ -f "$artifact_dir/tombstone_latest.txt" ]; then
    tombstone="$artifact_dir/tombstone_latest.txt"
  else
    tombstone="$(find artifacts/npu_diagnostics -maxdepth 2 -name tombstone_latest.txt -type f 2>/dev/null | sort | tail -n 1)"
  fi
  {
    printf 'tombstone=%s\n' "${tombstone:-none}"
    if [ -z "$tombstone" ] || [ ! -f "$tombstone" ]; then
      printf 'status=missing-tombstone\n'
      return
    fi
    printf '\n[backtrace offsets]\n'
    grep -E '      #[0-9]+ pc .*(liblitertlm_jni\.so|libLiteRt\.so|libLiteRtDispatch_Qualcomm\.so)' "$tombstone" || true
    printf '\n[addr2line]\n'
    local addr2line_bin="addr2line"
    if command -v llvm-addr2line >/dev/null 2>&1; then
      addr2line_bin="llvm-addr2line"
    fi
    grep -E '      #[0-9]+ pc .*(liblitertlm_jni\.so|libLiteRt\.so|libLiteRtDispatch_Qualcomm\.so)' "$tombstone" |
      while IFS= read -r line; do
        local offset lib local_lib
        offset="$(printf '%s\n' "$line" | awk '{print $3}')"
        lib="$(printf '%s\n' "$line" | grep -oE 'lib(litertlm_jni|LiteRt|LiteRtDispatch_Qualcomm)\.so' | tail -n 1)"
        local_lib="$lami_dir/$lib"
        if [ -f "$local_lib" ]; then
          printf '%s\n' "$line"
          "$addr2line_bin" -f -C -e "$local_lib" "0x$offset" 2>/dev/null || true
        else
          printf '%s\nlocal-lib-missing=%s\n' "$line" "$local_lib"
        fi
      done
  } >"$out"
}

probe_device_runtime() {
  local out="$1"
  {
    if ! command -v adb >/dev/null 2>&1; then
      printf 'adb=missing\n'
      return
    fi
    local count
    count="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
    printf 'adb_device_count=%s\n' "$count"
    [ "$count" -gt 0 ] || return
    for prop in ro.product.model ro.soc.model ro.soc.manufacturer ro.hardware ro.build.version.sdk ro.build.fingerprint; do
      printf '%s=' "$prop"
      adb shell getprop "$prop" 2>/dev/null | tr -d '\r'
    done
    printf '\n[package nativeLibraryDir]\n'
    adb shell dumpsys package "$APP_ID" 2>/dev/null | grep -E 'nativeLibraryDir=|primaryCpuAbi=|legacyNativeLibraryDir=' || true
    printf '\n[/data/local/tmp/qairt]\n'
    adb shell ls -la /data/local/tmp/qairt 2>&1 | tr -d '\r' || true
    adb shell find /data/local/tmp/qairt -maxdepth 3 -type f -name 'libQnn*.so' 2>/dev/null | tr -d '\r' | sort || true
    printf '\n[app native libs]\n'
    local native_dir legacy_dir
    native_dir="$(adb shell dumpsys package "$APP_ID" 2>/dev/null | sed -n 's/.*nativeLibraryDir=//p' | tr -d '\r' | head -n 1)"
    legacy_dir="$(adb shell dumpsys package "$APP_ID" 2>/dev/null | sed -n 's/.*legacyNativeLibraryDir=//p' | tr -d '\r' | head -n 1)"
    if [ -z "$native_dir" ] && [ -n "$legacy_dir" ]; then
      native_dir="$legacy_dir/arm64"
    fi
    if [ -n "$native_dir" ]; then
      adb shell ls -l "$native_dir" 2>&1 | tr -d '\r' || true
      for lib in libLiteRtDispatch_Qualcomm.so libLiteRt.so liblitertlm_jni.so libQnnSystem.so libQnnHtp.so libQnnHtpPrepare.so libQnnHtpV79Skel.so libQnnHtpV79Stub.so; do
        adb shell sha256sum "$native_dir/$lib" 2>/dev/null | tr -d '\r' || true
      done
    else
      printf 'nativeLibraryDir=unknown\n'
    fi
  } >"$out"
}

log "output: $OUT_DIR"

GALLERY_DIR="$(extract_apk_libs "$GALLERY_APK" "$OUT_DIR/gallery_apk")"
LAMI_DIR="$(extract_apk_libs "$LAMI_APK" "$OUT_DIR/lami_apk")"
STANDARD_DIR="$(extract_apk_libs "$STANDARD_APK" "$OUT_DIR/standard_apk")"

{
  printf '# LiteRT-LM AAR runtime version matrix\n\n'
  printf 'Generated: %s\n\n' "$(date -Is)"
  printf '| GAV | AAR path | arm64 native libs | libLiteRt.so build id | liblitertlm_jni.so build id | version hints |\n'
  printf '| --- | --- | --- | --- | --- | --- |\n'
  for gav in "${FOCUS_AARS[@]}"; do
    aar="$(find_aar "$gav")"
    label="$(safe_name "$gav")"
    aar_dir="$(extract_aar_libs "$aar" "$OUT_DIR/aars/$label")"
    libs="none"
    if [ -d "$aar_dir" ]; then
      libs="$(find "$aar_dir" -maxdepth 1 -type f -name '*.so' 2>/dev/null | sed 's#.*/##' | sort | paste -sd ',' -)"
      [ -n "$libs" ] || libs="none"
    fi
    version_hints="none"
    if [ -f "$aar" ]; then
      version_hints="$(unzip -p "$aar" AndroidManifest.xml 2>/dev/null | strings 2>/dev/null | grep -Ei 'version|litert|qnn|genai' | head -n 6 | paste -sd ';' -)"
      [ -n "$version_hints" ] || version_hints="none"
    fi
    printf '| `%s` | `%s` | `%s` | `%s` | `%s` | `%s` |\n' \
      "$gav" "${aar:-missing}" "$libs" "$(build_id_for "$aar_dir/libLiteRt.so")" "$(build_id_for "$aar_dir/liblitertlm_jni.so")" "$version_hints"
  done
} >"$OUT_DIR/aar_version_matrix.md"

{
  printf 'source\tlibrary\tsha256\tbuild_id\tsoname\tneeded\texported_symbols\tLiteRtDispatchGetApi\tLiteRtQualcommOptionsGet\telf_class\tpath\n'
  collect_source_matrix "gallery-sm8750-apk" "$GALLERY_DIR"
  collect_source_matrix "lami-npuExperiment-apk" "$LAMI_DIR"
  collect_source_matrix "lami-standard-apk" "$STANDARD_DIR"
  for gav in "${FOCUS_AARS[@]}"; do
    aar="$(find_aar "$gav")"
    label="$(safe_name "$gav")"
    aar_dir="$OUT_DIR/aars/$label/jni/arm64-v8a"
    [ -d "$aar_dir" ] || aar_dir="$OUT_DIR/aars/$label/lib/arm64-v8a"
    collect_source_matrix "aar-$gav" "$aar_dir"
  done
} >"$OUT_DIR/native_lib_matrix.tsv"

comparison_state "$GALLERY_DIR" "$LAMI_DIR" "$OUT_DIR/gallery_vs_lami_state.tsv"

mkdir -p "$OUT_DIR/string_hints"
for file in \
  "$GALLERY_DIR/libLiteRtDispatch_Qualcomm.so" \
  "$GALLERY_DIR/libLiteRt.so" \
  "$GALLERY_DIR/liblitertlm_jni.so" \
  "$LAMI_DIR/libLiteRtDispatch_Qualcomm.so" \
  "$LAMI_DIR/libLiteRt.so" \
  "$LAMI_DIR/liblitertlm_jni.so"; do
  if [ -f "$file" ]; then
    strings_probe "$(basename "$(dirname "$file")")" "$file" "$OUT_DIR/string_hints/$(basename "$file").$(sha_for "$file" | cut -c1-8).txt"
  fi
done

if [ -z "$LATEST_NPU_ARTIFACT" ]; then
  LATEST_NPU_ARTIFACT="$(find artifacts/npu_diagnostics -maxdepth 1 -type d 2>/dev/null | sort | tail -n 1)"
fi
analyze_offsets "$LATEST_NPU_ARTIFACT" "$LAMI_DIR" "$OUT_DIR/tombstone_offset_analysis.txt"
probe_device_runtime "$OUT_DIR/device_runtime_probe.txt"

{
  printf '# LiteRT runtime compatibility summary\n\n'
  printf 'Generated: %s\n\n' "$(date -Is)"
  printf '%s\n' "- Gallery APK: \`$GALLERY_APK\`"
  printf '%s\n' "- Lami APK: \`$LAMI_APK\`"
  printf '%s\n' "- Standard APK: \`$STANDARD_APK\`"
  printf '%s\n\n' "- Latest NPU artifact: \`${LATEST_NPU_ARTIFACT:-none}\`"
  printf '## Key files\n\n'
  printf '%s\n' '- `aar_version_matrix.md`'
  printf '%s\n' '- `native_lib_matrix.tsv`'
  printf '%s\n' '- `gallery_vs_lami_state.tsv`'
  printf '%s\n' '- `string_hints/`'
  printf '%s\n' '- `tombstone_offset_analysis.txt`'
  printf '%s\n\n' '- `device_runtime_probe.txt`'
  printf '## Gallery vs Lami state\n\n'
  column -t -s $'\t' "$OUT_DIR/gallery_vs_lami_state.tsv" 2>/dev/null || cat "$OUT_DIR/gallery_vs_lami_state.tsv"
} >"$OUT_DIR/summary.md"

log "done"
printf '%s\n' "$OUT_DIR"
