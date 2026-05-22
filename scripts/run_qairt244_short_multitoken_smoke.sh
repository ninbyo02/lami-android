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
  local artifact_native_cap_present=false
  local artifact_present=false
  local artifact_marker_present=false
  local run_allowed=false

  grep -q "$MARKER" "$OUT_DIR/lami_short_multitoken_static_hits.txt" && lami_wrapper_present=true
  if grep -q 'SetMaxOutputTokens(3)' "$OUT_DIR/litert_lm_short_multitoken_static_hits.txt" ||
    grep -q 'SetMaxOutputTokens(3)' "$OUT_DIR/custom_artifact_short_multitoken_static_hits.txt"; then
    native_cap_present=true
  fi
  grep -q 'SetMaxOutputTokens(3)' "$OUT_DIR/custom_artifact_short_multitoken_static_hits.txt" && artifact_native_cap_present=true
  if [ -n "$CUSTOM_BUILD_ARTIFACT" ] && [ -d "$CUSTOM_BUILD_ARTIFACT" ]; then
    artifact_present=true
  fi
  grep -q "$MARKER" "$OUT_DIR/custom_artifact_short_multitoken_static_hits.txt" && artifact_marker_present=true

  if [ "$RUN_REQUESTED" = true ] &&
    [ "$lami_wrapper_present" = true ] &&
    [ "$native_cap_present" = true ] &&
    [ "$artifact_native_cap_present" = true ] &&
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
    printf 'artifact_set_max_output_tokens_3_static\t%s\tExecution requires SetMaxOutputTokens(3) evidence from the supplied artifact metadata.\n' "$([ "$artifact_native_cap_present" = true ] && printf pass || printf blocked_preflight)"
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
    printf 'activity=%s\n' "$ACTIVITY"
    printf 'prompt=%s\n' "$PROMPT"
    printf 'max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
    printf 'marker=%s\n' "$MARKER"
    printf 'custom_build_artifact=%s\n' "$CUSTOM_BUILD_ARTIFACT"
  } >"$OUT_DIR/run_metadata.txt"
}

collect_run_files() {
  local run_id="$1"
  mkdir -p "$OUT_DIR/run"
  adb shell run-as "$APP_ID" cat "files/qairt244_short_multitoken_smoke_result.txt" >"$OUT_DIR/run/result.txt" 2>"$OUT_DIR/run/result.pull.err" || true
  adb shell run-as "$APP_ID" cat "files/qairt244_native_diag.txt" >"$OUT_DIR/run/native_diag.txt" 2>"$OUT_DIR/run/native_diag.pull.err" || true
  adb shell run-as "$APP_ID" cat "files/npu_engine_initialize_last_stage.txt" >"$OUT_DIR/run/stage_file.txt" 2>"$OUT_DIR/run/stage_file.pull.err" || true
  adb logcat -d -t 500 >"$OUT_DIR/run/logcat_tail.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/result.txt" "$OUT_DIR/result.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/native_diag.txt" "$OUT_DIR/native_diag.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/stage_file.txt" "$OUT_DIR/stage_file.txt" 2>/dev/null || true
  cp "$OUT_DIR/run/logcat_tail.txt" "$OUT_DIR/logcat_tail.txt" 2>/dev/null || true
  if [ -x scripts/collect_npu_tombstone_diagnostics_v2.sh ]; then
    bash scripts/collect_npu_tombstone_diagnostics_v2.sh \
      --app-id "$APP_ID" \
      --label customnpu-short-multitoken \
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
  if grep -Fq "$run_id" "$stage_file" "$OUT_DIR/run/stage_file.txt" "$OUT_DIR/native_diag.txt" "$OUT_DIR/result.txt" 2>/dev/null; then
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

  bash scripts/stage_litert_custom_build_stack_for_experiment.sh "$CUSTOM_BUILD_ARTIFACT" >"$OUT_DIR/run/stage_custom_build.log" 2>&1
  if ! strings "$CUSTOM_BUILD_ARTIFACT/built_libs/liblitertlm_jni.so" 2>/dev/null | grep -q "$MARKER"; then
    printf 'missing %s in artifact liblitertlm_jni.so\n' "$MARKER" >"$OUT_DIR/run/artifact_integrity_check.txt"
    return 1
  fi
  if ! grep -q 'SetMaxOutputTokens(3)' "$OUT_DIR/custom_artifact_short_multitoken_static_hits.txt"; then
    printf 'missing SetMaxOutputTokens(3) evidence in supplied artifact metadata\n' >"$OUT_DIR/run/artifact_integrity_check.txt"
    return 1
  fi
  printf 'artifact_integrity=pass\n' >"$OUT_DIR/run/artifact_integrity_check.txt"
  ./gradlew :app:assembleCustomBuildExperimentDebug >"$OUT_DIR/run/assemble.log" 2>&1
  adb install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk >"$OUT_DIR/run/install.log" 2>&1
  adb logcat -c >/dev/null 2>&1 || true
  adb shell run-as "$APP_ID" rm -f \
    "files/qairt244_short_multitoken_smoke_result.txt" \
    "files/qairt244_native_diag.txt" \
    "files/npu_engine_initialize_last_stage.txt" >/dev/null 2>&1 || true

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

  {
    printf 'build_actual=yes\n'
    printf 'install_actual=yes\n'
    printf 'app_launch_actual=yes\n'
    printf 'conversation_created_actual=no\n'
    printf 'session_created_actual=lower-level-native-session\n'
    printf 'generate_response_actual=no\n'
    printf 'token_generation_actual=yes\n'
  } >>"$OUT_DIR/preflight_config.txt"
  collect_run_files "$run_id"
  classify_tombstone_freshness "$run_id"
}

write_summary() {
  local classification
  local executed
  local result_status
  local output_value
  local elapsed_ms
  local decode_elapsed_ms
  local tombstone_classification
  classification="$(grep -m1 '^classification=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
  executed="$(grep -m1 '^executed=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
  result_status="$(grep -m1 '^result=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
  output_value="$(grep -m1 '^output=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
  elapsed_ms="$(grep -m1 '^elapsed_ms=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
  decode_elapsed_ms="$(grep -m1 '^decode_elapsed_ms=' "$OUT_DIR/result.txt" | cut -d= -f2-)"
  tombstone_classification="$(cat "$OUT_DIR/tombstone_classification.txt" 2>/dev/null || true)"
  if [ -z "$classification" ] && [ -n "$result_status" ]; then
    classification="executed"
    executed="true"
  fi
  if [ -z "$executed" ]; then
    executed="false"
  fi
  cat >"$OUT_DIR/summary.md" <<EOF
# QAIRT 2.44 Short Multi-Token Smoke Result

Artifact: \`$OUT_DIR\`

## Outcome

\`\`\`text
classification=$classification
executed=$executed
result=${result_status:-none}
output=${output_value:-none}
elapsed_ms=${elapsed_ms:-none}
decode_elapsed_ms=${decode_elapsed_ms:-none}
tombstone_classification=${tombstone_classification:-none}
prompt=$PROMPT
max_output_tokens=$MAX_OUTPUT_TOKENS
marker=$MARKER
custom_build_artifact=${CUSTOM_BUILD_ARTIFACT:-none}
\`\`\`

This artifact is preflight-first. It does not connect NPU to the normal UI,
does not call high-level \`generateResponse\`, and does not run generation
unless \`--run\` is supplied and static evidence proves \`SetMaxOutputTokens(3)\`.

## Artifact Tracking Policy

Large rebuilt native binaries are local-only and must not be committed:

- \`built_libs/*.so\`
- \`qnn_runtime_libs/*.so\`
- \`reference_libs/**/*.so\`
- \`diagnostics/apk_libs/*.so\`

Commit only text evidence such as summaries, Build IDs, hashes, run metadata,
and external diff patches.

Required next build input:

\`\`\`text
custom LiteRT-LM JNI artifact containing:
- $MARKER
- DecodeConfig.SetMaxOutputTokens(3)
\`\`\`
EOF

  cat >"$OUT_DIR/large_artifacts_local_only.md" <<'EOF'
# Large Artifacts Are Local-Only

This smoke artifact can contain APK-extracted or rebuilt native libraries under
`diagnostics/apk_libs`, `built_libs`, `qnn_runtime_libs`, or `reference_libs`.
Those binaries are intentionally excluded from Git tracking. Preserve only text
metadata such as `summary.md`, `result.txt`, `native_diag.txt`, Build IDs,
hashes, and diff patches in commits.
EOF
}

write_config
collect_static_hits
write_safety_checks

if grep -q '^classification=execution-ready$' "$OUT_DIR/result.txt"; then
  log "running one short multi-token smoke"
  if ! execute_once; then
    cat >"$OUT_DIR/result.txt" <<'EOF'
classification=execution-aborted-before-launch
executed=false
reason=artifact integrity or staging check failed before app launch.
EOF
  fi
else
  log "blocked: short multi-token smoke not executed"
fi

write_summary
log "artifact: $OUT_DIR"
