#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_CHECKOUT="${LITERT_LM_SOURCE_CHECKOUT:-$HOME/project/litert-custom-build/LiteRT-LM}"
SELECTED_REF="${LITERT_LM_REF:-v0.11.0}"
EXPECTED_COMMIT="${LITERT_LM_EXPECTED_COMMIT:-c87189528a758db32ead241f4fc9c64836398ee7}"
BASE_PATCH="${QAIRT244_PATCH:-$ROOT_DIR/patches/qairt244_litertlm_utf8_128token.patch}"
EXTRA_PATCH="${QAIRT244_EXTRA_PATCH:-$ROOT_DIR/patches/qairt244_litertlm_utf8_128token_persistent_probe.patch}"
CONVERSATION_PATCH="${QAIRT244_CONVERSATION_PATCH:-$ROOT_DIR/patches/qairt244_litertlm_conversation_api_probe.patch}"
QAIRT_ROOT="${QAIRT244_ROOT:-$HOME/compose/qairt/workspace/sdk/qairt/2.44.0.260225}"
LABEL="${QAIRT244_BUILD_LABEL:-qairt244_128token_persistent_probe_repro}"
REQUIRE_PERSISTENT_PROBE="${QAIRT244_REQUIRE_PERSISTENT_PROBE:-true}"
WORKTREE_ROOT="${LITERT_LM_WORKTREE_ROOT:-$HOME/.cache/lami-qairt244-worktrees}"
ANDROID_HOME_VALUE="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ANDROID_NDK_HOME_VALUE="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-$ANDROID_HOME_VALUE/ndk/28.2.13676358}}"
ARTIFACT_DIR=""
KEEP_WORKTREE=false
KEEP_BAZEL_OUTPUT_BASE=false
SKIP_STAGE=false
PREFLIGHT_ONLY=false
REPRO_BAZEL_OUTPUT_BASE="${BAZEL_OUTPUT_BASE:-}"
BAZEL_OUTPUT_BASE_OWNED=false
BAZEL_COMMAND=""

usage() {
  cat <<'USAGE'
Usage:
  scripts/rebuild_qairt244_standard_debug_native_stack.sh [options]

Options:
  --source-checkout PATH  Source/mirror LiteRT-LM checkout. Its working tree is never reset or cleaned.
  --selected-ref REF      Fetchable LiteRT-LM ref. Default: v0.11.0.
  --expected-commit SHA   Required commit resolved by --selected-ref.
  --base-patch PATH       Base qairt244 JNI patch.
  --extra-patch PATH      Follow-up persistent-probe patch; pass an empty string to disable it.
  --conversation-patch PATH
                          Isolated C++ Conversation API probe patch; pass an empty string to disable it.
  --qairt-root PATH       Exact QAIRT 2.44 SDK root.
  --label LABEL           Artifact directory label.
  --require-persistent-probe BOOL
                          Require persistent-probe source markers and GLOBAL JNI exports.
  --artifact-dir PATH     Explicit output directory under this repository's artifacts/ tree.
  --keep-worktree         Keep the isolated patched worktree for inspection.
  --keep-bazel-output-base
                          Keep the temporary clean Bazel output base for inspection.
  --skip-stage            Build and verify without copying libraries into app source sets.
  --preflight-only        Verify pin and patch application in isolation without building.
  --help                  Show this help.

The script creates a detached temporary Git worktree at the pinned commit,
applies patches only there, builds the limited native targets, verifies the
separated SONAME and JNI exports, and stages only verified outputs.
USAGE
}

while (($#)); do
  case "$1" in
    --source-checkout) SOURCE_CHECKOUT="${2:?missing path}"; shift 2 ;;
    --selected-ref) SELECTED_REF="${2:?missing ref}"; shift 2 ;;
    --expected-commit) EXPECTED_COMMIT="${2:?missing sha}"; shift 2 ;;
    --base-patch) BASE_PATCH="${2:?missing path}"; shift 2 ;;
    --extra-patch) EXTRA_PATCH="${2-}"; shift 2 ;;
    --conversation-patch) CONVERSATION_PATCH="${2-}"; shift 2 ;;
    --qairt-root) QAIRT_ROOT="${2:?missing path}"; shift 2 ;;
    --label) LABEL="${2:?missing label}"; shift 2 ;;
    --require-persistent-probe) REQUIRE_PERSISTENT_PROBE="${2:?missing boolean}"; shift 2 ;;
    --artifact-dir) ARTIFACT_DIR="${2:?missing path}"; shift 2 ;;
    --keep-worktree) KEEP_WORKTREE=true; shift ;;
    --keep-bazel-output-base) KEEP_BAZEL_OUTPUT_BASE=true; shift ;;
    --skip-stage) SKIP_STAGE=true; shift ;;
    --preflight-only) PREFLIGHT_ONLY=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'ERROR: unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 65
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command is unavailable: $1"
}

sha_for() {
  sha256sum "$1" | awk '{print $1}'
}

status_hash_for() {
  git -C "$1" status --porcelain=v1 -z --untracked-files=all | sha256sum | awk '{print $1}'
}

worktree_fingerprint_for() {
  local checkout="$1"
  {
    printf 'head\0'
    git -C "$checkout" rev-parse HEAD
    printf 'tracked-diff\0'
    git -C "$checkout" diff --binary --no-ext-diff HEAD --
    printf 'untracked-files\0'
    while IFS= read -r -d '' relative_path; do
      local full_path="$checkout/$relative_path"
      printf '%s\0' "$relative_path"
      if [[ -L "$full_path" ]]; then
        printf 'symlink\0%s\0' "$(readlink "$full_path")"
      elif [[ -f "$full_path" ]]; then
        printf 'file\0%s\0' "$(sha_for "$full_path")"
      else
        printf 'other\0%s\0' "$(stat -c '%F:%s:%a' "$full_path" 2>/dev/null || printf missing)"
      fi
    done < <(git -C "$checkout" ls-files --others --exclude-standard -z | sort -z)
  } | sha256sum | awk '{print $1}'
}

soname_for() {
  readelf -d "$1" 2>/dev/null | sed -n 's/.*Library soname: \[\(.*\)\].*/\1/p' | head -n 1
}

build_id_for() {
  readelf -n "$1" 2>/dev/null | awk '/Build ID:/ && !found {print $3; found = 1}'
}

require_exported_symbol() {
  local file="$1"
  local symbol="$2"
  readelf -Ws "$file" 2>/dev/null | awk -v symbol="$symbol" '
    $0 ~ /GLOBAL/ && $0 ~ /DEFAULT/ && index($0, symbol) { found = 1 }
    END { exit found ? 0 : 1 }
  ' || fail "required GLOBAL JNI symbol is missing from $(basename "$file"): $symbol"
}

require_command git
require_command sha256sum
require_command readelf
require_command strings
require_command nm
require_command patchelf
require_command realpath
require_command readlink
require_command sort
require_command stat

git -C "$SOURCE_CHECKOUT" rev-parse --is-inside-work-tree >/dev/null 2>&1 ||
  fail "LiteRT-LM source checkout is not a Git worktree: $SOURCE_CHECKOUT"
[[ -f "$BASE_PATCH" ]] || fail "base patch is missing: $BASE_PATCH"
[[ -z "$EXTRA_PATCH" || -f "$EXTRA_PATCH" ]] || fail "extra patch is missing: $EXTRA_PATCH"
[[ -z "$CONVERSATION_PATCH" || -f "$CONVERSATION_PATCH" ]] || fail "conversation patch is missing: $CONVERSATION_PATCH"
[[ -d "$QAIRT_ROOT" ]] || fail "QAIRT root is missing: $QAIRT_ROOT"
[[ "$(basename "$QAIRT_ROOT")" == "2.44.0.260225" ]] || fail "QAIRT root must resolve to 2.44.0.260225: $QAIRT_ROOT"
[[ -d "$ANDROID_HOME_VALUE" ]] || fail "Android SDK is missing: $ANDROID_HOME_VALUE"
[[ -d "$ANDROID_NDK_HOME_VALUE" ]] || fail "Android NDK is missing: $ANDROID_NDK_HOME_VALUE"
case "$REQUIRE_PERSISTENT_PROBE" in
  true|false) ;;
  *) fail "--require-persistent-probe must be true or false: $REQUIRE_PERSISTENT_PROBE" ;;
esac

SOURCE_CHECKOUT="$(realpath "$SOURCE_CHECKOUT")"
BASE_PATCH="$(realpath "$BASE_PATCH")"
[[ -z "$EXTRA_PATCH" ]] || EXTRA_PATCH="$(realpath "$EXTRA_PATCH")"
[[ -z "$CONVERSATION_PATCH" ]] || CONVERSATION_PATCH="$(realpath "$CONVERSATION_PATCH")"
QAIRT_ROOT="$(realpath "$QAIRT_ROOT")"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
if [[ -z "$ARTIFACT_DIR" ]]; then
  ARTIFACT_DIR="$ROOT_DIR/artifacts/litert_custom_build/${TIMESTAMP}_${LABEL}"
fi
mkdir -p "$ARTIFACT_DIR" "$WORKTREE_ROOT"
ARTIFACT_DIR="$(realpath "$ARTIFACT_DIR")"
ROOT_REAL="$(realpath "$ROOT_DIR")"
case "$ARTIFACT_DIR" in
  "$ROOT_REAL"/artifacts/*) ;;
  *) fail "artifact directory must stay under $ROOT_REAL/artifacts: $ARTIFACT_DIR" ;;
esac

RUN_LOG="$ARTIFACT_DIR/reproducible_build.log"
exec > >(tee -a "$RUN_LOG") 2>&1

SOURCE_HEAD_BEFORE="$(git -C "$SOURCE_CHECKOUT" rev-parse HEAD)"
SOURCE_STATUS_HASH_BEFORE="$(status_hash_for "$SOURCE_CHECKOUT")"
SOURCE_WORKTREE_FINGERPRINT_BEFORE="$(worktree_fingerprint_for "$SOURCE_CHECKOUT")"
WORKTREE_DIR="$WORKTREE_ROOT/${TIMESTAMP}_$$"
WORKTREE_REGISTERED=false

cleanup() {
  local rc=$?
  trap - EXIT
  local bazel_output_base_removed=not_created
  if [[ "$BAZEL_OUTPUT_BASE_OWNED" == true && -n "$REPRO_BAZEL_OUTPUT_BASE" ]]; then
    bazel_output_base_removed=false
    if [[ "$KEEP_BAZEL_OUTPUT_BASE" != true ]]; then
      if [[ -n "$BAZEL_COMMAND" ]]; then
        "$BAZEL_COMMAND" --output_base="$REPRO_BAZEL_OUTPUT_BASE" shutdown >/dev/null 2>&1 || true
      fi
      case "$REPRO_BAZEL_OUTPUT_BASE" in
        "$HOME"/project/litert-custom-build/bazel_output_base/repro_*)
          chmod -R u+rwX -- "$REPRO_BAZEL_OUTPUT_BASE" 2>/dev/null || true
          rm -rf -- "$REPRO_BAZEL_OUTPUT_BASE" || true
          [[ ! -e "$REPRO_BAZEL_OUTPUT_BASE" ]] && bazel_output_base_removed=true
          ;;
        *)
          printf 'WARNING: refusing to remove unexpected owned Bazel output base: %s\n' "$REPRO_BAZEL_OUTPUT_BASE" >&2
          ;;
      esac
    fi
  fi
  if [[ "$WORKTREE_REGISTERED" == true && "$KEEP_WORKTREE" != true ]]; then
    git -C "$WORKTREE_DIR" worktree remove --force "$WORKTREE_DIR" >/dev/null 2>&1 || true
  fi
  local source_head_after source_status_hash_after source_worktree_fingerprint_after source_worktree_unchanged
  source_head_after="$(git -C "$SOURCE_CHECKOUT" rev-parse HEAD 2>/dev/null || printf unavailable)"
  source_status_hash_after="$(status_hash_for "$SOURCE_CHECKOUT" 2>/dev/null || printf unavailable)"
  source_worktree_fingerprint_after="$(worktree_fingerprint_for "$SOURCE_CHECKOUT" 2>/dev/null || printf unavailable)"
  source_worktree_unchanged=false
  if [[ "$SOURCE_HEAD_BEFORE" == "$source_head_after" &&
        "$SOURCE_STATUS_HASH_BEFORE" == "$source_status_hash_after" &&
        "$SOURCE_WORKTREE_FINGERPRINT_BEFORE" == "$source_worktree_fingerprint_after" ]]; then
    source_worktree_unchanged=true
  fi
  if [[ "$source_worktree_unchanged" != true ]]; then
    printf 'ERROR: source checkout content changed during isolated build: %s\n' "$SOURCE_CHECKOUT" >&2
    rc=70
  fi
  if [[ "$BAZEL_OUTPUT_BASE_OWNED" == true &&
        "$KEEP_BAZEL_OUTPUT_BASE" != true &&
        "$bazel_output_base_removed" != true ]]; then
    printf 'ERROR: temporary Bazel output base could not be removed: %s\n' "$REPRO_BAZEL_OUTPUT_BASE" >&2
    if [[ "$rc" -eq 0 ]]; then
      rc=71
    fi
  fi
  {
    printf 'source_head_before=%s\n' "$SOURCE_HEAD_BEFORE"
    printf 'source_head_after=%s\n' "$source_head_after"
    printf 'source_status_hash_before=%s\n' "$SOURCE_STATUS_HASH_BEFORE"
    printf 'source_status_hash_after=%s\n' "$source_status_hash_after"
    printf 'source_worktree_fingerprint_before=%s\n' "$SOURCE_WORKTREE_FINGERPRINT_BEFORE"
    printf 'source_worktree_fingerprint_after=%s\n' "$source_worktree_fingerprint_after"
    printf 'source_worktree_unchanged=%s\n' "$source_worktree_unchanged"
    printf 'isolated_worktree=%s\n' "$WORKTREE_DIR"
    printf 'isolated_worktree_kept=%s\n' "$KEEP_WORKTREE"
    printf 'bazel_output_base=%s\n' "${REPRO_BAZEL_OUTPUT_BASE:-not_created}"
    printf 'bazel_output_base_owned=%s\n' "$BAZEL_OUTPUT_BASE_OWNED"
    printf 'bazel_output_base_kept=%s\n' "$KEEP_BAZEL_OUTPUT_BASE"
    printf 'bazel_output_base_removed=%s\n' "$bazel_output_base_removed"
    printf 'exit_code=%s\n' "$rc"
  } >"$ARTIFACT_DIR/source_checkout_integrity.txt"
  printf 'artifact_dir=%s\n' "$ARTIFACT_DIR"
  exit "$rc"
}
trap cleanup EXIT

printf '== QAIRT244 reproducible native build ==\n'
printf 'time=%s\n' "$(date -Is)"
printf 'source_checkout=%s\n' "$SOURCE_CHECKOUT"
printf 'selected_ref=%s\n' "$SELECTED_REF"
printf 'expected_commit=%s\n' "$EXPECTED_COMMIT"
printf 'base_patch=%s\n' "$BASE_PATCH"
printf 'extra_patch=%s\n' "${EXTRA_PATCH:-<none>}"
printf 'conversation_patch=%s\n' "${CONVERSATION_PATCH:-<none>}"
printf 'qairt_root=%s\n' "$QAIRT_ROOT"
printf 'artifact_dir=%s\n' "$ARTIFACT_DIR"

git -C "$SOURCE_CHECKOUT" fetch --tags origin
SELECTED_COMMIT="$(git -C "$SOURCE_CHECKOUT" rev-parse --verify "${SELECTED_REF}^{commit}")"
[[ "$SELECTED_COMMIT" == "$EXPECTED_COMMIT" ]] || fail "selected ref resolved to $SELECTED_COMMIT, expected $EXPECTED_COMMIT"
if ! git -C "$SOURCE_CHECKOUT" for-each-ref --contains "$SELECTED_COMMIT" refs/remotes/origin refs/tags | grep . >/dev/null; then
  fail "selected commit is not reachable from fetched origin refs/tags: $SELECTED_COMMIT"
fi

GIT_LFS_SKIP_SMUDGE=1 git -C "$SOURCE_CHECKOUT" worktree add --detach "$WORKTREE_DIR" "$SELECTED_COMMIT"
WORKTREE_REGISTERED=true
[[ "$(git -C "$WORKTREE_DIR" rev-parse HEAD)" == "$SELECTED_COMMIT" ]] || fail "isolated worktree HEAD mismatch"

PROVIDER_REL="prebuilt/android_arm64/libGemmaModelConstraintProvider.so"
PROVIDER_FILE="$WORKTREE_DIR/$PROVIDER_REL"
[[ -f "$PROVIDER_FILE" ]] || fail "selected ref is missing the Gemma provider: $PROVIDER_FILE"
if head -n 1 "$PROVIDER_FILE" 2>/dev/null | grep -q 'git-lfs.github.com/spec'; then
  git -C "$WORKTREE_DIR" lfs checkout "$PROVIDER_REL" >/dev/null 2>&1 || true
fi
if head -n 1 "$PROVIDER_FILE" 2>/dev/null | grep -q 'git-lfs.github.com/spec'; then
  POINTER_OID="$(sed -n 's/^oid sha256://p' "$PROVIDER_FILE" | head -n 1)"
  SOURCE_PROVIDER="$SOURCE_CHECKOUT/$PROVIDER_REL"
  [[ -f "$SOURCE_PROVIDER" ]] || fail "materialized provider fallback is missing: $SOURCE_PROVIDER"
  SOURCE_PROVIDER_SHA="$(sha_for "$SOURCE_PROVIDER")"
  [[ "$POINTER_OID" == "$SOURCE_PROVIDER_SHA" ]] || fail "source provider SHA does not match selected-ref LFS pointer"
  cp -f "$SOURCE_PROVIDER" "$PROVIDER_FILE"
fi
if head -n 1 "$PROVIDER_FILE" 2>/dev/null | grep -q 'git-lfs.github.com/spec'; then
  fail "LiteRT-LM LFS provider was not materialized: $PROVIDER_FILE"
fi

apply_patch() {
  local patch="$1"
  local label="$2"
  git -C "$WORKTREE_DIR" apply --check "$patch" || fail "$label patch does not apply to $SELECTED_REF"
  git -C "$WORKTREE_DIR" apply "$patch"
  printf '%s_patch_sha256=%s\n' "$label" "$(sha_for "$patch")"
}

apply_patch "$BASE_PATCH" base
if [[ -n "$EXTRA_PATCH" ]]; then
  apply_patch "$EXTRA_PATCH" extra
fi
if [[ -n "$CONVERSATION_PATCH" ]]; then
  apply_patch "$CONVERSATION_PATCH" conversation
fi
git -C "$WORKTREE_DIR" diff --check

JNI_SOURCE="$WORKTREE_DIR/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc"
grep -Fq 'Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt' "$JNI_SOURCE" || fail "editable prompt JNI source marker is missing"
if [[ "$REQUIRE_PERSISTENT_PROBE" == true ]]; then
  grep -Fq 'Qairt244ShortMultitokenSmoke_nativeRunPersistentProbe' "$JNI_SOURCE" || fail "persistent probe JNI source marker is missing"
  grep -Fq 'Qairt244ShortMultitokenSmoke_nativeRunEditableEngineCreateOnlyMinimal' "$JNI_SOURCE" || fail "engine-create JNI source marker is missing"
fi
if [[ -n "$CONVERSATION_PATCH" ]]; then
  grep -Fq 'Qairt244ShortMultitokenSmoke_nativeRunConversationApiProbe' "$JNI_SOURCE" || fail "Conversation API JNI source marker is missing"
fi

{
  printf 'key\tvalue\n'
  printf 'selected_ref\t%s\n' "$SELECTED_REF"
  printf 'selected_commit\t%s\n' "$SELECTED_COMMIT"
  printf 'expected_commit\t%s\n' "$EXPECTED_COMMIT"
  printf 'source_checkout\t%s\n' "$SOURCE_CHECKOUT"
  printf 'source_head_before\t%s\n' "$SOURCE_HEAD_BEFORE"
  printf 'source_status_hash_before\t%s\n' "$SOURCE_STATUS_HASH_BEFORE"
  printf 'source_worktree_fingerprint_before\t%s\n' "$SOURCE_WORKTREE_FINGERPRINT_BEFORE"
  printf 'isolated_worktree\t%s\n' "$WORKTREE_DIR"
  printf 'base_patch\t%s\n' "$BASE_PATCH"
  printf 'base_patch_sha256\t%s\n' "$(sha_for "$BASE_PATCH")"
  printf 'extra_patch\t%s\n' "${EXTRA_PATCH:-none}"
  [[ -z "$EXTRA_PATCH" ]] || printf 'extra_patch_sha256\t%s\n' "$(sha_for "$EXTRA_PATCH")"
  printf 'conversation_patch\t%s\n' "${CONVERSATION_PATCH:-none}"
  [[ -z "$CONVERSATION_PATCH" ]] || printf 'conversation_patch_sha256\t%s\n' "$(sha_for "$CONVERSATION_PATCH")"
  printf 'provider_sha256\t%s\n' "$(sha_for "$PROVIDER_FILE")"
  printf 'require_persistent_probe\t%s\n' "$REQUIRE_PERSISTENT_PROBE"
  printf 'qairt_root\t%s\n' "$QAIRT_ROOT"
  printf 'android_home\t%s\n' "$ANDROID_HOME_VALUE"
  printf 'android_ndk_home\t%s\n' "$ANDROID_NDK_HOME_VALUE"
  printf 'patchelf\t%s\n' "$(patchelf --version | head -n 1)"
  printf 'bazel_output_base_policy\t%s\n' "ephemeral-clean-output-base"
} >"$ARTIFACT_DIR/reproducibility_inputs.tsv"

echo "preflight_status=ok"
if [[ "$PREFLIGHT_ONLY" == true ]]; then
  exit 0
fi

if [[ -z "$REPRO_BAZEL_OUTPUT_BASE" ]]; then
  REPRO_BAZEL_OUTPUT_BASE="$HOME/project/litert-custom-build/bazel_output_base/repro_${TIMESTAMP}_$$"
  BAZEL_OUTPUT_BASE_OWNED=true
fi
if [[ -x "$HOME/.local/bin/bazelisk" ]]; then
  BAZEL_COMMAND="$HOME/.local/bin/bazelisk"
else
  BAZEL_COMMAND="$(command -v bazelisk 2>/dev/null || command -v bazel 2>/dev/null || true)"
fi
[[ -n "$BAZEL_COMMAND" ]] || fail "bazelisk or bazel is required for the native build"
PATH="$HOME/.local/bin:$PATH" \
ANDROID_HOME="$ANDROID_HOME_VALUE" \
ANDROID_SDK_ROOT="$ANDROID_HOME_VALUE" \
ANDROID_NDK_HOME="$ANDROID_NDK_HOME_VALUE" \
OUT_DIR="$ARTIFACT_DIR" \
BAZEL_OUTPUT_BASE="$REPRO_BAZEL_OUTPUT_BASE" \
"$ROOT_DIR/scripts/build_litert_custom_artifacts.sh" \
  "$WORKTREE_DIR" \
  --qairt-root "$QAIRT_ROOT" \
  --label "$LABEL"

[[ -s "$ARTIFACT_DIR/build_results.tsv" ]] || fail "native target result file is missing"
if awk -F '\t' '$2 != 0 { bad = 1 } END { exit bad ? 0 : 1 }' "$ARTIFACT_DIR/build_results.tsv"; then
  cat "$ARTIFACT_DIR/build_results.tsv" >&2
  fail "one or more limited Bazel targets failed"
fi

NPU_JNI="$ARTIFACT_DIR/built_libs/liblami_qairt244_npu_jni.so"
LITERTLM_JNI="$ARTIFACT_DIR/built_libs/liblitertlm_jni.so"
[[ -f "$NPU_JNI" ]] || fail "separated qairt244 JNI artifact is missing: $NPU_JNI"
[[ -f "$LITERTLM_JNI" ]] || fail "base LiteRT-LM JNI artifact is missing: $LITERTLM_JNI"
[[ "$(soname_for "$NPU_JNI")" == "liblami_qairt244_npu_jni.so" ]] || fail "separated qairt244 JNI SONAME is invalid"
[[ "$(patchelf --print-soname "$NPU_JNI")" == "liblami_qairt244_npu_jni.so" ]] || fail "patchelf SONAME verification failed"

require_exported_symbol "$NPU_JNI" 'Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditablePrompt'
if [[ "$REQUIRE_PERSISTENT_PROBE" == true ]]; then
  require_exported_symbol "$NPU_JNI" 'Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunPersistentProbe'
  require_exported_symbol "$NPU_JNI" 'Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditableEngineCreateOnlyMinimal'
  for marker in \
    'sampler_config_profile=lami_stable_v1' \
    'sampler_backend=NPU' \
    'prompt_input_code_points=' \
    'sampler_top_k=40' \
    'sampler_top_p=0.9' \
    'sampler_temperature=0.3' \
    'sampler_seed=42' \
    'thinking_control=raw_prompt_answer_only' \
    'dispatch_api_preflight' \
    'dispatch_preflight_get_api_status=' \
    'dispatch_preflight_api_version=' \
    'dispatch_initialize_status=' \
    'dispatch_initialize_device_context_status='; do
    strings "$NPU_JNI" | grep -F "$marker" >/dev/null ||
      fail "stable NPU conversation policy marker is missing: $marker"
  done
fi
if [[ -n "$CONVERSATION_PATCH" ]]; then
  require_exported_symbol "$NPU_JNI" 'Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunConversationApiProbe'
  for marker in \
    'qairt244_conversation_api_probe_v5' \
    'conversation_api_used=true' \
    'conversation_api_surface=C++' \
    'app_template_used=false' \
    'model_template_source=model_metadata' \
    'greedy_top_k_1_v1' \
    'unsupported-sampler-profile' \
    'rehydrate_each_turn_v1' \
    'rehydrate_last_3_turns_v1' \
    'rehydrate_last_4_turns_v1' \
    'unsupported-conversation-state-profile'; do
    strings "$NPU_JNI" | grep -F "$marker" >/dev/null ||
      fail "Conversation API probe marker is missing: $marker"
  done
fi
if [[ "$(basename "${EXTRA_PATCH:-none}")" == "qairt244_litertlm_gpu_prefill_preinvoke_diag.patch" ]]; then
  strings "$NPU_JNI" | grep -F 'qairt244_gpu_prefill_preinvoke_v1' >/dev/null || fail "GPU prefill marker string is missing"
  require_exported_symbol "$NPU_JNI" 'Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244GpuPrefillPreinvokeArtifactMarker_nativeMarker'
fi

{
  printf 'library\tsize\tsha256\tbuild_id\tsoname\n'
  for file in "$ARTIFACT_DIR"/built_libs/*.so; do
    [[ -f "$file" ]] || continue
    printf '%s\t%s\t%s\t%s\t%s\n' \
      "$(basename "$file")" "$(wc -c <"$file" | tr -d ' ')" \
      "$(sha_for "$file")" "$(build_id_for "$file")" "$(soname_for "$file")"
  done
} >"$ARTIFACT_DIR/reproducibility_outputs.tsv"

if [[ "$SKIP_STAGE" != true ]]; then
  ARTIFACT_REL="${ARTIFACT_DIR#"$ROOT_REAL"/}"
  "$ROOT_DIR/scripts/stage_litert_custom_build_stack_for_experiment.sh" "$ARTIFACT_REL"
  STAGED_NPU_JNI="$ROOT_DIR/app/src/customBuildExperimentDebug/jniLibs/arm64-v8a/liblami_qairt244_npu_jni.so"
  [[ -f "$STAGED_NPU_JNI" ]] || fail "verified separated JNI was not staged"
  [[ "$(sha_for "$STAGED_NPU_JNI")" == "$(sha_for "$NPU_JNI")" ]] || fail "staged separated JNI SHA does not match artifact"
  [[ "$(soname_for "$STAGED_NPU_JNI")" == "liblami_qairt244_npu_jni.so" ]] || fail "staged separated JNI SONAME is invalid"
  printf 'stage_status=ok\n' >"$ARTIFACT_DIR/reproducibility_stage.txt"
  printf 'staged_file=%s\n' "$STAGED_NPU_JNI" >>"$ARTIFACT_DIR/reproducibility_stage.txt"
  printf 'staged_sha256=%s\n' "$(sha_for "$STAGED_NPU_JNI")" >>"$ARTIFACT_DIR/reproducibility_stage.txt"
else
  printf 'stage_status=skipped\n' >"$ARTIFACT_DIR/reproducibility_stage.txt"
fi

echo "build_status=ok"
echo "stage_status=$([[ "$SKIP_STAGE" == true ]] && printf skipped || printf ok)"
