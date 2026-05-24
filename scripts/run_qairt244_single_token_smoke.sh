#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LITERT_LM_ROOT="/home/sato/project/litert-custom-build/LiteRT-LM"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$ROOT_DIR/artifacts/qairt244_single_token_smoke/$TIMESTAMP"
APP_ID="io.github.ninbyo02.lami.customnpu"
PROMPT="Hi"
MAX_OUTPUT_TOKENS=1
TIMEOUT_SECONDS=30

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() {
  printf '[qairt244-single-token-smoke] %s\n' "$*"
}

{
  printf 'app_id=%s\n' "$APP_ID"
  printf 'prompt=%s\n' "$PROMPT"
  printf 'max_output_tokens=%s\n' "$MAX_OUTPUT_TOKENS"
  printf 'timeout_seconds=%s\n' "$TIMEOUT_SECONDS"
  printf 'mode=blocked-preflight\n'
  printf 'build=no\n'
  printf 'install=no\n'
  printf 'app_launch=no\n'
  printf 'conversation_created=no\n'
  printf 'session_created=no\n'
  printf 'generate_response=no\n'
  printf 'token_generation=no\n'
} >"$OUT_DIR/preflight_config.txt"

if command -v rg >/dev/null 2>&1 && [ -d "$LITERT_LM_ROOT" ]; then
  rg -n "nativeRunDecode|RunDecode\\(|DecodeConfig|SetMaxOutputTokens|SessionConfig|SamplerConfig|sendMessage|generateContent|createConversation|createSession" \
    "$LITERT_LM_ROOT/kotlin" "$LITERT_LM_ROOT/runtime" >"$OUT_DIR/api_surface_hits.txt" 2>/dev/null || true
else
  printf 'rg or LiteRT-LM root unavailable\n' >"$OUT_DIR/api_surface_hits.txt"
fi

cat >"$OUT_DIR/result.txt" <<'EOF'
classification=maxOutputTokens=1-not-guaranteed
executed=false
reason=Kotlin/JNI public Session.runDecode() calls native RunDecode() without DecodeConfig, and Conversation/sendMessage surfaces do not expose a hard one-token cap.
required_next_step=Implement a customBuildExperimentDebug-only lower-level JNI or CLI route that calls DecodeConfig.SetMaxOutputTokens(1), then re-run this smoke once after static checks pass.
EOF

cat >"$OUT_DIR/safety_checks.tsv" <<'EOF'
check	status	detail
customBuildExperimentDebug_only	blocked_no_run	Future implementation must use io.github.ninbyo02.lami.customnpu only.
explicit_opt_in	blocked_no_run	Future runner must require explicit acknowledgement before one-token generation.
max_output_tokens_eq_1	fail	No current app-accessible API path statically guarantees SetMaxOutputTokens(1).
prompt_short	pass_planned	Planned prompt is Hi.
timeout_configured	pass_planned	Planned timeout is 30 seconds.
engine_close_required	pass_planned	Future implementation must close session/engine in finally.
normal_ui_disconnected	pass	This preflight does not touch normal UI inference.
conversation_created	pass_not_run	No Conversation was created.
session_created	pass_not_run	No Session was created.
generate_response_called	pass_not_run	No generateResponse or token generation was run.
EOF

cat >"$OUT_DIR/classification.md" <<'EOF'
# Classification

`maxOutputTokens=1を保証できず、未実行`

This is the intended safe result for the current implementation-prep phase.
EOF

cat >"$OUT_DIR/summary.md" <<'EOF'
# QAIRT 2.44 Single-Token Smoke Preflight

Result: blocked before execution.

The current app-accessible LiteRT-LM Kotlin/JNI path cannot guarantee
`maxOutputTokens=1`:

- `Session.runDecode()` calls JNI `nativeRunDecode(handle)` with no
  `DecodeConfig`.
- `Conversation` / `sendMessage*` / `generateContent*` surfaces can generate,
  but do not expose a verified hard one-token output cap.
- Lower-level C++ has `DecodeConfig.SetMaxOutputTokens(1)`, but no
  customBuildExperimentDebug-only app entrypoint currently calls it.

Therefore this script intentionally did not build, install, launch the app,
create `Conversation`, create `Session`, call `generateResponse`, or generate
tokens.

Next required implementation: add an isolated customBuildExperimentDebug-only
JNI or CLI route that statically contains `SetMaxOutputTokens(1)` and no normal
UI routing.

Artifact files:

- `preflight_config.txt`
- `api_surface_hits.txt`
- `safety_checks.tsv`
- `classification.md`
- `result.txt`
EOF

log "blocked: maxOutputTokens=1 is not guaranteed by the current app-accessible API"
log "artifact: $OUT_DIR"
exit 0
