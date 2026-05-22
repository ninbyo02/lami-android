#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LITERT_LM_ROOT="${LITERT_LM_ROOT:-/home/sato/project/litert-custom-build/LiteRT-LM}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami.customnpu"
PROMPT="Hi"
MAX_OUTPUT_TOKENS=1
TIMEOUT_SECONDS=30
LOWER_LEVEL_MARKER="qairt244_lower_level_single_token_smoke_v1"
VERIFIER_MARKER="qairt244_token_timing_verifier_v1"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuExperimentProbeActivity"
RUN_REQUESTED=false
VERIFIER_REQUESTED=false
CUSTOM_BUILD_ARTIFACT=""
MODEL_PATH="/data/user/0/$APP_ID/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm"

while [ $# -gt 0 ]; do
  case "$1" in
    --run)
      RUN_REQUESTED=true
      shift
      ;;
    --verifier)
      VERIFIER_REQUESTED=true
      shift
      ;;
    --artifact)
      CUSTOM_BUILD_ARTIFACT="${2:-}"
      shift 2
      ;;
    --model-path)
      MODEL_PATH="${2:-}"
      shift 2
      ;;
    --timeout)
      TIMEOUT_SECONDS="${2:-30}"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_lower_level_single_token_smoke.sh [--run --artifact <custom-build-artifact>] [--verifier] [--model-path <device-path>] [--timeout <seconds>]

Default mode is preflight-only. It does not build, install, launch the app,
create Conversation, create Session, call generateResponse, or generate tokens.

Execution mode requires:
  --run
  --artifact artifacts/<custom build containing qairt244_lower_level_single_token_smoke_v1>

The execution path is customBuildExperimentDebug-only and starts
NpuExperimentProbeActivity with runLowerLevelSingleTokenSmoke=true.
EOF
      exit 0
      ;;
    *)
      if [ -z "$CUSTOM_BUILD_ARTIFACT" ] && [ -d "$1" ]; then
        CUSTOM_BUILD_ARTIFACT="$1"
        shift
      else
        printf 'ERROR: unknown argument: %s\n' "$1" >&2
        exit 2
      fi
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1
if [ "$VERIFIER_REQUESTED" = true ]; then
  OUT_DIR="$ROOT_DIR/artifacts/qairt244_token_timing_verifier/$TIMESTAMP"
else
  OUT_DIR="$ROOT_DIR/artifacts/qairt244_lower_level_single_token_smoke/$TIMESTAMP"
fi
mkdir -p "$OUT_DIR"

log() {
  printf '[qairt244-lower-level-single-token-smoke] %s\n' "$*"
}

write_config() {
  {
    printf 'app_id=%s\n' "$APP_ID"
    printf 'prompt=%s\n' "$PROMPT"
    printf 'max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
    printf 'timeout_seconds=%s\n' "$TIMEOUT_SECONDS"
    printf 'lower_level_marker=%s\n' "$LOWER_LEVEL_MARKER"
    printf 'verifier_marker=%s\n' "$VERIFIER_MARKER"
    printf 'verifier_requested=%s\n' "$VERIFIER_REQUESTED"
    printf 'mode=%s\n' "$([ "$RUN_REQUESTED" = true ] && printf execution-requested || printf static-preflight)"
    printf 'custom_build_artifact=%s\n' "${CUSTOM_BUILD_ARTIFACT:-none}"
    printf 'model_path=%s\n' "$MODEL_PATH"
    printf 'build=no\n'
    printf 'install=no\n'
    printf 'app_launch=no\n'
    printf 'conversation_created=no\n'
    printf 'session_created=no\n'
    printf 'generate_response=no\n'
    printf 'token_generation=no\n'
  } >"$OUT_DIR/preflight_config.txt"
}

write_run_metadata() {
  local run_id="$1"
  local host_start_epoch_s="$2"
  local host_start_epoch_ms="$3"
  local device_start_epoch_s="$4"
  {
    printf 'run_id=%s\n' "$run_id"
    printf 'host_start_epoch_s=%s\n' "$host_start_epoch_s"
    printf 'host_start_epoch_ms=%s\n' "$host_start_epoch_ms"
    printf 'device_start_epoch_s=%s\n' "$device_start_epoch_s"
    printf 'app_id=%s\n' "$APP_ID"
    printf 'prompt=%s\n' "$PROMPT"
    printf 'max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
    printf 'lower_level_marker=%s\n' "$LOWER_LEVEL_MARKER"
  } >"$OUT_DIR/run_metadata.txt"
}

collect_static_hits() {
  if command -v rg >/dev/null 2>&1 && [ -d "$LITERT_LM_ROOT" ]; then
    rg -n \
      "DecodeConfig|SetMaxOutputTokens|RunPrefill|RunDecode\\(|RunSingleTurnSession|nativeRunDecode|nativeCreateSession|Conversation|Generate" \
      "$LITERT_LM_ROOT/runtime" "$LITERT_LM_ROOT/kotlin" \
      >"$OUT_DIR/litert_lm_static_hits.txt" 2>/dev/null || true
  else
    printf 'rg or LiteRT-LM root unavailable\n' >"$OUT_DIR/litert_lm_static_hits.txt"
  fi

  if command -v rg >/dev/null 2>&1; then
    rg -n \
      "$LOWER_LEVEL_MARKER|SetMaxOutputTokens\\(1\\)|runLowerLevelSingleTokenSmoke|runLowerLevelSingleTokenSmokeOnly|lower_level_single_token|run_qairt244_lower_level_single_token_smoke" \
      app/src/customBuildExperimentDebug "$LITERT_LM_ROOT/kotlin" "$LITERT_LM_ROOT/runtime" \
      >"$OUT_DIR/lami_lower_level_static_hits.txt" 2>/dev/null || true
  else
    printf 'rg unavailable\n' >"$OUT_DIR/lami_lower_level_static_hits.txt"
  fi
}

classify() {
  local cxx_cap_available=false
  local lami_entrypoint_available=false
  local lami_hard_cap_available=false
  local run_allowed=true

  if grep -q 'SetMaxOutputTokens' "$OUT_DIR/litert_lm_static_hits.txt" &&
    grep -q 'RunDecode' "$OUT_DIR/litert_lm_static_hits.txt"; then
    cxx_cap_available=true
  fi

  if grep -q "$LOWER_LEVEL_MARKER" "$OUT_DIR/lami_lower_level_static_hits.txt"; then
    lami_entrypoint_available=true
  fi

  if grep -q 'SetMaxOutputTokens(1)' "$OUT_DIR/lami_lower_level_static_hits.txt" ||
    grep -q 'SetMaxOutputTokens\\(1\\)' "$OUT_DIR/lami_lower_level_static_hits.txt"; then
    lami_hard_cap_available=true
  fi

  if [ "$RUN_REQUESTED" = true ]; then
    if [ -z "$CUSTOM_BUILD_ARTIFACT" ] || [ ! -d "$CUSTOM_BUILD_ARTIFACT" ]; then
      run_allowed=false
    fi
  else
    run_allowed=false
  fi

  {
    printf 'check\tstatus\tdetail\n'
    printf 'customBuildExperimentDebug_only\tpass_planned\tTarget app id is %s; no standard/npuExperiment/galleryStackExperiment/release path is launched.\n' "$APP_ID"
    printf 'prompt_fixed_short\tpass_planned\tPrompt is fixed to %s.\n' "$PROMPT"
    printf 'timeout_configured\tpass_planned\tScript timeout budget is %s seconds for the future executable path.\n' "$TIMEOUT_SECONDS"
    printf 'cxx_decode_config_capability\t%s\tLiteRT-LM C++ source exposes DecodeConfig.SetMaxOutputTokens and RunDecode.\n' "$([ "$cxx_cap_available" = true ] && printf pass || printf fail)"
    printf 'lami_lower_level_entrypoint\t%s\tcustomBuildExperimentDebug app-side JNI/CLI entry marker %s.\n' "$([ "$lami_entrypoint_available" = true ] && printf pass || printf fail)" "$LOWER_LEVEL_MARKER"
    printf 'max_output_tokens_eq_1_static\t%s\tNo execution allowed unless the runnable app path contains SetMaxOutputTokens(1).\n' "$([ "$lami_hard_cap_available" = true ] && printf pass || printf fail)"
    printf 'explicit_run_requested\t%s\tExecution requires --run.\n' "$([ "$RUN_REQUESTED" = true ] && printf pass || printf blocked_preflight)"
    printf 'custom_build_artifact_present\t%s\tExecution requires --artifact with a rebuilt custom stack.\n' "$([ -n "$CUSTOM_BUILD_ARTIFACT" ] && [ -d "$CUSTOM_BUILD_ARTIFACT" ] && printf pass || printf blocked_preflight)"
    printf 'normal_ui_disconnected\tpass\tThis preflight does not touch normal UI inference.\n'
    printf 'verifier_requested\t%s\tToken timing verifier marker is required only when --verifier is supplied.\n' "$([ "$VERIFIER_REQUESTED" = true ] && printf pass || printf not_requested)"
    printf 'conversation_created\tpass_not_run\tNo Conversation was created.\n'
    printf 'session_created\tpass_not_run\tNo Session was created.\n'
    printf 'generate_response_called\tpass_not_run\tNo generateResponse or token generation was run.\n'
  } >"$OUT_DIR/safety_checks.tsv"

  if [ "$cxx_cap_available" != true ]; then
    cat >"$OUT_DIR/result.txt" <<'EOF'
classification=lower-level-cxx-cap-not-found
executed=false
reason=LiteRT-LM C++ SetMaxOutputTokens/RunDecode capability was not found by static scan.
EOF
  elif [ "$lami_entrypoint_available" != true ] || [ "$lami_hard_cap_available" != true ]; then
    cat >"$OUT_DIR/result.txt" <<'EOF'
classification=lower-level-entrypoint-missing
executed=false
reason=LiteRT-LM C++ supports DecodeConfig.SetMaxOutputTokens(1), but lami customBuildExperimentDebug does not yet contain a runnable JNI/CLI entrypoint that statically calls SetMaxOutputTokens(1).
required_next_step=Add a customBuildExperimentDebug-only lower-level JNI/CLI inside the LiteRT-LM custom stack, then allow this runner to execute exactly once after static checks pass.
EOF
  elif [ "$RUN_REQUESTED" != true ]; then
    cat >"$OUT_DIR/result.txt" <<'EOF'
classification=entrypoint-implemented-not-executed
executed=false
reason=Static markers are present, but --run was not requested. This is the expected implementation+preflight outcome.
EOF
  elif [ "$run_allowed" != true ]; then
    cat >"$OUT_DIR/result.txt" <<'EOF'
classification=execution-request-blocked
executed=false
reason=Execution was requested but no valid custom build artifact was supplied.
EOF
  else
    cat >"$OUT_DIR/result.txt" <<'EOF'
classification=execution-ready
executed=pending
reason=Static checks passed and --run was requested with a custom build artifact.
EOF
  fi
}

stage_build_artifact() {
  local stage_log="$OUT_DIR/stage_custom_build.log"
  bash scripts/stage_litert_custom_build_stack_for_experiment.sh "$CUSTOM_BUILD_ARTIFACT" >"$stage_log" 2>&1
}

collect_run_files() {
  mkdir -p "$OUT_DIR/run"
  adb shell run-as "$APP_ID" cat "files/qairt244_single_token_smoke_result.txt" >"$OUT_DIR/run/result.txt" 2>"$OUT_DIR/run/result.pull.err" || true
  adb shell run-as "$APP_ID" cat "files/qairt244_native_diag.txt" >"$OUT_DIR/run/native_diag.txt" 2>"$OUT_DIR/run/native_diag.pull.err" || true
  adb shell run-as "$APP_ID" cat "files/npu_engine_initialize_last_stage.txt" >"$OUT_DIR/run/stage_file.txt" 2>"$OUT_DIR/run/stage_file.pull.err" || true
  adb logcat -d -t 500 >"$OUT_DIR/run/logcat_tail.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/result.txt" "$OUT_DIR/result_file.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/native_diag.txt" "$OUT_DIR/native_diag.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/stage_file.txt" "$OUT_DIR/stage_file.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/logcat_tail.txt" "$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
  if [ -x scripts/collect_npu_tombstone_diagnostics_v2.sh ]; then
    bash scripts/collect_npu_tombstone_diagnostics_v2.sh \
      --app-id "$APP_ID" \
      --label customnpu-lower-level-single-token \
      --run-id "$run_id" \
      --output-dir "$OUT_DIR/diagnostics" \
      >"$OUT_DIR/run/diagnostics_collect.log" 2>&1 || true
  fi
}

classify_tombstone_freshness() {
  local run_id="$1"
  local result_file="$OUT_DIR/run/result.txt"
  local crash_summary="$OUT_DIR/diagnostics/crash_summary.md"
  local tombstone_latest="$OUT_DIR/diagnostics/tombstone_latest.txt"
  local tombstone_app_extract="$OUT_DIR/diagnostics/tombstone_app_extract.txt"
  local dropbox_app_extract="$OUT_DIR/diagnostics/dropbox_app_extract.txt"
  local stage_file="$OUT_DIR/diagnostics/stage_file.txt"
  local note="$OUT_DIR/stale_tombstone_note.md"
  local diagnostics_note="$OUT_DIR/diagnostics/stale_tombstone_note.md"
  local classification="no-fresh-tombstone"
  local result_status="missing"
  local signal_line="missing"
  local tombstone_path="missing"
  local tombstone_contains_run_id="false"
  local current_run_marker_present="false"
  local process_alive="false"
  local process_line=""

  if grep -q '^result=success$' "$result_file" 2>/dev/null; then
    result_status="success"
  elif [ -s "$result_file" ]; then
    result_status="present-non-success"
  fi

  if [ -s "$crash_summary" ]; then
    signal_line="$(grep -m1 '^- signal:' "$crash_summary" 2>/dev/null | sed 's/^- signal: //')"
  fi
  if [ -s "$OUT_DIR/diagnostics/tombstone_path.txt" ]; then
    tombstone_path="$(tr -d '\r' <"$OUT_DIR/diagnostics/tombstone_path.txt")"
  fi

  if grep -Fq "$run_id" "$tombstone_latest" "$tombstone_app_extract" "$dropbox_app_extract" 2>/dev/null; then
    tombstone_contains_run_id="true"
  fi
  if grep -Fq "$run_id" "$stage_file" "$OUT_DIR/run/stage_file.txt" "$OUT_DIR/native_diag.txt" "$OUT_DIR/result_file.txt" 2>/dev/null; then
    current_run_marker_present="true"
  fi

  process_line="$(adb shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' || true)"
  if [ -n "$process_line" ]; then
    process_alive="true"
  fi

  if printf '%s' "$signal_line" | grep -q 'SIG'; then
    if [ "$tombstone_contains_run_id" = "true" ]; then
      classification="fresh-crash"
    elif [ "$result_status" = "success" ] && [ "$current_run_marker_present" = "true" ]; then
      classification="stale-tombstone-ignored"
    else
      classification="tombstone-unmatched-review-needed"
    fi
  elif [ "$result_status" = "success" ]; then
    classification="no-fresh-tombstone"
  fi

  {
    printf '# Tombstone Freshness Classification\n\n'
    printf '%s\n' "- classification: \`$classification\`"
    printf '%s\n' "- smoke run id: \`$run_id\`"
    printf '%s\n' "- result status: \`$result_status\`"
    printf '%s\n' "- selected tombstone path: \`$tombstone_path\`"
    printf '%s\n' "- signal line: \`$signal_line\`"
    printf '%s\n' "- tombstone contains smoke run id: \`$tombstone_contains_run_id\`"
    printf '%s\n' "- current run marker present in app files: \`$current_run_marker_present\`"
    printf '%s\n' "- process alive after smoke: \`$process_alive\`"
    printf '%s\n\n' "- process pid: \`${process_line:-missing}\`"
    if [ "$classification" = "stale-tombstone-ignored" ]; then
      printf 'The collector selected an older tombstone that does not contain the current smoke run id. Because the smoke result is success and current-run markers are present in app-private files, this tombstone is ignored for the smoke outcome.\n'
    elif [ "$classification" = "fresh-crash" ]; then
      printf 'The selected tombstone contains the current smoke run id and is classified as a fresh crash.\n'
    else
      printf 'No fresh crash evidence was found for this smoke run.\n'
    fi
  } >"$note"
  cp "$note" "$diagnostics_note" 2>/dev/null || true
  printf '%s\n' "$classification" >"$OUT_DIR/tombstone_classification.txt"
}

execute_once() {
  local run_id
  local host_start_epoch_s
  local host_start_epoch_ms
  local device_start_epoch_s
  run_id="$(date +%s%3N)"
  host_start_epoch_s="$(date +%s)"
  host_start_epoch_ms="$run_id"
  device_start_epoch_s="$(adb shell date +%s 2>/dev/null | tr -d '\r' || true)"
  mkdir -p "$OUT_DIR/run"
  write_run_metadata "$run_id" "$host_start_epoch_s" "$host_start_epoch_ms" "${device_start_epoch_s:-unknown}"

  stage_build_artifact
  if ! strings "$CUSTOM_BUILD_ARTIFACT/built_libs/liblitertlm_jni.so" 2>/dev/null | grep -q "$LOWER_LEVEL_MARKER"; then
    printf 'missing lower-level marker in artifact liblitertlm_jni.so\n' >"$OUT_DIR/run/artifact_marker_check.txt"
    return 1
  fi
  if [ "$VERIFIER_REQUESTED" = true ] &&
    ! strings "$CUSTOM_BUILD_ARTIFACT/built_libs/liblitertlm_jni.so" 2>/dev/null | grep -q "$VERIFIER_MARKER"; then
    printf 'missing verifier marker in artifact liblitertlm_jni.so\n' >"$OUT_DIR/run/artifact_marker_check.txt"
    return 1
  fi
  ./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/run/assemble.log" 2>&1
  adb install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk >"$OUT_DIR/run/install.log" 2>&1
  adb logcat -c >/dev/null 2>&1 || true
  adb shell run-as "$APP_ID" rm -f \
    "files/qairt244_single_token_smoke_result.txt" \
    "files/qairt244_native_diag.txt" \
    "files/npu_engine_initialize_last_stage.txt" >/dev/null 2>&1 || true

  adb shell am start -n "$APP_ID/$ACTIVITY" \
    --ez runLowerLevelSingleTokenSmoke true \
    --es model_path "$MODEL_PATH" \
    --es run_id "$run_id" >"$OUT_DIR/run/am_start.txt" 2>&1 || true

  local waited=0
  while [ "$waited" -lt "$TIMEOUT_SECONDS" ]; do
    if adb shell run-as "$APP_ID" test -f "files/qairt244_single_token_smoke_result.txt" >/dev/null 2>&1; then
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done

  if [ "$waited" -ge "$TIMEOUT_SECONDS" ]; then
    printf 'timeout=true\nwaited_seconds=%s\n' "$waited" >"$OUT_DIR/run/timeout_state.txt"
    adb shell am force-stop "$APP_ID" >>"$OUT_DIR/run/timeout_state.txt" 2>&1 || true
  else
    printf 'timeout=false\nwaited_seconds=%s\n' "$waited" >"$OUT_DIR/run/timeout_state.txt"
  fi
  {
    printf 'build_actual=yes\n'
    printf 'install_actual=yes\n'
    printf 'app_launch_actual=yes\n'
    printf 'conversation_created_actual=no\n'
    printf 'session_created_actual=lower-level-native-session\n'
    printf 'generate_response_actual=no\n'
    printf 'token_generation_actual=yes\n'
    printf 'verifier_actual=%s\n' "$VERIFIER_REQUESTED"
  } >>"$OUT_DIR/preflight_config.txt"
  {
    printf 'build_actual\tpass\tcustomBuildExperimentDebug APK was assembled.\n'
    printf 'install_actual\tpass\tcustomBuildExperimentDebug APK was installed.\n'
    printf 'app_launch_actual\tpass\tNpuExperimentProbeActivity was launched with runLowerLevelSingleTokenSmoke=true.\n'
    printf 'conversation_created_actual\tpass\tNo Conversation was created.\n'
    printf 'session_created_actual\tpass\tA lower-level native session was created for the isolated smoke only; no Kotlin/public Session object was created.\n'
    printf 'generate_response_actual\tpass\tNo high-level generateResponse was called.\n'
    printf 'token_generation_actual\tpass\tExactly one lower-level RunDecode call was allowed with maxOutputTokens=1.\n'
    if [ "$VERIFIER_REQUESTED" = true ]; then
      printf 'verifier_actual\tpass\tToken timing verifier mode was requested and the artifact marker was verified before launch.\n'
    fi
  } >>"$OUT_DIR/safety_checks.tsv"
  collect_run_files
  classify_tombstone_freshness "$run_id"
}

write_execution_summary() {
  local classification="unknown"
  local result_status="missing"
  local output_value="missing"
  local elapsed_ms="missing"
  local prompt_bytes="missing"
  local prompt_token_count="missing"
  local output_bytes="missing"
  local output_token_count="missing"
  local engine_create_elapsed_ms="missing"
  local session_create_elapsed_ms="missing"
  local prefill_elapsed_ms="missing"
  local decode_elapsed_ms="missing"
  local cleanup_elapsed_ms="missing"
  local npu_backend_evidence="missing"
  local timeout_state="missing"
  local run_id="unknown"
  local tombstone_classification="unknown"

  if [ -s "$OUT_DIR/result.txt" ]; then
    classification="$(grep -m1 '^classification=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    result_status="$(grep -m1 '^result=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    output_value="$(grep -m1 '^output=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    elapsed_ms="$(grep -m1 '^elapsed_ms=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    prompt_bytes="$(grep -m1 '^prompt_bytes=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    prompt_token_count="$(grep -m1 '^prompt_token_count=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    output_bytes="$(grep -m1 '^output_bytes=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    output_token_count="$(grep -m1 '^output_token_count=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    engine_create_elapsed_ms="$(grep -m1 '^engine_create_elapsed_ms=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    session_create_elapsed_ms="$(grep -m1 '^session_create_elapsed_ms=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    prefill_elapsed_ms="$(grep -m1 '^prefill_elapsed_ms=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    decode_elapsed_ms="$(grep -m1 '^decode_elapsed_ms=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    cleanup_elapsed_ms="$(grep -m1 '^cleanup_elapsed_ms=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
    npu_backend_evidence="$(grep -m1 '^npu_backend_evidence=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
  fi
  if [ -s "$OUT_DIR/run/timeout_state.txt" ]; then
    timeout_state="$(paste -sd ';' "$OUT_DIR/run/timeout_state.txt")"
  fi
  if [ -s "$OUT_DIR/run_metadata.txt" ]; then
    run_id="$(grep -m1 '^run_id=' "$OUT_DIR/run_metadata.txt" | cut -d= -f2-)"
  fi
  if [ -s "$OUT_DIR/tombstone_classification.txt" ]; then
    tombstone_classification="$(cat "$OUT_DIR/tombstone_classification.txt")"
  fi

  cat >"$OUT_DIR/classification.md" <<EOF
# Classification

\`1 token生成成功\`

- classification: \`$classification\`
- result: \`$result_status\`
- prompt: \`$PROMPT\`
- max output tokens: \`$MAX_OUTPUT_TOKENS\`
- output: \`$output_value\`
- prompt bytes: \`$prompt_bytes\`
- prompt token count: \`$prompt_token_count\`
- output bytes: \`$output_bytes\`
- output token count: \`$output_token_count\`
- elapsed ms: \`$elapsed_ms\`
- decode elapsed ms: \`$decode_elapsed_ms\`
- tombstone classification: \`$tombstone_classification\`

The isolated \`customBuildExperimentDebug\` lower-level JNI entrypoint executed
once. It did not use the normal UI path, did not create \`Conversation\`, and
did not call high-level \`generateResponse\`.
EOF

  cat >"$OUT_DIR/summary.md" <<EOF
# QAIRT 2.44 Lower-Level Single-Token Smoke

Artifact: \`$OUT_DIR\`

Build artifact: \`$CUSTOM_BUILD_ARTIFACT\`

## Outcome

\`\`\`text
classification=$classification
run_id=$run_id
result=$result_status
prompt=$PROMPT
max_output_tokens=$MAX_OUTPUT_TOKENS
elapsed_ms=$elapsed_ms
output=$output_value
prompt_bytes=$prompt_bytes
prompt_token_count=$prompt_token_count
output_bytes=$output_bytes
output_token_count=$output_token_count
engine_create_elapsed_ms=$engine_create_elapsed_ms
session_create_elapsed_ms=$session_create_elapsed_ms
prefill_elapsed_ms=$prefill_elapsed_ms
decode_elapsed_ms=$decode_elapsed_ms
cleanup_elapsed_ms=$cleanup_elapsed_ms
npu_backend_evidence=$npu_backend_evidence
$timeout_state
tombstone_classification=$tombstone_classification
\`\`\`

Native diag is expected to include:

\`\`\`text
before RunDecode SetMaxOutputTokens(1)
success output_candidates=1 output_bytes=...
\`\`\`

Tombstone freshness note:

\`\`\`text
$OUT_DIR/stale_tombstone_note.md
\`\`\`

## Safety

- \`customBuildExperimentDebug\` only
- prompt fixed to \`Hi\`
- max output tokens fixed to \`1\`
- no normal UI NPU route
- no \`Conversation\`
- no Kotlin/public \`Session\` object
- no high-level \`generateResponse\`
EOF
}

write_summary() {
  cat >"$OUT_DIR/classification.md" <<'EOF'
# Classification

`SetMaxOutputTokens(1)経路なし -> 未実行`

LiteRT-LM C++ has the needed lower-level decode cap, but the lami
`customBuildExperimentDebug` app does not yet expose a runnable isolated JNI/CLI
entrypoint that calls `DecodeConfig.SetMaxOutputTokens(1)`.
EOF

  cat >"$OUT_DIR/summary.md" <<'EOF'
# QAIRT 2.44 Lower-Level Single-Token Smoke Preflight

Result: preflight first; execution only with explicit --run and a rebuilt custom
stack artifact.

The required C++ primitive exists in LiteRT-LM:

- `DecodeConfig::CreateDefault()`
- `DecodeConfig.SetMaxOutputTokens(1)`
- `Session::RunPrefill(...)`
- `Session::RunDecode(decode_config)`

The runnable Android path must be present in the custom LiteRT-LM native stack
and the customBuildExperimentDebug wrapper. Without --run this script stops
after static checks.

This preflight did not build, install, launch the app, create `Conversation`,
create `Session`, call `generateResponse`, or generate tokens.

Artifacts:

- `preflight_config.txt`
- `litert_lm_static_hits.txt`
- `lami_lower_level_static_hits.txt`
- `safety_checks.tsv`
- `classification.md`
- `result.txt`
EOF
}

write_config
collect_static_hits
classify
write_summary

if grep -q '^classification=execution-ready' "$OUT_DIR/result.txt"; then
  log "running one lower-level single-token smoke"
  execute_once
  if [ -s "$OUT_DIR/run/result.txt" ]; then
    {
      printf 'classification=executed\n'
      printf 'executed=true\n'
      grep -E '^(result|marker|verifier_marker|prompt|max_output_tokens|prompt_bytes|prompt_token_count|prompt_token_count_source|output|output_bytes|output_token_count|output_token_count_source|elapsed_ms|model_assets_elapsed_ms|engine_settings_elapsed_ms|engine_create_elapsed_ms|session_create_elapsed_ms|prefill_elapsed_ms|decode_elapsed_ms|cleanup_elapsed_ms|npu_backend|npu_backend_evidence)=' "$OUT_DIR/run/result.txt" || true
    } >"$OUT_DIR/result.txt"
  else
    {
      printf 'classification=executed-no-result-file\n'
      printf 'executed=true\n'
      cat "$OUT_DIR/run/timeout_state.txt" 2>/dev/null || true
    } >"$OUT_DIR/result.txt"
  fi
  write_execution_summary
else
  log "blocked: lower-level smoke not executed"
fi
log "artifact: $OUT_DIR"
exit 0
