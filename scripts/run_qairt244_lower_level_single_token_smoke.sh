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
    printf 'mode=static-preflight\n'
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
      "$LOWER_LEVEL_MARKER|SetMaxOutputTokens\\(1\\)|runLowerLevelSingleTokenSmoke|lower_level_single_token|run_qairt244_lower_level_single_token_smoke" \
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

  {
    printf 'check\tstatus\tdetail\n'
    printf 'customBuildExperimentDebug_only\tpass_planned\tTarget app id is %s; no standard/npuExperiment/galleryStackExperiment/release path is launched.\n' "$APP_ID"
    printf 'prompt_fixed_short\tpass_planned\tPrompt is fixed to %s.\n' "$PROMPT"
    printf 'timeout_configured\tpass_planned\tScript timeout budget is %s seconds for the future executable path.\n' "$TIMEOUT_SECONDS"
    printf 'cxx_decode_config_capability\t%s\tLiteRT-LM C++ source exposes DecodeConfig.SetMaxOutputTokens and RunDecode.\n' "$([ "$cxx_cap_available" = true ] && printf pass || printf fail)"
    printf 'lami_lower_level_entrypoint\t%s\tcustomBuildExperimentDebug app-side JNI/CLI entry marker %s.\n' "$([ "$lami_entrypoint_available" = true ] && printf pass || printf fail)" "$LOWER_LEVEL_MARKER"
    printf 'max_output_tokens_eq_1_static\t%s\tNo execution allowed unless the runnable app path contains SetMaxOutputTokens(1).\n' "$([ "$lami_hard_cap_available" = true ] && printf pass || printf fail)"
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
  else
    cat >"$OUT_DIR/result.txt" <<'EOF'
classification=preflight-pass-execution-still-disabled
executed=false
reason=Static markers were present, but this script revision intentionally stops before generation until the executable path is reviewed.
EOF
  fi
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

Result: blocked before execution.

The required C++ primitive exists in LiteRT-LM:

- `DecodeConfig::CreateDefault()`
- `DecodeConfig.SetMaxOutputTokens(1)`
- `Session::RunPrefill(...)`
- `Session::RunDecode(decode_config)`

The runnable Android path is still missing. Existing Kotlin/JNI exposes
`Session.runDecode()` through `nativeRunDecode(handle)`, which calls native
`RunDecode()` without a `DecodeConfig`. Therefore max output tokens are not
statically guaranteed from the app.

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

log "blocked: lower-level SetMaxOutputTokens(1) executable path is not wired into customBuildExperimentDebug"
log "artifact: $OUT_DIR"
exit 0
