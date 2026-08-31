#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

run_dry() {
  LAMI_BENCHMARK_DRY_RUN_COMMAND=true "$@"
}

assert_contains() {
  local text="$1"
  local expected="$2"
  [[ "$text" == *"$expected"* ]] || fail "expected dry-run output to contain: $expected"$'\n'"$text"
}

assert_not_contains() {
  local text="$1"
  local unexpected="$2"
  [[ "$text" != *"$unexpected"* ]] || fail "dry-run output should not contain: $unexpected"$'\n'"$text"
}

automatic_output="$(run_dry scripts/run_generic_fallback_litert_lm_20run.sh --backend automatic)"
assert_contains "$automatic_output" "backend=Automatic"
assert_contains "$automatic_output" "backend_variant=automatic"
assert_contains "$automatic_output" "model_path_source=generic_fallback"
assert_contains "$automatic_output" "requested_run_count=20"
assert_contains "$automatic_output" "receiver_extra=--es backend_variant automatic"
assert_not_contains "$automatic_output" "backend=GPU"
assert_not_contains "$automatic_output" "backend_variant=gpu"

default_output="$(run_dry scripts/run_generic_fallback_litert_lm_20run.sh --backend default)"
assert_contains "$default_output" "backend=Automatic"
assert_contains "$default_output" "backend_variant=automatic"
assert_contains "$default_output" "receiver_extra=--es backend_variant automatic"
assert_not_contains "$default_output" "backend=GPU"
assert_not_contains "$default_output" "backend_variant=gpu"

gpu_output="$(run_dry scripts/run_generic_fallback_litert_lm_20run.sh --backend gpu)"
assert_contains "$gpu_output" "backend=GPU"
assert_contains "$gpu_output" "backend_variant=gpu"
assert_contains "$gpu_output" "receiver_extra=--es backend_variant gpu"

cpu_output="$(run_dry scripts/run_generic_fallback_litert_lm_20run.sh --backend cpu)"
assert_contains "$cpu_output" "backend=CPU"
assert_contains "$cpu_output" "backend_variant=cpu"
assert_contains "$cpu_output" "receiver_extra=--es backend_variant cpu"

echo "generic fallback LiteRT-LM backend selection checks passed"
