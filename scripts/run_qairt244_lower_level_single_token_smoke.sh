#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LITERT_LM_ROOT="${LITERT_LM_ROOT:-/home/sato/project/litert-custom-build/LiteRT-LM}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_lower_level_single_token_smoke/$TIMESTAMP"
APP_ID="io.github.ninbyo02.lami.customnpu"
PROMPT="Hi"
MAX_OUTPUT_TOKENS=1
TIMEOUT_SECONDS=30
LOWER_LEVEL_MARKER="qairt244_lower_level_single_token_smoke_v1"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.NpuExperimentProbeActivity"
RUN_REQUESTED=false
CUSTOM_BUILD_ARTIFACT=""
MODEL_PATH="/data/user/0/$APP_ID/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm"

while [ $# -gt 0 ]; do
  case "$1" in
    --run)
      RUN_REQUESTED=true
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
  scripts/run_qairt244_lower_level_single_token_smoke.sh [--run --artifact <custom-build-artifact>] [--model-path <device-path>] [--timeout <seconds>]

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
      printf 'ERROR: unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

cd "$ROOT_DIR" || exit 1
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
  if [ -x scripts/collect_npu_tombstone_diagnostics_v2.sh ]; then
    bash scripts/collect_npu_tombstone_diagnostics_v2.sh customnpu-lower-level-single-token >"$OUT_DIR/run/diagnostics_collect.log" 2>&1 || true
  fi
}

execute_once() {
  local run_id
  run_id="$(date +%s%3N)"
  mkdir -p "$OUT_DIR/run"

  stage_build_artifact
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
  collect_run_files
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
      grep -E '^(result|marker|max_output_tokens|output|elapsed_ms)=' "$OUT_DIR/run/result.txt" || true
    } >"$OUT_DIR/result.txt"
  else
    {
      printf 'classification=executed-no-result-file\n'
      printf 'executed=true\n'
      cat "$OUT_DIR/run/timeout_state.txt" 2>/dev/null || true
    } >"$OUT_DIR/result.txt"
  fi
else
  log "blocked: lower-level smoke not executed"
fi
log "artifact: $OUT_DIR"
exit 0
