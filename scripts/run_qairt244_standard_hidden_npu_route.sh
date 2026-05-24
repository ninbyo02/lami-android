#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
RECEIVER="io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver"
ACTION="io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_standard_hidden_npu_route/$TIMESTAMP"
DEVICE_SERIAL=""
PROMPT="Hello"
TIMEOUT_SECONDS=45
TEMPLATE_MODE=""

while [ $# -gt 0 ]; do
  case "$1" in
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --prompt) PROMPT="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --template) TEMPLATE_MODE="${2:-}"; shift 2 ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_standard_hidden_npu_route.sh [--device <serial>] [--prompt <prompt>] [--timeout <seconds>] [--template <mode>]

Runs one standardDebug hidden qairt244 SM8750 NPU attempt through the debug-only
android.permission.DUMP-protected receiver. It enables developer_access_enabled and
dev_enable_qairt244_sm8750_npu_route via app code, then dispatches the prompt
without relying on manual IME focus.

When --template is set, the value is passed as the receiver extra template_mode
for template comparison experiments.
EOF
      exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() { printf '[qairt244-standard-hidden-run] %s\n' "$*"; }
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

pull_app_file() {
  local app_path="$1"
  local dest="$2"
  adb_cmd exec-out run-as "$APP_ID" cat "$app_path" >"$dest" 2>"$dest.err" || true
  if [ ! -s "$dest" ]; then
    rm -f "$dest"
  fi
}

wait_for_state() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if adb_cmd shell run-as "$APP_ID" test -s "files/qairt244_standard_hidden_prompt_state.txt" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 124
}

summary_value() {
  local key="$1"
  local file
  local value
  for file in \
    "$OUT_DIR/display_diagnostics.txt" \
    "$OUT_DIR/result.txt" \
    "$OUT_DIR/receiver_state.txt" \
    "$OUT_DIR/native_diag.txt"; do
    [ -f "$file" ] || continue
    value="$(awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print "FOUND:" $0; exit }' "$file")"
    if [ "${value#FOUND:}" != "$value" ]; then
      printf '%s' "${value#FOUND:}"
      return 0
    fi
  done
  printf 'unavailable'
}

write_summary() {
  local status="$1"
  {
    printf '# qairt244 standard hidden NPU route\n\n'
    printf -- '- artifact: `%s`\n' "${OUT_DIR#$ROOT_DIR/}"
    printf -- '- device: `%s`\n' "$DEVICE_SERIAL"
    printf -- '- prompt: `%s`\n' "$PROMPT"
    printf -- '- requested_template_mode: `%s`\n' "${TEMPLATE_MODE:-unset}"
    printf -- '- status: `%s`\n' "$status"
    printf '\n## Template comparison diagnostics\n\n'
    printf -- '- template_mode: `%s`\n' "$(summary_value template_mode)"
    printf -- '- final_model_input_length: `%s`\n' "$(summary_value final_model_input_length)"
    printf -- '- prompt_input_code_points: `%s`\n' "$(summary_value prompt_input_code_points)"
    printf -- '- prompt_input_code_point_limit: `%s`\n' "$(summary_value prompt_input_code_point_limit)"
    printf -- '- prompt_input_limit_mode: `%s`\n' "$(summary_value prompt_input_limit_mode)"
    printf -- '- native_prompt_input_code_point_limit: `%s`\n' "$(summary_value native_prompt_input_code_point_limit)"
    printf -- '- native_prompt_input_limit_mode: `%s`\n' "$(summary_value native_prompt_input_limit_mode)"
    printf -- '- raw_native_output_length: `%s`\n' "$(summary_value raw_native_output_length)"
    printf -- '- displayed_assistant_text_length: `%s`\n' "$(summary_value displayed_assistant_text_length)"
    printf -- '- decode_elapsed_ms: `%s`\n' "$(summary_value decode_elapsed_ms)"
    printf -- '- output_token_count: `%s`\n' "$(summary_value output_token_count)"
    printf -- '- finish_reason: `%s`\n' "$(summary_value finish_reason)"
    printf -- '- stop_reason: `%s`\n' "$(summary_value stop_reason)"
    printf -- '- output_contains_replacement_chars: `%s`\n' "$(summary_value output_contains_replacement_chars)"
    printf -- '- replacement_char_count: `%s`\n' "$(summary_value replacement_char_count)"
    printf -- '- output_unicode_summary: `%s`\n' "$(summary_value output_unicode_summary)"
    printf -- '- quality_classification: `%s`\n' "$(summary_value quality_classification)"
    printf '\n## State\n\n```text\n'
    cat "$OUT_DIR/receiver_state.txt" 2>/dev/null || true
    printf '```\n\n## Result\n\n```text\n'
    cat "$OUT_DIR/result.txt" 2>/dev/null || true
    printf '```\n\n## Display diagnostics\n\n```text\n'
    cat "$OUT_DIR/display_diagnostics.txt" 2>/dev/null || true
    printf '```\n\n## UI cleanup\n\n```text\n'
    cat "$OUT_DIR/ui_cleanup_state.txt" 2>/dev/null || true
    printf '```\n'
  } >"$OUT_DIR/summary.md"
}

main() {
  log "artifact: ${OUT_DIR#$ROOT_DIR/}"
  git status --short >"$OUT_DIR/git_status.txt" 2>&1 || true
  choose_device || { log "no non-emulator device"; write_summary no_device; exit 1; }
  printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"

  adb_cmd shell run-as "$APP_ID" rm -f \
    files/qairt244_short_multitoken_smoke_result.txt \
    files/qairt244_native_diag.txt \
    files/qairt244_chat_screen_model_path_resolution.txt \
    files/qairt244_chat_screen_real_npu_once_guard.txt \
    files/qairt244_dev_npu_ui_cleanup_state.txt \
    files/qairt244_standard_hidden_display_diagnostics.txt \
    files/qairt244_standard_hidden_prompt_state.txt >"$OUT_DIR/cleanup_app_files.txt" 2>&1 || true

  adb_cmd shell am start -W -n "$APP_ID/.MainActivity" >"$OUT_DIR/am_start.txt" 2>&1 || true

  broadcast_args=(
    shell am broadcast --receiver-foreground --user 0
    -a "$ACTION"
    -n "$APP_ID/$RECEIVER"
    --es prompt "$PROMPT"
    --ez enable_developer_access true
    --ez enable_route true
    --ez run true
  )
  if [ -n "$TEMPLATE_MODE" ]; then
    broadcast_args+=(--es template "$TEMPLATE_MODE" --es template_mode "$TEMPLATE_MODE")
  fi
  adb_cmd "${broadcast_args[@]}" >"$OUT_DIR/broadcast.txt" 2>&1 || true

  wait_status=success
  if ! wait_for_state; then
    wait_status=timeout
  fi

  pull_app_file "files/qairt244_standard_hidden_prompt_state.txt" "$OUT_DIR/receiver_state.txt"
  pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$OUT_DIR/result.txt"
  pull_app_file "files/qairt244_native_diag.txt" "$OUT_DIR/native_diag.txt"
  pull_app_file "files/qairt244_chat_screen_model_path_resolution.txt" "$OUT_DIR/resolved_model_path.txt"
  pull_app_file "files/qairt244_dev_npu_ui_cleanup_state.txt" "$OUT_DIR/ui_cleanup_state.txt"
  pull_app_file "files/qairt244_standard_hidden_display_diagnostics.txt" "$OUT_DIR/display_diagnostics.txt"
  adb_cmd logcat -d -t 800 >"$OUT_DIR/logcat_tail.txt" 2>&1 || true

  if [ "$wait_status" = success ] && grep -q '^success=true$' "$OUT_DIR/receiver_state.txt" 2>/dev/null; then
    write_summary success
    log "success"
    log "summary: ${OUT_DIR#$ROOT_DIR/}/summary.md"
    exit 0
  fi

  if [ "$wait_status" = success ]; then
    write_summary failure
  else
    write_summary "$wait_status"
  fi
  log "failure: $wait_status"
  log "summary: ${OUT_DIR#$ROOT_DIR/}/summary.md"
  exit 1
}

main "$@"
