#!/usr/bin/env bash
# qairt244 custom JNI forced-command extension for the restricted lami-build SSH controller.
#
# This file is intended to be sourced by:
#   /home/lami-build/lami-build-control/remote_control.sh
#
# It does not dispatch arbitrary shell commands. It exposes only:
#   qairt244-artifacts
#   stage-qairt244-custom-jni [artifact-dir-basename]
#   build-qairt244-custom-jni
#   qairt244-sdk-status
#
# Required globals from the parent controller are optional; sane defaults are used:
#   REPO, LOG_DIR, CMD, fail

: "${REPO:=$HOME/repos/lami-android}"
: "${LOG_DIR:=$HOME/build-logs}"
: "${CMD:=${SSH_ORIGINAL_COMMAND:-}}"
: "${LITERT_CUSTOM_ARTIFACT_ROOT:=$REPO/artifacts/litert_custom_build}"
: "${LITERT_LM_CHECKOUT:=}"
: "${QAIRT244_ROOT:=}"
: "${QAIRT244_BUILD_LABEL:=qairt244_128token_utf8prompt}"
: "${LITERT_LM_REPO:=https://github.com/google-ai-edge/LiteRT-LM.git}"
: "${LITERT_LM_REF:=v0.11.0}"
: "${QAIRT244_PATCH:=$REPO/patches/qairt244_litertlm_utf8_128token.patch}"

lami_qairt244_first_existing_dir() {
  local path
  for path in "$@"; do
    if [[ -n "$path" && -d "$path" ]]; then
      printf '%s\n' "$path"
      return 0
    fi
  done
  return 1
}

lami_qairt244_resolve_litert_lm_checkout() {
  if [[ -n "${LITERT_LM_CHECKOUT:-}" && -d "$LITERT_LM_CHECKOUT" ]]; then
    printf '%s\n' "$LITERT_LM_CHECKOUT"
    return 0
  fi
  lami_qairt244_first_existing_dir \
    "$HOME/project/litert-custom-build/LiteRT-LM" \
    /home/sato/project/litert-custom-build/LiteRT-LM \
    /home/lami-build/project/litert-custom-build/LiteRT-LM
}

lami_qairt244_default_litert_lm_checkout() {
  printf '%s\n' "$HOME/project/litert-custom-build/LiteRT-LM"
}

lami_qairt244_ensure_litert_lm_checkout() {
  local checkout
  checkout="$(lami_qairt244_resolve_litert_lm_checkout || true)"
  if [[ -z "$checkout" ]]; then
    checkout="$(lami_qairt244_default_litert_lm_checkout)"
    mkdir -p "$(dirname "$checkout")"
    echo "cloning LiteRT-LM into $checkout" >&2
    git clone "$LITERT_LM_REPO" "$checkout" >&2
  fi

  if [[ ! -d "$checkout/.git" ]]; then
    echo "LiteRT-LM checkout is not a git repo: $checkout" >&2
    exit 65
  fi

  git -C "$checkout" fetch --tags origin >&2
  git -C "$checkout" checkout "$LITERT_LM_REF" >&2
  git -C "$checkout" reset --hard "$LITERT_LM_REF" >&2
  git -C "$checkout" clean -fdx >&2

  if git -C "$checkout" lfs version >/dev/null 2>&1; then
    git -C "$checkout" lfs pull >&2
  fi

  local gemma_provider="$checkout/prebuilt/android_arm64/libGemmaModelConstraintProvider.so"
  if [[ -f "$gemma_provider" ]] && head -n 1 "$gemma_provider" 2>/dev/null | grep -q 'git-lfs.github.com/spec'; then
    echo "LiteRT-LM LFS object is not materialized: $gemma_provider" >&2
    echo "Install git-lfs and run: git -C $checkout lfs pull" >&2
    exit 65
  fi

  if [[ ! -f "$QAIRT244_PATCH" ]]; then
    echo "missing qairt244 patch: $QAIRT244_PATCH" >&2
    exit 65
  fi
  if git -C "$checkout" apply --check "$QAIRT244_PATCH"; then
    git -C "$checkout" apply "$QAIRT244_PATCH"
  else
    echo "qairt244 patch does not apply cleanly to $LITERT_LM_REF" >&2
    exit 65
  fi

  if ! grep -q 'Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt' \
    "$checkout/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc"; then
    echo "patched LiteRT-LM checkout is missing nativeRunEditablePrompt marker" >&2
    exit 65
  fi
  printf '%s\n' "$checkout"
}

lami_qairt244_resolve_qairt_root() {
  if [[ -n "${QAIRT244_ROOT:-}" && -d "$QAIRT244_ROOT" ]]; then
    printf '%s\n' "$QAIRT244_ROOT"
    return 0
  fi
  lami_qairt244_first_existing_dir \
    "$HOME/compose/qairt/workspace/sdk/qairt/2.44.0.260225" \
    /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
    /home/lami-build/compose/qairt/workspace/sdk/qairt/2.44.0.260225
}

lami_qairt244_sdk_status() {
  local path real required note
  printf 'candidate\texists\trealpath\trequired_files\tnote\n'
  for path in \
    "$HOME/compose/qairt/workspace/sdk/qairt/2.44.0.260225" \
    /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
    /home/lami-build/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
    "$HOME/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225" \
    /home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225 \
    /home/lami-build/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225 \
    "$HOME/compose/qairt/workspace/sdk/qairt/2.46.0.260424" \
    /home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424 \
    /home/lami-build/compose/qairt/workspace/sdk/qairt/2.46.0.260424; do
    if [[ -e "$path" ]]; then
      real="$(readlink -f "$path" 2>/dev/null || printf '%s' "$path")"
      required="missing"
      if [[ -f "$path/bin/envsetup.sh" && \
            -f "$path/bin/x86_64-linux-clang/qnn-net-run" && \
            -f "$path/lib/aarch64-android/libQnnSystem.so" && \
            -f "$path/lib/aarch64-android/libQnnHtp.so" ]]; then
        required="present"
      fi
      note="candidate"
      case "$real" in
        *2.46.0.260424*) note="2.46_or_overlay_not_exact_2.44" ;;
        *2.44.0.260225*) note="path_matches_2.44" ;;
      esac
      printf '%s\ttrue\t%s\t%s\t%s\n' "$path" "$real" "$required" "$note"
    else
      printf '%s\tfalse\t-\t-\tmissing\n' "$path"
    fi
  done
}

lami_qairt244_fail() {
  if declare -F fail >/dev/null 2>&1; then
    fail
  fi
  echo "not allowed: ${CMD:-<empty>}" >&2
  exit 64
}

lami_qairt244_validate_artifact_basename() {
  local name="$1"
  [[ "$name" =~ ^[0-9]{8}_[0-9]{6}_[A-Za-z0-9._-]+$ ]] || lami_qairt244_fail
  [[ "$name" != *..* ]] || lami_qairt244_fail
  [[ "$name" != */* ]] || lami_qairt244_fail
  printf '%s\n' "$name"
}

lami_qairt244_artifact_has_symbol() {
  local artifact_dir="$1"
  local lib="$artifact_dir/built_libs/liblitertlm_jni.so"
  [[ -f "$lib" ]] || return 1
  readelf -Ws "$lib" 2>/dev/null | grep -q 'Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt'
}

lami_qairt244_resolve_artifact_dir() {
  local requested="${1:-}"
  local candidate
  if [[ -n "$requested" ]]; then
    requested="$(lami_qairt244_validate_artifact_basename "$requested")"
    candidate="$LITERT_CUSTOM_ARTIFACT_ROOT/$requested"
    [[ -d "$candidate" ]] || lami_qairt244_fail
    lami_qairt244_artifact_has_symbol "$candidate" || {
      echo "artifact does not contain qairt244 nativeRunEditablePrompt symbol: $candidate" >&2
      exit 65
    }
    printf '%s\n' "$candidate"
    return 0
  fi

  while IFS= read -r candidate; do
    if lami_qairt244_artifact_has_symbol "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done < <(find "$LITERT_CUSTOM_ARTIFACT_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null | sort -rn | awk '{print $2}')

  echo "no qairt244 custom JNI artifact found under $LITERT_CUSTOM_ARTIFACT_ROOT" >&2
  exit 65
}

lami_qairt244_artifacts() {
  cd "$REPO"
  printf '# qairt244 artifacts\n'
  printf 'artifact\tstatus\tliblitertlm_jni_sha256\tbuild_id\n'
  while IFS= read -r artifact; do
    [[ -n "$artifact" ]] || continue
    local lib="$artifact/built_libs/liblitertlm_jni.so"
    local status="missing-liblitertlm_jni"
    local sha="-"
    local build_id="-"
    if [[ -f "$lib" ]]; then
      sha="$(sha256sum "$lib" | awk '{print $1}')"
      build_id="$(readelf -n "$lib" 2>/dev/null | awk '/Build ID:/ {print $3; exit}')"
      if lami_qairt244_artifact_has_symbol "$artifact"; then
        status="qairt244-symbol-present"
      else
        status="qairt244-symbol-missing"
      fi
    fi
    printf '%s\t%s\t%s\t%s\n' "$(basename "$artifact")" "$status" "$sha" "$build_id"
  done < <(find "$LITERT_CUSTOM_ARTIFACT_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null | sort -rn | awk '{print $2}' || true)
  printf '\n# qairt244 sdk candidates\n'
  lami_qairt244_sdk_status
}

lami_qairt244_stage_custom_jni() {
  local requested="${1:-}"
  local artifact_dir
  artifact_dir="$(lami_qairt244_resolve_artifact_dir "$requested")"

  mkdir -p "$LOG_DIR"
  local timestamp
  timestamp="$(date +%Y%m%d-%H%M%S)"
  local log_file="$LOG_DIR/stage-qairt244-custom-jni-${timestamp}.log"

  {
    echo "== LAMI stage qairt244 custom JNI =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "artifact=$artifact_dir"
    cd "$REPO"
    scripts/stage_litert_custom_build_stack_for_experiment.sh "${artifact_dir#$REPO/}"
    readelf -Ws app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/liblitertlm_jni.so | grep 'Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt'
    git status --short -- app/src/customBuildExperimentDebug/jniLibs/arm64-v8a || true
    echo "== STAGE OK =="
  } 2>&1 | tee "$log_file"

  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

lami_qairt244_build_custom_jni() {
  mkdir -p "$LOG_DIR"
  local timestamp
  timestamp="$(date +%Y%m%d-%H%M%S)"
  local litert_lm_checkout
  local qairt_root
  litert_lm_checkout="$(lami_qairt244_ensure_litert_lm_checkout)"
  qairt_root="$(lami_qairt244_resolve_qairt_root || true)"
  if [[ -z "$qairt_root" ]]; then
    echo "missing QAIRT 2.44 root; checked: $HOME/compose/qairt/workspace/sdk/qairt/2.44.0.260225, /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225" >&2
    exit 65
  fi

  local artifact_dir="$LITERT_CUSTOM_ARTIFACT_ROOT/${timestamp}_${QAIRT244_BUILD_LABEL}"
  local log_file="$LOG_DIR/build-qairt244-custom-jni-${timestamp}.log"

  {
    echo "== LAMI build qairt244 custom JNI =="
    echo "time=$(date -Is)"
    echo "host=$(hostname)"
    echo "user=$(id -un)"
    echo "litert_lm_checkout=$litert_lm_checkout"
    echo "qairt_root=$qairt_root"
    echo "label=$QAIRT244_BUILD_LABEL"
    cd "$REPO"
    OUT_DIR="$artifact_dir" \
      BAZEL_OUTPUT_BASE="$HOME/project/litert-custom-build/bazel_output_base/build_$timestamp" \
      scripts/build_litert_custom_artifacts.sh \
        "$litert_lm_checkout" \
        --qairt-root "$qairt_root" \
        --label "$QAIRT244_BUILD_LABEL"
    lami_qairt244_artifact_has_symbol "$artifact_dir"
    scripts/stage_litert_custom_build_stack_for_experiment.sh "${artifact_dir#$REPO/}"
    readelf -Ws app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/liblitertlm_jni.so | grep 'Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt'
    echo "== BUILD+STAGE OK =="
  } 2>&1 | tee "$log_file"

  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

# Optional helper for parent controllers that want a single dispatch call.
# Returns:
#   0: handled
#   1: not a qairt244 command
lami_qairt244_dispatch() {
  local command="${1:-$CMD}"
  local -a parts
  case "$command" in
    qairt244-artifacts)
      lami_qairt244_artifacts
      return 0
      ;;
    stage-qairt244-custom-jni*)
      # shellcheck disable=SC2206 # SSH_ORIGINAL_COMMAND is intentionally split after validation.
      parts=($command)
      [[ "${#parts[@]}" -ge 1 && "${#parts[@]}" -le 2 ]] || lami_qairt244_fail
      lami_qairt244_stage_custom_jni "${parts[1]:-}"
      return 0
      ;;
    build-qairt244-custom-jni)
      lami_qairt244_build_custom_jni
      return 0
      ;;
    qairt244-sdk-status)
      lami_qairt244_sdk_status
      return 0
      ;;
  esac
  return 1
}

lami_qairt244_help() {
  cat <<'EOF'
  qairt244-artifacts
  stage-qairt244-custom-jni [artifact-dir-basename]
  build-qairt244-custom-jni
  qairt244-sdk-status
EOF
}
