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
LIMIT_CASES=0
CUSTOM_PROMPT=""
ONLY_TEMPLATE=""
ONLY_TARGET=""
TARGETS=(1 8 16 32 64 128 256 384 512 640)
TEMPLATES=(raw simple_ja_chat gemma_it_like)

usage() {
  cat <<'EOF'
Usage:
  scripts/run_npu_512_sequence_probe.sh [--dry-run] [--execute] [--device <serial>] [--timeout <seconds>] [--max-output-tokens <n>] [--limit-cases <n>] [--prompt <text>] [--only-template <raw|simple_ja_chat|gemma_it_like>] [--only-target <n>]

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
  - prompt text is sent as UTF-8 base64 through prompt_base64 so generated
    filler prompts with spaces do not break adb shell am broadcast parsing
  - each probe case is force-stopped before dispatch to avoid sequential reuse
  - --limit-cases can restrict execution to the first N matrix rows for
    guarded real-device rechecks after ANR-like behavior
  - --prompt replaces the generated "x " filler for selected cases
  - --only-template and --only-target narrow the matrix to one intended case
    before --limit-cases is applied

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
    --limit-cases) LIMIT_CASES="${2:-}"; shift 2 ;;
    --prompt) CUSTOM_PROMPT="${2:-}"; shift 2 ;;
    --only-template) ONLY_TEMPLATE="${2:-}"; shift 2 ;;
    --only-target) ONLY_TARGET="${2:-}"; shift 2 ;;
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
if ! [[ "$LIMIT_CASES" =~ ^[0-9]+$ ]]; then
  printf 'ERROR: --limit-cases must be a non-negative integer\n' >&2
  exit 2
fi
if [ -n "$ONLY_TEMPLATE" ]; then
  case "$ONLY_TEMPLATE" in
    raw|simple_ja_chat|gemma_it_like) ;;
    *) printf 'ERROR: --only-template must be raw, simple_ja_chat, or gemma_it_like\n' >&2; exit 2 ;;
  esac
fi
if [ -n "$ONLY_TARGET" ] && ! [[ "$ONLY_TARGET" =~ ^[0-9]+$ ]]; then
  printf 'ERROR: --only-target must be a non-negative integer\n' >&2
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

run_adb_capture() {
  local seconds="$1"
  local dest="$2"
  local rc
  shift 2
  local adb_args=(adb)
  if [ -n "$DEVICE_SERIAL" ]; then
    adb_args=(adb -s "$DEVICE_SERIAL")
  fi
  if command -v timeout >/dev/null 2>&1; then
    timeout --kill-after=5s "${seconds}s" "${adb_args[@]}" "$@" >"$dest" 2>"$dest.err"
    rc=$?
  else
    "${adb_args[@]}" "$@" >"$dest" 2>"$dest.err"
    rc=$?
  fi
  printf 'exit_code=%s\n' "$rc" >"$dest.exit_code"
  return 0
}

line_count() {
  local file="$1"
  if [ -f "$file" ]; then
    wc -l <"$file" | tr -d ' '
  else
    printf '0'
  fi
}

exit_code_value() {
  local file="$1"
  if [ -f "$file.exit_code" ]; then
    awk -F= '$1 == "exit_code" { print $2; found=1 } END { if (!found) print "missing" }' "$file.exit_code"
  else
    printf 'missing'
  fi
}

collect_diagnostics() {
  local label="$1"
  local dest_dir="$2"
  mkdir -p "$dest_dir"
  run_adb_capture 10 "$dest_dir/logcat_${label}.txt" logcat -d -v time
  run_adb_capture 10 "$dest_dir/dumpsys_window_${label}.txt" shell dumpsys window
  run_adb_capture 10 "$dest_dir/dumpsys_activity_activities_${label}.txt" shell dumpsys activity activities
  run_adb_capture 10 "$dest_dir/dumpsys_activity_processes_${label}.txt" shell dumpsys activity processes
  run_adb_capture 10 "$dest_dir/dumpsys_activity_broadcasts_${label}.txt" shell dumpsys activity broadcasts
  run_adb_capture 10 "$dest_dir/dumpsys_activity_anr_${label}.txt" shell dumpsys activity anr
  run_adb_capture 10 "$dest_dir/dumpsys_input_${label}.txt" shell dumpsys input
  run_adb_capture 10 "$dest_dir/ps_A_${label}.txt" shell ps -A
  run_adb_capture 5 "$dest_dir/pidof_${label}.txt" shell pidof "$APP_ID"
  run_adb_capture 10 "$dest_dir/anr_traces_${label}.txt" exec-out cat /data/anr/traces.txt
  run_adb_capture 15 "$dest_dir/dropbox_system_app_anr_${label}.txt" shell dumpsys dropbox --print system_app_anr
  run_adb_capture 15 "$dest_dir/dropbox_data_app_anr_${label}.txt" shell dumpsys dropbox --print data_app_anr
  run_adb_capture 15 "$dest_dir/dropbox_system_app_crash_${label}.txt" shell dumpsys dropbox --print system_app_crash
  run_adb_capture 15 "$dest_dir/dropbox_data_app_crash_${label}.txt" shell dumpsys dropbox --print data_app_crash
  run_adb_capture 15 "$dest_dir/dropbox_tombstone_${label}.txt" shell dumpsys dropbox --print tombstone
  find "$dest_dir" -maxdepth 1 -type f | sort >"$dest_dir/file_list.txt"
}

app_process_alive_from_pidof() {
  local file="$1"
  if [ ! -f "$file" ]; then
    printf 'unknown'
  elif [ -s "$file" ]; then
    printf 'true'
  else
    printf 'false'
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

prompt_for_case() {
  local prompt_tokens="$1"
  if [ -n "$CUSTOM_PROMPT" ]; then
    printf '%s' "$CUSTOM_PROMPT"
  else
    prompt_for_target "$prompt_tokens"
  fi
}

prompt_chars_for_text() {
  local prompt="$1"
  printf '%s' "$prompt" | wc -m | tr -d ' '
}

prompt_base64_for_text() {
  local prompt="$1"
  if base64 --help 2>/dev/null | grep -q -- '-w'; then
    printf '%s' "$prompt" | base64 -w 0
  else
    printf '%s' "$prompt" | base64 | tr -d '\n'
  fi
}

case_selected_by_filters() {
  local template="$1"
  local target="$2"
  if [ -n "$ONLY_TEMPLATE" ] && [ "$template" != "$ONLY_TEMPLATE" ]; then
    return 1
  fi
  if [ -n "$ONLY_TARGET" ] && [ "$target" != "$ONLY_TARGET" ]; then
    return 1
  fi
  return 0
}

selected_case_count() {
  local count=0 template target
  for template in "${TEMPLATES[@]}"; do
    for target in "${TARGETS[@]}"; do
      if case_selected_by_filters "$template" "$target"; then
        count=$((count + 1))
      fi
    done
  done
  printf '%s' "$count"
}

markdown_cell() {
  printf '%s' "$1" | tr '\n\r' '  ' | sed 's/|/\\|/g'
}

markdown_preview_cell() {
  local escaped
  escaped="$(markdown_cell "$1")"
  if [ "${#escaped}" -gt 80 ]; then
    printf '%s...' "${escaped:0:80}"
  else
    printf '%s' "$escaped"
  fi
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

first_kv_value() {
  local key="$1"
  local file="$2"
  [ -f "$file" ] || {
    printf 'missing'
    return 0
  }
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; found=1; exit } END { if (!found) print "missing" }' "$file"
}

write_plan() {
  {
    printf '# QAIRT244 NPU 512 sequence/prefill probe plan\n\n'
    printf -- '- artifact: `%s`\n' "${OUT_DIR#$ROOT_DIR/}"
    printf -- '- execute: `%s`\n' "$EXECUTE"
    printf -- '- timeout_seconds: `%s`\n' "$TIMEOUT_SECONDS"
    printf -- '- max_output_tokens: `%s`\n' "$MAX_OUTPUT_TOKENS"
    printf -- '- prompt_transport: `base64`\n'
    printf -- '- limit_cases: `%s`\n' "$LIMIT_CASES"
    printf -- '- custom_prompt: `%s`\n' "$(if [ -n "$CUSTOM_PROMPT" ]; then printf true; else printf false; fi)"
    printf -- '- only_template: `%s`\n' "${ONLY_TEMPLATE:-all}"
    printf -- '- only_target: `%s`\n' "${ONLY_TARGET:-all}"
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

write_selected_cases_summary() {
  local limit="$1"
  local rows_written=0
  local template target overhead prompt_tokens prompt prompt_chars prompt_base64 prompt_base64_length final_chars expected_validation native_pre_reject prompt_preview
    printf '## Selected Cases\n\n'
    printf 'The rows below are the cases that this invocation will consider after `--only-template` and `--only-target`, then after `--limit-cases` if it is non-zero.\n\n'
    if [ -n "$CUSTOM_PROMPT" ]; then
      printf 'Note: `--prompt` is set, so `target` remains a case label only. It does not generate filler length. Use `prompt_chars` and `final_input_chars_approx` as the source of truth for prompt length and 128 gate expectations.\n\n'
    fi
    printf '| selected_index | template | target | prompt_chars | final_input_chars_approx | native_pre_reject_expected_by_128_gate | prompt_transport | prompt_base64_length | prompt_source | prompt_preview |\n'
  printf '| ---: | --- | ---: | ---: | ---: | --- | --- | ---: | --- | --- |\n'
  for template in "${TEMPLATES[@]}"; do
    for target in "${TARGETS[@]}"; do
      case_selected_by_filters "$template" "$target" || continue
      if [ "$limit" -gt 0 ] && [ "$rows_written" -ge "$limit" ]; then
        continue
      fi
      overhead="$(template_overhead_tokens "$template")"
      prompt_tokens=$((target - overhead))
      [ "$prompt_tokens" -gt 0 ] || prompt_tokens=1
      prompt="$(prompt_for_case "$prompt_tokens")"
      prompt_chars="$(prompt_chars_for_text "$prompt")"
      prompt_base64="$(prompt_base64_for_text "$prompt")"
      prompt_base64_length="${#prompt_base64}"
      final_chars=$((prompt_chars + $(template_overhead_chars "$template")))
      expected_validation="$(expected_validation_for_chars "$final_chars")"
      native_pre_reject="$(native_pre_reject_for_chars "$final_chars")"
      prompt_preview="$(markdown_preview_cell "$prompt")"
      printf '| %s | `%s` | %s | %s | %s | `%s` | `base64` | %s | `%s` | `%s` |\n' \
        "$((rows_written + 1))" "$template" "$target" "$prompt_chars" "$final_chars" \
        "$native_pre_reject" "$prompt_base64_length" "$(if [ -n "$CUSTOM_PROMPT" ]; then printf custom; else printf generated_x_filler; fi)" "$prompt_preview"
      if [ "$native_pre_reject" = true ]; then
        printf '\n`128 gate によりこのcaseは native前reject見込み`: template=`%s`, target=`%s`, final_input_chars_approx=`%s`, expected_validation=`%s`.\n\n' \
          "$template" "$target" "$final_chars" "$expected_validation"
      fi
      rows_written=$((rows_written + 1))
    done
  done
  if [ "$rows_written" -eq 0 ]; then
    printf '| 0 | `none` | 0 | 0 | 0 | `unknown` | `base64` | 0 | `none` | `no matching selected cases` |\n'
  fi
  printf '\n'
}

run_case() {
  local template="$1"
  local target="$2"
  local overhead prompt_tokens prompt prompt_chars prompt_base64 prompt_base64_length final_chars expected_validation native_pre_reject slug run_id run_dir status receiver_state_timeout success
  local adb_broadcast_timeout=false
  local force_stopped_after_timeout=false
  local force_stop_after_timeout_exit_code=not_run
  overhead="$(template_overhead_tokens "$template")"
  prompt_tokens=$((target - overhead))
  [ "$prompt_tokens" -gt 0 ] || prompt_tokens=1
  prompt="$(prompt_for_case "$prompt_tokens")"
  prompt_chars="$(prompt_chars_for_text "$prompt")"
  prompt_base64="$(prompt_base64_for_text "$prompt")"
  prompt_base64_length="${#prompt_base64}"
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
    printf 'prompt_source=%s\n' "$(if [ -n "$CUSTOM_PROMPT" ]; then printf custom; else printf generated_x_filler; fi)"
    printf 'prompt_transport=base64\n'
    printf 'prompt_chars=%s\n' "$prompt_chars"
    printf 'prompt_base64_length=%s\n' "$prompt_base64_length"
    printf 'prompt_preview=%s\n' "$(markdown_preview_cell "$prompt")"
    printf 'max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
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
  run_adb_capture "$TIMEOUT_SECONDS" "$run_dir/broadcast.txt" shell am broadcast --receiver-foreground --user 0 \
    -a "$ACTION" \
    -n "$APP_ID/$RECEIVER" \
    --es prompt_base64 "$prompt_base64" \
    --es run_id "$run_id" \
    --es template "$template" \
    --es template_mode "$template" \
    --ei max_output_tokens "$MAX_OUTPUT_TOKENS" \
    --ez allow_max_output_tokens_compare true \
    --ez enable_developer_access true \
    --ez enable_route true \
    --ez run true
  case "$(exit_code_value "$run_dir/broadcast.txt")" in
    124|137) adb_broadcast_timeout=true ;;
  esac

  receiver_state_timeout=true
  if [ "$adb_broadcast_timeout" = true ]; then
    adb_cmd shell am force-stop "$APP_ID" >"$run_dir/force_stop_after_broadcast_timeout.txt" 2>&1
    force_stop_after_timeout_exit_code=$?
    force_stopped_after_timeout=true
  else
    local deadline=$((SECONDS + TIMEOUT_SECONDS))
    while [ "$SECONDS" -lt "$deadline" ]; do
      if adb_cmd shell run-as "$APP_ID" test -s files/qairt244_standard_hidden_prompt_state.txt >/dev/null 2>&1; then
        receiver_state_timeout=false
        break
      fi
      sleep 1
    done
    if [ "$receiver_state_timeout" = true ]; then
      adb_cmd shell am force-stop "$APP_ID" >"$run_dir/force_stop_after_receiver_state_timeout.txt" 2>&1
      force_stop_after_timeout_exit_code=$?
      force_stopped_after_timeout=true
    fi
  fi
  pull_app_file files/qairt244_standard_hidden_prompt_state.txt "$run_dir/receiver_state.txt"
  pull_app_file files/qairt244_short_multitoken_smoke_result.txt "$run_dir/result.txt"
  pull_app_file files/qairt244_native_diag.txt "$run_dir/native_diag.txt"
  pull_app_file files/qairt244_standard_hidden_display_diagnostics.txt "$run_dir/display_diagnostics.txt"
  pull_app_file "files/terminal_trace_${run_id}.txt" "$run_dir/terminal_trace.txt"
  collect_diagnostics "after_case" "$run_dir/diagnostics_after_case"

  success="$(kv_value success "$run_dir/receiver_state.txt")"
  [ "$receiver_state_timeout" = false ] && [ "$success" = true ] && status=success || status=failure
  {
    cat "$run_dir/request.txt"
    printf 'status=%s\n' "$status"
    printf 'reasonCode=%s\n' "$(kv_value reasonCode "$run_dir/receiver_state.txt")"
    printf 'timeout=%s\n' "$receiver_state_timeout"
    printf 'receiver_state_timeout=%s\n' "$receiver_state_timeout"
    printf 'adb_broadcast_timeout=%s\n' "$adb_broadcast_timeout"
    printf 'force_stopped_after_timeout=%s\n' "$force_stopped_after_timeout"
    printf 'force_stop_after_timeout_exit_code=%s\n' "$force_stop_after_timeout_exit_code"
    printf 'receiver_success=%s\n' "$success"
    printf 'native_reached=%s\n' "$(grep -q 'qairt244_native_file_v1' "$run_dir/native_diag.txt" 2>/dev/null && printf true || printf false)"
    printf 'decode_reached=%s\n' "$(grep -q 'RunDecode' "$run_dir/native_diag.txt" "$run_dir/result.txt" 2>/dev/null && printf true || printf false)"
    printf 'editable_prompt_rejected=%s\n' "$(grep -Eq 'invalid_prompt|editable_prompt.*reject|prompt_validation reason=(fail|rejected|too_long|invalid)' "$run_dir/native_diag.txt" "$run_dir/result.txt" 2>/dev/null && printf true || printf false)"
    printf 'empty_output=%s\n' "$(grep -q '^reasonCode=empty_after_sanitize' "$run_dir/receiver_state.txt" "$run_dir/result.txt" 2>/dev/null && printf true || printf false)"
    printf 'fallback_used=%s\n' "$(kv_value fallback_used "$run_dir/receiver_state.txt")"
    printf 'fresh_crash=%s\n' "$(kv_value fresh_crash "$run_dir/receiver_state.txt")"
    printf 'npu_backend_evidence=%s\n' "$(kv_value npu_backend_evidence "$run_dir/receiver_state.txt")"
    printf 'prompt_transport=%s\n' "$(kv_value prompt_transport "$run_dir/receiver_state.txt")"
    printf 'prompt_base64_present=%s\n' "$(kv_value prompt_base64_present "$run_dir/receiver_state.txt")"
    printf 'prompt_decode_success=%s\n' "$(kv_value prompt_decode_success "$run_dir/receiver_state.txt")"
    printf 'final_model_input_code_points=%s\n' "$(kv_value final_model_input_code_points "$run_dir/receiver_state.txt")"
    printf 'replacement_char_count=%s\n' "$(kv_value replacement_char_count "$run_dir/display_diagnostics.txt")"
    printf 'requested_max_output_tokens=%s\n' "$(kv_value requested_max_output_tokens "$run_dir/receiver_state.txt")"
    printf 'effective_max_output_tokens=%s\n' "$(kv_value max_output_tokens "$run_dir/receiver_state.txt")"
    printf 'native_max_output_tokens_limit=%s\n' "$(kv_value native_max_output_tokens_limit "$run_dir/receiver_state.txt")"
    printf 'native_result_first_max_output_tokens=%s\n' "$(first_kv_value max_output_tokens "$run_dir/result.txt")"
    printf 'raw_native_output_length=%s\n' "$(kv_value raw_native_output_length "$run_dir/receiver_state.txt")"
    printf 'sanitized_output_length=%s\n' "$(kv_value sanitized_output_length "$run_dir/receiver_state.txt")"
    printf 'removed_prompt_echo=%s\n' "$(kv_value removed_prompt_echo "$run_dir/receiver_state.txt")"
    printf 'output_contains_control_chars=%s\n' "$(kv_value output_contains_control_chars "$run_dir/receiver_state.txt")"
    printf 'quality_classification=%s\n' "$(kv_value quality_classification "$run_dir/receiver_state.txt")"
    printf 'logcat_line_count=%s\n' "$(line_count "$run_dir/diagnostics_after_case/logcat_after_case.txt")"
    printf 'app_process_alive_after_probe=%s\n' "$(app_process_alive_from_pidof "$run_dir/diagnostics_after_case/pidof_after_case.txt")"
    printf 'app_not_responding_observed=unknown\n'
    printf 'diagnostic_artifact_dir=%s\n' "${run_dir#$ROOT_DIR/}/diagnostics_after_case"
  } >"$run_dir/case_summary.txt"
  cat "$run_dir/case_summary.txt" >>"$OUT_DIR/case_summaries.txt"
  printf '\n' >>"$OUT_DIR/case_summaries.txt"
}

write_summary() {
  {
    local adb_broadcast_timeout_summary=false
    local force_stopped_after_timeout_summary=false
    local logcat_line_count_summary=not_collected
    local app_process_alive_after_probe_summary=unknown
    if [ -f "$OUT_DIR/case_summaries.txt" ]; then
      grep -q '^adb_broadcast_timeout=true$' "$OUT_DIR/case_summaries.txt" && adb_broadcast_timeout_summary=true
      grep -q '^force_stopped_after_timeout=true$' "$OUT_DIR/case_summaries.txt" && force_stopped_after_timeout_summary=true
      logcat_line_count_summary="$(awk -F= '$1 == "logcat_line_count" { sum += $2; found=1 } END { if (found) print sum; else print "not_collected" }' "$OUT_DIR/case_summaries.txt")"
      app_process_alive_after_probe_summary="$(awk -F= '$1 == "app_process_alive_after_probe" { value=$2; found=1 } END { if (found) print value; else print "unknown" }' "$OUT_DIR/case_summaries.txt")"
    fi
    printf '# QAIRT244 NPU 512 sequence/prefill probe\n\n'
    printf -- '- artifact: `%s`\n' "${OUT_DIR#$ROOT_DIR/}"
    printf -- '- execute: `%s`\n' "$EXECUTE"
    printf -- '- device: `%s`\n' "${DEVICE_SERIAL:-not_selected}"
    printf -- '- prompt_transport: `base64`\n'
    printf -- '- limit_cases: `%s`\n' "$LIMIT_CASES"
    printf -- '- custom_prompt: `%s`\n' "$(if [ -n "$CUSTOM_PROMPT" ]; then printf true; else printf false; fi)"
    if [ -n "$CUSTOM_PROMPT" ]; then
      printf -- '- target_length_semantics: `case_label_only_custom_prompt`\n'
      printf -- '- input_length_source_of_truth: `prompt_chars_and_final_input_chars_approx`\n'
    else
      printf -- '- target_length_semantics: `generated_x_filler_input_length_approximation`\n'
      printf -- '- input_length_source_of_truth: `target_generated_filler_estimate`\n'
    fi
    printf -- '- only_template: `%s`\n' "${ONLY_TEMPLATE:-all}"
    printf -- '- only_target: `%s`\n' "${ONLY_TARGET:-all}"
    printf -- '- selected_case_count_before_limit: `%s`\n' "$(selected_case_count)"
    printf -- '- hidden_receiver_only: `true`\n'
    printf -- '- standard_chat_route_connected: `false`\n'
    printf -- '- db_tts_markdown_streaming_connected: `false`\n'
    printf -- '- selected_path_npu_saved_by_runner: `false`\n\n'
    printf '## Execution Safety\n\n'
    printf -- '- adb_broadcast_timeout: `%s`\n' "$adb_broadcast_timeout_summary"
    printf -- '- force_stopped_after_timeout: `%s`\n' "$force_stopped_after_timeout_summary"
    printf -- '- logcat_line_count: `%s`\n' "$logcat_line_count_summary"
    printf -- '- app_process_alive_after_probe: `%s`\n' "$app_process_alive_after_probe_summary"
    printf -- '- app_not_responding_observed: `unknown`\n'
    printf -- '- logcat_clear_before_probe_exit_code: `%s`\n' "$(exit_code_value "$OUT_DIR/logcat_clear_before_probe.txt")"
    printf '\n'
    printf 'Saved diagnostic artifact paths:\n\n'
    if [ -d "$OUT_DIR/diagnostics_before_probe" ]; then
      printf -- '- `%s`\n' "${OUT_DIR#$ROOT_DIR/}/diagnostics_before_probe"
    fi
    if [ -f "$OUT_DIR/case_summaries.txt" ]; then
      awk -F= '$1 == "diagnostic_artifact_dir" { printf "- `%s`\n", $2 }' "$OUT_DIR/case_summaries.txt"
    fi
    if [ -d "$OUT_DIR/diagnostics_after_probe" ]; then
      printf -- '- `%s`\n' "${OUT_DIR#$ROOT_DIR/}/diagnostics_after_probe"
    fi
    if [ -d "$OUT_DIR/diagnostics_interrupt" ]; then
      printf -- '- `%s`\n' "${OUT_DIR#$ROOT_DIR/}/diagnostics_interrupt"
    fi
    if [ ! -d "$OUT_DIR/diagnostics_before_probe" ] && [ ! -f "$OUT_DIR/case_summaries.txt" ] && [ ! -d "$OUT_DIR/diagnostics_after_probe" ] && [ ! -d "$OUT_DIR/diagnostics_interrupt" ]; then
      printf -- '- `not_collected`\n'
    fi
    printf '\n'
    write_selected_cases_summary "$LIMIT_CASES"
    printf '## 128 Gate Preflight\n\n'
    if [ -n "$CUSTOM_PROMPT" ]; then
      printf 'Important: because `--prompt` is set, selected-case gate expectations come from the Selected Cases table above. The full matrix table below still shows the default generated `x ` filler assumptions for comparison only.\n\n'
    fi
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
    printf 'scripts/run_npu_512_sequence_probe.sh --execute --timeout %s --max-output-tokens %s' "$TIMEOUT_SECONDS" "$MAX_OUTPUT_TOKENS"
    if [ -n "$CUSTOM_PROMPT" ]; then
      printf ' --prompt %q' "$CUSTOM_PROMPT"
    fi
    if [ -n "$ONLY_TEMPLATE" ]; then
      printf ' --only-template %q' "$ONLY_TEMPLATE"
    fi
    if [ -n "$ONLY_TARGET" ]; then
      printf ' --only-target %q' "$ONLY_TARGET"
    fi
    if [ "$LIMIT_CASES" -gt 0 ]; then
      printf ' --limit-cases %s' "$LIMIT_CASES"
    fi
    printf '\n'
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
      printf '\n## Output / Max Token Summary\n\n'
      printf '| template | target | reason | requested | effective | native_limit | native_file_first_max | raw_len | sanitized_len | quality | control_chars |\n'
      printf '| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |\n'
      awk -F= '
        /^template=/ { t=$2 }
        /^target_final_tokens_approx=/ { target=$2 }
        /^reasonCode=/ { reason=$2 }
        /^requested_max_output_tokens=/ { requested=$2 }
        /^effective_max_output_tokens=/ { effective=$2 }
        /^native_max_output_tokens_limit=/ { native_limit=$2 }
        /^native_result_first_max_output_tokens=/ { native_first=$2 }
        /^raw_native_output_length=/ { raw_len=$2 }
        /^sanitized_output_length=/ { sanitized_len=$2 }
        /^quality_classification=/ { quality=$2 }
        /^output_contains_control_chars=/ { control=$2 }
        /^$/ {
          if (t != "") {
            printf "| `%s` | %s | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` | `%s` |\n", t, target, reason, requested, effective, native_limit, native_first, raw_len, sanitized_len, quality, control
          }
          t=target=reason=requested=effective=native_limit=native_first=raw_len=sanitized_len=quality=control=""
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

interrupt_cleanup() {
  printf 'interrupted=true\n' >"$OUT_DIR/interrupted.txt"
  if [ -n "${DEVICE_SERIAL:-}" ]; then
    adb_cmd shell am force-stop "$APP_ID" >"$OUT_DIR/force_stop_on_interrupt.txt" 2>&1 || true
    collect_diagnostics "interrupt" "$OUT_DIR/diagnostics_interrupt"
  fi
  write_summary
  printf 'summary=%s\n' "$OUT_DIR/summary.md"
  exit 130
}

trap interrupt_cleanup INT TERM

choose_device || {
  write_summary
  printf 'ERROR: no device connected\n' >&2
  exit 1
}
printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"
run_adb_capture 10 "$OUT_DIR/logcat_clear_before_probe.txt" logcat -c
collect_diagnostics "before_probe" "$OUT_DIR/diagnostics_before_probe"
: >"$OUT_DIR/case_summaries.txt"
cases_run=0
limit_reached=false
for template in "${TEMPLATES[@]}"; do
  for target in "${TARGETS[@]}"; do
    case_selected_by_filters "$template" "$target" || continue
    if [ "$LIMIT_CASES" -gt 0 ] && [ "$cases_run" -ge "$LIMIT_CASES" ]; then
      limit_reached=true
      break
    fi
    run_case "$template" "$target"
    cases_run=$((cases_run + 1))
  done
  [ "$limit_reached" = true ] && break
done
printf 'cases_run=%s\nlimit_cases=%s\nlimit_reached=%s\n' "$cases_run" "$LIMIT_CASES" "$limit_reached" >"$OUT_DIR/execution_limit.txt"
collect_diagnostics "after_probe" "$OUT_DIR/diagnostics_after_probe"
write_summary
printf 'summary=%s\n' "$OUT_DIR/summary.md"
