#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK_PATH="${1:-$ROOT_DIR/app/build/outputs/apk/standard/debug/app-standard-debug.apk}"

TARGET_LIBS=(
  libLiteRt.so
  liblitertlm_jni.so
  libLiteRtDispatch_Qualcomm.so
  libLiteRtCompilerPlugin_Qualcomm.so
  libQnnSystem.so
  libQnnGpu.so
  libQnnHtp.so
  libQnnHtpPrepare.so
  libQnnHtpV79Stub.so
  libQnnHtpV79Skel.so
  libQnnDsp.so
  libGemmaModelConstraintProvider.so
)

print_header() {
  printf '\n== %s ==\n' "$1"
}

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

needed_for() {
  local file="$1"
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -d "$file" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p' | paste -sd ',' -
  fi
}

apk_sha_for_lib() {
  local apk="$1"
  local lib="$2"
  if unzip -p "$apk" "lib/arm64-v8a/$lib" >/dev/null 2>&1; then
    unzip -p "$apk" "lib/arm64-v8a/$lib" 2>/dev/null | sha256sum | awk '{print $1}'
  fi
}

apk_size_for_lib() {
  local apk="$1"
  local lib="$2"
  unzip -l "$apk" "lib/arm64-v8a/$lib" 2>/dev/null | awk -v path="lib/arm64-v8a/$lib" '$4 == path {print $1; exit}'
}

print_file_source_candidates() {
  local lib="$1"
  local final_sha="$2"
  local found="no"
  local roots=(
    "$ROOT_DIR/app/src/main/jniLibs"
    "$ROOT_DIR/app/src/debug/jniLibs"
    "$ROOT_DIR/app/src/standardDebug/jniLibs"
    "$ROOT_DIR/app/src/customBuildExperimentDebug/jniLibs"
    "$ROOT_DIR/app/src/galleryStackExperimentDebug/jniLibs"
    "$ROOT_DIR/app/src/galleryAlignedNpuProbeDebug/jniLibs"
    "$ROOT_DIR/app/src/npuExperimentDebug/jniLibs"
    "$ROOT_DIR/app/build/generated"
    "$ROOT_DIR/app/build/intermediates/merged_native_libs"
    "$ROOT_DIR/app/build/intermediates/stripped_native_libs"
    "${HOME:-}/.gradle/caches/transforms-3"
    "${HOME:-}/.gradle/caches/transforms-4"
  )

  for root in "${roots[@]}"; do
    [ -d "$root" ] || continue
    while IFS= read -r candidate; do
      [ -f "$candidate" ] || continue
      candidate_sha="$(sha_for "$candidate")"
      if [ "$candidate_sha" = "$final_sha" ]; then
        printf '%s;' "$candidate"
        found="yes"
      fi
    done < <(find "$root" -type f -name "$lib" 2>/dev/null)
  done

  if [ "$found" = "no" ]; then
    printf 'none'
  fi
}

print_aar_source_candidates() {
  local lib="$1"
  local final_sha="$2"
  local found="no"
  local cache_root="${HOME:-}/.gradle/caches/modules-2/files-2.1"

  [ -d "$cache_root" ] || {
    printf 'none'
    return
  }

  while IFS= read -r aar; do
    if unzip -l "$aar" "jni/arm64-v8a/$lib" >/dev/null 2>&1; then
      aar_sha="$(unzip -p "$aar" "jni/arm64-v8a/$lib" 2>/dev/null | sha256sum | awk '{print $1}')"
      if [ "$aar_sha" = "$final_sha" ]; then
        printf '%s!jni/arm64-v8a/%s;' "$aar" "$lib"
        found="yes"
      fi
    fi
  done < <(find "$cache_root" -type f -name '*.aar' 2>/dev/null | grep -Ei 'litert|tensorflow|ai.edge|mediapipe|tasks|qnn|qualcomm' || true)

  if [ "$found" = "no" ]; then
    printf 'none'
  fi
}

origin_bucket_for() {
  local file_sources="$1"
  local aar_sources="$2"
  case "$file_sources" in
    *"/app/build/generated/qairt244StandardDebugJniLibs/"*|*"/app/src/customBuildExperimentDebug/jniLibs/"*)
      printf 'qairt244_standard_debug_overlay'
      return
      ;;
    *"/app/src/main/jniLibs/"*)
      printf 'app_main_jniLibs'
      return
      ;;
  esac
  case "$aar_sources" in
    *".aar!"*)
      printf 'aar_dependency'
      return
      ;;
  esac
  case "$file_sources" in
    *"/app/build/intermediates/merged_native_libs/"*|*"/app/build/intermediates/stripped_native_libs/"*)
      printf 'intermediate_only'
      return
      ;;
  esac
  printf 'unknown'
}

print_header "standardDebug APK native library dump"
printf 'apk=%s\n' "$APK_PATH"

if [ ! -f "$APK_PATH" ]; then
  printf 'status=missing_apk\n'
  printf 'hint=run ./gradlew :app:assembleStandardDebug first, or pass an APK path as the first argument\n'
  exit 1
fi

printf 'status=ok\n'
printf 'apk_size_bytes=%s\n' "$(size_for "$APK_PATH")"
printf 'apk_sha256=%s\n' "$(sha_for "$APK_PATH")"

print_header "APK lib/arm64-v8a inventory"
printf 'path\tsize_bytes\tsha256\n'
unzip -l "$APK_PATH" 2>/dev/null |
  awk '$4 ~ /^lib\/arm64-v8a\/.*\.so$/ {print $4}' |
  sort |
  while IFS= read -r apk_lib_path; do
    lib="${apk_lib_path##*/}"
    printf '%s\t%s\t%s\n' "$apk_lib_path" "$(apk_size_for_lib "$APK_PATH" "$lib")" "$(apk_sha_for_lib "$APK_PATH" "$lib")"
  done

print_header "Target library matrix"
printf 'library\tpresent\tapk_size_bytes\tapk_sha256\tbuild_id\tneeded\torigin_bucket\tmatching_file_sources\tmatching_aar_sources\n'
for lib in "${TARGET_LIBS[@]}"; do
  extracted="${TMPDIR:-/tmp}/lami-apk-native-dump-$$/$lib"
  mkdir -p "$(dirname "$extracted")"
  if unzip -p "$APK_PATH" "lib/arm64-v8a/$lib" >"$extracted" 2>/dev/null; then
    final_sha="$(sha_for "$extracted")"
    file_sources="$(print_file_source_candidates "$lib" "$final_sha")"
    aar_sources="$(print_aar_source_candidates "$lib" "$final_sha")"
    origin_bucket="$(origin_bucket_for "$file_sources" "$aar_sources")"
    printf '%s\tyes\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$lib" \
      "$(size_for "$extracted")" \
      "$final_sha" \
      "$(build_id_for "$extracted" | awk '{print $1}')" \
      "$(needed_for "$extracted")" \
      "$origin_bucket" \
      "$file_sources" \
      "$aar_sources"
  else
    printf '%s\tno\tnone\tnone\tnone\tnone\tmissing\tnone\tnone\n' "$lib"
  fi
done

print_header "Overlay winner summary"
printf 'meaning=origin_bucket is decided by SHA match against the final APK entry\n'
printf 'qairt244_overlay_input=%s\n' "$ROOT_DIR/app/build/generated/qairt244StandardDebugJniLibs/arm64-v8a"
printf 'qairt244_overlay_source=%s\n' "$ROOT_DIR/app/src/customBuildExperimentDebug/jniLibs/arm64-v8a"
printf 'merged_native_libs=%s\n' "$ROOT_DIR/app/build/intermediates/merged_native_libs/standardDebug/mergeStandardDebugNativeLibs/out/lib/arm64-v8a"
printf 'stripped_native_libs=%s\n' "$ROOT_DIR/app/build/intermediates/stripped_native_libs/standardDebug/stripStandardDebugDebugSymbols/out/lib/arm64-v8a"

exit 0
