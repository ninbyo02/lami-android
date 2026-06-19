#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
EDGE_INPUT="${1:-}"
LAMI_INPUT="${2:-$ROOT_DIR/app/build/outputs/apk/standard/debug/app-standard-debug.apk}"

TARGET_LIBS=(
  libLiteRt.so
  liblitertlm_jni.so
)

KEYWORDS=(
  "Statically linked GPU accelerator registered"
  "Dynamically loaded GPU accelerator"
  "LiteRT GpuEnvironment"
  "OpenGL-OpenCL shared context"
  "OpenCL"
  "Vulkan"
  "WebGPU"
  "gpu_options"
  "convert_weights_on_gpu"
  "hint_fully_delegated_to_single_delegate"
  "libLiteRtGpuAccelerator"
  "libLiteRtOpenClAccelerator"
  "libLiteRtVulkanAccelerator"
  "libLiteRtWebGpuAccelerator"
)

PATTERN='Statically linked GPU accelerator registered|Dynamically loaded GPU accelerator|LiteRT GpuEnvironment|OpenGL-OpenCL shared context|OpenCL|OpenCl|Vulkan|WebGPU|WebGpu|gpu_options|convert_weights_on_gpu|hint_fully_delegated_to_single_delegate|libLiteRtGpuAccelerator|libLiteRtOpenClAccelerator|libLiteRtVulkanAccelerator|libLiteRtWebGpuAccelerator'
MAX_CONTEXT_MATCHES_PER_KEYWORD="${MAX_CONTEXT_MATCHES_PER_KEYWORD:-5}"
MAX_SYMBOL_LINES="${MAX_SYMBOL_LINES:-200}"

print_header() {
  printf '\n== %s ==\n' "$1"
}

usage() {
  printf 'usage: %s <edge-gallery-arm64-split.apk|edge-lib-dir> [lami-standard-debug.apk|lami-lib-dir]\n' "$0"
  printf 'default LAMI APK: %s\n' "$LAMI_INPUT"
}

extract_input() {
  local input="$1"
  local label="$2"
  local out_dir="$3"

  mkdir -p "$out_dir"
  if [ -f "$input" ] && printf '%s' "$input" | grep -qi '\.apk$'; then
    unzip -q -o "$input" 'lib/arm64-v8a/*' -d "$out_dir/apk" 2>/dev/null || true
    local apk_lib_dir="$out_dir/apk/lib/arm64-v8a"
    if [ -d "$apk_lib_dir" ]; then
      printf '%s\n' "$apk_lib_dir"
      return
    fi
  fi

  if [ -d "$input" ]; then
    if [ -d "$input/lib/arm64-v8a" ]; then
      printf '%s\n' "$input/lib/arm64-v8a"
      return
    fi
    if [ -d "$input/arm64-v8a" ]; then
      printf '%s\n' "$input/arm64-v8a"
      return
    fi
    printf '%s\n' "$input"
    return
  fi

  printf '%s: missing input %s\n' "$label" "$input" >&2
  printf '%s\n' "$out_dir/missing"
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

write_strings_file() {
  local file="$1"
  local out="$2"
  if command -v strings >/dev/null 2>&1 && [ -f "$file" ]; then
    strings -a "$file" 2>/dev/null | sort -u >"$out"
  else
    : >"$out"
  fi
}

write_symbol_file() {
  local file="$1"
  local out="$2"
  : >"$out"
  local tmp="$out.tmp"
  : >"$tmp"
  if command -v nm >/dev/null 2>&1 && [ -f "$file" ]; then
    nm -D "$file" 2>/dev/null | grep -Ei "$PATTERN|GpuAccelerator|OpenClAccelerator|VulkanAccelerator|WebGpuAccelerator|GpuEnvironment|TopKOpenCl|TopKWebGpu|LiteRt.*Gpu|OpenCL|OpenCl|Vulkan|WebGPU|WebGpu" >>"$tmp" || true
  fi
  if command -v readelf >/dev/null 2>&1 && [ -f "$file" ]; then
    readelf -Ws "$file" 2>/dev/null | grep -Ei "$PATTERN|GpuAccelerator|OpenClAccelerator|VulkanAccelerator|WebGpuAccelerator|GpuEnvironment|TopKOpenCl|TopKWebGpu|LiteRt.*Gpu|OpenCL|OpenCl|Vulkan|WebGPU|WebGpu" >>"$tmp" || true
  fi
  sort -u "$tmp" | head -n "$MAX_SYMBOL_LINES" >"$out"
  rm -f "$tmp"
}

print_keyword_presence() {
  local source="$1"
  local lib="$2"
  local strings_file="$3"

  for keyword in "${KEYWORDS[@]}"; do
    if grep -Fq "$keyword" "$strings_file"; then
      printf '%s\t%s\t%s\tyes\n' "$source" "$lib" "$keyword"
    else
      printf '%s\t%s\t%s\tno\n' "$source" "$lib" "$keyword"
    fi
  done
}

print_keyword_context() {
  local source="$1"
  local lib="$2"
  local raw_strings_file="$3"

  printf '\n-- %s %s keyword context --\n' "$source" "$lib"
  if [ ! -s "$raw_strings_file" ]; then
    printf 'none\n'
    return
  fi

  for keyword in "${KEYWORDS[@]}"; do
    printf '\n[%s]\n' "$keyword"
    match_lines="$(grep -nF "$keyword" "$raw_strings_file" 2>/dev/null | head -n "$MAX_CONTEXT_MATCHES_PER_KEYWORD" | cut -d: -f1 || true)"
    if [ -z "$match_lines" ]; then
      printf 'none\n'
      continue
    fi
    while IFS= read -r line_number; do
      [ -z "$line_number" ] && continue
      awk -v line="$line_number" 'NR >= line - 2 && NR <= line + 2 { printf "%8d\t%s\n", NR, $0 }' "$raw_strings_file"
      printf '%s\n' '--'
    done <<EOF
$match_lines
EOF
  done
}

print_symbol_focus() {
  local source="$1"
  local lib="$2"
  local symbol_file="$3"

  printf '\n-- %s %s focused dynamic symbols --\n' "$source" "$lib"
  if [ -s "$symbol_file" ]; then
    cat "$symbol_file"
  else
    printf 'none\n'
  fi
}

if [ -z "$EDGE_INPUT" ]; then
  usage
  exit 1
fi

if [ ! -e "$EDGE_INPUT" ]; then
  printf 'missing Edge Gallery input: %s\n' "$EDGE_INPUT"
  exit 1
fi

if [ ! -e "$LAMI_INPUT" ]; then
  printf 'missing LAMI input: %s\n' "$LAMI_INPUT"
  printf 'hint: run ./gradlew :app:assembleStandardDebug first, or pass a LAMI APK/lib dir as the second argument\n'
  exit 1
fi

WORK_DIR="${TMPDIR:-/tmp}/litert-gpu-strings-compare-$(date +%s)-$$"
EDGE_DIR="$(extract_input "$EDGE_INPUT" edge "$WORK_DIR/edge")"
LAMI_DIR="$(extract_input "$LAMI_INPUT" lami "$WORK_DIR/lami")"

print_header "LiteRT GPU accelerator string comparison"
printf 'edge_input=%s\n' "$EDGE_INPUT"
printf 'edge_arm64_dir=%s\n' "$EDGE_DIR"
printf 'lami_input=%s\n' "$LAMI_INPUT"
printf 'lami_arm64_dir=%s\n' "$LAMI_DIR"

print_header "Library identity"
printf 'source\tlibrary\tpresent\tsize_bytes\tsha256\tbuild_id\n'
for source in edge lami; do
  dir="$EDGE_DIR"
  [ "$source" = "lami" ] && dir="$LAMI_DIR"
  for lib in "${TARGET_LIBS[@]}"; do
    file="$dir/$lib"
    if [ -f "$file" ]; then
      printf '%s\t%s\tyes\t%s\t%s\t%s\n' "$source" "$lib" "$(size_for "$file")" "$(sha_for "$file")" "$(build_id_for "$file")"
    else
      printf '%s\t%s\tno\tnone\tnone\tnone\n' "$source" "$lib"
    fi
  done
done

print_header "Keyword presence"
printf 'source\tlibrary\tkeyword\tpresent\n'
for source in edge lami; do
  dir="$EDGE_DIR"
  [ "$source" = "lami" ] && dir="$LAMI_DIR"
  for lib in "${TARGET_LIBS[@]}"; do
    file="$dir/$lib"
    raw="$WORK_DIR/${source}_${lib}.strings.raw.txt"
    sorted="$WORK_DIR/${source}_${lib}.strings.sorted.txt"
    if [ -f "$file" ] && command -v strings >/dev/null 2>&1; then
      strings -a "$file" 2>/dev/null >"$raw"
      sort -u "$raw" >"$sorted"
    else
      : >"$raw"
      : >"$sorted"
    fi
    print_keyword_presence "$source" "$lib" "$sorted"
  done
done

print_header "Focused string diffs"
for lib in "${TARGET_LIBS[@]}"; do
  edge_strings="$WORK_DIR/edge_${lib}.strings.sorted.txt"
  lami_strings="$WORK_DIR/lami_${lib}.strings.sorted.txt"
  edge_focus="$WORK_DIR/edge_${lib}.gpu_focus.txt"
  lami_focus="$WORK_DIR/lami_${lib}.gpu_focus.txt"
  grep -Ei "$PATTERN" "$edge_strings" >"$edge_focus" 2>/dev/null || true
  grep -Ei "$PATTERN" "$lami_strings" >"$lami_focus" 2>/dev/null || true
  printf '\n-- %s Edge vs LAMI focused strings diff --\n' "$lib"
  if command -v diff >/dev/null 2>&1; then
    diff -u "$edge_focus" "$lami_focus" || true
  else
    printf 'diff unavailable\n'
  fi
done

print_header "Keyword context"
for source in edge lami; do
  for lib in "${TARGET_LIBS[@]}"; do
    print_keyword_context "$source" "$lib" "$WORK_DIR/${source}_${lib}.strings.raw.txt"
  done
done

print_header "Focused symbol diffs"
for lib in "${TARGET_LIBS[@]}"; do
  edge_file="$EDGE_DIR/$lib"
  lami_file="$LAMI_DIR/$lib"
  edge_symbols="$WORK_DIR/edge_${lib}.symbols.txt"
  lami_symbols="$WORK_DIR/lami_${lib}.symbols.txt"
  write_symbol_file "$edge_file" "$edge_symbols"
  write_symbol_file "$lami_file" "$lami_symbols"
  print_symbol_focus edge "$lib" "$edge_symbols"
  print_symbol_focus lami "$lib" "$lami_symbols"
  printf '\n-- %s Edge vs LAMI focused symbol diff --\n' "$lib"
  if command -v diff >/dev/null 2>&1; then
    diff -u "$edge_symbols" "$lami_symbols" || true
  else
    printf 'diff unavailable\n'
  fi
done

print_header "work directory"
printf '%s\n' "$WORK_DIR"

exit 0
