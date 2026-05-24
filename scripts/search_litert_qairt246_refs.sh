#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LITERT_DIR="${1:-$HOME/project/litert-custom-build/LiteRT}"
LITERT_LM_DIR="${2:-$HOME/project/litert-custom-build/LiteRT-LM}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/artifacts/litert_qairt246_ref_search/$TIMESTAMP}"

log() {
  printf '[qairt246-ref-search] %s\n' "$*"
}

run_capture() {
  local name="$1"
  shift
  local out="$OUT_DIR/$name"
  {
    printf '$'
    printf ' %q' "$@"
    printf '\n\n'
    "$@"
    local code=$?
    printf '\nexit_code=%s\n' "$code"
    return "$code"
  } >"$out" 2>&1
}

run_shell_capture() {
  local name="$1"
  local script="$2"
  local out="$OUT_DIR/$name"
  {
    printf '$ %s\n\n' "$script"
    /bin/bash -lc "$script"
    local code=$?
    printf '\nexit_code=%s\n' "$code"
    return "$code"
  } >"$out" 2>&1
}

append_result() {
  local name="$1"
  local code="$2"
  printf '%s\t%s\n' "$name" "$code" >>"$OUT_DIR/results.tsv"
}

mkdir -p "$OUT_DIR"
: >"$OUT_DIR/results.tsv"

{
  printf 'LiteRT dir: %s\n' "$LITERT_DIR"
  printf 'LiteRT-LM dir: %s\n' "$LITERT_LM_DIR"
  printf 'Output dir: %s\n' "$OUT_DIR"
  printf 'Build executed: no\n'
  printf 'App launched: no\n'
  printf 'Native artifacts generated: no\n'
} >"$OUT_DIR/search_env.txt"

if [ ! -d "$LITERT_DIR/.git" ]; then
  log "missing LiteRT checkout: $LITERT_DIR"
  printf 'missing LiteRT checkout: %s\n' "$LITERT_DIR" >"$OUT_DIR/ERROR.txt"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

if [ ! -d "$LITERT_LM_DIR/.git" ]; then
  log "missing LiteRT-LM checkout: $LITERT_LM_DIR"
  printf 'missing LiteRT-LM checkout: %s\n' "$LITERT_LM_DIR" >"$OUT_DIR/ERROR.txt"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

log "output: $OUT_DIR"

run_capture "litert_fetch_tags.txt" git -C "$LITERT_DIR" fetch --tags origin
append_result "litert_fetch_tags" "$?"

run_capture "litertlm_fetch_tags.txt" git -C "$LITERT_LM_DIR" fetch --tags origin
append_result "litertlm_fetch_tags" "$?"

run_capture "litert_fetch_main.txt" git -C "$LITERT_DIR" fetch origin main:refs/remotes/origin/main
append_result "litert_fetch_main" "$?"

run_capture "litertlm_fetch_main.txt" git -C "$LITERT_LM_DIR" fetch origin main:refs/remotes/origin/main
append_result "litertlm_fetch_main" "$?"

{
  printf '# LiteRT repository\n\n'
  printf 'path: %s\n' "$LITERT_DIR"
  printf 'HEAD: '
  git -C "$LITERT_DIR" rev-parse HEAD 2>/dev/null || true
  printf 'describe: '
  git -C "$LITERT_DIR" describe --tags --always --dirty 2>/dev/null || true
  printf 'origin/main: '
  git -C "$LITERT_DIR" rev-parse origin/main 2>/dev/null || true
  printf '\n# LiteRT-LM repository\n\n'
  printf 'path: %s\n' "$LITERT_LM_DIR"
  printf 'HEAD: '
  git -C "$LITERT_LM_DIR" rev-parse HEAD 2>/dev/null || true
  printf 'describe: '
  git -C "$LITERT_LM_DIR" describe --tags --always --dirty 2>/dev/null || true
  printf 'origin/main: '
  git -C "$LITERT_LM_DIR" rev-parse origin/main 2>/dev/null || true
} >"$OUT_DIR/repo_heads.txt"

LITERT_QAIRT_PATHS=(
  "ci/tools/python/vendor_sdk/qualcomm/setup.py"
  "third_party/qairt/workspace.bzl"
  "litert/vendors/CMakeLists.txt"
  "litert/version.bzl"
  "litert/google/npu_runtime_libraries/fetch_qualcomm_library.sh"
  "litert/google/npu_runtime_libraries/fetch_qualcomm_library_jit.sh"
)

LITERT_QAIRT_PATHS_QUOTED=""
for path in "${LITERT_QAIRT_PATHS[@]}"; do
  LITERT_QAIRT_PATHS_QUOTED+=" '$path'"
done

run_shell_capture \
  "litert_current_qairt_metadata.txt" \
  "git -C '$LITERT_DIR' grep -n -E '2\\.46\\.0\\.260424|2\\.46|260424|260424121129|2\\.45|2\\.44\\.0\\.260225|QAIRT_URL|QAIRT_CONTENT_DIR|strip_prefix|qairt_headers_dir' HEAD origin/main -- $LITERT_QAIRT_PATHS_QUOTED || true"
append_result "litert_current_qairt_metadata" "$?"

run_shell_capture \
  "litert_exact_246_history_search.txt" \
  "git -C '$LITERT_DIR' grep -n -E '2\\.46\\.0\\.260424|2\\.46|260424|260424121129' \$(git -C '$LITERT_DIR' for-each-ref --format='%(objectname)' refs/heads refs/remotes refs/tags) -- $LITERT_QAIRT_PATHS_QUOTED || true"
append_result "litert_exact_246_history_search" "$?"

run_shell_capture \
  "litert_qairt_version_change_log.txt" \
  "git -C '$LITERT_DIR' log --all --oneline -G '2\\.46|260424|2\\.45|2\\.44\\.0\\.260225|2\\.42|qairt|QAIRT' -- $LITERT_QAIRT_PATHS_QUOTED"
append_result "litert_qairt_version_change_log" "$?"

run_shell_capture \
  "litert_qairt_version_commits.txt" \
  "for commit in \$(git -C '$LITERT_DIR' log --all --format='%H' -- ci/tools/python/vendor_sdk/qualcomm/setup.py third_party/qairt/workspace.bzl litert/vendors/CMakeLists.txt litert/version.bzl | head -80); do echo '---' \$commit; git -C '$LITERT_DIR' show --no-patch --oneline \$commit; git -C '$LITERT_DIR' grep -n -E '2\\.[0-9]+\\.[0-9]+\\.[0-9]+|QAIRT_URL|QAIRT_CONTENT_DIR|strip_prefix|It contains QAIRT' \$commit -- ci/tools/python/vendor_sdk/qualcomm/setup.py third_party/qairt/workspace.bzl litert/vendors/CMakeLists.txt litert/version.bzl 2>/dev/null || true; done"
append_result "litert_qairt_version_commits" "$?"

run_capture "litert_tags.txt" git -C "$LITERT_DIR" tag --sort=creatordate
append_result "litert_tags" "$?"

run_shell_capture \
  "litertlm_workspace_refs_by_tag.tsv" \
  "printf 'tag\\tcommit\\tLITERT_REF\\n'; for tag in \$(git -C '$LITERT_LM_DIR' tag --sort=creatordate); do ref=\$(git -C '$LITERT_LM_DIR' show \"\$tag:WORKSPACE\" 2>/dev/null | sed -n 's/^LITERT_REF = \"\\([^\"]*\\)\".*/\\1/p' | head -1); commit=\$(git -C '$LITERT_LM_DIR' rev-parse \"\$tag^{commit}\" 2>/dev/null || true); printf '%s\\t%s\\t%s\\n' \"\$tag\" \"\$commit\" \"\$ref\"; done"
append_result "litertlm_workspace_refs_by_tag" "$?"

run_shell_capture \
  "litertlm_current_refs.txt" \
  "git -C '$LITERT_LM_DIR' grep -n -E 'LITERT_REF|2\\.46|260424|qairt|litertlm-android' HEAD origin/main -- WORKSPACE docs/api/kotlin/getting_started.md kotlin/java/com/google/ai/edge/litertlm/BUILD 2>/dev/null || true"
append_result "litertlm_current_refs" "$?"

LITERT_REF_FROM_LM_MAIN="$(git -C "$LITERT_LM_DIR" show origin/main:WORKSPACE 2>/dev/null | sed -n 's/^LITERT_REF = "\([^"]*\)".*/\1/p' | head -1 || true)"
if [ -n "$LITERT_REF_FROM_LM_MAIN" ]; then
  run_capture "litert_fetch_litertlm_main_ref.txt" git -C "$LITERT_DIR" fetch origin "$LITERT_REF_FROM_LM_MAIN"
  append_result "litert_fetch_litertlm_main_ref" "$?"
  run_shell_capture \
    "litertlm_main_litert_ref_qairt_metadata.txt" \
    "git -C '$LITERT_DIR' grep -n -E '2\\.46\\.0\\.260424|2\\.46|260424|260424121129|2\\.45|2\\.44\\.0\\.260225|QAIRT_URL|QAIRT_CONTENT_DIR|strip_prefix|qairt_headers_dir' '$LITERT_REF_FROM_LM_MAIN' -- $LITERT_QAIRT_PATHS_QUOTED || true"
  append_result "litertlm_main_litert_ref_qairt_metadata" "$?"
fi

run_shell_capture \
  "litertlm_246_history_search.txt" \
  "git -C '$LITERT_LM_DIR' grep -n -E '2\\.46|260424|260424121129' \$(git -C '$LITERT_LM_DIR' for-each-ref --format='%(objectname)' refs/heads refs/remotes refs/tags) -- WORKSPACE docs/api/kotlin/getting_started.md kotlin/java/com/google/ai/edge/litertlm/BUILD 2>/dev/null || true"
append_result "litertlm_246_history_search" "$?"

run_shell_capture \
  "litertlm_litert_ref_change_log.txt" \
  "git -C '$LITERT_LM_DIR' log --all --oneline -G 'LITERT_REF|47615eb6eaec25e8dfcd1aba922c560a57cba0a2|2\\.46|260424|qairt' -- WORKSPACE"
append_result "litertlm_litert_ref_change_log" "$?"

EXACT_246_LINES="$(grep -E '2\.46\.0\.260424|260424|260424121129' "$OUT_DIR/litert_exact_246_history_search.txt" 2>/dev/null | grep -v 'exit_code=' || true)"
EXACT_246_LINES="$(grep -E '^[0-9a-f]{7,40}:' "$OUT_DIR/litert_exact_246_history_search.txt" 2>/dev/null | grep -E '2\.46\.0\.260424|260424|260424121129' || true)"
LATEST_LITERT_MAIN="$(git -C "$LITERT_DIR" rev-parse --verify origin/main 2>/dev/null || true)"
LATEST_LITERT_MAIN_SHORT="$(git -C "$LITERT_DIR" rev-parse --short=12 --verify origin/main 2>/dev/null || true)"
LATEST_LM_MAIN="$(git -C "$LITERT_LM_DIR" rev-parse --verify origin/main 2>/dev/null || true)"
LATEST_LM_MAIN_SHORT="$(git -C "$LITERT_LM_DIR" rev-parse --short=12 --verify origin/main 2>/dev/null || true)"

{
  printf '# QAIRT 2.46 Source Ref Search Summary\n\n'
  printf -- '- Output: `%s`\n' "$OUT_DIR"
  printf -- '- Build executed: `no`\n'
  printf -- '- Query/cquery executed: `no`, because no exact QAIRT 2.46 LiteRT-LM candidate was identified.\n'
  printf -- '- LiteRT origin/main: `%s` (`%s`)\n' "$LATEST_LITERT_MAIN" "$LATEST_LITERT_MAIN_SHORT"
  printf -- '- LiteRT-LM origin/main: `%s` (`%s`)\n\n' "$LATEST_LM_MAIN" "$LATEST_LM_MAIN_SHORT"
  if [ -n "$LITERT_REF_FROM_LM_MAIN" ]; then
    printf -- '- LiteRT ref pinned by LiteRT-LM origin/main: `%s`\n\n' "$LITERT_REF_FROM_LM_MAIN"
  fi
  printf '## Result\n\n'
  if [ -n "$EXACT_246_LINES" ]; then
    printf 'Exact QAIRT 2.46 evidence was found in bounded LiteRT QAIRT metadata paths:\n\n'
    printf '```text\n%s\n```\n\n' "$EXACT_246_LINES"
  else
    printf 'No exact `2.46.0.260424`, `260424`, or `260424121129` evidence was found in bounded public LiteRT QAIRT metadata refs.\n\n'
  fi
  printf 'Current LiteRT `origin/main` still advertises QAIRT `2.44.0.260225` in `third_party/qairt/workspace.bzl` and `ci/tools/python/vendor_sdk/qualcomm/setup.py`.\n\n'
  printf '## Files\n\n'
  for file in "$OUT_DIR"/*.txt "$OUT_DIR"/*.tsv; do
    [ -f "$file" ] || continue
    printf -- '- `%s`\n' "$(basename "$file")"
  done
} >"$OUT_DIR/summary.md"

log "wrote $OUT_DIR/summary.md"
printf '%s\n' "$OUT_DIR"

exit 0
