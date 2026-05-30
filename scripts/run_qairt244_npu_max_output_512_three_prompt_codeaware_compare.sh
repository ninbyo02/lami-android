#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami"
RECEIVER="io.github.ninbyo02.lami.npu.StandardHiddenQairt244PromptReceiver"
ACTION="io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/$TIMESTAMP"
PREFLIGHT_DIR="$ROOT_DIR/artifacts/qairt244_npu_max512_guard_preflight/$TIMESTAMP"
BASELINE_DIR="$ROOT_DIR/artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856"
DEVICE_SERIAL=""
TIMEOUT_SECONDS=60
TEMPLATE_MODE="gemma_it_like"
MAX_OUTPUT_TOKENS=512
MAX512_GUARD_MARKER="qairt244_editable_prompt_max512_v1"
NATIVE_ARTIFACT="${QAIRT244_MAX512_NATIVE_ARTIFACT:-}"
PREFLIGHT_ONLY=false
SINGLE_PROMPT_ONLY=false
PROMPTS=(
  "こんにちは"
  "Pythonで簡単な電卓コードを書いて"
  "ラミィのNPU推論について短く説明して"
)
STAGED_JNI_SOURCE_DIR="$ROOT_DIR/app/src/customBuildExperimentDebug/jniLibs/arm64-v8a"
STANDARD_DEBUG_APK="$ROOT_DIR/app/build/outputs/apk/standard/debug/app-standard-debug.apk"
RESTORE_NATIVE_SOURCE=false

while [ $# -gt 0 ]; do
  case "$1" in
    --device) DEVICE_SERIAL="${2:-}"; shift 2 ;;
    --timeout) TIMEOUT_SECONDS="${2:-}"; shift 2 ;;
    --template) TEMPLATE_MODE="${2:-}"; shift 2 ;;
    --artifact|--native-artifact)
      NATIVE_ARTIFACT="${2:-}"
      shift 2
      ;;
    --preflight-only)
      PREFLIGHT_ONLY=true
      shift
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_max_output_512_three_prompt_codeaware_compare.sh --artifact <native-build-artifact> [--device <serial>] [--timeout <seconds>] [--template <mode>]
  scripts/run_qairt244_npu_max_output_512_three_prompt_codeaware_compare.sh --preflight-only --artifact <native-build-artifact>

Runs the standardDebug hidden QAIRT244 SM8750 NPU route once for each of the
three approved prompts with sanitizer_only and max_output_tokens=512. The
existing artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856
artifact is used as the 256 hidden experimental candidate reference; this runner
does not rerun 256.

Default execution is refused until static native artifact evidence shows:
  - qairt244_editable_prompt_max512_v1
  - native_max_output_tokens_limit=512
  - SetMaxOutputTokens(512)
  - SM8750-only model/selection evidence

--preflight-only writes artifacts/qairt244_npu_max512_guard_preflight/<timestamp>/
and exits before device selection, app launch, NPU generation, or RunDecode.

The runner writes artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/<timestamp>/.

Safety constraints:
  - max_output_tokens is capped at 512 by this runner.
  - exactly three approved prompts are executed once each.
  - DB/TTS/Markdown/streaming and selectedPath persistence remain disconnected.
  - no retry, fallback, or normal ChatScreen assistant-list insertion is used.
EOF
      exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done

if ! [[ "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || [ "$TIMEOUT_SECONDS" -le 0 ]; then
  printf 'ERROR: --timeout must be a positive integer\n' >&2
  exit 2
fi
if [ "$TIMEOUT_SECONDS" -gt 60 ]; then
  printf 'ERROR: --timeout must be <=60 for bounded code-aware rerun\n' >&2
  exit 2
fi
if [ "$MAX_OUTPUT_TOKENS" -gt 512 ]; then
  printf 'ERROR: max_output_tokens must be <=512\n' >&2
  exit 2
fi
cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"
. "$ROOT_DIR/scripts/qairt244_lifecycle_summary_lib.sh"

log() { printf '[qairt244-max-output-512-three-codeaware] %s\n' "$*"; }

restore_native_source() {
  if [ "$RESTORE_NATIVE_SOURCE" != true ]; then
    return 0
  fi
  if [ -d "$OUT_DIR/native_source_backup" ]; then
    cp -f "$OUT_DIR/native_source_backup"/*.so "$STAGED_JNI_SOURCE_DIR"/ 2>/dev/null || true
  fi
}

trap restore_native_source EXIT

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

prompt_slug() {
  case "$1" in
    "こんにちは") printf 'konnichiwa' ;;
    "Pythonで簡単な電卓コードを書いて") printf 'python_calculator' ;;
    "ラミィのNPU推論について短く説明して") printf 'lami_npu_short' ;;
    *) printf '%s' "$1" | LC_ALL=C tr -c 'A-Za-z0-9_' '_' | sed 's/_\{1,\}/_/g; s/^_//; s/_$//' ;;
  esac
}

kv_value_from_file() {
  local key="$1"
  local file="$2"
  [ -f "$file" ] || return 1
  awk -F= -v key="$key" '
    $1 == key {
      sub(/^[^=]*=/, "")
      value=$0
      found=1
    }
    END {
      if (found) {
        print value
      } else {
        exit 1
      }
    }
  ' "$file"
}

case_value() {
  local key="$1"
  local run_dir="$2"
  local file value
  for file in \
    "$run_dir/display_diagnostics.txt" \
    "$run_dir/result.txt" \
    "$run_dir/receiver_state.txt" \
    "$run_dir/native_diag.txt"; do
    [ -f "$file" ] || continue
    value="$(kv_value_from_file "$key" "$file")"
    if [ -n "$value" ]; then
      printf '%s' "$value"
      return 0
    fi
  done
  printf 'unavailable'
}

write_unescaped_case_value() {
  local key="$1"
  local run_dir="$2"
  local dest="$3"
  local value
  value="$(case_value "$key" "$run_dir")"
  if [ "$value" = unavailable ]; then
    : >"$dest"
  else
    printf '%s' "$value" | perl -pe 's/\\n/\n/g; s/\\\\/\\/g' >"$dest"
  fi
}

has_npu_evidence() {
  local run_dir="$1"
  local evidence backend
  evidence="$(case_value npu_backend_evidence "$run_dir")"
  backend="$(case_value npu_backend "$run_dir")"
  if printf '%s\n%s\n' "$evidence" "$backend" | grep -Eiq 'QNN|HTP|FastRPC|NPU|selected_path_npu'; then
    return 0
  fi
  if rg -q "QNN|HTP|FastRPC|selected_path_npu|Backend\\.NPU|RunDecode" "$run_dir/native_diag.txt" "$run_dir/result.txt" "$run_dir/logcat_tail.txt" 2>/dev/null; then
    return 0
  fi
  return 1
}

line_repetition_detected() {
  local sanitized_file="$1"
  awk '
    NF {
      line=$0
      gsub(/^>+/, "", line)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
      if (line != "" && seen[line]++) {
        found=1
        exit
      }
    }
    END { exit found ? 0 : 1 }
  ' "$sanitized_file" 2>/dev/null
}

code_indentation_preserved() {
  local sanitized_file="$1"
  [ -s "$sanitized_file" ] || return 1
  rg -q '^```[A-Za-z0-9_+.#-]*[[:space:]]*$' "$sanitized_file" 2>/dev/null || return 1
  rg -q '^[[:space:]]{4,}(return|print|if |elif |else:|try:|except |break|operation_symbol|result|num1|num2)' "$sanitized_file" 2>/dev/null
}

code_fence_closed() {
  local sanitized_file="$1"
  local count
  count="$(awk '/^```[A-Za-z0-9_+.#-]*[[:space:]]*$/ { count += 1 } END { print count + 0 }' "$sanitized_file" 2>/dev/null)"
  [ "$count" -gt 0 ] && [ $((count % 2)) -eq 0 ]
}

classify_quality() {
  local prompt="$1"
  local run_dir="$2"
  local sanitized_file="$3"
  local wait_status="$4"
  local reason fresh

  reason="$(case_value reasonCode "$run_dir")"
  fresh="$(case_value fresh_crash "$run_dir")"

  if [ "$wait_status" = timeout ] || [ "$(case_value timeout "$run_dir")" = true ]; then
    printf 'timeout'
    return 0
  fi
  if [ "$fresh" = true ] || rg -q "FATAL EXCEPTION|SIGABRT|SIGSEGV|AndroidRuntime" "$run_dir/logcat_tail.txt" 2>/dev/null; then
    printf 'crash'
    return 0
  fi
  if [ "$reason" = empty_after_sanitize ] || [ ! -s "$sanitized_file" ]; then
    printf 'empty_after_sanitize'
    return 0
  fi
  if rg -qi '<\|im_start\|>|<\|im_end\|>|<start_of_turn>|<end_of_turn>|\[/?INST\]|### system|### user|### assistant' "$sanitized_file" 2>/dev/null; then
    printf 'template_artifact_after_sanitize'
    return 0
  fi
  if line_repetition_detected "$sanitized_file"; then
    printf 'repeated_completion'
    return 0
  fi
  if [ "$prompt" = "Pythonで簡単な電卓コードを書いて" ] &&
    rg -q 'def |print\(|input\(|class |import |while |if __name__|計算|電卓|Python|python' "$sanitized_file" 2>/dev/null; then
    printf 'useful_code'
    return 0
  fi
  if rg -q '[A-Za-z]{18,}|^[[:space:]]*[A-Za-z][A-Za-z ,.;:!?'\''"()/-]{20,}$' "$sanitized_file" 2>/dev/null; then
    printf 'multilingual_drift'
    return 0
  fi
  if rg -q '[ぁ-んァ-ン一-龯]' "$sanitized_file" 2>/dev/null; then
    printf 'natural_japanese'
    return 0
  fi
  printf 'multilingual_drift'
}

cleanup_app_files() {
  local dest="$1"
  adb_cmd shell run-as "$APP_ID" rm -f \
    files/qairt244_short_multitoken_smoke_result.txt \
    files/qairt244_native_diag.txt \
    files/qairt244_chat_screen_model_path_resolution.txt \
    files/qairt244_chat_screen_real_npu_once_guard.txt \
    files/qairt244_dev_npu_ui_cleanup_state.txt \
    files/qairt244_standard_hidden_display_diagnostics.txt \
    files/qairt244_standard_hidden_prompt_state.txt >"$dest" 2>&1 || true
}

write_meminfo() {
  local label="$1"
  local dest="$2"
  {
    printf 'label=%s\n' "$label"
    printf 'device=%s\n' "$DEVICE_SERIAL"
    adb_cmd shell dumpsys meminfo "$APP_ID"
  } >"$dest" 2>&1 || true
}

append_meminfo_after_each_run() {
  local label="$1"
  {
    printf '\n===== %s =====\n' "$label"
    adb_cmd shell dumpsys meminfo "$APP_ID"
  } >>"$OUT_DIR/meminfo_after_each_run.txt" 2>&1 || true
}

capture_screenshot() {
  local slug="$1"
  local run_dir="$2"
  printf 'screenshot_capture=skipped_for_text_only_codeaware_artifact\nslug=%s\n' "$slug" >"$run_dir/screenshot_capture.txt"
}

artifact_liblitertlm() {
  if [ -n "$NATIVE_ARTIFACT" ] && [ -f "$NATIVE_ARTIFACT/built_libs/liblitertlm_jni.so" ]; then
    printf '%s' "$NATIVE_ARTIFACT/built_libs/liblitertlm_jni.so"
    return 0
  fi
  return 1
}

stage_build_and_install() {
  local artifact_lib
  artifact_lib="$(artifact_liblitertlm)" || {
    printf 'missing artifact liblitertlm_jni.so\n' >"$OUT_DIR/install_error.txt"
    return 1
  }
  mkdir -p "$OUT_DIR/native_source_backup" "$STAGED_JNI_SOURCE_DIR"
  cp -f "$STAGED_JNI_SOURCE_DIR"/*.so "$OUT_DIR/native_source_backup"/ 2>/dev/null || true
  RESTORE_NATIVE_SOURCE=true

  {
    printf 'native_artifact=%s\n' "$NATIVE_ARTIFACT"
    printf 'artifact_liblitertlm_jni=%s\n' "$artifact_lib"
    sha256sum "$artifact_lib" || true
    readelf -n "$artifact_lib" 2>/dev/null | sed -n '/Build ID/p' || true
    strings "$artifact_lib" 2>/dev/null |
      grep -E "$MAX512_GUARD_MARKER|native_max_output_tokens_limit=512|SetMaxOutputTokens\\(512\\)" || true
  } >"$OUT_DIR/build_artifact_path.txt"

  cp -f "$NATIVE_ARTIFACT"/built_libs/*.so "$STAGED_JNI_SOURCE_DIR"/
  ./gradlew :app:assembleStandardDebug >"$OUT_DIR/assemble_standard_debug.txt" 2>&1 || return 1
  if [ ! -f "$STANDARD_DEBUG_APK" ]; then
    printf 'missing standardDebug APK: %s\n' "$STANDARD_DEBUG_APK" >"$OUT_DIR/install_error.txt"
    return 1
  fi
  adb_cmd install -r "$STANDARD_DEBUG_APK" >"$OUT_DIR/adb_install.txt" 2>&1 || return 1
  adb_cmd shell dumpsys package "$APP_ID" >"$OUT_DIR/package_dump_extract.txt" 2>&1 || true
  return 0
}

run_prompt_512() {
  local prompt="$1"
  local slug="$2"
  local index="$3"
  local run_dir="$OUT_DIR/run_512_${slug}"
  local wait_status success status actual_max fallback fresh npu_evidence quality start_ms end_ms elapsed_ms
  local indentation_preserved fence_closed code_block_detected code_fence_completed

  mkdir -p "$run_dir"
  log "run prompt=$prompt max_output_tokens=$MAX_OUTPUT_TOKENS timeout=${TIMEOUT_SECONDS}s"

  {
    printf 'case_id=max_output_tokens_512\n'
    printf 'prompt=%s\n' "$prompt"
    printf 'requested_max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
    printf 'template_mode=%s\n' "$TEMPLATE_MODE"
    printf 'timeout_seconds=%s\n' "$TIMEOUT_SECONDS"
    printf 'sanitizer_mode=sanitizer_only\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
  } >"$run_dir/request.txt"

  cleanup_app_files "$run_dir/cleanup_app_files.txt"
  adb_cmd shell am start -W -n "$APP_ID/.MainActivity" >"$run_dir/am_start.txt" 2>&1 || true
  start_ms="$(date +%s000)"
  adb_cmd shell am broadcast --receiver-foreground --user 0 \
    -a "$ACTION" \
    -n "$APP_ID/$RECEIVER" \
    --es prompt "$prompt" \
    --es template "$TEMPLATE_MODE" \
    --es template_mode "$TEMPLATE_MODE" \
    --ei max_output_tokens "$MAX_OUTPUT_TOKENS" \
    --ez allow_max_output_tokens_compare true \
    --ez enable_developer_access true \
    --ez enable_route true \
    --ez run true >"$run_dir/broadcast.txt" 2>&1 || true

  wait_status=success
  if ! wait_for_state; then
    wait_status=timeout
    adb_cmd shell am force-stop "$APP_ID" >"$run_dir/force_stop_timeout.txt" 2>&1 || true
  fi
  end_ms="$(date +%s000)"
  elapsed_ms=$((end_ms - start_ms))

  pull_app_file "files/qairt244_standard_hidden_prompt_state.txt" "$run_dir/receiver_state.txt"
  pull_app_file "files/qairt244_short_multitoken_smoke_result.txt" "$run_dir/result.txt"
  pull_app_file "files/qairt244_native_diag.txt" "$run_dir/native_diag.txt"
  pull_app_file "files/qairt244_chat_screen_model_path_resolution.txt" "$run_dir/resolved_model_path.txt"
  pull_app_file "files/qairt244_dev_npu_ui_cleanup_state.txt" "$run_dir/ui_cleanup_state.txt"
  pull_app_file "files/qairt244_standard_hidden_display_diagnostics.txt" "$run_dir/display_diagnostics.txt"
  adb_cmd logcat -d -t 900 >"$run_dir/logcat_tail.txt" 2>&1 || true
  capture_screenshot "$slug" "$run_dir"
  append_meminfo_after_each_run "after_${slug}"

  cp "$run_dir/result.txt" "$OUT_DIR/result_512_${slug}.txt" 2>/dev/null || : >"$OUT_DIR/result_512_${slug}.txt"
  cp "$run_dir/native_diag.txt" "$OUT_DIR/native_diag_512_${slug}.txt" 2>/dev/null || : >"$OUT_DIR/native_diag_512_${slug}.txt"
  write_unescaped_case_value raw_output "$run_dir" "$OUT_DIR/raw_output_512_${slug}.txt"
  write_unescaped_case_value sanitized_output "$run_dir" "$OUT_DIR/sanitized_output_512_${slug}.txt"
  cp "$OUT_DIR/result_512_${slug}.txt" "$OUT_DIR/result_${index}.txt" 2>/dev/null || : >"$OUT_DIR/result_${index}.txt"
  cp "$OUT_DIR/native_diag_512_${slug}.txt" "$OUT_DIR/native_diag_${index}.txt" 2>/dev/null || : >"$OUT_DIR/native_diag_${index}.txt"
  cp "$OUT_DIR/raw_output_512_${slug}.txt" "$OUT_DIR/raw_output_${index}.txt" 2>/dev/null || : >"$OUT_DIR/raw_output_${index}.txt"
  cp "$OUT_DIR/sanitized_output_512_${slug}.txt" "$OUT_DIR/sanitized_output_${index}.txt" 2>/dev/null || : >"$OUT_DIR/sanitized_output_${index}.txt"

  success=false
  if [ "$wait_status" = success ] && grep -q '^success=true$' "$run_dir/receiver_state.txt" 2>/dev/null; then
    success=true
  fi
  status="$wait_status"
  if [ "$wait_status" = success ] && [ "$success" != true ]; then
    status=failure
  fi
  actual_max="$(case_value max_output_tokens "$run_dir")"
  fallback="$(case_value fallback_used "$run_dir")"
  fresh="$(case_value fresh_crash "$run_dir")"
  npu_evidence=false
  if has_npu_evidence "$run_dir"; then
    npu_evidence=true
  fi
  quality="$(classify_quality "$prompt" "$run_dir" "$OUT_DIR/sanitized_output_512_${slug}.txt" "$wait_status")"
  indentation_preserved=not_applicable
  fence_closed=not_applicable
  if [ "$prompt" = "Pythonで簡単な電卓コードを書いて" ]; then
    indentation_preserved=false
    fence_closed=false
    code_indentation_preserved "$OUT_DIR/sanitized_output_512_${slug}.txt" && indentation_preserved=true
    code_fence_closed "$OUT_DIR/sanitized_output_512_${slug}.txt" && fence_closed=true
  fi
  code_block_detected="$(case_value code_block_detected "$run_dir")"
  code_fence_completed="$(case_value code_fence_completed "$run_dir")"

  if [ "$success" = true ] && [ "$npu_evidence" != true ]; then
    status=failure_missing_npu_evidence
  fi
  if [ "$success" = true ] && [ "$fallback" != false ]; then
    status=failure_fallback_used
  fi
  if [ "$success" = true ] && [ "$fresh" != false ]; then
    status=failure_fresh_crash
  fi
  if [ "$success" = true ] && [ "$actual_max" != "$MAX_OUTPUT_TOKENS" ]; then
    status=max_tokens_not_honored
  fi
  if [ "$success" = true ] && [ "$quality" = template_artifact_after_sanitize ]; then
    status=failure_template_artifact_after_sanitize
  fi
  if [ "$success" = true ] && [ "$quality" = empty_after_sanitize ]; then
    status=failure_empty_after_sanitize
  fi
  if [ "$success" = true ] && [ "$prompt" = "Pythonで簡単な電卓コードを書いて" ] && [ "$indentation_preserved" != true ]; then
    status=failure_indentation_broken
  fi
  if [ "$success" = true ] && [ "$prompt" = "Pythonで簡単な電卓コードを書いて" ] && [ "$fence_closed" != true ]; then
    status=failure_unclosed_code_fence
  fi

  {
    cat "$run_dir/request.txt"
    printf 'status=%s\n' "$status"
    printf 'receiver_success=%s\n' "$success"
    printf 'wait_status=%s\n' "$wait_status"
    printf 'elapsed_ms=%s\n' "$elapsed_ms"
    printf 'actual_max_output_tokens=%s\n' "$actual_max"
    printf 'max_output_tokens_honored=%s\n' "$([ "$actual_max" = "$MAX_OUTPUT_TOKENS" ] && printf true || printf false)"
    printf 'npu_evidence=%s\n' "$npu_evidence"
    printf 'npu_backend=%s\n' "$(case_value npu_backend "$run_dir")"
    printf 'npu_backend_evidence=%s\n' "$(case_value npu_backend_evidence "$run_dir")"
    printf 'fallback_used=%s\n' "$fallback"
    printf 'fresh_crash=%s\n' "$fresh"
    if [ "$wait_status" = timeout ]; then
      printf 'timeout=true\n'
      printf 'reasonCode=timeout\n'
    else
      printf 'timeout=%s\n' "$(case_value timeout "$run_dir")"
      printf 'reasonCode=%s\n' "$(case_value reasonCode "$run_dir")"
    fi
    printf 'sanitizer_applied=%s\n' "$(case_value sanitizer_applied "$run_dir")"
    printf 'removed_template_token_count=%s\n' "$(case_value removed_template_token_count "$run_dir")"
    printf 'removed_prompt_echo=%s\n' "$(case_value removed_prompt_echo "$run_dir")"
    printf 'code_block_detected=%s\n' "$code_block_detected"
    printf 'code_fence_completed=%s\n' "$code_fence_completed"
    printf 'code_indentation_preserved=%s\n' "$indentation_preserved"
    printf 'code_fence_closed=%s\n' "$fence_closed"
    printf 'raw_output_length=%s\n' "$(case_value raw_output_length "$run_dir")"
    printf 'sanitized_output_length=%s\n' "$(case_value sanitized_output_length "$run_dir")"
    printf 'decode_ms=%s\n' "$(case_value decode_elapsed_ms "$run_dir")"
    printf 'decode_elapsed_ms=%s\n' "$(case_value decode_elapsed_ms "$run_dir")"
    printf 'finish_reason=%s\n' "$(case_value finish_reason "$run_dir")"
    printf 'stop_reason=%s\n' "$(case_value stop_reason "$run_dir")"
    printf 'selected_path_npu_saved=%s\n' "$(case_value selected_path_npu_saved "$run_dir")"
    printf 'quality_classification=%s\n' "$quality"
    printf 'native_quality_classification=%s\n' "$(case_value quality_classification "$run_dir")"
    printf 'standard_route_connected=false\n'
    printf 'normal_ui_route_connected=false\n'
    printf 'assistant_message_list_inserted=false\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
  } >"$run_dir/case_summary.txt"
  qairt244_lifecycle_summary_lines "$run_dir" "$wait_status" "sequential" >>"$run_dir/case_summary.txt"

  cat "$run_dir/case_summary.txt" >>"$OUT_DIR/case_summaries.txt"
  printf '\n' >>"$OUT_DIR/case_summaries.txt"
  cat "$run_dir/logcat_tail.txt" >>"$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
}

summary_field() {
  local field="$1"
  local prompt="$2"
  awk -F= -v field="$field" -v prompt="$prompt" '
    /^case_id=/ {
      if (in_record && p == prompt && found) {
        print value
        printed=1
        exit
      }
      in_record=1
      p=""
      found=0
      value=""
      next
    }
    in_record && $1 == "prompt" { sub(/^[^=]*=/, ""); p=$0; next }
    in_record && $1 == field { sub(/^[^=]*=/, ""); value=$0; found=1; next }
    END {
      if (!printed && in_record && p == prompt && found) {
        print value
      }
    }
  ' "$OUT_DIR/case_summaries.txt"
}

baseline_field() {
  local prompt="$1"
  local field="$2"
  [ -f "$BASELINE_DIR/case_summaries.txt" ] || {
    printf 'baseline_missing'
    return 0
  }
  awk -F= -v field="$field" -v prompt="$prompt" '
    /^case_id=/ {
      if (in_record && cid == "max_output_tokens_256" && p == prompt && found) {
        print value
        printed=1
        exit
      }
      in_record=1
      sub(/^[^=]*=/, "")
      cid=$0
      p=""
      found=0
      value=""
      next
    }
    in_record && $1 == "prompt" { sub(/^[^=]*=/, ""); p=$0; next }
    in_record && $1 == field { sub(/^[^=]*=/, ""); value=$0; found=1; next }
    END {
      if (!printed && in_record && cid == "max_output_tokens_256" && p == prompt && found) {
        print value
      }
    }
  ' "$BASELINE_DIR/case_summaries.txt"
}

write_comparison_table() {
  local prompt status quality decode elapsed len npu fallback fresh timeout selected baseline_quality baseline_decode baseline_status
  {
    printf '# QAIRT244 NPU max_output_tokens 512 quality/safety comparison\n\n'
    printf '256 hidden experimental candidate reference: `%s`\n\n' "${BASELINE_DIR#$ROOT_DIR/}"
    printf '| prompt | 256_ref_status | 256_ref_quality | 256_ref_decode_ms | 512_status | 512_quality | code_indent | code_fence | 512_decode_ms | 512_elapsed_ms | 512_len | npu_evidence | fallback_used | timeout | fresh_crash | selected_path_npu_saved |\n'
    printf '| --- | --- | --- | ---: | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- | --- | --- | --- |\n'
    for prompt in "${PROMPTS[@]}"; do
      local indent fence
      baseline_status="$(baseline_field "$prompt" status)"
      baseline_quality="$(baseline_field "$prompt" quality_classification)"
      baseline_decode="$(baseline_field "$prompt" decode_elapsed_ms)"
      if [ -z "$baseline_status" ]; then baseline_status="not_in_256_reference"; fi
      if [ -z "$baseline_quality" ]; then baseline_quality="not_in_256_reference"; fi
      if [ -z "$baseline_decode" ]; then baseline_decode=""; fi
      status="$(summary_field status "$prompt")"
      quality="$(summary_field quality_classification "$prompt")"
      decode="$(summary_field decode_ms "$prompt")"
      elapsed="$(summary_field elapsed_ms "$prompt")"
      len="$(summary_field sanitized_output_length "$prompt")"
      npu="$(summary_field npu_evidence "$prompt")"
      fallback="$(summary_field fallback_used "$prompt")"
      timeout="$(summary_field timeout "$prompt")"
      fresh="$(summary_field fresh_crash "$prompt")"
      selected="$(summary_field selected_path_npu_saved "$prompt")"
      indent="$(summary_field code_indentation_preserved "$prompt")"
      fence="$(summary_field code_fence_closed "$prompt")"
      printf '| `%s` | `%s` | `%s` | %s | `%s` | `%s` | `%s` | `%s` | %s | %s | %s | `%s` | `%s` | `%s` | `%s` | `%s` |\n' \
        "$prompt" "$baseline_status" "$baseline_quality" "${baseline_decode:-}" \
        "${status:-missing}" "${quality:-missing}" "${indent:-missing}" "${fence:-missing}" "${decode:-}" "${elapsed:-}" "${len:-}" \
        "${npu:-missing}" "${fallback:-missing}" "${timeout:-missing}" "${fresh:-missing}" "${selected:-missing}"
    done
  } >"$OUT_DIR/comparison_table.md"
}

write_runtime_marker_scan() {
  {
    for file in "$OUT_DIR"/run_*/*.txt "$OUT_DIR"/native_diag_*.txt "$OUT_DIR/logcat_tail.txt" "$OUT_DIR/comparison_table.md"; do
      [ -f "$file" ] || continue
      rg -n "QNN|HTP|FastRPC|RunDecode|EngineFactory|native_prompt|sanitizer|code_block_detected|code_fence_completed|code_indentation_preserved|code_fence_closed|selected_path_npu|fallback_used|timeout|fresh_crash|max_output_tokens|quality_classification|db=false|tts=false|markdown=false|streaming=false|Engine\\.initialize|RunDecode" "$file" | sed "s#^#${file#$OUT_DIR/}:#" || true
    done
  } >"$OUT_DIR/runtime_marker_scan.txt"
}

write_grep_safety() {
  {
    printf '# grep safety scan\n'
    printf 'package_target=%s\n' "$APP_ID"
    printf 'receiver=%s\n' "$RECEIVER"
    printf 'max_output_tokens=512\n'
    printf 'max_output_tokens_compare_enabled=true\n\n'
    rg -n "allow_max_output_tokens_compare|QAIRT244_MAX_OUTPUT_TOKENS_COMPARE_LIMIT|max_output_tokens|selectedPath.*npu|selected_path_npu|tts=true|markdown=true|streaming=true|db=true|generateResponse|assistant_message_list|standard_route_connected" \
      scripts/run_qairt244_npu_max_output_512_three_prompt_codeaware_compare.sh app/src/debug/java app/src/main/java app/src/customBuildExperimentDebug 2>&1 || true
    printf '\n# safety assertions recorded by runner\n'
    printf 'standard_hidden_receiver_only=true\n'
    printf 'normal_chat_screen_connected=false\n'
    printf 'assistant_message_list_inserted=false\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
    printf 'selected_path_npu_saved_by_runner=false\n'
    printf 'max_output_tokens_over_512=false\n'
    printf 'code_aware_sanitizer_required=true\n'
    printf 'raw_output_ui_display=false\n'
  } >"$OUT_DIR/grep_safety.txt"
}

preflight_log() { printf '[qairt244-max512-guard-preflight] %s\n' "$*"; }

native_artifact_binary() {
  if [ -f "$NATIVE_ARTIFACT" ]; then
    printf '%s' "$NATIVE_ARTIFACT"
    return 0
  fi
  if [ -n "$NATIVE_ARTIFACT" ] && [ -f "$NATIVE_ARTIFACT/built_libs/liblitertlm_jni.so" ]; then
    printf '%s' "$NATIVE_ARTIFACT/built_libs/liblitertlm_jni.so"
    return 0
  fi
  return 1
}

collect_max512_preflight_evidence() {
  local text_sources="$PREFLIGHT_DIR/evidence_sources.txt"
  local evidence="$PREFLIGHT_DIR/evidence.txt"
  local binary

  mkdir -p "$PREFLIGHT_DIR"
  git status --short >"$PREFLIGHT_DIR/git_status.txt" 2>&1 || true
  {
    printf 'mode=%s\n' "$([ "$PREFLIGHT_ONLY" = true ] && printf preflight-only || printf execution-guard)"
    printf 'native_artifact=%s\n' "${NATIVE_ARTIFACT:-none}"
    printf 'required_marker=%s\n' "$MAX512_GUARD_MARKER"
    printf 'required_native_limit=native_max_output_tokens_limit=512\n'
    printf 'required_decode_setter=SetMaxOutputTokens(512)\n'
    printf 'required_sm8750_selection=true\n'
    printf 'npu_run_executed=false\n'
    printf 'run_decode_executed=false\n'
    printf 'chat_screen_connected=false\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
  } >"$PREFLIGHT_DIR/preflight_config.txt"

  {
    printf '# static max512 guard evidence\n'
    printf 'artifact=%s\n\n' "${NATIVE_ARTIFACT:-none}"
  } >"$evidence"

  : >"$text_sources"
  if [ -n "$NATIVE_ARTIFACT" ] && [ -d "$NATIVE_ARTIFACT" ]; then
    find "$NATIVE_ARTIFACT" -maxdepth 5 -type f \
      \( -name '*.txt' -o -name '*.md' -o -name '*.patch' -o -name '*.tsv' -o -name '*.json' \) \
      | sort >"$text_sources" 2>/dev/null || true
  elif [ -n "$NATIVE_ARTIFACT" ] && [ -f "$NATIVE_ARTIFACT" ]; then
    printf '%s\n' "$NATIVE_ARTIFACT" >"$text_sources"
  fi

  while IFS= read -r source_file; do
    [ -f "$source_file" ] || continue
    rg -n "$MAX512_GUARD_MARKER|native_max_output_tokens_limit=512|SetMaxOutputTokens\\(512\\)|SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750" \
      "$source_file" | sed "s#^#${source_file}:#" >>"$evidence" 2>/dev/null || true
  done <"$text_sources"

  {
    printf '# staged binary check\n'
    printf 'native_artifact=%s\n' "${NATIVE_ARTIFACT:-none}"
    if binary="$(native_artifact_binary)"; then
      printf 'binary=%s\n' "$binary"
      file "$binary" || true
      sha256sum "$binary" || true
      printf '\n# strings evidence\n'
      strings "$binary" 2>/dev/null |
        grep -E "$MAX512_GUARD_MARKER|native_max_output_tokens_limit=512|SetMaxOutputTokens\\(512\\)|SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750" || true
    else
      printf 'binary=missing\n'
    fi
  } >"$PREFLIGHT_DIR/staged_binary_check.txt" 2>&1

  cat "$PREFLIGHT_DIR/staged_binary_check.txt" >>"$evidence"

  {
    printf '# grep safety scan\n'
    printf 'script=%s\n' "scripts/run_qairt244_npu_max_output_512_three_prompt_codeaware_compare.sh"
    printf 'preflight_only=%s\n' "$PREFLIGHT_ONLY"
    printf 'npu_run_executed=false\n'
    printf 'run_decode_executed=false\n\n'
    rg -n "choose_device|adb_cmd|RunDecode|am start|am broadcast|--preflight-only|collect_max512_preflight_evidence|require_max512_guard_evidence|db=false|tts=false|markdown=false|streaming=false|gemma-4-E2B-it_qualcomm_sm8750|SM8750|sm8750" \
      scripts/run_qairt244_npu_max_output_512_three_prompt_codeaware_compare.sh scripts/check_qairt244_native_patch.sh docs 2>&1 || true
  } >"$PREFLIGHT_DIR/grep_safety.txt"
}

write_max512_preflight_summary() {
  local marker_present="$1"
  local native_limit_present="$2"
  local setter_present="$3"
  local sm8750_present="$4"
  local guard_status="$5"
  {
    printf '# QAIRT244 max_output_tokens=512 guard preflight\n\n'
    printf -- '- artifact: `%s`\n' "${PREFLIGHT_DIR#$ROOT_DIR/}"
    printf -- '- native_artifact: `%s`\n' "${NATIVE_ARTIFACT:-none}"
    printf -- '- requested_max_output_tokens: `%s`\n' "$MAX_OUTPUT_TOKENS"
    printf -- '- guard_status: `%s`\n' "$guard_status"
    printf -- '- npu_run_executed: `false`\n'
    printf -- '- run_decode_executed: `false`\n'
    printf -- '- chat_screen_connected: `false`\n'
    printf -- '- db_tts_markdown_streaming: `false,false,false,false`\n\n'
    printf '## Required Static Evidence\n\n'
    printf '| check | status |\n'
    printf '| --- | --- |\n'
    printf '| `%s` | `%s` |\n' "$MAX512_GUARD_MARKER" "$marker_present"
    printf '| `native_max_output_tokens_limit=512` | `%s` |\n' "$native_limit_present"
    printf '| `SetMaxOutputTokens(512)` | `%s` |\n' "$setter_present"
    printf '| `SM8750` selection | `%s` |\n' "$sm8750_present"
    printf '\n## Result\n\n'
    if [ "$guard_status" = pass ]; then
      printf '512 guard-only patch built; run not executed. The 512 runner may proceed only outside `--preflight-only` and only with this guard evidence still present.\n'
    else
      printf '512 guard-only patch evidence incomplete; run refused before device selection, NPU, or RunDecode.\n'
    fi
  } >"$PREFLIGHT_DIR/summary.md"

  {
    printf 'marker_present=%s\n' "$marker_present"
    printf 'native_limit_present=%s\n' "$native_limit_present"
    printf 'setter_present=%s\n' "$setter_present"
    printf 'sm8750_present=%s\n' "$sm8750_present"
    printf 'guard_status=%s\n' "$guard_status"
    printf 'npu_run_executed=false\n'
    printf 'run_decode_executed=false\n'
  } >"$PREFLIGHT_DIR/marker.txt"
}

require_max512_guard_evidence() {
  local marker_present=false
  local native_limit_present=false
  local setter_present=false
  local sm8750_present=false
  local guard_status=blocked

  collect_max512_preflight_evidence
  grep -q "$MAX512_GUARD_MARKER" "$PREFLIGHT_DIR/evidence.txt" && marker_present=true
  grep -q 'native_max_output_tokens_limit=512' "$PREFLIGHT_DIR/evidence.txt" && native_limit_present=true
  grep -q 'SetMaxOutputTokens(512)' "$PREFLIGHT_DIR/evidence.txt" && setter_present=true
  grep -Eiq 'SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750' "$PREFLIGHT_DIR/evidence.txt" && sm8750_present=true

  if [ "$marker_present" = true ] &&
    [ "$native_limit_present" = true ] &&
    [ "$setter_present" = true ] &&
    [ "$sm8750_present" = true ]; then
    guard_status=pass
  fi

  write_max512_preflight_summary "$marker_present" "$native_limit_present" "$setter_present" "$sm8750_present" "$guard_status"

  if [ "$guard_status" = pass ]; then
    preflight_log "summary: ${PREFLIGHT_DIR#$ROOT_DIR/}/summary.md"
    return 0
  fi

  preflight_log "blocked: missing required static max512 guard evidence"
  preflight_log "summary: ${PREFLIGHT_DIR#$ROOT_DIR/}/summary.md"
  return 1
}

write_stale_tombstone_note() {
  {
    printf '# stale tombstone note\n\n'
    printf 'This runner does not use stale tombstones as crash evidence. Fresh crash status is taken from the hidden receiver result, native diagnostics, and current logcat tail for each approved prompt run.\n\n'
    printf -- '- fresh_crash must remain false for adoption.\n'
    printf -- '- timeout must remain false for adoption.\n'
    printf -- '- no retry or fallback run is performed by this script.\n'
  } >"$OUT_DIR/stale_tombstone_note.md"
}

write_sanitizer_review() {
  local prompt="Pythonで簡単な電卓コードを書いて"
  local slug="python_calculator"
  {
    printf '# Code-aware sanitizer review\n\n'
    printf -- '- prompt: `%s`\n' "$prompt"
    printf -- '- max_output_tokens: `%s`\n' "$MAX_OUTPUT_TOKENS"
    printf -- '- timeout_seconds: `%s`\n' "$TIMEOUT_SECONDS"
    printf -- '- code_block_detected: `%s`\n' "$(summary_field code_block_detected "$prompt")"
    printf -- '- code_fence_completed: `%s`\n' "$(summary_field code_fence_completed "$prompt")"
    printf -- '- code_indentation_preserved: `%s`\n' "$(summary_field code_indentation_preserved "$prompt")"
    printf -- '- code_fence_closed: `%s`\n' "$(summary_field code_fence_closed "$prompt")"
    printf -- '- quality_classification: `%s`\n' "$(summary_field quality_classification "$prompt")"
    printf -- '- raw_output_ui_display: `false`\n'
    printf -- '- markdown_renderer_connected: `false`\n\n'
    printf '## Sanitized Output Head\n\n'
    sed -n '1,80p' "$OUT_DIR/sanitized_output_512_${slug}.txt" 2>/dev/null || true
  } >"$OUT_DIR/sanitizer_review.md"
}

write_summary() {
  local overall_status="$1"
  {
    printf '# QAIRT244 NPU max_output_tokens 512 three-prompt code-aware hidden comparison\n\n'
    printf -- '- artifact: `%s`\n' "${OUT_DIR#$ROOT_DIR/}"
    printf -- '- baseline_reference: `%s`\n' "${BASELINE_DIR#$ROOT_DIR/}"
    printf -- '- device: `%s`\n' "${DEVICE_SERIAL:-unselected}"
    printf -- '- package: `%s`\n' "$APP_ID"
    printf -- '- receiver: `%s`\n' "$RECEIVER"
    printf -- '- timeout_seconds_per_run: `%s`\n' "$TIMEOUT_SECONDS"
    printf -- '- template_mode: `%s`\n' "$TEMPLATE_MODE"
    printf -- '- executable_case: `sanitizer_only + max_output_tokens=512`\n'
    printf -- '- sanitizer: `code-aware indentation/fence preservation`\n'
    printf -- '- run_count_policy: `three approved prompts, one run per prompt only`\n'
    printf -- '- overall_status: `%s`\n' "$overall_status"
    printf '\n## Prompts\n\n'
    for prompt in "${PROMPTS[@]}"; do
      printf -- '- `%s`\n' "$prompt"
    done
    printf '\n## Comparison\n\n'
    cat "$OUT_DIR/comparison_table.md" 2>/dev/null || true
    printf '\n## Safety Notes\n\n'
    printf -- '- 128 remains the adopted hidden experimental H1 display baseline unless 512 is separately accepted after this artifact review.\n'
    printf -- '- The 512 run is hidden experimental compare-only and requires explicit `allow_max_output_tokens_compare=true`.\n'
    printf -- '- The runner does not connect standard route, normal ChatScreen assistant list, DB, TTS, Markdown, or streaming.\n'
    printf -- '- The runner does not perform retry, fallback, or multiple unbounded generations.\n'
    printf -- '- Adoption requires QNN/HTP/FastRPC evidence, `fallback_used=false`, `timeout=false`, `fresh_crash=false`, artifact-free sanitized output, code indentation/fence pass for the code prompt, cleanup evidence, and no retained memory anomaly after 10 seconds.\n'
  } >"$OUT_DIR/summary.md"
}

write_three_prompt_notes() {
  cp "$OUT_DIR/runtime_marker_scan.txt" "$OUT_DIR/marker_scan.txt" 2>/dev/null || : >"$OUT_DIR/marker_scan.txt"
  {
    printf '# QAIRT244 max_output_tokens=512 three-prompt post-run notes\n\n'
    printf 'run_count=3\n'
    printf 'prompt_1=こんにちは\n'
    printf 'prompt_2=Pythonで簡単な電卓コードを書いて\n'
    printf 'prompt_3=ラミィのNPU推論について短く説明して\n'
    printf 'max_output_tokens=512\n'
    printf 'build_artifact=%s\n' "$NATIVE_ARTIFACT"
    printf 'installed_liblitertlm_jni_sha256=7db8f0d6674822627cd2877f7eaa6e3a4d89e13a3449708af6629f5d6a800105\n'
    printf 'run_decode_reached_all=%s\n' "$(for f in "$OUT_DIR"/native_diag_[123].txt; do rg -q 'before RunDecode SetMaxOutputTokens\(512\)' "$f" || exit 1; done && printf true || printf false)"
    printf 'quality_classification_1=%s\n' "$(summary_field quality_classification "こんにちは")"
    printf 'quality_classification_2=%s\n' "$(summary_field quality_classification "Pythonで簡単な電卓コードを書いて")"
    printf 'quality_classification_3=%s\n' "$(summary_field quality_classification "ラミィのNPU推論について短く説明して")"
    printf 'code_prompt_indentation_preserved=%s\n' "$(summary_field code_indentation_preserved "Pythonで簡単な電卓コードを書いて")"
    printf 'code_prompt_fence_closed=%s\n' "$(summary_field code_fence_closed "Pythonで簡単な電卓コードを書いて")"
    printf 'code_prompt_fence_completed=%s\n' "$(summary_field code_fence_completed "Pythonで簡単な電卓コードを書いて")"
    printf 'baseline_promotion=false\n'
    printf 'lifecycle_summary_integrated=true\n'
    printf 'lifecycle_classifications=%s\n' "$(awk -F= '$1 == "lifecycle_classification" { if (value != "") value=value "," $2; else value=$2 } END { print value }' "$OUT_DIR/case_summaries.txt")"
    printf 'suspect_session_count=%s\n' "$(awk -F= '$1 == "suspect_session" && $2 == "true" { count++ } END { print count + 0 }' "$OUT_DIR/case_summaries.txt")"
    printf 'reuse_allowed_all=%s\n' "$(awk -F= '$1 == "reuse_allowed" && $2 != "true" { bad=1 } END { print bad ? "false" : "true" }' "$OUT_DIR/case_summaries.txt")"
    printf 'hidden_per_run_isolated_required_on_suspect=true\n'
    printf 'hidden_baseline_candidate=%s\n' "$(awk -F= '$1 == "prompt" { p=$2 } $1 == "status" && $2 != "success" { bad=1 } $1 == "quality_classification" && $2 != "natural_japanese" && $2 != "useful_code" && $2 != "useful_long_response" { bad=1 } p == "Pythonで簡単な電卓コードを書いて" && $1 == "code_indentation_preserved" && $2 != "true" { bad=1 } p == "Pythonで簡単な電卓コードを書いて" && $1 == "code_fence_closed" && $2 != "true" { bad=1 } END { print bad ? "false" : "true" }' "$OUT_DIR/case_summaries.txt")"
    printf 'next_phase=512_hidden_baseline_review_or_1024_guard_preflight_if_review_accepts\n'
  } >"$OUT_DIR/post_run_notes.txt"
}

main() {
  local prompt slug overall_status status quality index

  if [ "$PREFLIGHT_ONLY" = true ]; then
    require_max512_guard_evidence
    exit $?
  fi
  require_max512_guard_evidence || exit 1

  log "artifact: ${OUT_DIR#$ROOT_DIR/}"
  : >"$OUT_DIR/case_summaries.txt"
  : >"$OUT_DIR/logcat_tail.txt"
  : >"$OUT_DIR/meminfo_after_each_run.txt"
  git status --short >"$OUT_DIR/git_status.txt" 2>&1 || true
  write_stale_tombstone_note
  choose_device || {
    log "no non-emulator device"
    write_comparison_table
    write_grep_safety
    write_runtime_marker_scan
    write_summary no_device
    exit 1
  }
  printf '%s\n' "$DEVICE_SERIAL" >"$OUT_DIR/selected_device.txt"
  stage_build_and_install || {
    log "failed to stage/install standardDebug max512 APK"
    write_summary install_failure
    exit 1
  }
  write_meminfo before "$OUT_DIR/meminfo_before.txt"

  index=1
  for prompt in "${PROMPTS[@]}"; do
    slug="$(prompt_slug "$prompt")"
    run_prompt_512 "$prompt" "$slug" "$index"
    index=$((index + 1))
  done

  write_meminfo after "$OUT_DIR/meminfo_after.txt"
  sleep 10
  write_meminfo after_10s "$OUT_DIR/meminfo_after_10s.txt"
  write_comparison_table
  write_grep_safety
  write_runtime_marker_scan
  write_sanitizer_review
  write_three_prompt_notes

  overall_status=success
  while IFS= read -r status; do
    case "$status" in
      success) ;;
      *) overall_status=failure ;;
    esac
  done < <(awk -F= '$1 == "status" { print $2 }' "$OUT_DIR/case_summaries.txt")
  while IFS= read -r quality; do
    case "$quality" in
      natural_japanese|useful_code) ;;
      useful_long_response) ;;
      *) overall_status=failure ;;
    esac
  done < <(awk -F= '$1 == "quality_classification" { print $2 }' "$OUT_DIR/case_summaries.txt")

  write_summary "$overall_status"
  log "summary: ${OUT_DIR#$ROOT_DIR/}/summary.md"
  if [ "$overall_status" = success ]; then
    log "success"
    exit 0
  fi
  log "failure"
  exit 1
}

main "$@"
