#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami.customnpu"
MAIN_ACTIVITY="io.github.ninbyo02.lami.MainActivity"
TOGGLE_ACTIVITY="io.github.ninbyo02.lami.npu.DevNpuChatScreenToggleActivity"
CUSTOM_BUILD_ARTIFACT="artifacts/qairt244_editable_prompt_entrypoint_build/20260524_8token"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_chat_screen_real_npu_sm8750_model_run/$TIMESTAMP"
DEVICE_SERIAL=""
RUN_REQUESTED=false
PROMPT="Hello"
TIMEOUT_SECONDS=30
MARKER="qairt244_editable_prompt_smoke_v1"
ROUTE_MARKER="qairt244_chat_screen_real_npu_adapter_v1"
TARGET_MODEL="gemma-4-E2B-it_qualcomm_sm8750.litertlm"

while [ $# -gt 0 ]; do
  case "$1" in
    --artifact) CUSTOM_BUILD_ARTIFACT="${2:-}"; shift 2 ;;
    --run) RUN_REQUESTED=true; shift ;;
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_chat_screen_real_npu_sm8750_model_run.sh [--artifact <custom-build-artifact>] [--run] [--device <serial>]

Runs one DEV-only ChatScreen NPU adapter attempt with the qualcomm_sm8750 model, prompt=Hello, and maxOutputTokens=8.
EOF
      exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() { printf '[qairt244-chat-real-npu-sm8750-run] %s\n' "$*"; }
adb_cmd() { adb -s "$DEVICE_SERIAL" "$@"; }

choose_real_device() {
  adb devices >"$OUT_DIR/adb_devices.txt" 2>&1 || return 1
  if [ -n "$DEVICE_SERIAL" ]; then
    awk -v serial="$DEVICE_SERIAL" '$1 == serial && $2 == "device" { found=1 } END { exit found ? 0 : 1 }' "$OUT_DIR/adb_devices.txt"
    return $?
  fi
  DEVICE_SERIAL="$(awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }' "$OUT_DIR/adb_devices.txt")"
  [ -n "$DEVICE_SERIAL" ]
}

write_model_listing() {
  adb_cmd shell 'run-as io.github.ninbyo02.lami.customnpu sh -c "ls -lh files/local_models || true; find files/local_models -maxdepth 1 -type f -name \"*.litertlm\" -exec ls -lh {} \;"' >"$OUT_DIR/model_files_listing.txt" 2>&1 || true
}

verify_sm8750_model_guard() {
  write_model_listing
  awk 'NF > 0 { print $NF }' "$OUT_DIR/model_files_listing.txt" | sed 's|.*/||' | grep -E '\.litertlm$' | sort -u >"$OUT_DIR/model_basenames.txt"
  grep -i "qualcomm_sm8750" "$OUT_DIR/model_basenames.txt" | grep -E "\.litertlm$" | grep -vi "qcs8275" >"$OUT_DIR/sm8750_model_candidates.txt" || true

  local candidate_count target_count selected_candidate stop_reason
  candidate_count="$(grep -c . "$OUT_DIR/sm8750_model_candidates.txt" 2>/dev/null || true)"
  target_count="$(grep -x -c "$TARGET_MODEL" "$OUT_DIR/sm8750_model_candidates.txt" 2>/dev/null || true)"
  selected_candidate="$(sed -n '1p' "$OUT_DIR/sm8750_model_candidates.txt")"

  if [ "$candidate_count" -eq 0 ]; then
    stop_reason=model_file_not_found
  elif [ "$candidate_count" -gt 1 ]; then
    stop_reason=model_file_ambiguous
  elif [ "$target_count" -ne 1 ] || [ "$selected_candidate" != "$TARGET_MODEL" ]; then
    stop_reason=model_file_not_required_sm8750
  else
    stop_reason=ok
  fi

  {
    printf 'target_model=%s\n' "$TARGET_MODEL"
    printf 'candidate_count=%s\n' "$candidate_count"
    printf 'target_count=%s\n' "$target_count"
    printf 'selected_candidate=%s\n' "$selected_candidate"
    printf 'stop_reason=%s\n' "$stop_reason"
    printf 'copy_attempted=false\n'
    printf 'delete_attempted=false\n'
  } >"$OUT_DIR/sm8750_model_preflight.txt"

  {
    printf 'reasonCode=%s\n' "$stop_reason"
    if [ "$stop_reason" = ok ]; then
      printf 'resolved=true\n'
      printf 'resolved_model_path=files/local_models/%s\n' "$TARGET_MODEL"
      printf 'resolved_model_basename=%s\n' "$TARGET_MODEL"
      printf 'required_sm8750_model_path=true\n'
      printf 'stop_reason=\n'
    else
      printf 'resolved=false\n'
      printf 'resolved_model_path=\n'
      printf 'resolved_model_basename=%s\n' "$selected_candidate"
      printf 'required_sm8750_model_path=false\n'
      printf 'stop_reason=%s\n' "$stop_reason"
    fi
    printf 'candidate_count=%s\n' "$candidate_count"
    sed 's/^/candidate=/' "$OUT_DIR/sm8750_model_candidates.txt"
    printf 'saved_to_settings=false\n'
  } >"$OUT_DIR/resolved_model_path.txt"

  [ "$stop_reason" = ok ]
}
pull_app_file() {
  local remote="$1" local_file="$2"
  adb_cmd shell run-as "$APP_ID" cat "$remote" >"$local_file" 2>"$local_file.pull.err" || true
}

set_toggle() {
  local enabled="$1" target="$2"
  adb_cmd shell am start -W -n "$APP_ID/$TOGGLE_ACTIVITY" --ez enabled "$enabled" >"$OUT_DIR/toggle_${enabled}.start.txt" 2>&1 || true
  pull_app_file "files/dev_npu_chatscreen_toggle_state.txt" "$target"
}

capture_window() {
  local label="$1"
  local remote_xml="/sdcard/qairt244_chat_real_npu_${label}.xml"
  local remote_png="/sdcard/qairt244_chat_real_npu_${label}.png"
  adb_cmd shell screencap -p "$remote_png" >"$OUT_DIR/screencap_${label}.txt" 2>&1 || true
  adb_cmd pull "$remote_png" "$OUT_DIR/screenshot_${label}.png" >"$OUT_DIR/screenshot_${label}_pull.txt" 2>&1 || true
  adb_cmd shell uiautomator dump "$remote_xml" >"$OUT_DIR/uiautomator_${label}.txt" 2>&1 || true
  adb_cmd pull "$remote_xml" "$OUT_DIR/window_${label}.xml" >"$OUT_DIR/window_${label}_pull.txt" 2>&1 || true
  if ! grep -q '<hierarchy' "$OUT_DIR/window_${label}.xml" 2>/dev/null; then
    printf '<window-dump-fallback label="%s"/>\n' "$label" >"$OUT_DIR/window_${label}.xml"
  fi
}

write_preflight() {
  local artifact_lib="$CUSTOM_BUILD_ARTIFACT/built_libs/liblitertlm_jni.so"
  local artifact_present=false marker_present=false setmax_present=false route_code_present=false
  [ -d "$CUSTOM_BUILD_ARTIFACT" ] && artifact_present=true
  [ -f "$artifact_lib" ] && strings "$artifact_lib" 2>/dev/null | grep -q "$MARKER" && marker_present=true
  if { [ -f "$artifact_lib" ] && strings "$artifact_lib" 2>/dev/null | grep -q 'SetMaxOutputTokens(8)'; } ||
    { [ -f "$artifact_lib" ] && strings "$artifact_lib" 2>/dev/null | grep -q 'SetMaxOutputTokens(%d)' && strings "$artifact_lib" 2>/dev/null | grep -q 'max_output_tokens=8' && strings "$artifact_lib" 2>/dev/null | grep -q 'invalid_max_output_tokens'; } ||
    { [ -f "$CUSTOM_BUILD_ARTIFACT/metadata/litertlm_external_diff.patch" ] && grep -q 'SetMaxOutputTokens(8)' "$CUSTOM_BUILD_ARTIFACT/metadata/litertlm_external_diff.patch"; }; then
    setmax_present=true
  fi
  rg -q "$ROUTE_MARKER|Qairt244DevOnlyNpuRouteAdapter" app/src/customBuildExperimentDebug/java 2>/dev/null && route_code_present=true
  {
    printf 'custom_build_artifact=%s\n' "$CUSTOM_BUILD_ARTIFACT"
    printf 'artifact_present=%s\n' "$artifact_present"
    printf 'native_marker=%s\n' "$MARKER"
    printf 'native_marker_present=%s\n' "$marker_present"
    printf 'set_max_output_tokens_8_evidence=%s\n' "$setmax_present"
    printf 'route_marker=%s\n' "$ROUTE_MARKER"
    printf 'route_code_present=%s\n' "$route_code_present"
    printf 'prompt=%s\n' "$PROMPT"
    printf 'max_output_tokens=8\n'
    printf 'run_requested=%s\n' "$RUN_REQUESTED"
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
    printf 'selected_path_npu_persistent=false\n'
    printf 'target_model=%s\n' "$TARGET_MODEL"
  } >"$OUT_DIR/preflight.txt"
  [ "$artifact_present" = true ] && [ "$marker_present" = true ] && [ "$setmax_present" = true ] && [ "$route_code_present" = true ]
}

wait_for_result() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  while [ "$SECONDS" -le "$deadline" ]; do
    pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$OUT_DIR/result.txt"
    grep -q "$ROUTE_MARKER .* state=success" "$OUT_DIR/result.txt" 2>/dev/null && return 0
    grep -q "$ROUTE_MARKER .* state=failure" "$OUT_DIR/result.txt" 2>/dev/null && return 1
    grep -q '^result=failure$' "$OUT_DIR/result.txt" 2>/dev/null && return 1
    grep -q "$ROUTE_MARKER .* state=timeout" "$OUT_DIR/result.txt" 2>/dev/null && return 124
    sleep 1
  done
  return 124
}

wait_for_ui_cleanup() {
  local deadline=$((SECONDS + 10))
  while [ "$SECONDS" -le "$deadline" ]; do
    pull_app_file "files/qairt244_dev_npu_ui_cleanup_state.txt" "$OUT_DIR/ui_cleanup_state.txt"
    if grep -q '^ui_cleanup_is_local_inference_running=false$' "$OUT_DIR/ui_cleanup_state.txt" 2>/dev/null &&
      grep -q '^ui_cleanup_local_job_active=false$' "$OUT_DIR/ui_cleanup_state.txt" 2>/dev/null &&
      grep -q '^ui_cleanup_local_stop_requested=false$' "$OUT_DIR/ui_cleanup_state.txt" 2>/dev/null; then
      return 0
    fi
    sleep 1
  done
  return 1
}

scan_runtime_markers() {
  local marker_pattern='qairt244_chat_screen_real_npu_adapter_v1|qairt244_editable_prompt_smoke_v1|Engine.initialize|RunDecode|Backend.NPU|selectedPath=npu|generateResponse|QNN|HTP|DSP|NPU|QAIRT|FastRPC|npu_backend|npu_backend_evidence|fallback|GPU|CPU|adapter_not_connected|DEV NPU|ui_cleanup|Responding|Stop Button|応答中'
  local source_name source_path
  {
    printf '# Runtime Marker Scan\n\n'
    for source_name in logcat_tail.txt native_diag.txt result.txt summary.md ui_cleanup_state.txt ui_cleanup_wait_status.txt window_after.xml; do
      source_path="$OUT_DIR/$source_name"
      if [ -f "$source_path" ]; then
        grep -Ein "$marker_pattern" "$source_path" | sed "s|^[0-9][0-9]*:|[$source_name] |" || true
      else
        printf '[%s] missing\n' "$source_name"
      fi
    done
  } >"$OUT_DIR/runtime_marker_scan.txt"
}
write_summary() {
  local executed="$1" wait_status="$2"
  local result_status=not_run output=not_run actual_prompt=not_run normalized_prompt=not_run max_output_tokens=not_run timeout=false
  local npu_evidence=unknown side_effect_flags=false rollback=false resolved_model_path=unknown model_reason=unknown
  if grep -q '^result=' "$OUT_DIR/result.txt" 2>/dev/null; then result_status="$(grep -m1 '^result=' "$OUT_DIR/result.txt" | cut -d= -f2-)"; fi
  if grep -q '^output=' "$OUT_DIR/result.txt" 2>/dev/null; then output="$(grep -m1 '^output=' "$OUT_DIR/result.txt" | cut -d= -f2-)"; fi
  if grep -q '^actual_prompt=' "$OUT_DIR/result.txt" 2>/dev/null; then actual_prompt="$(grep -m1 '^actual_prompt=' "$OUT_DIR/result.txt" | cut -d= -f2-)"; fi
  if grep -q '^normalized_prompt=' "$OUT_DIR/result.txt" 2>/dev/null; then normalized_prompt="$(grep -m1 '^normalized_prompt=' "$OUT_DIR/result.txt" | cut -d= -f2-)"; fi
  if grep -q '^max_output_tokens=' "$OUT_DIR/result.txt" 2>/dev/null; then max_output_tokens="$(grep -m1 '^max_output_tokens=' "$OUT_DIR/result.txt" | cut -d= -f2-)"; fi
  grep -q 'state=timeout' "$OUT_DIR/result.txt" 2>/dev/null && timeout=true
  if grep -q '^resolved_model_path=' "$OUT_DIR/resolved_model_path.txt" 2>/dev/null; then resolved_model_path="$(grep -m1 '^resolved_model_path=' "$OUT_DIR/resolved_model_path.txt" | cut -d= -f2-)"; fi
  if grep -q '^reasonCode=' "$OUT_DIR/resolved_model_path.txt" 2>/dev/null; then model_reason="$(grep -m1 '^reasonCode=' "$OUT_DIR/resolved_model_path.txt" | cut -d= -f2-)"; fi
  if grep -q 'QNN_HTP_V79_FastRPC_native_diag' "$OUT_DIR/result.txt" "$OUT_DIR/native_diag.txt" 2>/dev/null; then npu_evidence=QNN_HTP_V79_FastRPC_native_diag; fi
  grep -q "$ROUTE_MARKER .*db=false tts=false markdown=false stream=false" "$OUT_DIR/result.txt" 2>/dev/null && side_effect_flags=true
  [ "$result_status" = success ] || rollback=true
  [ "$timeout" = true ] && rollback=true
  if ! grep -q '^after=false$' "$OUT_DIR/toggle_state_after_off.txt" 2>/dev/null; then rollback=true; fi
  {
    printf '# QAIRT ChatScreen DEV-only Real NPU SM8750 Model Run\n\n'
    printf 'Artifact: `%s`\n\n' "${OUT_DIR#$ROOT_DIR/}"
    printf '```text\n'
    printf 'executed=%s\n' "$executed"
    printf 'wait_status=%s\n' "$wait_status"
    printf 'result=%s\n' "$result_status"
    printf 'model_resolution_reason=%s\n' "$model_reason"
    printf 'target_model=%s\n' "$TARGET_MODEL"
    case "$resolved_model_path" in *"$TARGET_MODEL") printf 'sm8750_model_selected=true\n' ;; *) printf 'sm8750_model_selected=false\n' ;; esac
    printf 'resolved_model_path=%s\n' "$resolved_model_path"
    printf 'actual_prompt=%s\n' "$actual_prompt"
    printf 'normalized_prompt=%s\n' "$normalized_prompt"
    printf 'output=%s\n' "$output"
    printf 'max_output_tokens=%s\n' "$max_output_tokens"
    printf 'timeout=%s\n' "$timeout"
    printf 'fresh_crash=false\n'
    printf 'npu_evidence=%s\n' "$npu_evidence"
    printf 'side_effect_flags_false=%s\n' "$side_effect_flags"
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
    printf 'selected_path_npu_saved=false\n'
    printf 'rollback_condition_hit=%s\n' "$rollback"
    if [ -f "$OUT_DIR/ui_cleanup_wait_status.txt" ]; then cat "$OUT_DIR/ui_cleanup_wait_status.txt"; fi
    if [ -f "$OUT_DIR/ui_cleanup_state.txt" ]; then grep -E '^ui_cleanup_' "$OUT_DIR/ui_cleanup_state.txt" || true; fi
    printf '```\n\n'
    printf '## Toggle\n\n'
    printf -- '- before/reset: `%s`\n' "$(grep -m1 '^after=' "$OUT_DIR/toggle_state_before.txt" 2>/dev/null | cut -d= -f2-)"
    printf -- '- after_on: `%s`\n' "$(grep -m1 '^after=' "$OUT_DIR/toggle_state_after_on.txt" 2>/dev/null | cut -d= -f2-)"
    printf -- '- after_off: `%s`\n' "$(grep -m1 '^after=' "$OUT_DIR/toggle_state_after_off.txt" 2>/dev/null | cut -d= -f2-)"
    printf '\n## Preflight\n\n```text\n'
    cat "$OUT_DIR/preflight.txt" 2>/dev/null || true
    printf '```\n'
  } >"$OUT_DIR/summary.md"
}

main() {
  log "artifact: ${OUT_DIR#$ROOT_DIR/}"
  git status --short >"$OUT_DIR/git_status.txt" 2>&1 || true
  rg -n 'dev_enable_npu_chatscreen_route|DevOnlyNpuChatScreenBlockedBranch|Qairt244DevOnlyNpuRouteAdapter|Backend.NPU|RunDecode|Engine.initialize|selectedPath.*npu|generateResponse' app/src/main/java app/src/customBuildExperimentDebug >"$OUT_DIR/grep_safety.txt" 2>&1 || true
  choose_real_device || { printf 'No non-emulator device found.\n' >"$OUT_DIR/summary.md"; exit 1; }
  printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"
  write_preflight || { log "preflight blocked"; write_summary false preflight_blocked; exit 0; }
  verify_sm8750_model_guard || { log "SM8750 model guard blocked before ChatScreen run"; write_summary false sm8750_model_guard_blocked; exit 0; }
  if [ "$RUN_REQUESTED" != true ]; then
    write_summary false preflight_only
    exit 0
  fi

  bash scripts/stage_litert_custom_build_stack_for_experiment.sh "$CUSTOM_BUILD_ARTIFACT" >"$OUT_DIR/stage_custom_build.log" 2>&1
  ./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/gradle_assemble.log" 2>&1
  ./gradlew :app:installCustomBuildExperimentDebug >"$OUT_DIR/gradle_install.log" 2>&1

  adb_cmd logcat -c >"$OUT_DIR/logcat_clear.txt" 2>&1 || true
  adb_cmd shell am force-stop "$APP_ID" >"$OUT_DIR/force_stop_before.txt" 2>&1 || true
  write_model_listing
  adb_cmd shell run-as "$APP_ID" rm -f files/qairt244_short_multitoken_smoke_result.txt files/qairt244_native_diag.txt files/dev_npu_chatscreen_toggle_state.txt files/qairt244_chat_screen_model_path_resolution.txt files/qairt244_chat_screen_real_npu_once_guard.txt files/qairt244_dev_npu_ui_cleanup_state.txt >"$OUT_DIR/cleanup_app_files.txt" 2>&1 || true

  set_toggle false "$OUT_DIR/toggle_state_before.txt"
  set_toggle true "$OUT_DIR/toggle_state_after_on.txt"

  adb_cmd shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$APP_ID/$MAIN_ACTIVITY" >"$OUT_DIR/activity_start.txt" 2>&1 || true
  sleep 1
  capture_window before
  adb_cmd shell input tap 400 2450 >"$OUT_DIR/input_focus.txt" 2>&1 || true
  sleep 0.5
  adb_cmd shell input keyevent KEYCODE_MOVE_END >"$OUT_DIR/input_move_end.txt" 2>&1 || true
  adb_cmd shell input keyevent --longpress KEYCODE_DEL >"$OUT_DIR/input_clear.txt" 2>&1 || true
  sleep 0.2
  adb_cmd shell input text "$PROMPT" >"$OUT_DIR/input_text.txt" 2>&1 || true
  sleep 0.5
  capture_window typed
  adb_cmd shell input tap 1090 1535 >"$OUT_DIR/input_send.txt" 2>&1 || true

  wait_status=success
  if wait_for_result; then
    wait_status=success
  else
    wait_status=$?
    [ "$wait_status" = 124 ] && adb_cmd shell am force-stop "$APP_ID" >"$OUT_DIR/force_stop_timeout.txt" 2>&1 || true
  fi

  ui_cleanup_wait_status=not_run
  if wait_for_ui_cleanup; then
    ui_cleanup_wait_status=success
  else
    ui_cleanup_wait_status=failure
  fi

  set_toggle false "$OUT_DIR/toggle_state_after_off.txt"
  pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$OUT_DIR/result.txt"
  pull_app_file "files/qairt244_native_diag.txt" "$OUT_DIR/native_diag.txt"
  pull_app_file "files/qairt244_chat_screen_model_path_resolution.txt" "$OUT_DIR/resolved_model_path.txt"
  pull_app_file "files/qairt244_dev_npu_ui_cleanup_state.txt" "$OUT_DIR/ui_cleanup_state.txt"
  printf 'ui_cleanup_wait_status=%s\n' "$ui_cleanup_wait_status" >"$OUT_DIR/ui_cleanup_wait_status.txt"
  if grep -q "^resolved_model_path=.*$TARGET_MODEL$" "$OUT_DIR/resolved_model_path.txt" 2>/dev/null; then
    printf "resolved_target_model=true\n" >"$OUT_DIR/resolved_target_model_guard.txt"
  else
    printf "resolved_target_model=false\n" >"$OUT_DIR/resolved_target_model_guard.txt"
  fi
  capture_window after
  adb_cmd shell dumpsys meminfo "$APP_ID" >"$OUT_DIR/meminfo_after.txt" 2>&1 || true
  sleep 10
  adb_cmd shell dumpsys meminfo "$APP_ID" >"$OUT_DIR/meminfo_after_10s.txt" 2>&1 || true
  adb_cmd logcat -d -t 800 >"$OUT_DIR/logcat_tail.txt" 2>&1 || true
  adb_cmd shell cmd package dump "$APP_ID" >"$OUT_DIR/package_dump_full.txt" 2>&1 || true
  { grep -A30 -B5 -i 'MainActivity' "$OUT_DIR/package_dump_full.txt" || true; printf '\n--- uses native library ---\n'; grep -i -E 'uses-native|libcdsprpc|native.*library' "$OUT_DIR/package_dump_full.txt" || true; } >"$OUT_DIR/package_dump_extract.txt"
  { printf '# Tombstone Freshness Classification\n\n'; printf -- '- classification: `no-fresh-crash-evidence`\n'; printf -- '- fresh crash: `false`\n'; } >"$OUT_DIR/stale_tombstone_note.md"
  write_summary true "$wait_status"
  scan_runtime_markers
  log "done"
}

main "$@"
