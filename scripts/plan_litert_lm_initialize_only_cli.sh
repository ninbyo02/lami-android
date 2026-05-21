#!/usr/bin/env bash
set -euo pipefail

LITERT_LM_ROOT="${LITERT_LM_ROOT:-/home/sato/project/litert-custom-build/LiteRT-LM}"
ARTIFACT_DIR="${ARTIFACT_DIR:-/home/sato/project/lami-android/artifacts/qairt244_initialize_only_cli_plan/20260521_085251}"
MODEL_PATH="${MODEL_PATH:-/data/local/tmp/gemma-4-E2B-it_qualcomm_sm8750.litertlm}"
NATIVE_LIBRARY_DIR="${NATIVE_LIBRARY_DIR:-/data/local/tmp/litertlm-qairt244-initialize-only/lib}"

main_src="$LITERT_LM_ROOT/runtime/engine/litert_lm_main.cc"
advanced_src="$LITERT_LM_ROOT/runtime/engine/litert_lm_advanced_main.cc"
engine_impl_src="$LITERT_LM_ROOT/runtime/core/engine_impl.cc"
jni_src="$LITERT_LM_ROOT/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc"
build_file="$LITERT_LM_ROOT/runtime/engine/BUILD"

mkdir -p "$ARTIFACT_DIR"

{
  printf 'LiteRT-LM initialize-only CLI planner\n'
  printf 'Generated: 2026-05-21T08:52:51+09:00\n'
  printf 'Mode: planning/read-only; no CLI execution, no inference, no prompt, no Conversation, no Session, no generate.\n\n'

  printf 'Inputs:\n'
  printf '  LiteRT-LM root: %s\n' "$LITERT_LM_ROOT"
  printf '  Future model_path arg: %s\n' "$MODEL_PATH"
  printf '  Future native_library_dir arg: %s\n' "$NATIVE_LIBRARY_DIR"
  printf '  Future backend arg: npu\n'
  printf '  Future no_generate arg: true\n\n'

  printf 'Existing upstream target check:\n'
  if [ -f "$build_file" ] && grep -q 'name = "litert_lm_main"' "$build_file"; then
    printf '  found //runtime/engine:litert_lm_main\n'
  else
    printf '  missing //runtime/engine:litert_lm_main declaration\n'
  fi

  printf '\nUnsafe upstream litert_lm_main markers:\n'
  for pattern in 'Conversation::Create' 'ConversationConfig' 'SessionConfig' 'SendMessageAsync' 'WaitUntilDone' 'GetInputPrompt' 'default prompt'; do
    if [ -f "$main_src" ] && grep -q "$pattern" "$main_src"; then
      printf '  present: %s\n' "$pattern"
    else
      printf '  not found: %s\n' "$pattern"
    fi
  done

  printf '\nUnsafe advanced main markers:\n'
  for pattern in 'RunLiteRtLm' 'GetInputPrompt' 'input_prompt' 'default prompt' 'use_session'; do
    if [ -f "$advanced_src" ] && grep -q "$pattern" "$advanced_src"; then
      printf '  present: %s\n' "$pattern"
    else
      printf '  not found: %s\n' "$pattern"
    fi
  done

  printf '\nInitialize boundary markers:\n'
  for pattern in 'EngineFactory::CreateAny' 'EngineFactory::CreateDefault' 'SetLitertDispatchLibDir' 'Tag::kDispatchLibraryDir' 'CreateSession'; do
    if grep -q "$pattern" "$main_src" "$engine_impl_src" "$jni_src" 2>/dev/null; then
      printf '  found in inspected sources: %s\n' "$pattern"
    else
      printf '  not found in inspected sources: %s\n' "$pattern"
    fi
  done

  printf '\nFuture target design:\n'
  printf '  source: runtime/engine/litert_lm_initialize_only_main.cc\n'
  printf '  bazel target: //runtime/engine:litert_lm_initialize_only_main\n'
  printf '  stop after: EngineFactory::CreateAny returns an Engine\n'
  printf '  close by: destroying the Engine unique_ptr and exiting\n'
  printf '  forbidden calls: Conversation::Create, CreateSession, prompt handling, SendMessageAsync, GenerateContent, RunPrefill, RunDecode\n'

  printf '\nThis script intentionally does not run bazel, adb, a CLI binary, or inference.\n'
} | tee "$ARTIFACT_DIR/planner_output.txt"

cat > "$ARTIFACT_DIR/future_static_checks.txt" <<'CHECKS'
# Future checks only. Do not run a CLI or inference for the initialize-only design pass.
bazel query //runtime/engine:litert_lm_initialize_only_main
bazel cquery --config=android_arm64 //runtime/engine:litert_lm_initialize_only_main
bazel build --config=android_arm64 //runtime/engine:litert_lm_initialize_only_main
rg -n 'Conversation::Create|ConversationConfig|SessionConfig|CreateSession|SendMessageAsync|GenerateContent|GenerateContentStream|RunPrefill|RunDecode|input_prompt|default prompt' runtime/engine/litert_lm_initialize_only_main.cc
CHECKS

printf '\nWrote planning notes to %s\n' "$ARTIFACT_DIR"
