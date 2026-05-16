#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-artifacts/litertlm_flavor_dependencies/$TIMESTAMP}"
GRADLE_CACHE="${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}"

CONFIGURATIONS=(
  "standardDebugRuntimeClasspath"
  "npuExperimentDebugRuntimeClasspath"
  "galleryStackExperimentDebugRuntimeClasspath"
  "customBuildExperimentDebugRuntimeClasspath"
  "standardReleaseRuntimeClasspath"
  "standardDebugCompileClasspath"
  "npuExperimentDebugCompileClasspath"
  "galleryStackExperimentDebugCompileClasspath"
  "customBuildExperimentDebugCompileClasspath"
)

DEPENDENCIES=(
  "litertlm-android"
  "tasks-genai"
  "litert"
  "mediapipe"
  "qnn-runtime"
  "qnn-litert-delegate"
)

LITERTLM_VERSIONS=(
  "0.10.0"
  "0.10.1"
  "0.10.2"
  "0.11.0"
)

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() {
  printf '[litertlm-flavor-deps] %s\n' "$*"
}

safe_name() {
  printf '%s' "$1" | tr ':/' '__'
}

run_gradle_capture() {
  local output="$1"
  shift
  {
    printf '$ ./gradlew'
    printf ' %q' "$@"
    printf '\n\n'
    ./gradlew "$@"
  } >"$output" 2>&1 || true
}

find_aar() {
  local group="$1"
  local artifact="$2"
  local version="$3"
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

extract_aar() {
  local aar="$1"
  local out="$2"
  mkdir -p "$out"
  if [ -f "$aar" ]; then
    unzip -q -o "$aar" -d "$out" 2>/dev/null || true
  fi
}

class_exists() {
  local jar="$1"
  local class_path="$2"
  if [ -f "$jar" ] && jar tf "$jar" 2>/dev/null | grep -q "^${class_path}\.class$"; then
    printf 'yes'
  else
    printf 'no'
  fi
}

javap_class() {
  local jar="$1"
  local class_name="$2"
  local out="$3"
  if [ -f "$jar" ] && command -v javap >/dev/null 2>&1; then
    javap -classpath "$jar" -public "$class_name" >"$out" 2>&1 || true
  else
    printf 'javap unavailable or classes.jar missing\n' >"$out"
  fi
}

write_version_probe() {
  local version="$1"
  local aar="$2"
  local out="$3"
  local extract_dir="$OUT_DIR/aar_litertlm_$version"
  extract_aar "$aar" "$extract_dir"
  local jar="$extract_dir/classes.jar"
  local lib_dirs=(
    "$extract_dir/jni/arm64-v8a"
    "$extract_dir/lib/arm64-v8a"
  )
  {
    printf 'version=%s\n' "$version"
    printf 'aar=%s\n' "${aar:-missing}"
    printf 'resolve_from_cache=%s\n' "$([ -f "$aar" ] && printf yes || printf no)"
    printf 'classes_jar=%s\n' "$([ -f "$jar" ] && printf yes || printf no)"
    printf '\n[classes]\n'
    printf 'Backend=%s\n' "$(class_exists "$jar" 'com/google/ai/edge/litertlm/Backend')"
    printf 'Backend.NPU=%s\n' "$(class_exists "$jar" 'com/google/ai/edge/litertlm/Backend$NPU')"
    printf 'Backend.GPU=%s\n' "$(class_exists "$jar" 'com/google/ai/edge/litertlm/Backend$GPU')"
    printf 'Backend.CPU=%s\n' "$(class_exists "$jar" 'com/google/ai/edge/litertlm/Backend$CPU')"
    printf 'EngineConfig=%s\n' "$(class_exists "$jar" 'com/google/ai/edge/litertlm/EngineConfig')"
    printf 'Engine=%s\n' "$(class_exists "$jar" 'com/google/ai/edge/litertlm/Engine')"
    printf 'Conversation=%s\n' "$(class_exists "$jar" 'com/google/ai/edge/litertlm/Conversation')"
    printf '\n[native libs]\n'
    for lib_dir in "${lib_dirs[@]}"; do
      [ -d "$lib_dir" ] || continue
      find "$lib_dir" -maxdepth 1 -type f -name '*.so' | sort | while IFS= read -r lib; do
        printf '%s\tsha256=%s\tbuild_id=%s\tneeded=%s\n' \
          "$(basename "$lib")" \
          "$(sha_for "$lib")" \
          "$(build_id_for "$lib")" \
          "$(needed_for "$lib")"
      done
    done
  } >"$out"

  javap_class "$jar" 'com.google.ai.edge.litertlm.Backend' "$OUT_DIR/javap_litertlm_${version}_Backend.txt"
  javap_class "$jar" 'com.google.ai.edge.litertlm.Backend$NPU' "$OUT_DIR/javap_litertlm_${version}_Backend_NPU.txt"
  javap_class "$jar" 'com.google.ai.edge.litertlm.EngineConfig' "$OUT_DIR/javap_litertlm_${version}_EngineConfig.txt"
  javap_class "$jar" 'com.google.ai.edge.litertlm.Engine' "$OUT_DIR/javap_litertlm_${version}_Engine.txt"
}

write_summary() {
  local out="$1"
  {
    printf '# LiteRT-LM flavor dependency summary\n\n'
    printf 'Artifact directory: `%s`\n\n' "$OUT_DIR"
    printf '## Current app declarations\n\n'
    grep -nE 'liteRtLmAndroid(Debug|Release|NpuExperimentDebug|GalleryStackExperimentDebug|CustomBuildExperimentDebug)Version|litertlm-android|debugImplementation|releaseImplementation|npuExperimentDebugImplementation|galleryStackExperimentDebugImplementation|customBuildExperimentDebugImplementation|standardDebugImplementation|npuExperimentImplementation|galleryStackExperimentImplementation|customBuildExperimentImplementation|standardImplementation' app/build.gradle.kts || true
    printf '\n## Configuration files\n\n'
    for config in "${CONFIGURATIONS[@]}"; do
      printf '%s\n' "- \`$config\`: \`${config}_dependencies.txt\`"
    done
    printf '\n## Dependency insight files\n\n'
    for config in "${CONFIGURATIONS[@]}"; do
      for dep in "${DEPENDENCIES[@]}"; do
        printf '%s\n' "- \`$config\` / \`$dep\`: \`${config}_$(safe_name "$dep")_insight.txt\`"
      done
    done
    printf '\n## LiteRT-LM AAR probes\n\n'
    for version in "${LITERTLM_VERSIONS[@]}"; do
      printf '%s\n' "- \`$version\`: \`litertlm_${version}_probe.txt\`"
    done
  } >"$out"
}

insight_has_version() {
  local config="$1"
  local version="$2"
  local file="$OUT_DIR/${config}_litertlm-android_insight.txt"
  if [ -f "$file" ] && grep -q "com.google.ai.edge.litertlm:litertlm-android:$version" "$file"; then
    printf 'yes'
  else
    printf 'no'
  fi
}

write_assertions() {
  local out="$1"
  local standard_debug_has_011
  local standard_debug_has_010
  local npu_debug_has_010
  local npu_debug_has_011
  local gallery_debug_has_010
  local gallery_debug_has_011
  local custom_debug_has_010
  local custom_debug_has_011
  local standard_release_has_010
  standard_debug_has_011="$(insight_has_version standardDebugRuntimeClasspath 0.11.0)"
  standard_debug_has_010="$(insight_has_version standardDebugRuntimeClasspath 0.10.0)"
  npu_debug_has_010="$(insight_has_version npuExperimentDebugRuntimeClasspath 0.10.0)"
  npu_debug_has_011="$(insight_has_version npuExperimentDebugRuntimeClasspath 0.11.0)"
  gallery_debug_has_010="$(insight_has_version galleryStackExperimentDebugRuntimeClasspath 0.10.0)"
  gallery_debug_has_011="$(insight_has_version galleryStackExperimentDebugRuntimeClasspath 0.11.0)"
  custom_debug_has_010="$(insight_has_version customBuildExperimentDebugRuntimeClasspath 0.10.0)"
  custom_debug_has_011="$(insight_has_version customBuildExperimentDebugRuntimeClasspath 0.11.0)"
  standard_release_has_010="$(insight_has_version standardReleaseRuntimeClasspath 0.10.0)"
  {
    printf 'artifact_dir=%s\n' "$OUT_DIR"
    printf 'standardDebugRuntimeClasspath has litertlm-android:0.11.0: %s\n' "$standard_debug_has_011"
    printf 'standardDebugRuntimeClasspath selects litertlm-android:0.10.0: %s\n' "$standard_debug_has_010"
    printf 'npuExperimentDebugRuntimeClasspath has litertlm-android:0.10.0: %s\n' "$npu_debug_has_010"
    printf 'npuExperimentDebugRuntimeClasspath selects litertlm-android:0.11.0: %s\n' "$npu_debug_has_011"
    printf 'galleryStackExperimentDebugRuntimeClasspath has litertlm-android:0.11.0: %s\n' "$gallery_debug_has_011"
    printf 'galleryStackExperimentDebugRuntimeClasspath selects litertlm-android:0.10.0: %s\n' "$gallery_debug_has_010"
    printf 'customBuildExperimentDebugRuntimeClasspath has litertlm-android:0.11.0: %s\n' "$custom_debug_has_011"
    printf 'customBuildExperimentDebugRuntimeClasspath selects litertlm-android:0.10.0: %s\n' "$custom_debug_has_010"
    printf 'standardReleaseRuntimeClasspath has litertlm-android:0.10.0: %s\n' "$standard_release_has_010"
    printf 'overall: %s\n' "$(
      if [ "$standard_debug_has_011" = yes ] &&
        [ "$standard_debug_has_010" = no ] &&
        [ "$npu_debug_has_010" = yes ] &&
        [ "$npu_debug_has_011" = no ] &&
        [ "$gallery_debug_has_011" = yes ] &&
        [ "$gallery_debug_has_010" = no ] &&
        [ "$custom_debug_has_011" = yes ] &&
        [ "$custom_debug_has_010" = no ] &&
        [ "$standard_release_has_010" = yes ]; then
        printf 'expected-split'
      else
        printf 'unexpected-resolution'
      fi
    )"
  } >"$out"
}

log "output: $OUT_DIR"

for config in "${CONFIGURATIONS[@]}"; do
  log "dependencies $config"
  run_gradle_capture "$OUT_DIR/${config}_dependencies.txt" ":app:dependencies" "--configuration" "$config"
  for dep in "${DEPENDENCIES[@]}"; do
    log "dependencyInsight $config $dep"
    run_gradle_capture "$OUT_DIR/${config}_$(safe_name "$dep")_insight.txt" \
      ":app:dependencyInsight" "--configuration" "$config" "--dependency" "$dep"
  done
done

for version in "${LITERTLM_VERSIONS[@]}"; do
  aar="$(find_aar 'com.google.ai.edge.litertlm' 'litertlm-android' "$version")"
  write_version_probe "$version" "$aar" "$OUT_DIR/litertlm_${version}_probe.txt"
done

write_summary "$OUT_DIR/summary.md"
write_assertions "$OUT_DIR/summary.txt"
log "wrote $OUT_DIR/summary.md"
log "wrote $OUT_DIR/summary.txt"
log "done"
