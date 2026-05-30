#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
PREFLIGHT_DIR="$ROOT_DIR/artifacts/qairt244_npu_max512_guard_preflight/$TIMESTAMP"
NATIVE_ARTIFACT="${QAIRT244_MAX512_NATIVE_ARTIFACT:-}"
SM8750_MODEL_EVIDENCE="${QAIRT244_SM8750_MODEL_EVIDENCE:-}"
MAX512_GUARD_MARKER="qairt244_editable_prompt_max512_v1"

while [ $# -gt 0 ]; do
  case "$1" in
    --artifact|--native-artifact)
      NATIVE_ARTIFACT="${2:-}"
      shift 2
      ;;
    --sm8750-evidence|--model-evidence)
      SM8750_MODEL_EVIDENCE="${2:-}"
      shift 2
      ;;
    --preflight-only)
      shift
      ;;
    --help|-h)
      cat <<'EOF'
Usage:
  scripts/run_qairt244_npu_max_output_512_guard_preflight.sh --preflight-only --artifact <native-build-artifact> [--sm8750-evidence <file-or-dir>]

This phase is preflight-only. It verifies static max512 native artifact
evidence and exits before device selection, app launch, NPU generation,
Engine.initialize, or RunDecode.
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
mkdir -p "$PREFLIGHT_DIR"

log() { printf '[qairt244-max512-guard-preflight] %s\n' "$*"; }

native_artifact_binary() {
  if [ -f "$NATIVE_ARTIFACT" ]; then
    printf '%s' "$NATIVE_ARTIFACT"
    return 0
  fi
  if [ -n "$NATIVE_ARTIFACT" ] && [ -f "$NATIVE_ARTIFACT/built_libs/liblitertlm_jni.so" ]; then
    printf '%s' "$NATIVE_ARTIFACT/built_libs/liblitertlm_jni.so"
    return 0
  fi
  return 1
}

artifact_exists() {
  [ -n "$NATIVE_ARTIFACT" ] && [ -e "$NATIVE_ARTIFACT" ]
}

append_text_sources_from_path() {
  local source_path="$1"
  local text_sources="$2"
  [ -n "$source_path" ] || return 0
  if [ -d "$source_path" ]; then
    find "$source_path" -maxdepth 5 -type f \
      \( -name '*.txt' -o -name '*.md' -o -name '*.patch' -o -name '*.tsv' -o -name '*.json' \) \
      2>/dev/null || true
  elif [ -f "$source_path" ]; then
    printf '%s\n' "$source_path"
  fi >>"$text_sources"
}

write_config() {
  {
    printf 'mode=preflight-only\n'
    printf 'native_artifact=%s\n' "${NATIVE_ARTIFACT:-none}"
    printf 'native_artifact_present=%s\n' "$(artifact_exists && printf true || printf false)"
    printf 'sm8750_model_evidence=%s\n' "${SM8750_MODEL_EVIDENCE:-none}"
    printf 'sm8750_model_evidence_present=%s\n' "$([ -n "$SM8750_MODEL_EVIDENCE" ] && [ -e "$SM8750_MODEL_EVIDENCE" ] && printf true || printf false)"
    printf 'required_marker=%s\n' "$MAX512_GUARD_MARKER"
    printf 'required_native_limit=native_max_output_tokens_limit=512\n'
    printf 'required_decode_setter=SetMaxOutputTokens(512)\n'
    printf 'required_sm8750_selection=true\n'
    printf 'npu_run_executed=false\n'
    printf 'engine_initialize_executed=false\n'
    printf 'run_decode_executed=false\n'
    printf 'chat_screen_connected=false\n'
    printf 'assistant_message_list_inserted=false\n'
    printf 'selected_path_npu_saved=false\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n'
  } >"$PREFLIGHT_DIR/preflight_config.txt"
  {
    printf 'native_artifact=%s\n' "${NATIVE_ARTIFACT:-none}"
    printf 'native_artifact_present=%s\n' "$(artifact_exists && printf true || printf false)"
    printf 'sm8750_model_evidence=%s\n' "${SM8750_MODEL_EVIDENCE:-none}"
    printf 'sm8750_model_evidence_present=%s\n' "$([ -n "$SM8750_MODEL_EVIDENCE" ] && [ -e "$SM8750_MODEL_EVIDENCE" ] && printf true || printf false)"
  } >"$PREFLIGHT_DIR/build_artifact_path.txt"
}

collect_text_sources() {
  local text_sources="$PREFLIGHT_DIR/evidence_sources.txt"
  : >"$text_sources"
  append_text_sources_from_path "$NATIVE_ARTIFACT" "$text_sources"
  append_text_sources_from_path "$SM8750_MODEL_EVIDENCE" "$text_sources"
  sort -u "$text_sources" -o "$text_sources" 2>/dev/null || true
}

collect_evidence() {
  local text_sources="$PREFLIGHT_DIR/evidence_sources.txt"
  local evidence="$PREFLIGHT_DIR/evidence.txt"
  local marker_scan="$PREFLIGHT_DIR/native_marker_scan.txt"
  local setter_evidence="$PREFLIGHT_DIR/set_max_output_tokens_512_evidence.txt"
  local binary

  write_config
  git status --short >"$PREFLIGHT_DIR/git_status.txt" 2>&1 || true
  collect_text_sources

  {
    printf '# static max512 guard evidence\n'
    printf 'artifact=%s\n\n' "${NATIVE_ARTIFACT:-none}"
    printf 'sm8750_model_evidence=%s\n\n' "${SM8750_MODEL_EVIDENCE:-none}"
  } >"$evidence"
  : >"$marker_scan"
  : >"$setter_evidence"

  while IFS= read -r source_file; do
    [ -f "$source_file" ] || continue
    rg -n "$MAX512_GUARD_MARKER|native_max_output_tokens_limit=512|SetMaxOutputTokens\\(512\\)|SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750" \
      "$source_file" | sed "s#^#${source_file}:#" >>"$evidence" 2>/dev/null || true
    rg -n "$MAX512_GUARD_MARKER|native_max_output_tokens_limit=512|SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750" \
      "$source_file" | sed "s#^#${source_file}:#" >>"$marker_scan" 2>/dev/null || true
    rg -n "SetMaxOutputTokens\\(512\\)" "$source_file" |
      sed "s#^#${source_file}:#" >>"$setter_evidence" 2>/dev/null || true
  done <"$text_sources"

  {
    printf '# staged binary check\n'
    printf 'native_artifact=%s\n' "${NATIVE_ARTIFACT:-none}"
    if binary="$(native_artifact_binary)"; then
      printf 'binary=%s\n' "$binary"
      file "$binary" || true
      sha256sum "$binary" || true
      printf '\n# strings evidence\n'
      strings "$binary" 2>/dev/null |
        grep -E "$MAX512_GUARD_MARKER|native_max_output_tokens_limit=512|SetMaxOutputTokens\\(512\\)|SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750" || true
    else
      printf 'binary=missing\n'
    fi
  } >"$PREFLIGHT_DIR/staged_binary_check.txt" 2>&1

  cat "$PREFLIGHT_DIR/staged_binary_check.txt" >>"$evidence"
  cat "$PREFLIGHT_DIR/staged_binary_check.txt" >>"$marker_scan"
  cat "$PREFLIGHT_DIR/staged_binary_check.txt" >>"$setter_evidence"

  {
    printf '# grep safety scan\n'
    printf 'script=scripts/run_qairt244_npu_max_output_512_guard_preflight.sh\n'
    printf 'preflight_only=true\n'
    printf 'npu_run_executed=false\n'
    printf 'engine_initialize_executed=false\n'
    printf 'run_decode_executed=false\n'
    printf 'assistant_message_list_inserted=false\n'
    printf 'db=false\n'
    printf 'tts=false\n'
    printf 'markdown=false\n'
    printf 'streaming=false\n\n'
    rg -n "RunDecode|Engine\\.initialize|am start|am broadcast|adb|selectedPath.*npu|selected_path_npu|db=true|tts=true|markdown=true|streaming=true|assistant_message_list|generateResponse" \
      scripts/run_qairt244_npu_max_output_512_guard_preflight.sh scripts/check_qairt244_native_patch.sh docs 2>&1 || true
  } >"$PREFLIGHT_DIR/grep_safety.txt"
}

write_summary() {
  local artifact_present="$1"
  local binary_present="$2"
  local marker_present="$3"
  local native_limit_present="$4"
  local setter_present="$5"
  local sm8750_present="$6"
  local guard_status="$7"
  {
    printf '# QAIRT244 max_output_tokens=512 guard preflight\n\n'
    printf -- '- artifact: `%s`\n' "${PREFLIGHT_DIR#$ROOT_DIR/}"
    printf -- '- native_artifact: `%s`\n' "${NATIVE_ARTIFACT:-none}"
    printf -- '- native_artifact_present: `%s`\n' "$artifact_present"
    printf -- '- staged_binary_present: `%s`\n' "$binary_present"
    printf -- '- sm8750_model_evidence: `%s`\n' "${SM8750_MODEL_EVIDENCE:-none}"
    printf -- '- requested_max_output_tokens: `512`\n'
    printf -- '- guard_status: `%s`\n' "$guard_status"
    printf -- '- npu_run_executed: `false`\n'
    printf -- '- engine_initialize_executed: `false`\n'
    printf -- '- run_decode_executed: `false`\n'
    printf -- '- chat_screen_connected: `false`\n'
    printf -- '- db_tts_markdown_streaming: `false,false,false,false`\n\n'
    printf '## Required Static Evidence\n\n'
    printf '| check | status |\n'
    printf '| --- | --- |\n'
    printf '| native artifact path exists | `%s` |\n' "$artifact_present"
    printf '| staged `liblitertlm_jni.so` present | `%s` |\n' "$binary_present"
    printf '| `%s` | `%s` |\n' "$MAX512_GUARD_MARKER" "$marker_present"
    printf '| `native_max_output_tokens_limit=512` | `%s` |\n' "$native_limit_present"
    printf '| `SetMaxOutputTokens(512)` | `%s` |\n' "$setter_present"
    printf '| `SM8750` selection | `%s` |\n' "$sm8750_present"
    printf '\n## Result\n\n'
    if [ "$guard_status" = pass ]; then
      printf '512 guard-only patch built; run not executed. The next phase must be a separately approved single-prompt hidden run.\n'
    else
      printf '512 guard-only patch evidence incomplete; run refused before device selection, NPU, Engine.initialize, or RunDecode.\n'
    fi
  } >"$PREFLIGHT_DIR/summary.md"

  {
    printf 'marker_present=%s\n' "$marker_present"
    printf 'native_artifact_present=%s\n' "$artifact_present"
    printf 'staged_binary_present=%s\n' "$binary_present"
    printf 'native_limit_present=%s\n' "$native_limit_present"
    printf 'setter_present=%s\n' "$setter_present"
    printf 'sm8750_present=%s\n' "$sm8750_present"
    printf 'guard_status=%s\n' "$guard_status"
    printf 'npu_run_executed=false\n'
    printf 'engine_initialize_executed=false\n'
    printf 'run_decode_executed=false\n'
  } >"$PREFLIGHT_DIR/marker.txt"
}

main() {
  local artifact_present=false
  local binary_present=false
  local marker_present=false
  local native_limit_present=false
  local setter_present=false
  local sm8750_present=false
  local guard_status=blocked

  if [ -z "$NATIVE_ARTIFACT" ]; then
    printf 'ERROR: --artifact is required for max512 preflight\n' >&2
    exit 2
  fi

  artifact_exists && artifact_present=true
  if native_artifact_binary >/dev/null; then
    binary_present=true
  fi

  collect_evidence
  grep -q "$MAX512_GUARD_MARKER" "$PREFLIGHT_DIR/evidence.txt" && marker_present=true
  grep -q 'native_max_output_tokens_limit=512' "$PREFLIGHT_DIR/evidence.txt" && native_limit_present=true
  grep -q 'SetMaxOutputTokens(512)' "$PREFLIGHT_DIR/evidence.txt" && setter_present=true
  grep -Eiq 'SM8750|sm8750|gemma-4-E2B-it_qualcomm_sm8750' "$PREFLIGHT_DIR/evidence.txt" && sm8750_present=true

  if [ "$artifact_present" = true ] &&
    [ "$binary_present" = true ] &&
    [ "$marker_present" = true ] &&
    [ "$native_limit_present" = true ] &&
    [ "$setter_present" = true ] &&
    [ "$sm8750_present" = true ]; then
    guard_status=pass
  fi

  write_summary "$artifact_present" "$binary_present" "$marker_present" "$native_limit_present" "$setter_present" "$sm8750_present" "$guard_status"

  if [ "$guard_status" = pass ]; then
    log "summary: ${PREFLIGHT_DIR#$ROOT_DIR/}/summary.md"
    exit 0
  fi

  log "blocked: missing required static max512 guard evidence"
  log "summary: ${PREFLIGHT_DIR#$ROOT_DIR/}/summary.md"
  exit 1
}

main "$@"
