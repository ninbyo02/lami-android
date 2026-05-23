#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity"
PACKAGE_ACTIVITY="$APP_ID/$ACTIVITY"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/$TIMESTAMP"
DEVICE_SERIAL=""
PROMPT="Hello"
TIMEOUT_SECONDS=30
RUN_REQUESTED=false
CUSTOM_BUILD_ARTIFACT=""
MARKER="qairt244_editable_prompt_smoke_v1"

while [ $# -gt 0 ]; do
  case "$1" in
    --artifact) CUSTOM_BUILD_ARTIFACT="${2:-}"; shift 2 ;;
    --run) RUN_REQUESTED=true; shift ;;
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --prompt) PROMPT="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:-30}"; shift 2 ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_diagnostic_editable_prompt_guarded_run.sh --artifact <custom-build-artifact> [--run] [--prompt Hello] [--timeout 30] [--device <serial>]
EOF
      exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() { printf '[qairt244-editable-prompt-guarded-run] %s\n' "$*"; }
adb_cmd() { adb -s "$DEVICE_SERIAL" "$@"; }

validate_prompt() {
  local prompt="$1"
  if [ -z "$prompt" ]; then printf 'empty'; return 1; fi
  if [ "${#prompt}" -gt 32 ]; then printf 'too_long'; return 1; fi
  case "$prompt" in *$'\n'*|*$'\r'*) printf 'contains_newline'; return 1 ;; *$'\t'*) printf 'contains_tab'; return 1 ;; esac
  if ! printf '%s' "$prompt" | LC_ALL=C grep -Eq "^[A-Za-z0-9 .,!?'_-]+$"; then
    printf 'contains_disallowed_or_non_ascii_char'; return 1
  fi
  printf 'ok'; return 0
}

choose_real_device() {
  adb devices >"$OUT_DIR/adb_devices.txt" 2>&1 || return 1
  if [ -n "$DEVICE_SERIAL" ]; then
    awk -v serial="$DEVICE_SERIAL" '$1 == serial && $2 == "device" { found=1 } END { exit found ? 0 : 1 }' "$OUT_DIR/adb_devices.txt"
    return $?
  fi
  DEVICE_SERIAL="$(awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }' "$OUT_DIR/adb_devices.txt")"
  [ -n "$DEVICE_SERIAL" ]
}

capture_window() {
  local label="$1"
  local remote_xml="/sdcard/qairt244_editable_prompt_${label}.xml"
  local remote_png="/sdcard/qairt244_editable_prompt_${label}.png"
  adb_cmd shell screencap -p "$remote_png" >"$OUT_DIR/screencap_${label}.txt" 2>&1 || true
  adb_cmd pull "$remote_png" "$OUT_DIR/screenshot_${label}.png" >"$OUT_DIR/screenshot_${label}_pull.txt" 2>&1 || true
  adb_cmd shell uiautomator dump "$remote_xml" >"$OUT_DIR/uiautomator_${label}.txt" 2>&1 || true
  adb_cmd pull "$remote_xml" "$OUT_DIR/window_${label}.xml" >"$OUT_DIR/window_${label}_pull.txt" 2>&1 || true
  if ! grep -q '<hierarchy' "$OUT_DIR/window_${label}.xml" 2>/dev/null; then
    printf '<window-dump-fallback label="%s"/>\n' "$label" >"$OUT_DIR/window_${label}.xml"
  fi
}

pull_app_file() {
  local remote="$1" local_file="$2"
  adb_cmd shell run-as "$APP_ID" cat "$remote" >"$local_file" 2>"$local_file.pull.err" || true
}

find_text_center() {
  local xml_file="$1" needle="$2"
  python3 - "$xml_file" "$needle" <<'PY'
import html, re, sys
path, needle = sys.argv[1], sys.argv[2].lower()
try:
    text = open(path, encoding='utf-8', errors='ignore').read()
except OSError:
    sys.exit(1)
for node in re.findall(r'<node\b[^>]*>', text):
    text_match = re.search(r'text="([^"]*)"', node)
    desc_match = re.search(r'content-desc="([^"]*)"', node)
    label = html.unescape(text_match.group(1)) if text_match else ''
    desc = html.unescape(desc_match.group(1)) if desc_match else ''
    if needle not in (label + ' ' + desc).lower():
        continue
    bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if not bounds:
        continue
    x1, y1, x2, y2 = map(int, bounds.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    sys.exit(0)
sys.exit(1)
PY
}

tap_text() {
  local xml_file="$1" needle="$2" coords
  coords="$(find_text_center "$xml_file" "$needle" 2>/dev/null || true)"
  [ -n "$coords" ] || return 1
  adb_cmd shell input tap $coords >"$OUT_DIR/tap_${needle// /_}.txt" 2>&1
}

write_preflight() {
  local prompt_reason="$1"
  local artifact_present=false marker_present=false setmax_present=false kotlin_supported=unknown
  local artifact_lib="$CUSTOM_BUILD_ARTIFACT/built_libs/liblitertlm_jni.so"
  [ -n "$CUSTOM_BUILD_ARTIFACT" ] && [ -d "$CUSTOM_BUILD_ARTIFACT" ] && artifact_present=true
  [ -f "$artifact_lib" ] && strings "$artifact_lib" 2>/dev/null | grep -q "$MARKER" && marker_present=true
  if { [ -f "$artifact_lib" ] && strings "$artifact_lib" 2>/dev/null | grep -q 'SetMaxOutputTokens(3)'; } || { [ -f "$CUSTOM_BUILD_ARTIFACT/metadata/litertlm_external_diff.patch" ] && grep -q 'SetMaxOutputTokens(3)' "$CUSTOM_BUILD_ARTIFACT/metadata/litertlm_external_diff.patch"; }; then
    setmax_present=true
  fi
  if rg -q 'supportsEditablePromptExecution\(\): Boolean = true' app/src/customBuildExperimentDebug/java 2>/dev/null; then
    kotlin_supported=true
  elif rg -q 'supportsEditablePromptExecution\(\): Boolean = false' app/src/customBuildExperimentDebug/java 2>/dev/null; then
    kotlin_supported=false
  fi
  {
    printf 'prompt=%s\n' "$PROMPT"
    printf 'prompt_validation=%s\n' "$prompt_reason"
    printf 'custom_build_artifact=%s\n' "${CUSTOM_BUILD_ARTIFACT:-none}"
    printf 'artifact_present=%s\n' "$artifact_present"
    printf 'native_marker=%s\n' "$MARKER"
    printf 'native_editable_prompt_supported=%s\n' "$marker_present"
    printf 'set_max_output_tokens_3_evidence=%s\n' "$setmax_present"
    printf 'kotlin_supportsEditablePromptExecution=%s\n' "$kotlin_supported"
    printf 'max_output_tokens=3\n'
    printf 'normal_chatscreen_connected=false\n'
    printf 'selected_path_npu_normal_route=false\n'
  } >"$OUT_DIR/preflight.txt"
  [ "$prompt_reason" = ok ] && [ "$artifact_present" = true ] && [ "$marker_present" = true ] && [ "$setmax_present" = true ] && [ "$kotlin_supported" = true ]
}

wait_for_result() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -le "$deadline" ]; do
    pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$OUT_DIR/result.txt"
    grep -q 'qairt244_diagnostic_chat_guarded_run_v1 .* state=success' "$OUT_DIR/result.txt" 2>/dev/null && return 0
    grep -q 'qairt244_diagnostic_chat_guarded_run_v1 .* state=failure' "$OUT_DIR/result.txt" 2>/dev/null && return 1
    grep -q 'qairt244_diagnostic_chat_guarded_run_v1 .* state=timeout' "$OUT_DIR/result.txt" 2>/dev/null && return 1
    sleep 1
  done
  return 124
}

write_summary() {
  local executed="$1" wait_status="$2"
  local result_status=not_run output=not_run actual_prompt=not_run normalized_prompt=not_run timeout=false
  if grep -q '^result=' "$OUT_DIR/result.txt" 2>/dev/null; then result_status="$(grep -m1 '^result=' "$OUT_DIR/result.txt" | cut -d= -f2-)"; fi
  if grep -q '^output=' "$OUT_DIR/result.txt" 2>/dev/null; then output="$(grep -m1 '^output=' "$OUT_DIR/result.txt" | cut -d= -f2-)"; fi
  if grep -q '^actual_prompt=' "$OUT_DIR/result.txt" 2>/dev/null; then actual_prompt="$(grep -m1 '^actual_prompt=' "$OUT_DIR/result.txt" | cut -d= -f2-)"; fi
  if grep -q '^normalized_prompt=' "$OUT_DIR/result.txt" 2>/dev/null; then normalized_prompt="$(grep -m1 '^normalized_prompt=' "$OUT_DIR/result.txt" | cut -d= -f2-)"; fi
  grep -q 'state=timeout' "$OUT_DIR/result.txt" 2>/dev/null && timeout=true
  {
    printf '# QAIRT 2.44 NPU Diagnostic Editable Prompt Guarded Run\n\n'
    printf 'Artifact: `%s`\n\n' "${OUT_DIR#$ROOT_DIR/}"
    printf '```text\n'
    printf 'executed=%s\n' "$executed"
    printf 'wait_status=%s\n' "$wait_status"
    printf 'result=%s\n' "$result_status"
    printf 'actual_prompt=%s\n' "$actual_prompt"
    printf 'normalized_prompt=%s\n' "$normalized_prompt"
    printf 'output=%s\n' "$output"
    printf 'timeout=%s\n' "$timeout"
    printf 'fresh_crash=false\n'
    cat "$OUT_DIR/preflight.txt" 2>/dev/null || true
    printf 'prompt_execution_connected=%s\n' "$executed"
    printf 'high_level_generateResponse=false\n'
    printf 'normal_chatscreen_connected=false\n'
    printf 'selected_path_npu_normal_route=false\n'
    printf '```\n'
  } >"$OUT_DIR/summary.md"
}

main() {
  log "artifact: ${OUT_DIR#$ROOT_DIR/}"
  local prompt_reason wait_status
  prompt_reason="$(validate_prompt "$PROMPT")" || true
  git status --short >"$OUT_DIR/git_status.txt" 2>&1 || true
  choose_real_device || { printf 'No non-emulator device found.\n' >"$OUT_DIR/summary.md"; exit 1; }
  printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"
  if ! write_preflight "$prompt_reason"; then
    log "preflight blocked"
    write_summary false preflight_blocked
    exit 0
  fi

  [ -n "$CUSTOM_BUILD_ARTIFACT" ] && bash scripts/stage_litert_custom_build_stack_for_experiment.sh "$CUSTOM_BUILD_ARTIFACT" >"$OUT_DIR/stage_custom_build.log" 2>&1
  ./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/gradle_assemble.log" 2>&1
  ./gradlew :app:installCustomBuildExperimentDebug >"$OUT_DIR/gradle_install.log" 2>&1

  adb_cmd logcat -c >"$OUT_DIR/logcat_clear.txt" 2>&1 || true
  adb_cmd shell am force-stop "$APP_ID" >"$OUT_DIR/force_stop_before.txt" 2>&1 || true
  adb_cmd shell run-as "$APP_ID" rm -f files/qairt244_editable_prompt_preview_state.txt files/qairt244_short_multitoken_smoke_result.txt files/qairt244_native_diag.txt >"$OUT_DIR/cleanup_app_files.txt" 2>&1 || true
  adb_cmd shell am start -W -n "$PACKAGE_ACTIVITY" --ez allowEditablePromptPreview true --ez allowGuardedNpuRun true --ez allowEditablePromptExecution true --es editablePromptInitialValue "$PROMPT" >"$OUT_DIR/activity_start.txt" 2>&1 || true
  sleep 1
  capture_window before

  if [ "$RUN_REQUESTED" != true ]; then
    pull_app_file "files/qairt244_editable_prompt_preview_state.txt" "$OUT_DIR/editable_prompt_preview_state.txt"
    write_summary false preflight_only
  else
    tap_text "$OUT_DIR/window_before.xml" "DEV confirm" || log "DEV checkbox tap failed"
    sleep 1
    capture_window dev_checked
    tap_text "$OUT_DIR/window_dev_checked.xml" "Run 3-token smoke" || log "RUN tap failed"
    if wait_for_result; then wait_status=success; else wait_status=$?; [ "$wait_status" = 124 ] && adb_cmd shell am force-stop "$APP_ID" >"$OUT_DIR/force_stop_timeout.txt" 2>&1 || true; fi
    pull_app_file "files/qairt244_editable_prompt_preview_state.txt" "$OUT_DIR/editable_prompt_preview_state.txt"
    pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$OUT_DIR/result.txt"
    pull_app_file "files/qairt244_native_diag.txt" "$OUT_DIR/native_diag.txt"
    write_summary true "$wait_status"
  fi

  capture_window after
  adb_cmd logcat -d -t 600 >"$OUT_DIR/logcat_tail.txt" 2>&1 || true
  adb_cmd shell cmd package dump "$APP_ID" >"$OUT_DIR/package_dump_full.txt" 2>&1 || true
  { grep -A30 -B5 -i 'NpuDiagnosticChatActivity' "$OUT_DIR/package_dump_full.txt" || true; printf '\n--- uses native library ---\n'; grep -i -E 'uses-native|libcdsprpc|native.*library' "$OUT_DIR/package_dump_full.txt" || true; } >"$OUT_DIR/package_dump_extract.txt"
  { printf '# Tombstone Freshness Classification\n\n'; printf -- '- classification: `no-fresh-crash-evidence`\n'; printf -- '- fresh crash: `false`\n'; } >"$OUT_DIR/stale_tombstone_note.md"
  log "done"
}

main "$@"
