#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=../debug_token_observer_policy.sh
source "$ROOT/scripts/debug_token_observer_policy.sh"

assert_eq() {
  local expected="$1" actual="$2"
  [[ "$actual" == "$expected" ]] || {
    printf 'expected=%s actual=%s\n' "$expected" "$actual" >&2
    exit 1
  }
}

running_state=$'timestamp=20260724_120000\nstatus=running'
terminal_state=$'timestamp=20260724_120000\nstatus=partial'
blocked_state=$'timestamp=20260724_120000\nstatus=blocked'
stale_state=$'timestamp=20260724_115959\nstatus=success'

assert_eq running "$(debug_token_observer_state_class 20260724_120000 "$running_state")"
assert_eq terminal "$(debug_token_observer_state_class 20260724_120000 "$terminal_state")"
assert_eq terminal "$(debug_token_observer_state_class 20260724_120000 "$blocked_state")"
assert_eq stale "$(debug_token_observer_state_class 20260724_120000 "$stale_state")"

fresh_marker=$'timestamp=20260724_120000\nstage=prompt_started\nwall_time_ms=1784894699000\n'
stale_marker=$'timestamp=20260724_120000\nstage=prompt_started\nwall_time_ms=1784894000000\n'
wrong_marker=$'timestamp=20260724_115959\nstage=prompt_started\nwall_time_ms=1784894699000\n'
assert_eq fresh "$(debug_token_observer_marker_freshness 20260724_120000 "$fresh_marker" 1784894700 600)"
assert_eq stale "$(debug_token_observer_marker_freshness 20260724_120000 "$stale_marker" 1784894700 600)"
assert_eq missing "$(debug_token_observer_marker_freshness 20260724_120000 "$wrong_marker" 1784894700 600)"
assert_eq invalid "$(debug_token_observer_marker_freshness 20260724_120000 $'timestamp=20260724_120000\nstage=prompt_started\nwall_time_ms=bad' 1784894700 600)"
assert_eq invalid_stage "$(debug_token_observer_marker_freshness 20260724_120000 $'timestamp=20260724_120000\nstage=completed\nwall_time_ms=1784894699000' 1784894700 600)"

assert_eq 600 "$(debug_token_observer_remaining_seconds 1000 1000 600)"
assert_eq 1 "$(debug_token_observer_remaining_seconds 1000 1599 600)"
assert_eq 0 "$(debug_token_observer_remaining_seconds 1000 1600 600)"
assert_eq 0 "$(debug_token_observer_remaining_seconds 1000 1700 600)"

exact_component='io.github.ninbyo02.lami.gpunoconstraint/io.github.ninbyo02.lami.gpu.DebugTokenBenchmarkActivity'
assert_eq ok "$(debug_token_observer_running_process_gate 1234 1234 "topResumedActivity=$exact_component" "$exact_component")"
assert_eq ok "$(debug_token_observer_running_process_gate 1234 1234 "topResumedActivity=ActivityRecord{abc123 u0 $exact_component t42}" "$exact_component")"
assert_eq pid_missing "$(debug_token_observer_running_process_gate 1234 '' "topResumedActivity=$exact_component" "$exact_component")"
assert_eq pid_replaced "$(debug_token_observer_running_process_gate 1234 5678 "topResumedActivity=$exact_component" "$exact_component")"
assert_eq foreground_lost "$(debug_token_observer_running_process_gate 1234 1234 'topResumedActivity=com.example/.OtherActivity' "$exact_component")"
assert_eq foreground_lost "$(debug_token_observer_running_process_gate 1234 1234 "topResumedActivity=${exact_component}Suffix" "$exact_component")"

assert_eq pid_absent "$(debug_token_observer_terminal_cleanup_gate 1234 '' false false)"
assert_eq closed "$(debug_token_observer_terminal_cleanup_gate 1234 1234 true true)"
assert_eq waiting "$(debug_token_observer_terminal_cleanup_gate 1234 1234 true false)"
assert_eq waiting "$(debug_token_observer_terminal_cleanup_gate 1234 1234 false true)"
assert_eq pid_replaced "$(debug_token_observer_terminal_cleanup_gate 1234 5678 true true)"
assert_eq invalid "$(debug_token_observer_terminal_cleanup_gate not-a-pid 1234 true true)"

assert_eq ok "$(debug_token_observer_dual_running_process_gate 1234 1234 2234 2234 "topResumedActivity=$exact_component" "$exact_component")"
assert_eq main_pid_missing "$(debug_token_observer_dual_running_process_gate 1234 '' 2234 2234 "topResumedActivity=$exact_component" "$exact_component")"
assert_eq main_pid_replaced "$(debug_token_observer_dual_running_process_gate 1234 5678 2234 2234 "topResumedActivity=$exact_component" "$exact_component")"
assert_eq benchmark_pid_missing "$(debug_token_observer_dual_running_process_gate 1234 1234 2234 '' "topResumedActivity=$exact_component" "$exact_component")"
assert_eq benchmark_pid_replaced "$(debug_token_observer_dual_running_process_gate 1234 1234 2234 6678 "topResumedActivity=$exact_component" "$exact_component")"
assert_eq foreground_lost "$(debug_token_observer_dual_running_process_gate 1234 1234 2234 2234 'topResumedActivity=com.example/.OtherActivity' "$exact_component")"

assert_eq closed "$(debug_token_observer_dual_terminal_cleanup_gate 1234 1234 2234 2234 true true)"
assert_eq benchmark_process_terminated "$(debug_token_observer_dual_terminal_cleanup_gate 1234 1234 2234 '' false false)"
assert_eq processes_absent "$(debug_token_observer_dual_terminal_cleanup_gate 1234 '' 2234 '' false false)"
assert_eq waiting "$(debug_token_observer_dual_terminal_cleanup_gate 1234 1234 2234 2234 true false)"
assert_eq benchmark_pid_replaced "$(debug_token_observer_dual_terminal_cleanup_gate 1234 1234 2234 6678 true true)"
assert_eq main_pid_replaced "$(debug_token_observer_dual_terminal_cleanup_gate 1234 5678 2234 2234 true true)"
assert_eq closed "$(debug_token_observer_dual_terminal_cleanup_gate 1234 '' 2234 2234 true true)"
assert_eq invalid "$(debug_token_observer_dual_terminal_cleanup_gate bad 1234 2234 2234 true true)"

# A successful foreground benchmark must retain both exact processes and prove
# both native resources closed. Process disappearance is valid cleanup evidence
# for cancellation/failure observers, but not for a successful acceptance row.
assert_eq ok "$(debug_token_observer_success_process_gate 1234 1234 2234 2234 closed true true)"
assert_eq main_pid_missing "$(debug_token_observer_success_process_gate 1234 '' 2234 2234 processes_absent true true)"
assert_eq benchmark_pid_missing "$(debug_token_observer_success_process_gate 1234 1234 2234 '' benchmark_process_terminated false false)"
assert_eq cleanup_not_closed "$(debug_token_observer_success_process_gate 1234 1234 2234 2234 waiting true false)"

artifact_dir="$(mktemp -d)"
trap 'rm -rf "$artifact_dir"' EXIT
timestamp=20260724_120000
csv="$artifact_dir/litert_lm_gpu_benchmark_$timestamp.csv"
markdown="$artifact_dir/litert_lm_gpu_benchmark_$timestamp.md"
cat >"$csv" <<CSV
timestamp,status,reason,timeout,max_output_tokens,fallback_used,fresh_crash,sanitized_output,raw_output
$timestamp,success,completed,false,400,false,false,ok,ok
CSV
cat >"$markdown" <<MD
# LiteRT-LM GPU benchmark

- timestamp: \`$timestamp\`
- status: \`success\`
- reason: \`completed\`
MD
assert_eq ok "$(debug_token_validate_terminal_artifacts "$timestamp" success completed "$csv" "$markdown")"
cp "$csv" "$artifact_dir/valid.csv"
printf '%s\n' "$timestamp,success,completed,false,400,false,false,duplicate,duplicate" >>"$csv"
assert_eq invalid "$(debug_token_validate_terminal_artifacts "$timestamp" success completed "$csv" "$markdown")"
cp "$artifact_dir/valid.csv" "$csv"
assert_eq invalid "$(debug_token_validate_terminal_artifacts 20260724_120001 success completed "$csv" "$markdown")"
assert_eq invalid "$(debug_token_validate_terminal_artifacts "$timestamp" failure completed "$csv" "$markdown")"
printf '%s\n' '# LiteRT-LM GPU benchmark' '' '- timestamp: `20260724_115959`' '- status: success' '- reason: completed' >"$markdown"
assert_eq invalid "$(debug_token_validate_terminal_artifacts "$timestamp" success completed "$csv" "$markdown")"

echo "debug_token_observer_policy_test=pass"
