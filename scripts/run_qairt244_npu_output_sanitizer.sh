#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
RECEIVER="io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver"
ACTION="io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_output_sanitizer/$TIMESTAMP"
DEVICE_SERIAL=""
PROMPT="こんにちは"
TIMEOUT_SECONDS=90
TEMPLATE_MODE="gemma_it_like"

while [ $# -gt 0 ]; do
  case "$1" in
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --prompt) PROMPT="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --template) TEMPLATE_MODE="${2:-}"; shift 2 ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_output_sanitizer.sh [--device <serial>] [--prompt <prompt>] [--timeout <seconds>] [--template <mode>]

Runs the standardDebug hidden qairt244 NPU route once and records the raw versus
sanitized DEV-only NPU output. Defaults to prompt=こんにちは and
template=gemma_it_like.
EOF
      exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() { printf '[qairt244-npu-output-sanitizer] %s\n' "$*"; }
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

write_unescaped_value() {
  local key="$1"
  local dest="$2"
  summary_value "$key" | perl -pe 's/\\n/\n/g; s/\\\\/\\/g' >"$dest"
}

capture_screenshot() {
  local remote_png="/sdcard/qairt244_npu_output_sanitizer.png"
  adb_cmd shell screencap -p "$remote_png" >"$OUT_DIR/screenshot_capture.txt" 2>&1 || true
  adb_cmd pull "$remote_png" "$OUT_DIR/screenshot.png" >"$OUT_DIR/screenshot_pull.txt" 2>&1 || true
  adb_cmd shell rm -f "$remote_png" >/dev/null 2>&1 || true
}

write_runtime_marker_scan() {
  {
    for file in "$OUT_DIR/logcat_tail.txt" "$OUT_DIR/native_diag.txt" "$OUT_DIR/result.txt" "$OUT_DIR/summary.md"; do
      [ -f "$file" ] || continue
      rg -n "QNN|HTP|FastRPC|RunDecode|EngineFactory|native_prompt|sanitizer|selected_path_npu|fallback_used|timeout|fresh_crash" "$file" | sed "s#^#$(basename "$file"):#" || true
    done
  } >"$OUT_DIR/runtime_marker_scan.txt"
}

write_grep_safety() {
  rg -n "selectedPath.*npu|selected_path_npu|tts=true|markdown=true|streaming=true|Backend\\.NPU|gemma-4-E2B-it_qualcomm_sm8750|qcs8275|generic" \
    app/src/debug/java app/src/main/java app/src/customBuildExperimentDebug scripts docs >"$OUT_DIR/grep_safety.txt" 2>&1 || true
}

write_summary() {
  local status="$1"
  {
    printf '# qairt244 DEV-only NPU output sanitizer\n\n'
    printf -- '- artifact: `%s`\n' "${OUT_DIR#$ROOT_DIR/}"
    printf -- '- device: `%s`\n' "$DEVICE_SERIAL"
    printf -- '- prompt: `%s`\n' "$PROMPT"
    printf -- '- template_mode: `%s`\n' "$(summary_value template_mode)"
    printf -- '- requested_template_mode: `%s`\n' "$TEMPLATE_MODE"
    printf -- '- status: `%s`\n' "$status"
    printf -- '- result: `%s`\n' "$(summary_value result)"
    printf -- '- reasonCode: `%s`\n' "$(summary_value reasonCode)"
    printf -- '- max_output_tokens: `%s`\n' "$(summary_value max_output_tokens)"
    printf -- '- npu_backend: `%s`\n' "$(summary_value npu_backend)"
    printf -- '- npu_backend_evidence: `%s`\n' "$(summary_value npu_backend_evidence)"
    printf -- '- fallback_used: `%s`\n' "$(summary_value fallback_used)"
    printf -- '- timeout: `%s`\n' "$(summary_value timeout)"
    printf -- '- fresh_crash: `%s`\n' "$(summary_value fresh_crash)"
    printf -- '- sanitizer_applied: `%s`\n' "$(summary_value sanitizer_applied)"
    printf -- '- removed_template_token_count: `%s`\n' "$(summary_value removed_template_token_count)"
    printf -- '- removed_prompt_echo: `%s`\n' "$(summary_value removed_prompt_echo)"
    printf -- '- raw_output_length: `%s`\n' "$(summary_value raw_output_length)"
    printf -- '- sanitized_output_length: `%s`\n' "$(summary_value sanitized_output_length)"
    printf -- '- ui_cleanup_wait_status: `%s`\n' "$(summary_value ui_cleanup_wait_status)"
    printf '\n## Raw Output\n\n```text\n'
    cat "$OUT_DIR/raw_output.txt" 2>/dev/null || true
    printf '\n```\n\n## Sanitized Output\n\n```text\n'
    cat "$OUT_DIR/sanitized_output.txt" 2>/dev/null || true
    printf '\n```\n\n## Result\n\n```text\n'
    cat "$OUT_DIR/result.txt" 2>/dev/null || true
    printf '```\n\n## Display Diagnostics\n\n```text\n'
    cat "$OUT_DIR/display_diagnostics.txt" 2>/dev/null || true
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
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -a "$ACTION" \
    -n "$APP_ID/$RECEIVER" \
    --es prompt "$PROMPT" \
    --es template "$TEMPLATE_MODE" \
    --es template_mode "$TEMPLATE_MODE" \
    --ez enable_developer_access true \
    --ez enable_route true \
    --ez run true >"$OUT_DIR/broadcast.txt" 2>&1 || true

  wait_status=success
  if ! wait_for_state; then
    wait_status=timeout
  fi

  pull_app_file "files/qairt244_standard_hidden_prompt_state.txt" "$OUT_DIR/receiver_state.txt"
  pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$OUT_DIR/result.txt"
  pull_app_file "files/qairt244_native_diag.txt" "$OUT_DIR/native_diag.txt"
  pull_app_file "files/qairt244_standard_hidden_display_diagnostics.txt" "$OUT_DIR/display_diagnostics.txt"
  pull_app_file "files/qairt244_dev_npu_ui_cleanup_state.txt" "$OUT_DIR/ui_cleanup_state.txt"
  adb_cmd logcat -d -t 800 >"$OUT_DIR/logcat_tail.txt" 2>&1 || true
  capture_screenshot

  write_unescaped_value raw_output "$OUT_DIR/raw_output.txt"
  write_unescaped_value sanitized_output "$OUT_DIR/sanitized_output.txt"
  write_summary "$wait_status"
  write_runtime_marker_scan
  write_grep_safety

  if [ "$wait_status" = success ] && grep -q '^success=true$' "$OUT_DIR/receiver_state.txt" 2>/dev/null; then
    if rg -q '<end_of_turn>|<start_of_turn>' "$OUT_DIR/sanitized_output.txt"; then
      log "failure: sanitizer tokens remain"
      exit 1
    fi
    log "success"
    log "summary: ${OUT_DIR#$ROOT_DIR/}/summary.md"
    exit 0
  fi

  log "failure: $wait_status"
  log "summary: ${OUT_DIR#$ROOT_DIR/}/summary.md"
  exit 1
}

main "$@"
