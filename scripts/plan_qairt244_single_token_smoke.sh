#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LITERT_LM_ROOT="/home/sato/project/litert-custom-build/LiteRT-LM"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_single_token_smoke_plan/$TIMESTAMP"

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

{
  printf 'mode=planning/read-only\n'
  printf 'build=no\n'
  printf 'adb=no\n'
  printf 'app_launch=no\n'
  printf 'conversation_created=no\n'
  printf 'session_created=no\n'
  printf 'generation=no\n'
  printf 'root_dir=%s\n' "$ROOT_DIR"
  printf 'litert_lm_root=%s\n' "$LITERT_LM_ROOT"
} >"$OUT_DIR/planner_output.txt"

if command -v rg >/dev/null 2>&1 && [ -d "$LITERT_LM_ROOT" ]; then
  rg -n "createConversation|sendMessage|sendMessageAsync|createSession|runPrefill|runDecode|DecodeConfig|SetMaxOutputTokens|GenerateContent|GenerateContentStream" \
    "$LITERT_LM_ROOT/kotlin" "$LITERT_LM_ROOT/runtime" >"$OUT_DIR/api_surface_hits.txt" 2>/dev/null || true
  rg -n "Conversation|Session|generateResponse|sendMessage|LocalStreamingRunner|SetMaxOutputTokens" \
    app/src scripts docs >"$OUT_DIR/forbidden_marker_scan.txt" 2>/dev/null || true
else
  printf 'rg or LiteRT-LM root unavailable\n' >"$OUT_DIR/api_surface_hits.txt"
  printf 'rg or LiteRT-LM root unavailable\n' >"$OUT_DIR/forbidden_marker_scan.txt"
fi

cat >"$OUT_DIR/future_static_checks.txt" <<'EOF'
Future implementation static checks, not executed in this planning phase:

1. Smoke source must be customBuildExperimentDebug-only.
2. Smoke source must contain SetMaxOutputTokens(1) or equivalent hard cap.
3. Smoke source must not reference LocalStreamingRunner or normal chat UI.
4. Smoke source must not use unconstrained Conversation.sendMessage*.
5. APK/sourceSet check must show no standard, npuExperiment, galleryStackExperiment, or release changes.
EOF

cat >"$OUT_DIR/summary.md" <<'EOF'
# QAIRT 2.44 Single-Token Smoke Plan

This is a plan-only artifact. It does not build, install, create a
Conversation, create a Session, call generateResponse, or run inference.

## Required Safety Gates

- Run only in `customBuildExperimentDebug`.
- Keep `selectedPath` and normal UI routing unchanged.
- Require an explicit ADB extra such as `run_qairt244_single_token_smoke=true`.
- Refuse to run unless the latest initialize-only stability result is 2/2
  success with `Engine.close` success and no crash marker.
- Use a separate probe Activity or explicit branch in `NpuExperimentProbeActivity`.
- Write result files under app private storage and collect tombstone/logcat.
- Keep a short timeout and kill only the probe process if it exceeds timeout.

## API Finding

The Kotlin `Conversation.sendMessage*` surface is not the preferred first smoke
path because a hard one-token cap is not obvious from that API. The safer future
implementation should use a lower-level customBuildExperimentDebug-only native
or CLI path where decode can be capped explicitly with
`DecodeConfig.SetMaxOutputTokens(1)`.

## Proposed Future Runtime Shape

1. Create `Backend.NPU(nativeLibraryDir)`.
2. Create `EngineConfig(modelPath, Backend.NPU, null, null, maxNumTokens=1,
   maxNumImages=null, cacheDir/filesDir)`.
3. Construct `Engine`.
4. Call `Engine.initialize()`.
5. Enter a dedicated lower-level smoke path, not normal UI inference.
6. Prefill prompt `Hi`.
7. Decode with `SetMaxOutputTokens(1)`.
8. Close all native/session/engine resources in `finally`.

## Stop Conditions

- Any missing max-token control means the smoke must not run.
- Any crash, timeout, or QNN backend error stops the experiment.
- No fallback to CPU/GPU inside this probe; the goal is to classify NPU behavior.

## Non-Goals

- No normal chat UI connection.
- No long prompt.
- No streaming UI.
- No automatic fallback path mutation.
EOF

printf '[qairt244-single-token-plan] wrote %s\n' "$OUT_DIR/summary.md"
