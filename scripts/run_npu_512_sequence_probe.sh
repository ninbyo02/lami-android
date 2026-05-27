#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
RECEIVER="io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver"
ACTION="io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_512_sequence_probe/$TIMESTAMP"
DEVICE_SERIAL=""
TIMEOUT_SECONDS=60
MAX_OUTPUT_TOKENS=16
EXECUTE=false
HIDDEN_TEMPLATE_MAX_LENGTH=128
TARGETS=(1 8 16 32 64 128 256 384 512 640)
TEMPLATES=(raw simple_ja_chat gemma_it_like)

usage() {
  cat <<'EOF'
Usage:
  scripts/run_npu_512_sequence_probe.sh [--dry-run] [--execute] [--device <serial>] [--timeout <seconds>] [--max-output-tokens <n>]

Prepares or runs a dev-only hidden NPU sequence/prefill probe matrix. Default
mode is preflight-only and does not execute NPU.
--dry-run is an explicit alias for the default preflight-only mode.

Matrix:
  templates: raw, simple_ja_chat, gemma_it_like
  approximate final-input token targets: 1,8,16,32,64,128,256,384,512,640

Safety:
  - hidden StandardHiddenQairt244PromptReceiver only
  - no standard ChatScreen route connection
  - no DB/TTS/Markdown/streaming
  - no selectedPath=NPU persistence
  - no fallback hiding
  - max_output_tokens defaults to 16 to isolate prefill/input length behavior
  - each probe case is force-stopped before dispatch to avoid sequential reuse

Prerequisite:
  - install a standardDebug build that already contains the QAIRT244 max512
    native guard and hidden receiver route; this script does not stage native
    libraries or rebuild QAIRT.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) EXECUTE=false; shift ;;
    --execute) EXECUTE=true; shift ;;
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --max-output-tokens) MAX_OUTPUT_TOKENS="${2:-}"; shift 2 ;;
    --out-dir) OUT_DIR="${2:-}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

if ! [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || [ "$TIMEOUT_SECONDS" -le 0 ] || [ "$TIMEOUT_SECONDS" -gt 60 ]; then
  printf 'ERROR: --timeout must be 1..60\n' >&2
  exit 2
fi
if ! [[ "$MAX_OUTPUT_TOKENS" =~ ^[0-9]+$ ]] || [ "$MAX_OUTPUT_TOKENS" -le 0 ] || [ "$MAX_OUTPUT_TOKENS" -gt 512 ]; then
  printf 'ERROR: --max-output-tokens must be 1..512\n' >&2
  exit 2
fi

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

adb_cmd() {
  if [ -n "$DEVICE_SERIAL" ]; then
    adb -s "$DEVICE_SERIAL" "$@"
  else
    adb "$@"
  fi
}

choose_device() {
  adb devices >"$OUT_DIR/adb_devices.txt" 2>&1 || return 1
  if [ -n "$DEVICE_SERIAL" ]; then
    awk -v serial="$DEVICE_SERIAL" '$1 == serial && $2 == "device" { found=1 } END { exit found ? 0 : 1 }' "$OUT_DIR/adb_devices.txt"
    return $?
  fi
  DEVICE_SERIAL="$(awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }' "$OUT_DIR/adb_devices.txt")"
  [ -n "$DEVICE_SERIAL" ]
}

prompt_for_target() {
  local target="$1"
  local i=1
  while [ "$i" -le "$target" ]; do
    printf 'x '
    i=$((i + 1))
  done
}

template_overhead_tokens() {
  case "$1" in
    raw) printf '0' ;;
    simple_ja_chat) printf '3' ;;
    gemma_it_like) printf '4' ;;
    *) printf '0' ;;
  esac
}

template_overhead_chars() {
  case "$1" in
    raw) printf '0' ;;
    simple_ja_chat) printf '26' ;;
    gemma_it_like) printf '42' ;;
    *) printf '0' ;;
  esac
}

expected_validation_for_chars() {
  local final_chars="$1"
  if [ "$final_chars" -le "$HIDDEN_TEMPLATE_MAX_LENGTH" ]; then
    printf 'within_existing_128_codepoint_gate'
  else
    printf 'expected_app_prompt_validation_reject'
  fi
}

native_pre_reject_for_chars() {
  local final_chars="$1"
  if [ "$final_chars" -le "$HIDDEN_TEMPLATE_MAX_LENGTH" ]; then
    printf 'false'
  else
    printf 'true'
  fi
}

pull_app_file() {
  local app_path="$1"
  local dest="$2"
  adb_cmd exec-out run-as "$APP_ID" cat "$app_path" >"$dest" 2>"$dest.err" || true
  if [ ! -s "$dest" ]; then
    rm -f "$dest"
  fi
}

kv_value() {
  local key="$1"
  local file="$2"
  [ -f "$file" ] || {
    printf 'missing'
    return 0
  }
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); value=$0; found=1 } END { if (found) print value; else print "missing" }' "$file"
}

write_plan() {
  {
    printf '# QAIRT244 NPU 512 sequence/prefill probe plan\n\n'
    printf -- '- artifact: `%s`\n' "${OUT_DIR#$ROOT_DIR/}"
    printf -- '- execute: `%s`\n' "$EXECUTE"
    printf -- '- timeout_seconds: `%s`\n' "$TIMEOUT_SECONDS"
    printf -- '- max_output_tokens: `%s`\n' "$MAX_OUTPUT_TOKENS"
    printf -- '- hidden_template_codepoint_gate: `%s`\n' "$HIDDEN_TEMPLATE_MAX_LENGTH"
    printf -- '- case_count: `%s`\n\n' "$((${#TARGETS[@]} * ${#TEMPLATES[@]}))"
    printf '| template | target_final_tokens_approx | prompt_tokens_approx | final_input_chars_approx | expected_existing_app_validation | native_pre_reject_expected_by_128_gate | command_mode |\n'
    printf '| --- | ---: | ---: | ---: | --- | --- | --- |\n'
    for template in "${TEMPLATES[@]}"; do
      local overhead prompt_tokens prompt_chars final_chars expected_validation native_pre_reject
      overhead="$(template_overhead_tokens "$template")"
      for target in "${TARGETS[@]}"; do
        prompt_tokens=$((target - overhead))
        [ "$prompt_tokens" -gt 0 ] || prompt_tokens=1
        prompt_chars=$((prompt_tokens * 2))
        final_chars=$((prompt_chars + $(template_overhead_chars "$template")))
        expected_validation="$(expected_validation_for_chars "$final_chars")"
        native_pre_reject="$(native_pre_reject_for_chars "$final_chars")"
        printf '| `%s` | %s | %s | %s | `%s` | `%s` | `%s` |\n' "$template" "$target" "$prompt_tokens" "$final_chars" "$expected_validation" "$native_pre_reject" "$(if [ "$EXECUTE" = true ]; then printf execute; else printf preflight_only; fi)"
      done
    done
  } >"$OUT_DIR/plan.md"
}

run_case() {
  local template="$1"
  local target="$2"
  local overhead prompt_tokens prompt prompt_chars final_chars expected_validation native_pre_reject slug run_id run_dir status timeout success
  overhead="$(template_overhead_tokens "$template")"
  prompt_tokens=$((target - overhead))
  [ "$prompt_tokens" -gt 0 ] || prompt_tokens=1
  prompt="$(prompt_for_target "$prompt_tokens")"
  prompt_chars="$(printf '%s' "$prompt" | wc -m)"
  final_chars=$((prompt_chars + $(template_overhead_chars "$template")))
  expected_validation="$(expected_validation_for_chars "$final_chars")"
  native_pre_reject="$(native_pre_reject_for_chars "$final_chars")"
  slug="${template}_${target}"
  run_id="seqprobe_${slug}_${TIMESTAMP}"
  run_dir="$OUT_DIR/$slug"
  mkdir -p "$run_dir"
  printf '%s' "$prompt" >"$run_dir/prompt.txt"
  {
    printf 'template=%s\n' "$template"
    printf 'target_final_tokens_approx=%s\n' "$target"
    printf 'prompt_tokens_approx=%s\n' "$prompt_tokens"
    printf 'prompt_chars=%s\n' "$prompt_chars"
    printf 'final_input_chars_approx=%s\n' "$final_chars"
    printf 'expected_existing_app_validation=%s\n' "$expected_validation"
    printf 'native_pre_reject_expected_by_128_gate=%s\n' "$native_pre_reject"
    printf 'run_id=%s\n' "$run_id"
  } >"$run_dir/request.txt"

  adb_cmd shell am force-stop "$APP_ID" >"$run_dir/force_stop_before.txt" 2>&1 || true
  adb_cmd shell am start -W -n "$APP_ID/.MainActivity" >"$run_dir/am_start.txt" 2>&1 || true
  adb_cmd shell run-as "$APP_ID" rm -f \
    files/qairt244_standard_hidden_prompt_state.txt \
    files/qairt244_short_multitoken_smoke_result.txt \
    files/qairt244_native_diag.txt \
    files/qairt244_standard_hidden_display_diagnostics.txt \
    "files/terminal_trace_${run_id}.txt" >"$run_dir/cleanup_app_files.txt" 2>&1 || true
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -a "$ACTION" \
    -n "$APP_ID/$RECEIVER" \
    --es prompt "$prompt" \
    --es run_id "$run_id" \
    --es template "$template" \
    --es template_mode "$template" \
    --ei max_output_tokens "$MAX_OUTPUT_TOKENS" \
    --ez allow_max_output_tokens_compare true \
    --ez enable_developer_access true \
    --ez enable_route true \
    --ez run true >"$run_dir/broadcast.txt" 2>&1 || true

  timeout=true
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if adb_cmd shell run-as "$APP_ID" test -s files/qairt244_standard_hidden_prompt_state.txt >/dev/null 2>&1; then
      timeout=false
      break
    fi
    sleep 1
  done
  pull_app_file files/qairt244_standard_hidden_prompt_state.txt "$run_dir/receiver_state.txt"
  pull_app_file files/qairt244_short_multitoken_smoke_result.txt "$run_dir/result.txt"
  pull_app_file files/qairt244_native_diag.txt "$run_dir/native_diag.txt"
  pull_app_file files/qairt244_standard_hidden_display_diagnostics.txt "$run_dir/display_diagnostics.txt"
  pull_app_file "files/terminal_trace_${run_id}.txt" "$run_dir/terminal_trace.txt"
  adb_cmd logcat -d -t 300 >"$run_dir/logcat_tail.txt" 2>&1 || true

  success="$(kv_value success "$run_dir/receiver_state.txt")"
  [ "$timeout" = false ] && [ "$success" = true ] && status=success || status=failure
  {
    cat "$run_dir/request.txt"
    printf 'status=%s\n' "$status"
    printf 'timeout=%s\n' "$timeout"
    printf 'receiver_success=%s\n' "$success"
    printf 'native_reached=%s\n' "$(grep -q 'qairt244_native_file_v1' "$run_dir/native_diag.txt" 2>/dev/null && printf true || printf false)"
    printf 'decode_reached=%s\n' "$(grep -q 'RunDecode' "$run_dir/native_diag.txt" "$run_dir/result.txt" 2>/dev/null && printf true || printf false)"
    printf 'editable_prompt_rejected=%s\n' "$(grep -Eq 'invalid_prompt|editable_prompt.*reject|prompt_validation reason=(fail|rejected|too_long|invalid)' "$run_dir/native_diag.txt" "$run_dir/result.txt" 2>/dev/null && printf true || printf false)"
    printf 'empty_output=%s\n' "$(grep -q '^reasonCode=empty_after_sanitize' "$run_dir/receiver_state.txt" "$run_dir/result.txt" 2>/dev/null && printf true || printf false)"
    printf 'fallback_used=%s\n' "$(kv_value fallback_used "$run_dir/receiver_state.txt")"
    printf 'fresh_crash=%s\n' "$(kv_value fresh_crash "$run_dir/receiver_state.txt")"
    printf 'npu_backend_evidence=%s\n' "$(kv_value npu_backend_evidence "$run_dir/receiver_state.txt")"
    printf 'replacement_char_count=%s\n' "$(kv_value replacement_char_count "$run_dir/display_diagnostics.txt")"
  } >"$run_dir/case_summary.txt"
  cat "$run_dir/case_summary.txt" >>"$OUT_DIR/case_summaries.txt"
  printf '\n' >>"$OUT_DIR/case_summaries.txt"
}

write_summary() {
  {
    printf '# QAIRT244 NPU 512 sequence/prefill probe\n\n'
    printf -- '- artifact: `%s`\n' "${OUT_DIR#$ROOT_DIR/}"
    printf -- '- execute: `%s`\n' "$EXECUTE"
    printf -- '- device: `%s`\n' "${DEVICE_SERIAL:-not_selected}"
    printf -- '- hidden_receiver_only: `true`\n'
    printf -- '- standard_chat_route_connected: `false`\n'
    printf -- '- db_tts_markdown_streaming_connected: `false`\n'
    printf -- '- selected_path_npu_saved_by_runner: `false`\n\n'
    printf '## 128 Gate Preflight\n\n'
    printf 'Cases with `final_input_chars_approx > %s` are expected to reject before native entry through the current hidden-route prompt validation gate. These rows prove app-side validation behavior, not the `.litertlm` graph sequence limit.\n\n' "$HIDDEN_TEMPLATE_MAX_LENGTH"
    printf 'For rows marked `true`: `128 gate によりこのcaseは native前reject見込み`.\n\n'
    printf '| template | target | final_input_chars_approx | native_pre_reject_expected_by_128_gate |\n'
    printf '| --- | ---: | ---: | --- |\n'
    for template in "${TEMPLATES[@]}"; do
      local overhead prompt_tokens prompt_chars final_chars native_pre_reject
      overhead="$(template_overhead_tokens "$template")"
      for target in "${TARGETS[@]}"; do
        prompt_tokens=$((target - overhead))
        [ "$prompt_tokens" -gt 0 ] || prompt_tokens=1
        prompt_chars=$((prompt_tokens * 2))
        final_chars=$((prompt_chars + $(template_overhead_chars "$template")))
        native_pre_reject="$(native_pre_reject_for_chars "$final_chars")"
        printf '| `%s` | %s | %s | `%s` |\n' "$template" "$target" "$final_chars" "$native_pre_reject"
      done
    done
    printf '\n'
    printf '## Reproduction\n\n'
    printf '```bash\n'
    printf 'scripts/run_npu_512_sequence_probe.sh --execute --timeout %s --max-output-tokens %s\n' "$TIMEOUT_SECONDS" "$MAX_OUTPUT_TOKENS"
    printf '```\n\n'
    printf '## Case Summary\n\n'
    if [ -f "$OUT_DIR/case_summaries.txt" ]; then
      printf '| template | target | status | timeout | native | decode | npu_evidence | fallback | fresh_crash |\n'
      printf '| --- | ---: | --- | --- | --- | --- | --- | --- | --- |\n'
      awk -F= '
        /^template=/ { t=$2 }
        /^target_final_tokens_approx=/ { target=$2 }
        /^status=/ { status=$2 }
        /^timeout=/ { timeout=$2 }
        /^native_reached=/ { native=$2 }
        /^decode_reached=/ { decode=$2 }
        /^npu_backend_evidence=/ { npu=$2 }
        /^fallback_used=/ { fallback=$2 }
        /^fresh_crash=/ { fresh=$2 }
        /^$/ {
          if (t != "") {
            printf "| `%s` | %s | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` |\n", t, target, status, timeout, native, decode, npu, fallback, fresh
          }
          t=target=status=timeout=native=decode=npu=fallback=fresh=""
        }
      ' "$OUT_DIR/case_summaries.txt"
    else
      printf 'No runtime cases executed. Re-run with `--execute` to collect behavior.\n'
    fi
  } >"$OUT_DIR/summary.md"
}

write_plan
if [ "$EXECUTE" != true ]; then
  write_summary
  printf 'summary=%s\n' "$OUT_DIR/summary.md"
  exit 0
fi

choose_device || {
  write_summary
  printf 'ERROR: no device connected\n' >&2
  exit 1
}
printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"
: >"$OUT_DIR/case_summaries.txt"
for template in "${TEMPLATES[@]}"; do
  for target in "${TARGETS[@]}"; do
    run_case "$template" "$target"
  done
done
write_summary
printf 'summary=%s\n' "$OUT_DIR/summary.md"
