#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-artifacts/gallery_litert_version_mapping/$TIMESTAMP}"
APK_PATH="${1:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
GRADLE_CACHE="${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}"
GALLERY_1012_DIR="${GALLERY_1012_DIR:-/tmp/google-ai-edge-gallery-1.0.12}"
GALLERY_1013_DIR="${GALLERY_1013_DIR:-/tmp/google-ai-edge-gallery-1.0.13}"

FOCUS_LIB_REGEX='^(libLiteRt\.so|liblitertlm_jni\.so|libLiteRtDispatch_Qualcomm\.so|libLiteRtDispatch\.so|libQnn.*\.so|lib.*[Dd]ispatch.*\.so|lib.*[Cc]ompiler.*\.so)$'
STRING_HINTS='litertlm|LiteRT|LiteRt|dispatch|Dispatch|Qualcomm|QNN|Qnn|HTP|Htp|version|build|git|0\.10|0\.11|1\.0|third_party_ai_edge|github_release|sm8750'

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() {
  printf '[gallery-litert-map] %s\n' "$*"
}

find_aapt() {
  if command -v aapt >/dev/null 2>&1; then
    command -v aapt
    return
  fi
  find "$HOME/Android/Sdk/build-tools" -path '*/aapt' -type f 2>/dev/null | sort -V | tail -n 1
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

elf_file_for() {
  local file="$1"
  if command -v file >/dev/null 2>&1 && [ -f "$file" ]; then
    file -b "$file" | sed 's/[[:space:]]\+/ /g'
  fi
}

safe_unzip() {
  local archive="$1"
  local pattern="$2"
  local out="$3"
  if [ -f "$archive" ]; then
    unzip -q -o "$archive" "$pattern" -d "$out" 2>/dev/null || true
  fi
}

extract_apk_libs() {
  local apk="$1"
  local out="$2"
  mkdir -p "$out"
  safe_unzip "$apk" 'lib/arm64-v8a/*' "$out"
  printf '%s\n' "$out/lib/arm64-v8a"
}

find_aar() {
  local group="$1"
  local artifact="$2"
  local version="$3"
  find "$GRADLE_CACHE" -path "*/$group/$artifact/$version/*/$artifact-$version.aar" -type f 2>/dev/null | head -n 1
}

extract_aar_libs() {
  local aar="$1"
  local out="$2"
  mkdir -p "$out"
  safe_unzip "$aar" 'jni/arm64-v8a/*' "$out"
  safe_unzip "$aar" 'lib/arm64-v8a/*' "$out"
  if [ -d "$out/jni/arm64-v8a" ]; then
    printf '%s\n' "$out/jni/arm64-v8a"
  elif [ -d "$out/lib/arm64-v8a" ]; then
    printf '%s\n' "$out/lib/arm64-v8a"
  else
    printf '%s\n' "$out/no-arm64-libs"
  fi
}

lib_row() {
  local source="$1"
  local file="$2"
  local lib
  lib="$(basename "$file")"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$source" \
    "$lib" \
    "$(sha_for "$file")" \
    "$(build_id_for "$file")" \
    "$(soname_for "$file")" \
    "$(needed_for "$file")" \
    "$(export_count_for "$file")" \
    "$(has_symbol "$file" 'LiteRtDispatchGetApi')" \
    "$(has_symbol "$file" 'LiteRtQualcommOptionsGet')" \
    "$(elf_file_for "$file")"
}

collect_lib_matrix() {
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

source_probe() {
  local label="$1"
  local dir="$2"
  local out="$3"
  {
    printf '## %s\n\n' "$label"
    printf 'path: `%s`\n\n' "$dir"
    if [ ! -d "$dir/.git" ]; then
      printf 'status: missing local clone\n\n'
      return
    fi
    (
      cd "$dir" || exit 0
      printf 'commit: `%s`\n' "$(git rev-parse HEAD 2>/dev/null || true)"
      printf 'tag: `%s`\n\n' "$(git describe --tags --exact-match 2>/dev/null || true)"
    )
    local android="$dir/Android/src"
    if [ -f "$android/gradle/libs.versions.toml" ]; then
      printf '### version catalog excerpts\n\n'
      grep -Ei 'litertlm|tasks-genai|genai|tflite|qnn|mediapipe|playServicesTflite|mlkit' "$android/gradle/libs.versions.toml" || true
      printf '\n'
    fi
    if [ -f "$android/app/build.gradle.kts" ]; then
      printf '### app build.gradle.kts excerpts\n\n'
      grep -Ei 'applicationId|namespace|compileSdk|targetSdk|versionCode|versionName|litertlm|tasks|qnn|jniLibs|implementation' "$android/app/build.gradle.kts" || true
      printf '\n'
    fi
    printf '### source dispatch/QNN packaging hits\n\n'
    grep -RInE 'LiteRtDispatch|dispatch_api|Qnn|QNN|jniLibs|nativeLibraryDir|Backend.NPU|EngineConfig' "$android" 2>/dev/null | head -n 200 || true
    printf '\n'
  } >"$out"
}

write_apk_metadata() {
  local apk="$1"
  local out="$2"
  local aapt_bin
  aapt_bin="$(find_aapt)"
  {
    printf 'apk=%s\n' "$apk"
    if [ ! -f "$apk" ]; then
      printf 'status=missing\n'
      return
    fi
    printf 'sha256=%s\n\n' "$(sha_for "$apk")"
    if [ -n "$aapt_bin" ]; then
      printf '[aapt dump badging]\n'
      "$aapt_bin" dump badging "$apk" 2>&1 || true
      printf '\n[aapt manifest excerpts]\n'
      "$aapt_bin" dump xmltree "$apk" AndroidManifest.xml 2>&1 |
        grep -E 'package=|compileSdkVersion|targetSdkVersion|minSdkVersion|uses-native-library|application|activity' || true
    else
      printf 'aapt=missing\n'
    fi
    printf '\n[native libs]\n'
    unzip -l "$apk" 'lib/*' 2>/dev/null | grep -E '\.so$|Archive:|Name' || true
    printf '\n[assets/res/META-INF overview]\n'
    unzip -l "$apk" 2>/dev/null | grep -E '(^Archive:|META-INF/|assets/|res/raw/)' | head -n 300 || true
    printf '\n[dex/apk string hints]\n'
    {
      unzip -p "$apk" 'classes*.dex' 2>/dev/null | strings 2>/dev/null || true
      strings "$apk" 2>/dev/null || true
    } | grep -Ei "$STRING_HINTS" | sort -u | head -n 500 || true
  } >"$out"
}

write_lami_dependencies() {
  local out="$1"
  {
    printf '[Lami Gradle dependency declarations]\n'
    grep -RInE 'litertlm|tasks-genai|qnn-runtime|qnn-litert-delegate|mediapipe|LiteRt|QNN' app/build.gradle.kts build.gradle.kts settings.gradle.kts gradle 2>/dev/null || true
    printf '\n[Lami focused AAR cache paths]\n'
    for gav in \
      'com.google.ai.edge.litertlm:litertlm-android:0.11.0' \
      'com.google.ai.edge.litertlm:litertlm-android:0.10.0' \
      'com.google.mediapipe:tasks-genai:0.10.33' \
      'com.qualcomm.qti:qnn-runtime:2.34.0' \
      'com.qualcomm.qti:qnn-litert-delegate:2.34.0'
    do
      local group artifact version aar
      group="$(printf '%s' "$gav" | cut -d: -f1)"
      artifact="$(printf '%s' "$gav" | cut -d: -f2)"
      version="$(printf '%s' "$gav" | cut -d: -f3)"
      aar="$(find_aar "$group" "$artifact" "$version")"
      printf '%s\t%s\n' "$gav" "${aar:-missing}"
    done
  } >"$out"
}

write_summary() {
  local out="$1"
  {
    printf '# Gallery LiteRT version mapping summary\n\n'
    printf 'APK: `%s`\n\n' "$APK_PATH"
    printf 'Artifact directory: `%s`\n\n' "$OUT_DIR"
    printf 'Key files:\n\n'
    printf '%s\n' '- `apk_metadata.txt`'
    printf '%s\n' '- `native_lib_matrix.tsv`'
    printf '%s\n' '- `source_1.0.12.md`'
    printf '%s\n' '- `source_1.0.13.md`'
    printf '%s\n\n' '- `lami_dependencies.txt`'
    printf 'Static conclusion template:\n\n'
    printf '%s\n' '- Gallery public source tag 1.0.12 uses `com.google.ai.edge.litertlm:litertlm-android:0.10.0`.'
    printf '%s\n' '- The SM8750 APK native payload contains dispatch/QNN libraries not declared by the public Gradle project.'
    printf '%s\n' '- Compare Build IDs in `native_lib_matrix.tsv` before treating any dispatch runtime as same-generation.'
  } >"$out"
}

log "output: $OUT_DIR"

write_apk_metadata "$APK_PATH" "$OUT_DIR/apk_metadata.txt"

printf 'source\tlibrary\tsha256\tbuild_id\tsoname\tneeded\texported_symbols\tLiteRtDispatchGetApi\tLiteRtQualcommOptionsGet\tfile\n' >"$OUT_DIR/native_lib_matrix.tsv"
if [ -f "$APK_PATH" ]; then
  GALLERY_LIB_DIR="$(extract_apk_libs "$APK_PATH" "$OUT_DIR/gallery_apk")"
  collect_lib_matrix "gallery-sm8750-apk" "$GALLERY_LIB_DIR" >>"$OUT_DIR/native_lib_matrix.tsv"
else
  log "missing APK: $APK_PATH"
fi

for gav in \
  'com.google.ai.edge.litertlm:litertlm-android:0.11.0' \
  'com.google.ai.edge.litertlm:litertlm-android:0.10.0' \
  'com.google.mediapipe:tasks-genai:0.10.33' \
  'com.qualcomm.qti:qnn-runtime:2.34.0' \
  'com.qualcomm.qti:qnn-litert-delegate:2.34.0'
do
  group="$(printf '%s' "$gav" | cut -d: -f1)"
  artifact="$(printf '%s' "$gav" | cut -d: -f2)"
  version="$(printf '%s' "$gav" | cut -d: -f3)"
  aar="$(find_aar "$group" "$artifact" "$version")"
  label="$artifact-$version"
  if [ -n "$aar" ]; then
    lib_dir="$(extract_aar_libs "$aar" "$OUT_DIR/aar_$label")"
    collect_lib_matrix "$gav" "$lib_dir" >>"$OUT_DIR/native_lib_matrix.tsv"
  fi
done

source_probe "google-ai-edge/gallery 1.0.12" "$GALLERY_1012_DIR" "$OUT_DIR/source_1.0.12.md"
source_probe "google-ai-edge/gallery 1.0.13" "$GALLERY_1013_DIR" "$OUT_DIR/source_1.0.13.md"
write_lami_dependencies "$OUT_DIR/lami_dependencies.txt"
write_summary "$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
log "wrote $OUT_DIR/native_lib_matrix.tsv"
log "done"
