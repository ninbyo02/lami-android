#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LITERT_LM_ROOT="${LITERT_LM_ROOT:-/home/sato/project/litert-custom-build/LiteRT-LM}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
APP_ID="io.github.ninbyo02.lami.customnpu"
ACTIVITY="io.github.ninbyo02.lami.ui.screens.home.Qairt244ShortMultitokenSmokeActivity"
PROMPT="Hi"
MAX_OUTPUT_TOKENS=3
TIMEOUT_SECONDS=30
MARKER="qairt244_short_multitoken_smoke_v1"
MODEL_PATH="/data/user/0/$APP_ID/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm"
RUN_REQUESTED=false
CUSTOM_BUILD_ARTIFACT=""

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
  scripts/run_qairt244_short_multitoken_smoke.sh [--run --artifact <custom-build-artifact>] [--model-path <device-path>] [--timeout <seconds>]

Default mode is preflight-only. It does not build, install, launch the app,
create Conversation, call high-level generateResponse, or generate tokens.

Execution is blocked unless static evidence proves a customBuildExperimentDebug
lower-level path with qairt244_short_multitoken_smoke_v1 and
DecodeConfig.SetMaxOutputTokens(3).
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
OUT_DIR="$ROOT_DIR/artifacts/qairt244_short_multitoken_smoke/$TIMESTAMP"
mkdir -p "$OUT_DIR"

log() {
  printf '[qairt244-short-multitoken-smoke] %s\n' "$*"
}

write_config() {
  {
    printf 'app_id=%s\n' "$APP_ID"
    printf 'activity=%s\n' "$ACTIVITY"
    printf 'prompt=%s\n' "$PROMPT"
    printf 'max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
    printf 'timeout_seconds=%s\n' "$TIMEOUT_SECONDS"
    printf 'marker=%s\n' "$MARKER"
    printf 'mode=%s\n' "$([ "$RUN_REQUESTED" = true ] && printf execution-requested || printf static-preflight)"
    printf 'custom_build_artifact=%s\n' "${CUSTOM_BUILD_ARTIFACT:-none}"
    printf 'model_path=%s\n' "$MODEL_PATH"
    printf 'normal_ui_connected=no\n'
    printf 'selected_path_npu_normal_route=no\n'
    printf 'high_level_generate_response=no\n'
    printf 'streaming_generation=no\n'
  } >"$OUT_DIR/preflight_config.txt"
}

collect_static_hits() {
  if command -v rg >/dev/null 2>&1; then
    rg -n \
      "$MARKER|Qairt244ShortMultitokenSmoke|runShortMultitokenSmoke|qairt244_short_multitoken_smoke_result" \
      app/src/customBuildExperimentDebug \
      >"$OUT_DIR/lami_short_multitoken_static_hits.txt" 2>/dev/null || true
  else
    printf 'rg unavailable\n' >"$OUT_DIR/lami_short_multitoken_static_hits.txt"
  fi

  if command -v rg >/dev/null 2>&1 && [ -d "$LITERT_LM_ROOT" ]; then
    rg -n \
      "$MARKER|SetMaxOutputTokens\\(3\\)|SetMaxOutputTokens\\($MAX_OUTPUT_TOKENS\\)|RunPrefill|RunDecode\\(" \
      "$LITERT_LM_ROOT/kotlin" "$LITERT_LM_ROOT/runtime" \
      >"$OUT_DIR/litert_lm_short_multitoken_static_hits.txt" 2>/dev/null || true
  else
    printf 'rg or LiteRT-LM root unavailable\n' >"$OUT_DIR/litert_lm_short_multitoken_static_hits.txt"
  fi

  if [ -n "$CUSTOM_BUILD_ARTIFACT" ] && [ -d "$CUSTOM_BUILD_ARTIFACT" ]; then
    {
      strings "$CUSTOM_BUILD_ARTIFACT/built_libs/liblitertlm_jni.so" 2>/dev/null | grep -E "$MARKER|qairt244|max_output|token" || true
      if [ -f "$CUSTOM_BUILD_ARTIFACT/metadata/litertlm_external_diff.patch" ]; then
        grep -E "$MARKER|SetMaxOutputTokens\\(3\\)|SetMaxOutputTokens\\($MAX_OUTPUT_TOKENS\\)" \
          "$CUSTOM_BUILD_ARTIFACT/metadata/litertlm_external_diff.patch" || true
      fi
    } >"$OUT_DIR/custom_artifact_short_multitoken_static_hits.txt"
  else
    printf 'custom artifact not supplied\n' >"$OUT_DIR/custom_artifact_short_multitoken_static_hits.txt"
  fi
}

write_safety_checks() {
  local lami_wrapper_present=false
  local native_cap_present=false
  local artifact_present=false
  local artifact_marker_present=false
  local run_allowed=false

  grep -q "$MARKER" "$OUT_DIR/lami_short_multitoken_static_hits.txt" && lami_wrapper_present=true
  if grep -q 'SetMaxOutputTokens(3)' "$OUT_DIR/litert_lm_short_multitoken_static_hits.txt" ||
    grep -q 'SetMaxOutputTokens(3)' "$OUT_DIR/custom_artifact_short_multitoken_static_hits.txt"; then
    native_cap_present=true
  fi
  if [ -n "$CUSTOM_BUILD_ARTIFACT" ] && [ -d "$CUSTOM_BUILD_ARTIFACT" ]; then
    artifact_present=true
  fi
  grep -q "$MARKER" "$OUT_DIR/custom_artifact_short_multitoken_static_hits.txt" && artifact_marker_present=true

  if [ "$RUN_REQUESTED" = true ] &&
    [ "$lami_wrapper_present" = true ] &&
    [ "$native_cap_present" = true ] &&
    [ "$artifact_present" = true ] &&
    [ "$artifact_marker_present" = true ]; then
    run_allowed=true
  fi

  {
    printf 'check\tstatus\tdetail\n'
    printf 'customBuildExperimentDebug_only\tpass\tTarget app id is %s.\n' "$APP_ID"
    printf 'normal_ui_disconnected\tpass\tNo ChatScreen route or selectedPath=npu normal path is touched.\n'
    printf 'prompt_fixed_short\tpass\tPrompt is fixed to %s.\n' "$PROMPT"
    printf 'max_output_tokens_le_3\tpass\tRequested hard cap is %s.\n' "$MAX_OUTPUT_TOKENS"
    printf 'timeout_configured\tpass\tTimeout is %s seconds.\n' "$TIMEOUT_SECONDS"
    printf 'lami_wrapper_present\t%s\tcustomBuildExperimentDebug wrapper marker %s.\n' "$([ "$lami_wrapper_present" = true ] && printf pass || printf fail)" "$MARKER"
    printf 'native_set_max_output_tokens_3_static\t%s\tExecution requires SetMaxOutputTokens(3) static evidence in external source or artifact metadata.\n' "$([ "$native_cap_present" = true ] && printf pass || printf blocked_preflight)"
    printf 'custom_build_artifact_present\t%s\tExecution requires a rebuilt custom stack artifact.\n' "$([ "$artifact_present" = true ] && printf pass || printf blocked_preflight)"
    printf 'artifact_marker_present\t%s\tExecution requires %s inside artifact metadata/native strings.\n' "$([ "$artifact_marker_present" = true ] && printf pass || printf blocked_preflight)" "$MARKER"
    printf 'explicit_run_requested\t%s\tExecution requires --run.\n' "$([ "$RUN_REQUESTED" = true ] && printf pass || printf blocked_preflight)"
    printf 'conversation_created\tpass_not_run\tNo Conversation is created by preflight.\n'
    printf 'high_level_generate_response\tpass_not_run\tNo high-level generateResponse is called by preflight.\n'
  } >"$OUT_DIR/safety_checks.tsv"

  if [ "$run_allowed" = true ]; then
    printf 'classification=execution-ready\nexecuted=pending\n' >"$OUT_DIR/result.txt"
  elif [ "$RUN_REQUESTED" = true ]; then
    cat >"$OUT_DIR/result.txt" <<EOF
classification=execution-request-blocked
executed=false
reason=maxOutputTokens=3 static guarantee is not complete.
EOF
  else
    cat >"$OUT_DIR/result.txt" <<EOF
classification=preflight-blocked-native-artifact-required
executed=false
reason=A custom LiteRT-LM native rebuild with $MARKER and DecodeConfig.SetMaxOutputTokens(3) is required before one allowed run.
EOF
  fi
}

execute_once() {
  local run_id
  run_id="$(date +%s%3N)"
  mkdir -p "$OUT_DIR/run"

  bash scripts/stage_litert_custom_build_stack_for_experiment.sh "$CUSTOM_BUILD_ARTIFACT" >"$OUT_DIR/run/stage_custom_build.log" 2>&1
  ./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/run/assemble.log" 2>&1
  adb install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk >"$OUT_DIR/run/install.log" 2>&1
  adb logcat -c >/dev/null 2>&1 || true
  adb shell run-as "$APP_ID" rm -f "files/qairt244_short_multitoken_smoke_result.txt" "files/qairt244_native_diag.txt" >/dev/null 2>&1 || true

  adb shell am start -n "$APP_ID/$ACTIVITY" \
    --ez runShortMultitokenSmoke true \
    --es model_path "$MODEL_PATH" \
    --es run_id "$run_id" >"$OUT_DIR/run/am_start.txt" 2>&1 || true

  local waited=0
  while [ "$waited" -lt "$TIMEOUT_SECONDS" ]; do
    if adb shell run-as "$APP_ID" test -f "files/qairt244_short_multitoken_smoke_result.txt" >/dev/null 2>&1; then
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

  adb shell run-as "$APP_ID" cat "files/qairt244_short_multitoken_smoke_result.txt" >"$OUT_DIR/run/result.txt" 2>"$OUT_DIR/run/result.pull.err" || true
  adb shell run-as "$APP_ID" cat "files/qairt244_native_diag.txt" >"$OUT_DIR/run/native_diag.txt" 2>"$OUT_DIR/run/native_diag.pull.err" || true
  adb logcat -d -t 500 >"$OUT_DIR/run/logcat_tail.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/result.txt" "$OUT_DIR/result_file.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/native_diag.txt" "$OUT_DIR/native_diag.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/logcat_tail.txt" "$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
}

write_summary() {
  local classification
  classification="$(grep -m1 '^classification=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
  cat >"$OUT_DIR/summary.md" <<EOF
# QAIRT 2.44 Short Multi-Token Smoke Preflight

Artifact: \`$OUT_DIR\`

## Outcome

\`\`\`text
classification=$classification
executed=$(grep -m1 '^executed=' "$OUT_DIR/result.txt" | cut -d= -f2-)
prompt=$PROMPT
max_output_tokens=$MAX_OUTPUT_TOKENS
marker=$MARKER
custom_build_artifact=${CUSTOM_BUILD_ARTIFACT:-none}
\`\`\`

This artifact is preflight-first. It does not connect NPU to the normal UI,
does not call high-level \`generateResponse\`, and does not run generation
unless \`--run\` is supplied and static evidence proves \`SetMaxOutputTokens(3)\`.

Required next build input:

\`\`\`text
custom LiteRT-LM JNI artifact containing:
- $MARKER
- DecodeConfig.SetMaxOutputTokens(3)
\`\`\`
EOF
}

write_config
collect_static_hits
write_safety_checks

if grep -q '^classification=execution-ready$' "$OUT_DIR/result.txt"; then
  log "running one short multi-token smoke"
  execute_once
else
  log "blocked: short multi-token smoke not executed"
fi

write_summary
log "artifact: $OUT_DIR"
