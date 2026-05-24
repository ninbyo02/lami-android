#!/usr/bin/env bash
set -euo pipefail

LITERT_LM_ROOT="${LITERT_LM_ROOT:-/home/sato/project/litert-custom-build/LiteRT-LM}"
QAIRT_ROOT="${QAIRT_ROOT:-/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225}"
MODEL_PATH="${MODEL_PATH:-/data/local/tmp/gemma-4-E2B-it_qualcomm_sm8750.litertlm}"
STAGE_DIR="${STAGE_DIR:-/data/local/tmp/litertlm-qairt244-cli-proof}"

main_src="$LITERT_LM_ROOT/runtime/engine/litert_lm_main.cc"
build_file="$LITERT_LM_ROOT/runtime/engine/BUILD"

printf 'LiteRT-LM CLI NPU proof planner only\n'
printf 'No build, adb push, CLI execution, Conversation, Session, or generation is performed.\n\n'

printf 'LiteRT-LM root: %s\n' "$LITERT_LM_ROOT"
printf 'QAIRT root: %s\n' "$QAIRT_ROOT"
printf 'Model path candidate: %s\n' "$MODEL_PATH"
printf 'Future stage dir candidate: %s\n\n' "$STAGE_DIR"

printf 'Target declaration check:\n'
if [ -f "$build_file" ] && grep -q 'name = "litert_lm_main"' "$build_file"; then
  printf '  found //runtime/engine:litert_lm_main\n'
else
  printf '  missing //runtime/engine:litert_lm_main declaration\n'
fi

printf '\nSafety scan of upstream litert_lm_main.cc:\n'
for pattern in 'Conversation::Create' 'SendMessageAsync' 'WaitUntilDone' 'GetInputPrompt' 'default prompt'; do
  if [ -f "$main_src" ] && grep -q "$pattern" "$main_src"; then
    printf '  unsafe execution marker present: %s\n' "$pattern"
  else
    printf '  marker not found: %s\n' "$pattern"
  fi
done

printf '\nQAIRT 2.44 file presence candidates:\n'
for path in \
  "$QAIRT_ROOT/lib/aarch64-android/libQnnSystem.so" \
  "$QAIRT_ROOT/lib/aarch64-android/libQnnHtp.so" \
  "$QAIRT_ROOT/lib/aarch64-android/libQnnHtpPrepare.so" \
  "$QAIRT_ROOT/lib/aarch64-android/libQnnHtpV79Stub.so" \
  "$QAIRT_ROOT/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so"; do
  if [ -f "$path" ]; then
    printf '  present: %s\n' "$path"
  else
    printf '  missing: %s\n' "$path"
  fi
done

printf '\nFuture explicit environment candidate, not exported by this script:\n'
printf '  LD_LIBRARY_PATH=%s/lib\n' "$STAGE_DIR"
printf '  ADSP_LIBRARY_PATH=%s/dsp\n' "$STAGE_DIR"

printf '\nDo not execute upstream litert_lm_main for this task. It creates a Conversation and sends a prompt.\n'
