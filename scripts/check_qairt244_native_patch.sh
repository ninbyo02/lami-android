#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECKOUT_DIR="${1:-/home/sato/project/litert-custom-build/LiteRT-LM}"
PATCH_FILE="${2:-$ROOT_DIR/patches/qairt244_litertlm_utf8_128token_128input.patch}"
TARGET_FILE="kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc"
MAX256_MARKER="qairt244_editable_prompt_max256_v1"

if [ ! -d "$CHECKOUT_DIR/.git" ]; then
  printf 'status=missing_checkout\n'
  printf 'checkout=%s\n' "$CHECKOUT_DIR"
  exit 2
fi

if [ ! -f "$PATCH_FILE" ]; then
  printf 'status=missing_patch\n'
  printf 'patch=%s\n' "$PATCH_FILE"
  exit 2
fi

HEAD_SHA="$(git -C "$CHECKOUT_DIR" rev-parse HEAD 2>/dev/null || true)"
printf 'checkout=%s\n' "$CHECKOUT_DIR"
printf 'head=%s\n' "$HEAD_SHA"
printf 'patch=%s\n' "$PATCH_FILE"
printf 'target=%s\n' "$TARGET_FILE"
printf 'max256_marker=%s\n' "$MAX256_MARKER"
printf 'patch_has_max256_marker=%s\n' "$(grep -q "$MAX256_MARKER" "$PATCH_FILE" && printf true || printf false)"
printf 'patch_has_native_limit_256=%s\n' "$(grep -q 'native_max_output_tokens_limit=256' "$PATCH_FILE" && printf true || printf false)"
printf 'patch_has_set_max_output_tokens_256=%s\n' "$(grep -q 'SetMaxOutputTokens(256)' "$PATCH_FILE" && printf true || printf false)"
printf 'patch_has_sm8750_evidence=%s\n' "$(grep -Eiq 'SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750' "$PATCH_FILE" && printf true || printf false)"

if git -C "$CHECKOUT_DIR" apply --check "$PATCH_FILE" >/dev/null 2>&1; then
  printf 'status=not_applied\n'
  printf 'next=git -C %s apply %s\n' "$CHECKOUT_DIR" "$PATCH_FILE"
  exit 0
fi

if git -C "$CHECKOUT_DIR" apply --reverse --check "$PATCH_FILE" >/dev/null 2>&1; then
  printf 'status=applied\n'
  printf 'next=no_action\n'
  exit 0
fi

printf 'status=mismatch\n'
printf 'next=inspect_target_diff\n'
exit 1
