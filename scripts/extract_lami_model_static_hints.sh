#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="$ROOT_DIR/artifacts/lami_model_static"
DRY_RUN=0
INPUTS=()

MODEL_PATTERN='GPU_ARTISAN|CPU_ARTISAN|GOOGLE_TENSOR_ARTISAN|backend|constraint|requires one of|requires_one_of|sm8750|SM8750|qualcomm|Qualcomm|artisan|Artisan|gpu|GPU|npu|NPU|litert|LiteRT|executor|RuntimeConfig|EngineConfig|tflite_gpu_kv_cache|tflite_opencl_kv_cache'
DEFAULT_DEVICE_MODEL_PATH='/data/user/0/io.github.ninbyo02.lami/files/local_models/1781265409941_gemma-4-E2B-it.litertlm'
DEFAULT_PACKAGE='io.github.ninbyo02.lami'

usage() {
  printf 'usage: %s [--input <model-file-or-dir>] [--output <out-dir>] [--dry-run]\n' "$0"
  printf 'default output: %s\n' "$OUTPUT_DIR"
  printf 'device reference path: %s\n' "$DEFAULT_DEVICE_MODEL_PATH"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --input)
      shift
      INPUTS+=("${1:-}")
      ;;
    --output)
      shift
      OUTPUT_DIR="${1:-}"
      ;;
    --dry-run)
      DRY_RUN=1
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      INPUTS+=("$1")
      ;;
  esac
  shift || true
done

safe_name() {
  basename "$1" | sed 's/[^A-Za-z0-9._-]/_/g'
}

sha_for() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1 && [ -f "$file" ]; then
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
    printf '0'
  fi
}

write_keyword_presence_header() {
  local out="$1"
  printf 'model_file\tkeyword\tpresent\n' >"$out"
}

append_keyword_presence() {
  local model_file="$1"
  local hint_file="$2"
  local out="$3"
  while IFS= read -r keyword; do
    [ -z "$keyword" ] && continue
    if grep -Fqi "$keyword" "$hint_file" 2>/dev/null; then
      printf '%s\t%s\tyes\n' "$model_file" "$keyword" >>"$out"
    else
      printf '%s\t%s\tno\n' "$model_file" "$keyword" >>"$out"
    fi
  done <<'EOF'
GPU_ARTISAN
CPU_ARTISAN
GOOGLE_TENSOR_ARTISAN
backend
constraint
requires one of
sm8750
qualcomm
gpu
npu
artisan
tflite_gpu_kv_cache
tflite_opencl_kv_cache
EOF
}

collect_input_files() {
  if [ "${#INPUTS[@]}" -eq 0 ]; then
    for candidate in \
      "$OUTPUT_DIR/input" \
      "$ROOT_DIR/artifacts/lami_model_static/input" \
      "$ROOT_DIR/artifacts/lami_model_static"; do
      [ -d "$candidate" ] || continue
      find "$candidate" -maxdepth 2 -type f \( -name '*.litertlm' -o -name '*.task' -o -name '*.bin' \) 2>/dev/null
    done
    return
  fi

  for input in "${INPUTS[@]}"; do
    [ -z "$input" ] && continue
    if [ -d "$input" ]; then
      find "$input" -maxdepth 2 -type f \( -name '*.litertlm' -o -name '*.task' -o -name '*.bin' \) 2>/dev/null
    elif [ -f "$input" ]; then
      printf '%s\n' "$input"
    else
      printf 'missing_input=%s\n' "$input" >&2
    fi
  done
}

write_device_instructions() {
  local out="$1"
  cat >"$out" <<EOF
# LAMI local model static pull/list instructions

No logcat is required.

Reference model path:

  $DEFAULT_DEVICE_MODEL_PATH

List LAMI local models when the installed app is debuggable:

  adb shell run-as $DEFAULT_PACKAGE ls -la files/local_models

Pull one model through run-as + stdout redirection if permitted by your shell:

  adb exec-out run-as $DEFAULT_PACKAGE cat files/local_models/<model-file-name> > artifacts/lami_model_static/input/<model-file-name>

If run-as is unavailable, record:

  run_as_available=false
  run_as_failure=<exact shell message>

Then run:

  scripts/extract_lami_model_static_hints.sh --input artifacts/lami_model_static/input
EOF
}

printf 'lami_model_static_output=%s\n' "$OUTPUT_DIR"

if [ "$DRY_RUN" = "1" ]; then
  printf 'dry_run=true\n'
  printf 'planned_outputs=summary.txt,model_inventory.tsv,model_keyword_presence.tsv,strings/*.backend_hints.txt,all_model_backend_hints.txt,device_pull_instructions.md\n'
  if [ "${#INPUTS[@]}" -gt 0 ]; then
    printf 'planned_inputs=%s\n' "${INPUTS[*]}"
  else
    printf 'planned_inputs=artifacts/lami_model_static/input/*.litertlm\n'
  fi
  exit 0
fi

mkdir -p "$OUTPUT_DIR/strings"
mkdir -p "$OUTPUT_DIR/input"

SUMMARY="$OUTPUT_DIR/summary.txt"
INVENTORY="$OUTPUT_DIR/model_inventory.tsv"
KEYWORD_PRESENCE="$OUTPUT_DIR/model_keyword_presence.tsv"
ALL_HINTS="$OUTPUT_DIR/all_model_backend_hints.txt"

: >"$ALL_HINTS"
printf 'model_file\tsize_bytes\tsha256\tbackend_hint_file\n' >"$INVENTORY"
write_keyword_presence_header "$KEYWORD_PRESENCE"

{
  printf 'generated_at_utc=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || printf 'unavailable')"
  printf 'output_dir=%s\n' "$OUTPUT_DIR"
  printf 'pattern=%s\n' "$MODEL_PATTERN"
} >"$SUMMARY"

FILES="$(collect_input_files | sort -u)"
if [ -z "$FILES" ]; then
  printf 'no_local_model_files_found=true\n' >>"$SUMMARY"
else
  while IFS= read -r file; do
    [ -z "$file" ] && continue
    [ -f "$file" ] || continue
    out="$OUTPUT_DIR/strings/$(safe_name "$file").backend_hints.txt"
    strings -a "$file" 2>/dev/null |
      grep -Ea "$MODEL_PATTERN" |
      sort -u >"$out" || true
    cat "$out" >>"$ALL_HINTS"
    append_keyword_presence "$file" "$out" "$KEYWORD_PRESENCE"
    printf '%s\t%s\t%s\t%s\n' "$file" "$(size_for "$file")" "$(sha_for "$file")" "$out" >>"$INVENTORY"
  done <<EOF
$FILES
EOF
fi

sort -u "$ALL_HINTS" -o "$ALL_HINTS"
write_device_instructions "$OUTPUT_DIR/device_pull_instructions.md"

{
  printf 'model_inventory=%s\n' "$INVENTORY"
  printf 'model_keyword_presence=%s\n' "$KEYWORD_PRESENCE"
  printf 'all_model_backend_hints=%s\n' "$ALL_HINTS"
  printf 'device_pull_instructions=%s\n' "$OUTPUT_DIR/device_pull_instructions.md"
} >>"$SUMMARY"

printf 'wrote %s\n' "$OUTPUT_DIR"
