#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECKOUT_DIR="/home/sato/project/litert-custom-build/LiteRT-LM"
PATCH_FILE="$ROOT_DIR/patches/qairt244_litertlm_utf8_128token_128input.patch"
TARGET_FILE="kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc"
MAX256_MARKER="qairt244_editable_prompt_max256_v1"
MAX512_MARKER="qairt244_editable_prompt_max512_v1"
REQUIRE_MAX512=false
EVIDENCE_ONLY=false
SM8750_EVIDENCE=""

POSITIONAL=()
while [ $# -gt 0 ]; do
  case "$1" in
    --require-max512)
      REQUIRE_MAX512=true
      shift
      ;;
    --evidence-only)
      EVIDENCE_ONLY=true
      shift
      ;;
    --sm8750-evidence|--model-evidence)
      SM8750_EVIDENCE="${2:-}"
      shift 2
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/check_qairt244_native_patch.sh [--require-max512] [--evidence-only] [--sm8750-evidence <file-or-dir>] [checkout-dir] [patch-file]

--require-max512 stops with status=max512_evidence_missing unless the patch
contains qairt244_editable_prompt_max512_v1,
native_max_output_tokens_limit=512, SetMaxOutputTokens(512), and either the
patch or --sm8750-evidence contains SM8750 model selection evidence.
The only formal 512 runtime gate is hidden_per_run_isolated_512; sequential
and Activity-restart-only 512 remain rollback modes.
--evidence-only exits after evidence validation and does not run git apply
checks against the external checkout.
EOF
      exit 0
      ;;
    *)
      POSITIONAL+=("$1")
      shift
      ;;
  esac
done

if [ "${#POSITIONAL[@]}" -gt 0 ]; then
  CHECKOUT_DIR="${POSITIONAL[0]}"
fi
if [ "${#POSITIONAL[@]}" -gt 1 ]; then
  PATCH_FILE="${POSITIONAL[1]}"
fi

path_has_evidence() {
  local pattern="$1"
  local source_path="$2"
  [ -n "$source_path" ] && [ -e "$source_path" ] || return 1
  if [ -d "$source_path" ]; then
    rg -q "$pattern" "$source_path" 2>/dev/null
  else
    grep -Eq "$pattern" "$source_path"
  fi
}

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
PATCH_EVIDENCE_DIR="$(dirname "$PATCH_FILE")"
patch_has_sm8750_evidence="$(path_has_evidence 'SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750' "$PATCH_FILE" && printf true || printf false)"
max512_marker_present="$(path_has_evidence "$MAX512_MARKER" "$PATCH_FILE" && printf true || printf false)"
native_limit_512_present="$(path_has_evidence 'native_max_output_tokens_limit=512' "$PATCH_FILE" && printf true || printf false)"
setter_512_present="$(path_has_evidence 'SetMaxOutputTokens\(512\)' "$PATCH_FILE" && printf true || printf false)"
if [ "$native_limit_512_present" != true ] && path_has_evidence 'native_max_output_tokens_limit=512' "$PATCH_EVIDENCE_DIR"; then
  native_limit_512_present=true
fi
if [ "$setter_512_present" != true ] && path_has_evidence 'SetMaxOutputTokens\(512\)' "$PATCH_EVIDENCE_DIR"; then
  setter_512_present=true
fi
sm8750_sidecar_present=false
sm8750_sidecar_has_evidence=false
if [ -n "$SM8750_EVIDENCE" ] && [ -e "$SM8750_EVIDENCE" ]; then
  sm8750_sidecar_present=true
  if path_has_evidence 'SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750' "$SM8750_EVIDENCE"; then
    sm8750_sidecar_has_evidence=true
  fi
fi

printf 'checkout=%s\n' "$CHECKOUT_DIR"
printf 'head=%s\n' "$HEAD_SHA"
printf 'patch=%s\n' "$PATCH_FILE"
printf 'target=%s\n' "$TARGET_FILE"
printf 'require_max512=%s\n' "$REQUIRE_MAX512"
printf 'evidence_only=%s\n' "$EVIDENCE_ONLY"
printf 'sm8750_evidence=%s\n' "${SM8750_EVIDENCE:-none}"
printf 'sm8750_evidence_present=%s\n' "$sm8750_sidecar_present"
printf 'sm8750_evidence_has_selection=%s\n' "$sm8750_sidecar_has_evidence"
printf 'max256_marker=%s\n' "$MAX256_MARKER"
printf 'patch_has_max256_marker=%s\n' "$(grep -q "$MAX256_MARKER" "$PATCH_FILE" && printf true || printf false)"
printf 'patch_has_native_limit_256=%s\n' "$(grep -q 'native_max_output_tokens_limit=256' "$PATCH_FILE" && printf true || printf false)"
printf 'patch_has_set_max_output_tokens_256=%s\n' "$(grep -q 'SetMaxOutputTokens(256)' "$PATCH_FILE" && printf true || printf false)"
printf 'max512_marker=%s\n' "$MAX512_MARKER"
printf 'max512_formal_mode=hidden_per_run_isolated_512\n'
printf 'max512_required_execution_isolation=per_run_force_stop\n'
printf 'sequential_512_rollback=true\n'
printf 'activity_restart_only_512_rollback=true\n'
printf 'patch_has_max512_marker=%s\n' "$(grep -q "$MAX512_MARKER" "$PATCH_FILE" && printf true || printf false)"
printf 'patch_has_native_limit_512=%s\n' "$(grep -q 'native_max_output_tokens_limit=512' "$PATCH_FILE" && printf true || printf false)"
printf 'patch_has_set_max_output_tokens_512=%s\n' "$(grep -q 'SetMaxOutputTokens(512)' "$PATCH_FILE" && printf true || printf false)"
printf 'max512_marker_present=%s\n' "$max512_marker_present"
printf 'native_limit_512_evidence_present=%s\n' "$native_limit_512_present"
printf 'set_max_output_tokens_512_evidence_present=%s\n' "$setter_512_present"
printf 'patch_has_sm8750_evidence=%s\n' "$patch_has_sm8750_evidence"

if [ "$REQUIRE_MAX512" = true ]; then
  missing_max512=false
  [ "$max512_marker_present" = true ] || missing_max512=true
  [ "$native_limit_512_present" = true ] || missing_max512=true
  [ "$setter_512_present" = true ] || missing_max512=true
  if [ "$patch_has_sm8750_evidence" != true ] && [ "$sm8750_sidecar_has_evidence" != true ]; then
    missing_max512=true
  fi
  if [ "$missing_max512" = true ]; then
    printf 'status=max512_evidence_missing\n'
    printf 'next=provide_patch_with_max512_marker_limit_setter_and_sm8750_evidence\n'
    exit 1
  fi
  if [ "$EVIDENCE_ONLY" = true ]; then
    printf 'status=max512_evidence_present\n'
    printf 'next=preflight_only_or_hidden_per_run_isolated_512_gate\n'
    exit 0
  fi
fi

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
