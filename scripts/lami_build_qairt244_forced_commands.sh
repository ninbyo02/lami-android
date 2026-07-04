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
#   qairt244-repeat-stability
#
# Required globals from the parent controller are optional; sane defaults are used:
#   REPO, LOG_DIR, CMD, fail

: "${REPO:=$HOME/repos/lami-android}"
: "${LOG_DIR:=$HOME/build-logs}"
: "${CMD:=${SSH_ORIGINAL_COMMAND:-}}"
: "${LITERT_CUSTOM_ARTIFACT_ROOT:=$REPO/artifacts/litert_custom_build}"
: "${LITERT_LM_CHECKOUT:=}"
: "${QAIRT244_ROOT:=}"
: "${QAIRT244_BUILD_LABEL:=qairt244_128token_gpu_prefill_preinvoke_diag}"
: "${LITERT_LM_REPO:=https://github.com/google-ai-edge/LiteRT-LM.git}"
: "${LITERT_LM_REF:=v0.11.0}"
: "${QAIRT244_PATCH:=$REPO/patches/qairt244_litertlm_utf8_128token.patch}"
: "${QAIRT244_EXTRA_PATCH:=$REPO/patches/qairt244_litertlm_gpu_prefill_preinvoke_diag.patch}"
: "${QAIRT244_GPU_PREFILL_PREINVOKE_MARKER:=qairt244_gpu_prefill_preinvoke_v1}"
: "${QAIRT244_GPU_PREFILL_PREINVOKE_C_SYMBOL:=Qairt244GpuPrefillPreinvokeArtifactMarker}"
: "${QAIRT244_GPU_PREFILL_PREINVOKE_JNI_SYMBOL:=Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244GpuPrefillPreinvokeArtifactMarker_nativeMarker}"
: "${QAIRT244_REQUIRE_PERSISTENT_PROBE:=false}"

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

lami_qairt244_sha256_for() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1 && [[ -f "$file" ]]; then
    sha256sum "$file" | awk '{print $1}'
  else
    printf 'missing'
  fi
}

lami_qairt244_source_marker_present() {
  local file="$1"
  local marker="$2"
  [[ -f "$file" ]] || return 1
  grep -aFq "$marker" "$file"
}

lami_qairt244_bool_from_command() {
  "$@" && printf true || printf false
}

lami_qairt244_string_marker_present() {
  local file="$1"
  local marker="$2"
  [[ -f "$file" ]] || return 1
  strings "$file" 2>/dev/null | grep -Fq "$marker"
}

lami_qairt244_readelf_marker_present() {
  local file="$1"
  local marker="$2"
  [[ -f "$file" ]] || return 1
  readelf -p .rodata "$file" 2>/dev/null | grep -Fq "$marker"
}

lami_qairt244_exported_symbol_present() {
  local file="$1"
  local symbol="$2"
  [[ -f "$file" ]] || return 1
  readelf -Ws "$file" 2>/dev/null | awk -v symbol="$symbol" '
    $0 ~ /GLOBAL/ && $0 ~ /DEFAULT/ && index($0, symbol) { found = 1 }
    END { exit found ? 0 : 1 }
  '
}

lami_qairt244_print_source_marker_stage_diagnostic() {
  local stage="$1"
  local file="$2"
  local sha marker_present
  sha="$(lami_qairt244_sha256_for "$file")"
  marker_present=false
  lami_qairt244_source_marker_present "$file" "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER" && marker_present=true
  printf 'qairt244_marker_stage stage=%s kind=source path=%s sha256=%s marker=%s marker_present=%s strings_present=n/a rodata_present=n/a c_symbol_exported=n/a jni_symbol_exported=n/a\n' \
    "$stage" "$file" "$sha" "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER" "$marker_present" >&2
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
  local selected_commit
  selected_commit="$(git -C "$checkout" rev-parse --verify "${LITERT_LM_REF}^{commit}")" || {
    echo "LiteRT-LM ref is not a fetchable commit/tree: $LITERT_LM_REF" >&2
    exit 65
  }
  if ! git -C "$checkout" for-each-ref --contains "$selected_commit" \
      refs/remotes/origin refs/tags | grep -q .; then
    echo "LiteRT-LM commit is not reachable from fetched origin refs/tags: $selected_commit ($LITERT_LM_REF)" >&2
    exit 65
  fi
  echo "selected_litert_lm_ref=$LITERT_LM_REF" >&2
  echo "selected_litert_lm_commit=$selected_commit" >&2
  git -C "$checkout" checkout --detach "$selected_commit" >&2
  git -C "$checkout" reset --hard "$selected_commit" >&2
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
  echo "applying qairt244 base patch: $QAIRT244_PATCH" >&2
  if git -C "$checkout" apply --check "$QAIRT244_PATCH"; then
    echo "qairt244_base_patch_check=ok" >&2
    git -C "$checkout" apply "$QAIRT244_PATCH"
  else
    echo "qairt244 patch does not apply cleanly to $LITERT_LM_REF" >&2
    exit 65
  fi

  if [[ -n "${QAIRT244_EXTRA_PATCH:-}" ]]; then
    if [[ ! -f "$QAIRT244_EXTRA_PATCH" ]]; then
      echo "missing qairt244 extra patch: $QAIRT244_EXTRA_PATCH" >&2
      exit 65
    fi
    echo "applying qairt244 extra patch: $QAIRT244_EXTRA_PATCH" >&2
    if git -C "$checkout" apply --check "$QAIRT244_EXTRA_PATCH"; then
      echo "qairt244_extra_patch_check_after_base=ok" >&2
      git -C "$checkout" apply "$QAIRT244_EXTRA_PATCH"
    else
      echo "qairt244 extra patch does not apply cleanly to $LITERT_LM_REF after base patch" >&2
      exit 65
    fi
  fi

  if ! grep -q 'Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt' \
    "$checkout/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc"; then
    echo "patched LiteRT-LM checkout is missing nativeRunEditablePrompt marker" >&2
    exit 65
  fi
  if [[ "$QAIRT244_REQUIRE_PERSISTENT_PROBE" == "true" ]]; then
    if ! grep -q 'Qairt244ShortMultitokenSmoke_nativeRunPersistentProbe' \
      "$checkout/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc"; then
      echo "patched LiteRT-LM checkout is missing nativeRunPersistentProbe marker" >&2
      exit 65
    fi
  fi
  if [[ -n "${QAIRT244_EXTRA_PATCH:-}" ]] &&
     [[ "$(basename "$QAIRT244_EXTRA_PATCH")" == "qairt244_litertlm_gpu_prefill_preinvoke_diag.patch" ]] &&
     ! grep -q "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER" \
       "$checkout/runtime/executor/llm_litert_compiled_model_executor.cc"; then
    echo "patched LiteRT-LM checkout is missing $QAIRT244_GPU_PREFILL_PREINVOKE_MARKER executor marker" >&2
    exit 65
  fi
  if [[ -n "${QAIRT244_EXTRA_PATCH:-}" ]] &&
     [[ "$(basename "$QAIRT244_EXTRA_PATCH")" == "qairt244_litertlm_gpu_prefill_preinvoke_diag.patch" ]] &&
     ! grep -q "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER" \
       "$checkout/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc"; then
    echo "patched LiteRT-LM checkout is missing $QAIRT244_GPU_PREFILL_PREINVOKE_MARKER JNI marker" >&2
    exit 65
  fi
  if [[ -n "${QAIRT244_EXTRA_PATCH:-}" ]] &&
     [[ "$(basename "$QAIRT244_EXTRA_PATCH")" == "qairt244_litertlm_gpu_prefill_preinvoke_diag.patch" ]]; then
    lami_qairt244_print_source_marker_stage_diagnostic \
      "patched-source-after-apply-executor" \
      "$checkout/runtime/executor/llm_litert_compiled_model_executor.cc"
    lami_qairt244_print_source_marker_stage_diagnostic \
      "patched-source-after-apply-jni" \
      "$checkout/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc"
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
  [[ "$name" =~ ^[0-9]{8}[-_][0-9]{6}_[A-Za-z0-9._-]+$ ]] || lami_qairt244_fail
  [[ "$name" != *..* ]] || lami_qairt244_fail
  [[ "$name" != */* ]] || lami_qairt244_fail
  printf '%s\n' "$name"
}

lami_qairt244_artifact_has_editable_symbol() {
  local artifact_dir="$1"
  local lib="$artifact_dir/built_libs/liblitertlm_jni.so"
  [[ -f "$lib" ]] || return 1
  readelf -Ws "$lib" 2>/dev/null | awk '
    $0 ~ /GLOBAL/ && $0 ~ /DEFAULT/ &&
      index($0, "Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt") {
        found = 1
      }
    END { exit found ? 0 : 1 }
  '
}

lami_qairt244_artifact_has_persistent_symbol() {
  local artifact_dir="$1"
  local lib="$artifact_dir/built_libs/liblitertlm_jni.so"
  [[ -f "$lib" ]] || return 1
  readelf -Ws "$lib" 2>/dev/null | awk '
    $0 ~ /GLOBAL/ && $0 ~ /DEFAULT/ &&
      index($0, "Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunPersistentProbe") {
        found = 1
      }
    END { exit found ? 0 : 1 }
  '
}

lami_qairt244_artifact_has_symbol() {
  local artifact_dir="$1"
  lami_qairt244_artifact_has_editable_symbol "$artifact_dir" || return 1
  if [[ "$QAIRT244_REQUIRE_PERSISTENT_PROBE" == "true" ]]; then
    lami_qairt244_artifact_has_persistent_symbol "$artifact_dir" || return 1
  fi
}

lami_qairt244_gpu_prefill_preinvoke_marker_file_complete() {
  local lib="$1"
  [[ -f "$lib" ]] || return 1
  lami_qairt244_string_marker_present "$lib" "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER" &&
    lami_qairt244_readelf_marker_present "$lib" "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER" &&
    lami_qairt244_exported_symbol_present "$lib" "$QAIRT244_GPU_PREFILL_PREINVOKE_C_SYMBOL" &&
    lami_qairt244_exported_symbol_present "$lib" "$QAIRT244_GPU_PREFILL_PREINVOKE_JNI_SYMBOL"
}

lami_qairt244_marker_stage_evidence_complete() {
  local artifact_dir="$1"
  local lib="$2"
  local marker_stage_log="$artifact_dir/marker_stage_diagnostics.log"
  [[ -f "$marker_stage_log" && -f "$lib" ]] || return 1

  local sha
  sha="$(lami_qairt244_sha256_for "$lib")"
  [[ -n "$sha" && "$sha" != "missing" ]] || return 1

  awk \
    -v expected_stage="artifact-after-copy-liblitertlm_jni" \
    -v expected_path="$lib" \
    -v expected_sha="$sha" \
    -v expected_marker="$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER" '
    {
      delete field
      for (i = 1; i <= NF; i += 1) {
        split($i, pair, "=")
        key = pair[1]
        value = substr($i, length(key) + 2)
        field[key] = value
      }
      if (field["stage"] == expected_stage &&
          field["kind"] == "elf" &&
          field["path"] == expected_path &&
          field["sha256"] == expected_sha &&
          field["marker"] == expected_marker &&
          field["marker_present"] == "true" &&
          field["strings_present"] == "true" &&
          field["rodata_present"] == "true" &&
          field["c_symbol_exported"] == "true" &&
          field["jni_symbol_exported"] == "true") {
        found = 1
      }
    }
    END { exit found ? 0 : 1 }
  ' "$marker_stage_log"
}

lami_qairt244_artifact_has_gpu_prefill_preinvoke_marker() {
  local artifact_dir="$1"
  local marker_lib="liblitertlm_jni.so"
  local lib="$artifact_dir/built_libs/$marker_lib"
  [[ -f "$lib" ]] || return 1
  lami_qairt244_marker_stage_evidence_complete "$artifact_dir" "$lib" ||
    lami_qairt244_gpu_prefill_preinvoke_marker_file_complete "$lib"
}

lami_qairt244_print_gpu_prefill_preinvoke_marker_evidence() {
  local artifact_dir="$1"
  local lib
  for lib in "$artifact_dir"/built_libs/*.so; do
    [[ -f "$lib" ]] || continue
    local strings_present=false
    local rodata_present=false
    local c_symbol_exported=false
    local jni_symbol_exported=false
    lami_qairt244_string_marker_present "$lib" "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER" && strings_present=true
    lami_qairt244_readelf_marker_present "$lib" "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER" && rodata_present=true
    lami_qairt244_exported_symbol_present "$lib" "$QAIRT244_GPU_PREFILL_PREINVOKE_C_SYMBOL" && c_symbol_exported=true
    lami_qairt244_exported_symbol_present "$lib" "$QAIRT244_GPU_PREFILL_PREINVOKE_JNI_SYMBOL" && jni_symbol_exported=true
    printf 'gpu_prefill_preinvoke_marker_library=%s strings_present=%s rodata_present=%s c_symbol_exported=%s jni_symbol_exported=%s\n' \
      "$(basename "$lib")" "$strings_present" "$rodata_present" "$c_symbol_exported" "$jni_symbol_exported"
  done
}

lami_qairt244_print_artifact_marker_stage_diagnostic() {
  local stage="$1"
  local file="$2"
  local sha="missing"
  local marker_present=false
  local strings_present=false
  local rodata_present=false
  local c_symbol_exported=false
  local jni_symbol_exported=false

  if [[ -f "$file" ]]; then
    sha="$(lami_qairt244_sha256_for "$file")"
    strings_present="$(lami_qairt244_bool_from_command lami_qairt244_string_marker_present "$file" "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER")"
    rodata_present="$(lami_qairt244_bool_from_command lami_qairt244_readelf_marker_present "$file" "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER")"
    c_symbol_exported="$(lami_qairt244_bool_from_command lami_qairt244_exported_symbol_present "$file" "$QAIRT244_GPU_PREFILL_PREINVOKE_C_SYMBOL")"
    jni_symbol_exported="$(lami_qairt244_bool_from_command lami_qairt244_exported_symbol_present "$file" "$QAIRT244_GPU_PREFILL_PREINVOKE_JNI_SYMBOL")"
    if [[ "$strings_present" == true && "$rodata_present" == true ]]; then
      marker_present=true
    fi
  fi

  printf 'qairt244_marker_stage stage=%s kind=elf path=%s sha256=%s marker=%s marker_present=%s strings_present=%s rodata_present=%s c_symbol_exported=%s jni_symbol_exported=%s\n' \
    "$stage" "$file" "$sha" "$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER" \
    "$marker_present" "$strings_present" "$rodata_present" \
    "$c_symbol_exported" "$jni_symbol_exported"
}

lami_qairt244_resolve_artifact_dir() {
  local requested="${1:-}"
  local candidate
  if [[ -n "$requested" ]]; then
    requested="$(lami_qairt244_validate_artifact_basename "$requested")"
    candidate="$LITERT_CUSTOM_ARTIFACT_ROOT/$requested"
    [[ -d "$candidate" ]] || lami_qairt244_fail
    lami_qairt244_artifact_has_symbol "$candidate" || {
      echo "artifact does not contain required qairt244 JNI symbols: $candidate" >&2
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

lami_qairt244_dump_customnpu_private_diag() {
  local package="io.github.ninbyo02.lami.customnpu"
  printf '\n# customnpu private diagnostics\n'
  adb devices -l || true
  for file in \
    qairt244_short_multitoken_smoke_result.txt \
    qairt244_native_diag.txt \
    qairt244_chat_screen_model_path_resolution.txt \
    qairt244_diagnostic_runner_summary.txt; do
    printf '\n===== files/%s =====\n' "$file"
    adb exec-out run-as "$package" cat "files/$file" 2>/dev/null || true
  done
  printf '\n===== logcat crash buffer =====\n'
  adb logcat -d -b crash -v time 2>/dev/null | tail -300 || true
  printf '\n===== logcat lami/native tail =====\n'
  adb logcat -d -v time 2>/dev/null | grep -Ei 'FATAL EXCEPTION|DEBUG|backtrace|tombstone|signal |Abort|liblitertlm|LiteRT|QNN|HTP|Qairt|QAIRT|lami|customnpu' | tail -300 || true
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
        status="qairt244-required-symbols-present"
      elif lami_qairt244_artifact_has_editable_symbol "$artifact"; then
        status="qairt244-editable-symbol-present"
      else
        status="qairt244-required-symbols-missing"
      fi
    fi
    printf '%s\t%s\t%s\t%s\n' "$(basename "$artifact")" "$status" "$sha" "$build_id"
  done < <(find "$LITERT_CUSTOM_ARTIFACT_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' 2>/dev/null | sort -rn | awk '{print $2}' || true)
  printf '\n# qairt244 sdk candidates\n'
  lami_qairt244_sdk_status
  lami_qairt244_dump_customnpu_private_diag || true
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
    lami_qairt244_artifact_has_symbol "$artifact_dir"
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
    echo "selected_litert_lm_ref=$LITERT_LM_REF"
    echo "selected_litert_lm_commit=$(git -C "$litert_lm_checkout" rev-parse HEAD)"
    echo "selected_litert_lm_describe=$(git -C "$litert_lm_checkout" describe --tags --always --dirty)"
    echo "qairt_root=$qairt_root"
    echo "label=$QAIRT244_BUILD_LABEL"
    echo "base_patch=$QAIRT244_PATCH"
    echo "extra_patch=${QAIRT244_EXTRA_PATCH:-<none>}"
    echo "require_persistent_probe=$QAIRT244_REQUIRE_PERSISTENT_PROBE"
    echo "required_marker=$QAIRT244_GPU_PREFILL_PREINVOKE_MARKER"
    echo "required_c_symbol=$QAIRT244_GPU_PREFILL_PREINVOKE_C_SYMBOL"
    echo "required_jni_symbol=$QAIRT244_GPU_PREFILL_PREINVOKE_JNI_SYMBOL"
    cd "$REPO"
    OUT_DIR="$artifact_dir" \
      BAZEL_OUTPUT_BASE="$HOME/project/litert-custom-build/bazel_output_base/build_$timestamp" \
      scripts/build_litert_custom_artifacts.sh \
        "$litert_lm_checkout" \
        --qairt-root "$qairt_root" \
        --label "$QAIRT244_BUILD_LABEL"
    lami_qairt244_artifact_has_symbol "$artifact_dir"
    if [[ "${QAIRT244_BUILD_LABEL}" == *gpu_prefill_preinvoke_diag* ]]; then
      lami_qairt244_print_artifact_marker_stage_diagnostic \
        "forced-command-final-liblitertlm_jni" \
        "$artifact_dir/built_libs/liblitertlm_jni.so"
      lami_qairt244_artifact_has_gpu_prefill_preinvoke_marker "$artifact_dir" || {
        lami_qairt244_print_gpu_prefill_preinvoke_marker_evidence "$artifact_dir" >&2 || true
        lami_qairt244_print_artifact_marker_stage_diagnostic \
          "forced-command-final-liblitertlm_jni-failed" \
          "$artifact_dir/built_libs/liblitertlm_jni.so" >&2 || true
        echo "artifact is missing $QAIRT244_GPU_PREFILL_PREINVOKE_MARKER in built_libs/liblitertlm_jni.so: $artifact_dir" >&2
        exit 65
      }
      lami_qairt244_print_gpu_prefill_preinvoke_marker_evidence "$artifact_dir"
      echo "gpu_prefill_preinvoke_marker_present=true"
      echo "gpu_prefill_preinvoke_marker_check=strings $artifact_dir/built_libs/liblitertlm_jni.so | grep -F $QAIRT244_GPU_PREFILL_PREINVOKE_MARKER"
      echo "gpu_prefill_preinvoke_marker_readelf_check=readelf -p .rodata $artifact_dir/built_libs/liblitertlm_jni.so | grep -F $QAIRT244_GPU_PREFILL_PREINVOKE_MARKER"
      echo "gpu_prefill_preinvoke_symbol_check=readelf -Ws $artifact_dir/built_libs/liblitertlm_jni.so | grep -E '$QAIRT244_GPU_PREFILL_PREINVOKE_C_SYMBOL|$QAIRT244_GPU_PREFILL_PREINVOKE_JNI_SYMBOL'"
    fi
    scripts/stage_litert_custom_build_stack_for_experiment.sh "${artifact_dir#$REPO/}"
    lami_qairt244_artifact_has_symbol "$artifact_dir"
    echo "== BUILD+STAGE OK =="
  } 2>&1 | tee "$log_file"

  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}

lami_qairt244_choose_adb_device() {
  local serial
  serial="$(adb devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1; exit }')"
  if [[ -z "$serial" ]]; then
    echo "no connected non-emulator Android device" >&2
    exit 65
  fi
  printf '%s\n' "$serial"
}

lami_qairt244_meminfo_metric_kb() {
  local file="$1"
  local metric="$2"
  case "$metric" in
    total_pss)
      awk '/^[[:space:]]*TOTAL[[:space:]]/ { print $2; found=1; exit } END { if (!found) print "unavailable" }' "$file" 2>/dev/null
      ;;
    native_heap_pss)
      awk '/^[[:space:]]*Native Heap[[:space:]]/ { print $3; found=1; exit } END { if (!found) print "unavailable" }' "$file" 2>/dev/null
      ;;
    dalvik_heap_pss)
      awk '/^[[:space:]]*Dalvik Heap[[:space:]]/ { print $3; found=1; exit } END { if (!found) print "unavailable" }' "$file" 2>/dev/null
      ;;
    *)
      printf 'unavailable\n'
      ;;
  esac
}

lami_qairt244_result_value() {
  local file="$1"
  local key="$2"
  awk -F= -v key="$key" '$1 == key { sub(/^[^=]*=/, ""); print; found=1; exit } END { if (!found) print "" }' "$file" 2>/dev/null
}

lami_qairt244_repeat_stability() {
  cd "$REPO"
  mkdir -p "$LOG_DIR"
  local timestamp
  timestamp="$(date +%Y%m%d-%H%M%S)"
  local out_dir="$REPO/artifacts/qairt244_repeat_stability/$timestamp"
  local log_file="$LOG_DIR/qairt244-repeat-stability-${timestamp}.log"
  mkdir -p "$out_dir"

  local package="io.github.ninbyo02.lami.customnpu"
  local action="io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION"
  local result_file="dev_only_npu_one_turn_conversation_result.txt"
  local serial
  serial="$(lami_qairt244_choose_adb_device)"
  local receiver_component="$package/io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationReceiver"

  local -a prompts=(
    "今日の予定を短く教えて"
    "雨の日の散歩の注意点は？"
    "眠気覚ましの方法を一つ教えて"
    "ありがとう"
    "こんにちは"
    "あなたは誰ですか"
    "Pythonとは何ですか"
    "Androidについて一言で説明して"
    "日本語で短く答えてください"
    "今日の挨拶をしてください"
    "ありがとう"
    "またね"
    "1+1は？"
    "短い俳句を作って"
    "明日の予定を一言で確認して"
    "雨の日の持ち物を三つ教えて"
    "バイクの点検項目を短く教えて"
    "買い物メモを一文にまとめて"
    "休憩を促す短いメッセージを書いて"
    "朝の挨拶を丁寧に書いて"
    "今日の作業を応援して"
    "安全運転の注意を一つ教えて"
    "LAMIの特徴を短く説明して"
    "最後に短く成功と言って"
  )

  {
    prompts=("こんにちは")
    echo "== LAMI qairt244 repeat stability =="
    echo "time=$(date -Is)"
    echo "repo_head=$(git rev-parse --short HEAD)"
    echo "device=$serial"
    echo "package=$package"
    echo "run_count_requested=${#prompts[@]}"
    echo "route_type=dev_only_one_turn_conversation"
    echo "prompt_tail_variant=gemma_it_user_model"
    echo "streaming=false"
    echo "tts=false"
    echo "db=false"
    echo "markdown=false"
    echo "out_dir=${out_dir#$REPO/}"
    echo "preflight_force_stop=true"
    adb -s "$serial" logcat -c >/dev/null 2>&1 || true
    adb -s "$serial" shell am force-stop "$package" >/dev/null 2>&1 || true
    sleep 2
    adb -s "$serial" shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >"$out_dir/preflight_start.txt" 2>&1 || true
    sleep 3

    adb -s "$serial" shell pidof "$package" >"$out_dir/pid_before.txt" 2>&1 || true
    adb -s "$serial" shell dumpsys meminfo "$package" >"$out_dir/meminfo_before.txt" 2>&1 || true
    adb -s "$serial" shell dumpsys package "$package" | grep -E 'DevOnlyNpuOneTurnConversationReceiver|DEV_ONLY_NPU_ONE_TURN_CONVERSATION' >"$out_dir/receiver_manifest_check.txt" 2>&1 || true
    sed 's/^/receiver_manifest_check: /' "$out_dir/receiver_manifest_check.txt" || true
    local before_total before_native before_dalvik
    before_total="$(lami_qairt244_meminfo_metric_kb "$out_dir/meminfo_before.txt" total_pss)"
    before_native="$(lami_qairt244_meminfo_metric_kb "$out_dir/meminfo_before.txt" native_heap_pss)"
    before_dalvik="$(lami_qairt244_meminfo_metric_kb "$out_dir/meminfo_before.txt" dalvik_heap_pss)"

    local success_count=0
    local failure_count=0
    local decode_count=0
    local fallback_count=0
    local timeout_count=0
    local fresh_crash_count=0
    local total_ms_sum=0
    local total_ms_count=0
    local run_index=1
    local prompt

    for prompt in "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}" "${prompts[@]}"; do
      local run_dir="$out_dir/run${run_index}"
      mkdir -p "$run_dir"
      printf '%s\n' "$prompt" >"$run_dir/prompt.txt"
      adb -s "$serial" logcat -c >/dev/null 2>&1 || true
      adb -s "$serial" shell run-as "$package" rm -f "files/$result_file" files/qairt244_short_multitoken_smoke_result.txt files/qairt244_native_diag.txt >/dev/null 2>&1 || true
      adb -s "$serial" shell am broadcast \
        -a "$action" \
        -p "$package" \
        -n "$receiver_component" \
        --es user_prompt "$prompt" \
        --ez unsafe_dev_bypass_prompt_length_gate true \
        --es prompt_tail_variant gemma_it_user_model \
        >"$run_dir/broadcast.txt" 2>&1 || true
      echo "broadcast_output_begin_run=$run_index"
      sed 's/^/broadcast: /' "$run_dir/broadcast.txt" || true

      local waited=0
      while [[ "$waited" -lt 75 ]]; do
        adb -s "$serial" exec-out run-as "$package" cat "files/$result_file" >"$run_dir/result.txt" 2>"$run_dir/result.err" || true
        local status
        status="$(lami_qairt244_result_value "$run_dir/result.txt" status)"
        if [[ "$status" == "success" || "$status" == "failure" ]]; then
          break
        fi
        sleep 1
        waited=$((waited + 1))
      done
      echo "$waited" >"$run_dir/waited_seconds.txt"
      adb -s "$serial" exec-out run-as "$package" cat files/qairt244_short_multitoken_smoke_result.txt >"$run_dir/native_result.txt" 2>"$run_dir/native_result.err" || true
      adb -s "$serial" exec-out run-as "$package" cat files/qairt244_native_diag.txt >"$run_dir/native_diag.txt" 2>"$run_dir/native_diag.err" || true
      adb -s "$serial" shell dumpsys meminfo "$package" >"$run_dir/meminfo_after.txt" 2>&1 || true

      local result success decode fallback timeout fresh_crash reason evidence sanitized elapsed
      local persistent_requested persistent_decode persistent_mode
      local run_total run_native run_dalvik waited_seconds native_call_returned native_cleanup_reached native_session_destroy_reached receiver_lifecycle receiver_finally_entered pending_result_finish_called
      result="$(lami_qairt244_result_value "$run_dir/result.txt" result)"
      success="$(lami_qairt244_result_value "$run_dir/result.txt" success)"
      decode="$(lami_qairt244_result_value "$run_dir/result.txt" run_decode_reached)"
      fallback="$(lami_qairt244_result_value "$run_dir/result.txt" fallback_used)"
      timeout="$(lami_qairt244_result_value "$run_dir/result.txt" timeout)"
      fresh_crash="$(lami_qairt244_result_value "$run_dir/result.txt" fresh_crash)"
      reason="$(lami_qairt244_result_value "$run_dir/result.txt" reason)"
      evidence="$(lami_qairt244_result_value "$run_dir/result.txt" npu_backend_evidence)"
      sanitized="$(lami_qairt244_result_value "$run_dir/result.txt" sanitized_output)"
      persistent_requested="$(lami_qairt244_result_value "$run_dir/result.txt" persistent_full_20_requested_count)"
      persistent_decode="$(lami_qairt244_result_value "$run_dir/result.txt" persistent_full_20_decode_count)"
      persistent_mode="$(lami_qairt244_result_value "$run_dir/result.txt" native_probe_mode)"
      elapsed="$(lami_qairt244_result_value "$run_dir/native_result.txt" elapsed_ms)"
      run_total="$(lami_qairt244_meminfo_metric_kb "$run_dir/meminfo_after.txt" total_pss)"
      run_native="$(lami_qairt244_meminfo_metric_kb "$run_dir/meminfo_after.txt" native_heap_pss)"
      run_dalvik="$(lami_qairt244_meminfo_metric_kb "$run_dir/meminfo_after.txt" dalvik_heap_pss)"
      waited_seconds="$(tr -d '\r\n' <"$run_dir/waited_seconds.txt" 2>/dev/null || true)"
      native_call_returned="$(lami_qairt244_result_value "$run_dir/result.txt" native_call_returned)"
      native_cleanup_reached="$(lami_qairt244_result_value "$run_dir/result.txt" native_cleanup_reached)"
      native_session_destroy_reached="$(lami_qairt244_result_value "$run_dir/result.txt" native_session_destroy_reached)"
      receiver_lifecycle="$(lami_qairt244_result_value "$run_dir/result.txt" receiver_lifecycle)"
      receiver_finally_entered="$(lami_qairt244_result_value "$run_dir/result.txt" receiver_finally_entered)"
      pending_result_finish_called="$(lami_qairt244_result_value "$run_dir/result.txt" pending_result_finish_called)"

      [[ "$success" == "true" ]] && success_count=$((success_count + 1)) || failure_count=$((failure_count + 1))
      [[ "$decode" == "true" ]] && decode_count=$((decode_count + 1))
      [[ "$fallback" == "true" ]] && fallback_count=$((fallback_count + 1))
      [[ "$timeout" == "true" ]] && timeout_count=$((timeout_count + 1))
      [[ "$fresh_crash" == "true" ]] && fresh_crash_count=$((fresh_crash_count + 1))
      if [[ "$elapsed" =~ ^[0-9]+$ ]]; then
        total_ms_sum=$((total_ms_sum + elapsed))
        total_ms_count=$((total_ms_count + 1))
      fi

      printf 'run=%s result=%s success=%s decode=%s fallback=%s timeout=%s fresh_crash=%s elapsed_ms=%s evidence=%s reason=%s persistent_mode=%s persistent_requested=%s persistent_decode=%s mem_total_pss_after_kb=%s mem_native_heap_pss_after_kb=%s mem_dalvik_heap_pss_after_kb=%s waited_seconds=%s native_call_returned=%s native_cleanup_reached=%s native_session_destroy_reached=%s receiver_lifecycle=%s receiver_finally_entered=%s pending_result_finish_called=%s output=%s\n' \
        "$run_index" "${result:-missing}" "${success:-missing}" "${decode:-missing}" \
        "${fallback:-missing}" "${timeout:-missing}" "${fresh_crash:-missing}" \
        "${elapsed:-unavailable}" "${evidence:-unavailable}" "${reason:-unavailable}" \
        "${persistent_mode:-unavailable}" "${persistent_requested:-unavailable}" "${persistent_decode:-unavailable}" \
        "${run_total:-unavailable}" "${run_native:-unavailable}" "${run_dalvik:-unavailable}" "${waited_seconds:-unavailable}" \
        "${native_call_returned:-unavailable}" "${native_cleanup_reached:-unavailable}" "${native_session_destroy_reached:-unavailable}" \
        "${receiver_lifecycle:-unavailable}" "${receiver_finally_entered:-unavailable}" "${pending_result_finish_called:-unavailable}" "${sanitized:-}" \
        | tee "$run_dir/summary_line.txt"

      if [[ -z "${result:-}" && -s "$run_dir/result.err" ]]; then
        echo "result_err_begin_run=$run_index"
        sed 's/^/result.err: /' "$run_dir/result.err" || true
      fi

      if [[ "$success" != "true" || "$decode" != "true" || "$fallback" == "true" || "$timeout" == "true" || "$fresh_crash" == "true" ]]; then
        echo "stopping_after_run=$run_index"
        adb -s "$serial" shell pidof "$package" >"$run_dir/pid_on_stop.txt" 2>&1 || true
        local stop_pid
        stop_pid="$(tr -d '\r\n' <"$run_dir/pid_on_stop.txt" 2>/dev/null || true)"
        if [[ -n "$stop_pid" ]]; then
          adb -s "$serial" shell ps -T -p "$stop_pid" >"$run_dir/threads_on_stop.txt" 2>&1 || true
        fi
        adb -s "$serial" shell dumpsys meminfo "$package" >"$run_dir/meminfo_on_stop.txt" 2>&1 || true
        adb -s "$serial" exec-out run-as "$package" cat "files/$result_file" >"$run_dir/result_on_stop.txt" 2>"$run_dir/result_on_stop.err" || true
        adb -s "$serial" exec-out run-as "$package" cat files/qairt244_short_multitoken_smoke_result.txt >"$run_dir/native_result_on_stop.txt" 2>"$run_dir/native_result_on_stop.err" || true
        adb -s "$serial" exec-out run-as "$package" cat files/qairt244_native_diag.txt >"$run_dir/native_diag_on_stop.txt" 2>"$run_dir/native_diag_on_stop.err" || true
        adb -s "$serial" logcat -d -v time | grep -Ei 'AndroidRuntime|ActivityManager|BroadcastQueue|ClassNotFound|NoClassDefFound|VerifyError|Exception|DevOnlyNpuOneTurnConversation|NpuStandardRoutePersistentProbeRunner|LamiNpuEngine|RunDecode|cleanup|session_destroy|FastRPC|QNN|HTP|LiteRT|customnpu|io.github.ninbyo02.lami' | tail -500 >"$run_dir/logcat_on_stop.txt" 2>/dev/null || true
        adb -s "$serial" logcat -d -v time | tail -220 >"$run_dir/logcat_raw_tail_on_stop.txt" 2>/dev/null || true
        sed 's/^/stop.thread: /' "$run_dir/threads_on_stop.txt" 2>/dev/null | head -80 || true
        sed 's/^/stop.result_head: /' "$run_dir/result_on_stop.txt" 2>/dev/null | head -80 || true
        sed 's/^/stop.result: /' "$run_dir/result_on_stop.txt" 2>/dev/null | tail -80 || true
        sed 's/^/stop.native_diag: /' "$run_dir/native_diag_on_stop.txt" 2>/dev/null | tail -120 || true
        sed 's/^/stop.native_result: /' "$run_dir/native_result_on_stop.txt" 2>/dev/null | tail -120 || true
        sed 's/^/stop.logcat: /' "$run_dir/logcat_on_stop.txt" 2>/dev/null | tail -120 || true
        sed 's/^/stop.rawlogcat: /' "$run_dir/logcat_raw_tail_on_stop.txt" 2>/dev/null | tail -160 || true
        break
      fi
      run_index=$((run_index + 1))
      sleep 2
    done

    adb -s "$serial" shell pidof "$package" >"$out_dir/pid_after.txt" 2>&1 || true
    adb -s "$serial" shell dumpsys meminfo "$package" >"$out_dir/meminfo_after.txt" 2>&1 || true
    adb -s "$serial" logcat -d -b crash -v time | tail -300 >"$out_dir/logcat_crash_tail.txt" 2>/dev/null || true
    adb -s "$serial" logcat -d -v time | grep -Ei 'FATAL EXCEPTION|AndroidRuntime|ActivityManager|BroadcastQueue|ClassNotFound|NoClassDefFound|VerifyError|Exception|tombstone|signal |Abort|liblitertlm|LiteRT|QNN|HTP|Qairt|QAIRT|customnpu|io.github.ninbyo02.lami' | tail -500 >"$out_dir/logcat_lami_native_tail.txt" 2>/dev/null || true

    local after_total after_native after_dalvik average_total_ms
    after_total="$(lami_qairt244_meminfo_metric_kb "$out_dir/meminfo_after.txt" total_pss)"
    after_native="$(lami_qairt244_meminfo_metric_kb "$out_dir/meminfo_after.txt" native_heap_pss)"
    after_dalvik="$(lami_qairt244_meminfo_metric_kb "$out_dir/meminfo_after.txt" dalvik_heap_pss)"
    if [[ "$total_ms_count" -gt 0 ]]; then
      average_total_ms=$((total_ms_sum / total_ms_count))
    else
      average_total_ms="unavailable"
    fi

    echo "== SUMMARY =="
    echo "run_count_requested=20"
    echo "run_count_completed=$((success_count + failure_count))"
    echo "success_count=$success_count"
    echo "failure_count=$failure_count"
    echo "run_decode_reached_count=$decode_count"
    echo "fallback_used_count=$fallback_count"
    echo "timeout_count=$timeout_count"
    echo "fresh_crash_count=$fresh_crash_count"
    echo "average_total_ms=$average_total_ms"
    echo "mem_total_pss_before_kb=$before_total"
    echo "mem_total_pss_after_kb=$after_total"
    echo "mem_native_heap_pss_before_kb=$before_native"
    echo "mem_native_heap_pss_after_kb=$after_native"
    echo "mem_dalvik_heap_pss_before_kb=$before_dalvik"
    echo "mem_dalvik_heap_pss_after_kb=$after_dalvik"
    echo "artifact=${out_dir#$REPO/}"
    if [[ "$success_count" -eq 20 && "$failure_count" -eq 0 && "$fallback_count" -eq 0 && "$timeout_count" -eq 0 && "$fresh_crash_count" -eq 0 ]]; then
      echo "== REPEAT STABILITY OK =="
    else
      echo "== REPEAT STABILITY FAILED =="
      exit 65
    fi
  } 2>&1 | tee "$log_file"

  ln -sfn "$log_file" "$LOG_DIR/latest.log"
}


lami_qairt244_validate_probe_tokens() {
  local tokens="$1"
  case "$tokens" in
    16|32|128|256|512|1024|2048|4096|8192|16384|32768)
      printf '%s\n' "$tokens" ;;
    *) lami_qairt244_fail ;;
  esac
}

lami_qairt244_validate_model_hint() {
  local hint="${1:-current}"
  case "$hint" in
    current|e2b|e4b)
      printf '%s\n' "$hint" ;;
    *) lami_qairt244_fail ;;
  esac
}

lami_qairt244_token_limit_probe() {
  local requested_tokens model_hint
  requested_tokens="$(lami_qairt244_validate_probe_tokens "$1")"
  model_hint="$(lami_qairt244_validate_model_hint "${2:-current}")"
  cd "$REPO"
  mkdir -p "$LOG_DIR"
  local timestamp out_dir log_file package action result_file serial receiver_component
  timestamp="$(date +%Y%m%d-%H%M%S)"
  out_dir="$REPO/artifacts/qairt244_token_limit_probe/$timestamp"
  log_file="$LOG_DIR/qairt244-token-limit-probe-${timestamp}-${requested_tokens}-${model_hint}.log"
  mkdir -p "$out_dir"
  package="io.github.ninbyo02.lami"
  action="io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION"
  result_file="dev_only_npu_one_turn_conversation_result.txt"
  serial="$(lami_qairt244_choose_adb_device)"
  receiver_component="$package/io.github.ninbyo02.lami.npu.DevOnlyNpuOneTurnConversationReceiver"
  {
    echo "== LAMI qairt244 token limit probe =="
    echo "time=$(date -Is)"
    echo "repo_head=$(git rev-parse --short HEAD)"
    echo "device=$serial"
    echo "package=$package"
    echo "requested_probe_tokens=$requested_tokens"
    echo "model_hint=$model_hint"
    echo "route_type=dev_only_one_turn_conversation_token_limit_probe"
    adb -s "$serial" logcat -c >/dev/null 2>&1 || true
    adb -s "$serial" shell am force-stop "$package" >/dev/null 2>&1 || true
    sleep 1
    adb -s "$serial" shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >"$out_dir/preflight_start.txt" 2>&1 || true
    sleep 2
    adb -s "$serial" shell dumpsys package "$package" | grep -E 'DevOnlyNpuOneTurnConversationReceiver|DEV_ONLY_NPU_ONE_TURN_CONVERSATION' >"$out_dir/receiver_manifest_check.txt" 2>&1 || true
    sed 's/^/receiver_manifest_check: /' "$out_dir/receiver_manifest_check.txt" || true
    adb -s "$serial" shell run-as "$package" rm -f "files/$result_file" files/qairt244_short_multitoken_smoke_result.txt files/qairt244_native_diag.txt >/dev/null 2>&1 || true
    adb -s "$serial" shell am broadcast \
      -a "$action" \
      -p "$package" \
      -n "$receiver_component" \
      --es user_prompt "こんにちは" \
      --ei max_output_tokens "$requested_tokens" \
      --ez unsafe_dev_bypass_prompt_length_gate true \
      --es prompt_tail_variant gemma_it_user_model \
      >"$out_dir/broadcast.txt" 2>&1 || true
    sed 's/^/broadcast: /' "$out_dir/broadcast.txt" || true
    local waited=0 status
    while [[ "$waited" -lt 120 ]]; do
      adb -s "$serial" exec-out run-as "$package" cat "files/$result_file" >"$out_dir/result.txt" 2>"$out_dir/result.err" || true
      status="$(lami_qairt244_result_value "$out_dir/result.txt" status)"
      if [[ "$status" == "success" || "$status" == "failure" ]]; then
        break
      fi
      sleep 1
      waited=$((waited + 1))
    done
    echo "waited_seconds=$waited"
    adb -s "$serial" exec-out run-as "$package" cat files/qairt244_short_multitoken_smoke_result.txt >"$out_dir/native_result.txt" 2>"$out_dir/native_result.err" || true
    adb -s "$serial" exec-out run-as "$package" cat files/qairt244_native_diag.txt >"$out_dir/native_diag.txt" 2>"$out_dir/native_diag.err" || true
    adb -s "$serial" shell dumpsys meminfo "$package" >"$out_dir/meminfo_after.txt" 2>&1 || true
    local result success reason requested effective limit decode fallback timeout fresh_crash native_tail
    result="$(lami_qairt244_result_value "$out_dir/result.txt" result)"
    success="$(lami_qairt244_result_value "$out_dir/result.txt" success)"
    reason="$(lami_qairt244_result_value "$out_dir/result.txt" reason)"
    requested="$(lami_qairt244_result_value "$out_dir/result.txt" requested_max_output_tokens)"
    effective="$(lami_qairt244_result_value "$out_dir/result.txt" effective_max_output_tokens)"
    limit="$(lami_qairt244_result_value "$out_dir/result.txt" native_max_output_tokens_limit)"
    decode="$(lami_qairt244_result_value "$out_dir/result.txt" run_decode_reached)"
    fallback="$(lami_qairt244_result_value "$out_dir/result.txt" fallback_used)"
    timeout="$(lami_qairt244_result_value "$out_dir/result.txt" timeout)"
    fresh_crash="$(lami_qairt244_result_value "$out_dir/result.txt" fresh_crash)"
    native_tail="$(tail -20 "$out_dir/native_diag.txt" 2>/dev/null | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g')"
    printf 'result=%s success=%s reason=%s requested=%s effective=%s native_limit=%s decode=%s fallback=%s timeout=%s fresh_crash=%s model_hint=%s artifact=%s\n' \
      "${result:-missing}" "${success:-missing}" "${reason:-unavailable}" \
      "${requested:-unavailable}" "${effective:-unavailable}" "${limit:-unavailable}" \
      "${decode:-missing}" "${fallback:-missing}" "${timeout:-missing}" "${fresh_crash:-missing}" \
      "$model_hint" "${out_dir#$REPO/}"
    echo "native_diag_tail=$native_tail"
    if [[ "$success" != "true" || "$decode" != "true" || "$fallback" == "true" || "$timeout" == "true" || "$fresh_crash" == "true" ]]; then
      echo "== TOKEN LIMIT PROBE FAILED =="
      exit 65
    fi
    echo "== TOKEN LIMIT PROBE OK =="
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
    qairt244-repeat-stability)
      lami_qairt244_repeat_stability
      return 0
      ;;
    qairt244-token-limit-probe*)
      parts=($command)
      [[ "${#parts[@]}" -ge 2 && "${#parts[@]}" -le 3 ]] || lami_qairt244_fail
      lami_qairt244_token_limit_probe "${parts[1]}" "${parts[2]:-current}"
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
  qairt244-repeat-stability
  qairt244-token-limit-probe <16|32|128|256|512|1024|2048|4096|8192|16384|32768> [current|e2b|e4b]
EOF
}
