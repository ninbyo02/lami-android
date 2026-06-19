#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCAN_ROOT="$ROOT_DIR"

KEYWORD_REGEX='NPU_S[1-5]|npu_s[1-5]|NPU S[1-5]|S[1-5] (response|DB|Markdown|Streaming|TTS)|legacy phase|developer phase|NPU standard route phase|NPU Experimental|NPU Beta|user_facing_npu_experimental'

usage() {
  cat <<'USAGE'
Usage:
  scripts/review_npu_legacy_s1_s5_inventory.sh
  scripts/review_npu_legacy_s1_s5_inventory.sh --root /path/to/repo-or-fixture
  scripts/review_npu_legacy_s1_s5_inventory.sh --self-test

Inventories legacy NPU S1-S5 references and classifies them into compatibility,
developer-only, cleanup-candidate, and do-not-remove-yet groups. This script is
static grep/rg analysis only and does not change runtime behavior.
USAGE
}

count_lines() {
  local file="$1"
  if [[ ! -s "$file" ]]; then
    printf '0\n'
  else
    wc -l <"$file" | tr -d ' '
  fi
}

count_matching_lines() {
  local file="$1"
  local pattern="$2"
  if [[ ! -s "$file" ]]; then
    printf '0\n'
  else
    { grep -Ei "$pattern" "$file" || true; } | wc -l | tr -d ' '
  fi
}

unique_paths_matching() {
  local file="$1"
  local pattern="$2"
  if [[ ! -s "$file" ]]; then
    printf 'none\n'
    return 0
  fi
  { grep -Ei "$pattern" "$file" || true; } |
    cut -d: -f1 |
    sed "s#^$SCAN_ROOT/##" |
    sort -u |
    paste -sd, - |
    sed 's/^$/none/'
}

count_npu_experimental_user_facing_lines() {
  local file="$1"
  if [[ ! -s "$file" ]]; then
    printf '0\n'
  else
    {
      grep -Ei 'NPU Experimental' "$file" || true
    } |
      { grep -Eiv 'user_facing_npu_experimental|npu_standard_route_user_facing|diagnostic|diagnostics|artifact|parser|compatib|fixture|self-test|R1B_DIAGNOSTICS_MODE|selection_mode|source marker|compatibility term|legacy user-facing label' || true; } |
      wc -l |
      tr -d ' '
  fi
}

paths_npu_experimental_user_facing() {
  local file="$1"
  if [[ ! -s "$file" ]]; then
    printf 'none\n'
    return 0
  fi
  {
    grep -Ei 'NPU Experimental' "$file" || true
  } |
    { grep -Eiv 'user_facing_npu_experimental|npu_standard_route_user_facing|diagnostic|diagnostics|artifact|parser|compatib|fixture|self-test|R1B_DIAGNOSTICS_MODE|selection_mode|source marker|compatibility term|legacy user-facing label' || true; } |
    cut -d: -f1 |
    sed "s#^$SCAN_ROOT/##" |
    sort -u |
    paste -sd, - |
    sed 's/^$/none/'
}

run_inventory() {
  local root="$1"
  local tmpdir refs_file
  SCAN_ROOT="$root"
  tmpdir="$(mktemp -d)"
  refs_file="$tmpdir/legacy_refs.txt"

  if [[ -d "$root" ]]; then
    rg -n --hidden \
      -g '!.git' \
      -g '!build' \
      -g '!artifacts' \
      -g '!*.png' \
      -g '!*.jpg' \
      -g '!*.jpeg' \
      -g '!*.webp' \
      -e "$KEYWORD_REGEX" \
      "$root" >"$refs_file" 2>/dev/null || true
  else
    : >"$refs_file"
  fi

  local total runtime settings tests docs keep developer user_facing cleanup do_not_remove
  local npu_experimental_user_facing npu_beta_user_facing deprecate_count keep_count cleanup_count do_not_remove_count
  local status safe_to_remove stage blockers next keep_summary developer_summary cleanup_summary
  local npu_experimental_cleanup_summary
  total="$(count_lines "$refs_file")"
  runtime="$(count_matching_lines "$refs_file" '/app/src/main/java/')"
  settings="$(count_matching_lines "$refs_file" '/app/src/main/java/.*/settings/|Settings(Data|Preferences)?\.kt|InferenceBackendSelection\.kt')"
  tests="$(count_matching_lines "$refs_file" '/app/src/test/')"
  docs="$(count_matching_lines "$refs_file" '/docs/')"
  keep="$(count_matching_lines "$refs_file" 'preference|persist|enum|fromSettings|fromStorage|compatib|migration|parseable|legacy unspecified|LEGACY_UNSPECIFIED|user_facing_npu_experimental|npu_standard_route_user_facing|selected_backend=NPU_S5|route_family=npu_s5')"
  developer="$(count_matching_lines "$refs_file" 'DEV|developer|debug|diagnostic|legacy|phase selector|compact|full dump|test|fixture')"
  user_facing="$(count_matching_lines "$refs_file" 'NPU S[1-5] (response|応答|DB|Markdown|Streaming|TTS)|S[1-5] (response|DB|Markdown|Streaming|TTS)')"
  npu_experimental_user_facing="$(count_npu_experimental_user_facing_lines "$refs_file")"
  npu_beta_user_facing="$(count_matching_lines "$refs_file" 'NPU Beta')"
  cleanup="$((user_facing + npu_experimental_user_facing))"
  do_not_remove="$runtime"
  deprecate_count="$cleanup"
  keep_count="$keep"
  cleanup_count="$cleanup"
  do_not_remove_count="$do_not_remove"

  if ((total == 0)); then
    status="no_legacy_references_found"
  elif ((user_facing > 0)); then
    status="legacy_references_present_with_cleanup_candidates"
  else
    status="legacy_references_present_developer_or_compatibility_only"
  fi

  if ((total == 0)); then
    safe_to_remove="not_applicable"
    stage="stage4_no_references"
    blockers="none"
    next="no_legacy_cleanup_needed"
  else
    safe_to_remove="false"
    stage="stage0_current_hidden_developer_compatibility"
    blockers="preference_key_compatibility,developer_override_compatibility,artifact_parser_compatibility,route_execution_compatibility"
    if ((cleanup > 0)); then
      next="rename_or_hide_user_facing_legacy_labels_before_cleanup"
    else
      next="keep_legacy_s1_s5_as_developer_only_until_migration_window"
    fi
  fi

  keep_summary="$(unique_paths_matching "$refs_file" 'preference|persist|enum|fromSettings|fromStorage|compatib|migration|parseable|legacy unspecified|LEGACY_UNSPECIFIED')"
  developer_summary="$(unique_paths_matching "$refs_file" 'DEV|developer|debug|diagnostic|legacy|phase selector|compact|full dump|test|fixture')"
  cleanup_summary="$(unique_paths_matching "$refs_file" 'NPU S[1-5] (response|応答|DB|Markdown|Streaming|TTS)|S[1-5] (response|DB|Markdown|Streaming|TTS)')"
  npu_experimental_cleanup_summary="$(paths_npu_experimental_user_facing "$refs_file")"
  if [[ "$cleanup_summary" == "none" ]]; then
    cleanup_summary="$npu_experimental_cleanup_summary"
  elif [[ "$npu_experimental_cleanup_summary" != "none" ]]; then
    cleanup_summary="$cleanup_summary,$npu_experimental_cleanup_summary"
  fi

  printf 'NPU_LEGACY_S1_S5_INVENTORY_STATUS=%s\n' "$status"
  printf 'LEGACY_REFERENCE_COUNT=%s\n' "$total"
  printf 'LEGACY_RUNTIME_REFERENCE_COUNT=%s\n' "$runtime"
  printf 'LEGACY_SETTINGS_REFERENCE_COUNT=%s\n' "$settings"
  printf 'LEGACY_TEST_REFERENCE_COUNT=%s\n' "$tests"
  printf 'LEGACY_DOC_REFERENCE_COUNT=%s\n' "$docs"
  printf 'NPU_EXPERIMENTAL_USER_FACING_REFERENCE_COUNT=%s\n' "$npu_experimental_user_facing"
  printf 'NPU_BETA_USER_FACING_REFERENCE_COUNT=%s\n' "$npu_beta_user_facing"
  printf 'KEEP_COUNT=%s\n' "$keep_count"
  printf 'DEPRECATE_COUNT=%s\n' "$deprecate_count"
  printf 'CLEANUP_CANDIDATE_COUNT=%s\n' "$cleanup_count"
  printf 'DO_NOT_REMOVE_YET_COUNT=%s\n' "$do_not_remove_count"
  printf 'LEGACY_SAFE_TO_REMOVE_NOW=%s\n' "$safe_to_remove"
  printf 'LEGACY_DEPRECATION_STAGE=%s\n' "$stage"
  printf 'KEEP_FOR_COMPATIBILITY=%s\n' "$keep_summary"
  printf 'DEVELOPER_ONLY_REFERENCES=%s\n' "$developer_summary"
  printf 'USER_FACING_REFERENCES=%s\n' "$cleanup_summary"
  printf 'CLEANUP_CANDIDATES=%s\n' "$cleanup_summary"
  printf 'REMOVAL_BLOCKERS=%s\n' "$blockers"
  printf 'DO_NOT_REMOVE_YET=route_execution_compatibility,final_promotion_parser_compatibility,rollout_monitor_compatibility,legacy_debug_override\n'
  printf 'SAFE_NEXT_ACTION=%s\n' "$next"

  rm -rf "$tmpdir"
}

write_file() {
  local file="$1"
  shift
  mkdir -p "$(dirname "$file")"
  printf '%s\n' "$@" >"$file"
}

expect_output_contains() {
  local output="$1"
  local expected="$2"
  if ! grep -Fqx "$expected" "$output"; then
    printf 'self-test failed: expected %s in %s\n' "$expected" "$output" >&2
    cat "$output" >&2
    return 1
  fi
}

self_test() {
  local tmpdir out
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' RETURN

  mkdir -p "$tmpdir/no_refs"
  out="$tmpdir/no_refs.out"
  run_inventory "$tmpdir/no_refs" >"$out"
  expect_output_contains "$out" "NPU_LEGACY_S1_S5_INVENTORY_STATUS=no_legacy_references_found"
  expect_output_contains "$out" "LEGACY_REFERENCE_COUNT=0"

  mkdir -p "$tmpdir/keep/app/src/main/java/io/example/settings"
  write_file "$tmpdir/keep/app/src/main/java/io/example/settings/InferenceBackendSelection.kt" \
    "enum class Backend { NPU_S1, NPU_S5 }" \
    "fun fromStorage(raw: String) = raw // preference compatibility for NPU_S5" \
    "val selection = \"user_facing_npu_experimental\" // diagnostic compatibility"
  out="$tmpdir/keep.out"
  run_inventory "$tmpdir/keep" >"$out"
  grep -Fq "KEEP_FOR_COMPATIBILITY=" "$out"
  expect_output_contains "$out" "LEGACY_SAFE_TO_REMOVE_NOW=false"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_USER_FACING_REFERENCE_COUNT=0"

  mkdir -p "$tmpdir/user/docs"
  write_file "$tmpdir/user/docs/settings.md" \
    "NPU S1 response only" \
    "NPU S2 DB save" \
    "NPU Experimental"
  out="$tmpdir/user.out"
  run_inventory "$tmpdir/user" >"$out"
  expect_output_contains "$out" "NPU_LEGACY_S1_S5_INVENTORY_STATUS=legacy_references_present_with_cleanup_candidates"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_USER_FACING_REFERENCE_COUNT=1"
  grep -Fq "CLEANUP_CANDIDATES=docs/settings.md" "$out"

  mkdir -p "$tmpdir/developer/app/src/test/java"
  write_file "$tmpdir/developer/app/src/test/java/Test.kt" \
    "val label = \"DEV: NPU S5 TTS\"" \
    "val route = \"npu_s5\" // diagnostic fixture"
  out="$tmpdir/developer.out"
  run_inventory "$tmpdir/developer" >"$out"
  grep -Fq "DEVELOPER_ONLY_REFERENCES=app/src/test/java/Test.kt" "$out"

  mkdir -p "$tmpdir/beta/docs"
  write_file "$tmpdir/beta/docs/settings.md" \
    "NPU Beta"
  out="$tmpdir/beta.out"
  run_inventory "$tmpdir/beta" >"$out"
  expect_output_contains "$out" "NPU_BETA_USER_FACING_REFERENCE_COUNT=1"
  expect_output_contains "$out" "NPU_EXPERIMENTAL_USER_FACING_REFERENCE_COUNT=0"

  mkdir -p "$tmpdir/mixed/app/src/main/java/io/example/settings" "$tmpdir/mixed/docs"
  write_file "$tmpdir/mixed/app/src/main/java/io/example/settings/SettingsPreferences.kt" \
    "val key = \"NPU_S3\" // persisted preference compatibility"
  write_file "$tmpdir/mixed/docs/ui.md" \
    "NPU S3 Markdown"
  out="$tmpdir/mixed.out"
  run_inventory "$tmpdir/mixed" >"$out"
  expect_output_contains "$out" "NPU_LEGACY_S1_S5_INVENTORY_STATUS=legacy_references_present_with_cleanup_candidates"
  grep -Fq "REMOVAL_BLOCKERS=preference_key_compatibility" "$out"

  printf 'self-test passed\n'
}

while (($#)); do
  case "$1" in
    --root)
      SCAN_ROOT="${2:-}"
      shift 2
      ;;
    --self-test)
      self_test
      exit 0
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

run_inventory "$SCAN_ROOT"
