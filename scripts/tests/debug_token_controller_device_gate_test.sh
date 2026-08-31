#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONTROLLER="$ROOT/scripts/lami_build_remote_control_full.sh"
reviewed_assemble_block="$(sed -n '/^run_assemble_reviewed_gpu_30400_candidate() (/,/^)[[:space:]]*$/p' "$CONTROLLER")"
dirty_full_block="$(sed -n '/^  test-dirty-standard-full)/,/^  assemble-standard)/p' "$CONTROLLER")"
sealed_apply_block="$(sed -n '/^run_git_apply_sealed_patch() {/,/^}/p' "$CONTROLLER")"
[[ -n "$reviewed_assemble_block" ]] || { echo 'missing reviewed no-install assemble function' >&2; exit 1; }
[[ -n "$sealed_apply_block" ]] || { echo 'missing sealed patch application helper' >&2; exit 1; }
[[ "$reviewed_assemble_block" == *'t57-publication-candidate.patch'* ]]
[[ "$reviewed_assemble_block" == *'refs/remotes/origin/future'* ]]
[[ "$reviewed_assemble_block" == *'status --porcelain --untracked-files=all'* ]]
[[ "$sealed_apply_block" == *'(("apply", "--check", "--index", "-"), ("apply", "--index", "-"))'* ]]
[[ "$reviewed_assemble_block" == *'sha256sum'* ]]
[[ "$reviewed_assemble_block" == *':app:assembleStandardGpuNoConstraintProviderDebug'* ]]
[[ "$reviewed_assemble_block" == *':app:testStandardDebugUnitTest --rerun-tasks'* ]]
[[ "$reviewed_assemble_block" == *'standard_unit_tests='* ]]
[[ "$reviewed_assemble_block" == *'app-standardGpuNoConstraintProvider-debug.apk'* ]]
[[ "$reviewed_assemble_block" == *'artifact_task_dir="$artifact_base/t57-gpu-30400"'* ]]
[[ -n "$dirty_full_block" ]] || { echo 'missing dirty full Standard unit-test command' >&2; exit 1; }
[[ "$(grep -Fc './gradlew --no-daemon :app:testStandardDebugUnitTest --rerun-tasks' <<<"$dirty_full_block")" -eq 1 ]]
[[ "$reviewed_assemble_block" == *'cp --reflink=auto'* ]]
[[ "$reviewed_assemble_block" == *'realpath'* ]]
[[ "$reviewed_assemble_block" == *'stat -c'* ]]
if [[ "$reviewed_assemble_block" =~ (^|[^[:alnum:]_])(adb|installDebug|installStandardGpuNoConstraintProviderDebug|input[[:space:]]+tap|device_serial)([^[:alnum:]_]|$) ]]; then
  echo 'reviewed assemble function contains a device/install operation' >&2
  exit 1
fi
controller_source="$(cat "$CONTROLLER")"
receiver_source="$(cat "$ROOT/app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt")"
report_writer_block="$(sed -n '/^internal fun writeReports(/,/^}/p' "$ROOT/app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt")"
[[ "$(grep -c 'writeUtf8Atomically(' <<<"$report_writer_block")" == 2 ]]
[[ "$report_writer_block" != *'.writeText('* ]]
[[ "$controller_source" == *'assemble-reviewed-gpu-30400-candidate\ *)'* ]]
[[ "$controller_source" == *'[[ "${#parts[@]}" -eq 3 ]]'* ]]
[[ "$controller_source" == *'assemble-reviewed-gpu-30400-candidate <origin-future-commit> <patch-sha256>'* ]]
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/home/repos" "$TMP/bin"
ln -s "$ROOT" "$TMP/home/repos/lami-android"

cat >"$TMP/bin/adb" <<'ADB'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${FAKE_ADB_LOG:?}"
case "$*" in
  "devices") printf 'List of devices attached\n192.168.52.52:45678\tdevice\n' ;;
  "connect 192.168.52.52:45678") echo "connected to 192.168.52.52:45678" ;;
  "-s 192.168.52.52:45678 get-state") echo device ;;
  "-s 192.168.52.52:45678 shell getprop ro.product.model") printf 'NX733J\r\n' ;;
  "-s 192.168.52.52:45678 shell pidof io.github.ninbyo02.lami.gpunoconstraint")
    if [[ "${FAKE_PID_MODE:-}" == replace || "${FAKE_PID_MODE:-}" == absent-terminal || "${FAKE_PID_MODE:-}" == error-terminal ]]; then
      count="$(cat "${FAKE_PID_COUNT:?}" 2>/dev/null || echo 0)"; count=$((count + 1)); echo "$count" >"$FAKE_PID_COUNT"
      if (( count == 1 )); then
        echo 1234
      elif [[ "$FAKE_PID_MODE" == replace ]]; then
        echo 5678
      elif [[ "$FAKE_PID_MODE" == error-terminal ]]; then
        exit 42
      else
        exit 1
      fi
    else
      echo 1234
    fi ;;
  "-s 192.168.52.52:45678 shell pidof io.github.ninbyo02.lami.gpunoconstraint:gpu_benchmark_probe")
    if [[ "${FAKE_BENCH_PID_MODE:-}" == absent-terminal || "${FAKE_BENCH_PID_MODE:-}" == error-terminal || "${FAKE_BENCH_PID_MODE:-}" == replace ]]; then
      count="$(cat "${FAKE_BENCH_PID_COUNT:?}" 2>/dev/null || echo 0)"; count=$((count + 1)); echo "$count" >"$FAKE_BENCH_PID_COUNT"
      if (( count == 1 )); then
        echo 2234
      elif [[ "$FAKE_BENCH_PID_MODE" == replace ]]; then
        echo 6678
      elif [[ "$FAKE_BENCH_PID_MODE" == error-terminal ]]; then
        exit 42
      else
        exit 1
      fi
    else
      echo 2234
    fi ;;
  "-s 192.168.52.52:45678 shell dumpsys activity activities")
    if [[ "${FAKE_TOP_MODE:-}" == lost ]]; then echo 'topResumedActivity=other/.MainActivity'; else echo 'topResumedActivity=io.github.ninbyo02.lami.gpunoconstraint/io.github.ninbyo02.lami.gpu.DebugTokenBenchmarkActivity'; fi ;;
  "-s 192.168.52.52:45678 exec-out run-as io.github.ninbyo02.lami.gpunoconstraint cat files/litert_lm_gpu_benchmark_state.txt")
    if [[ "${FAKE_OBSERVER_MODE:-}" == success ]]; then
      count="$(cat "${FAKE_STATE_COUNT:?}" 2>/dev/null || echo 0)"; count=$((count + 1)); echo "$count" >"$FAKE_STATE_COUNT"
      if (( count == 1 )); then printf 'timestamp=%s\nstatus=running\n' "${FAKE_TIMESTAMP:?}"; else printf 'timestamp=%s\nstatus=success\n' "${FAKE_TIMESTAMP:?}"; fi
    elif [[ "${FAKE_OBSERVER_MODE:-}" == pid_replace ]]; then
      printf 'timestamp=%s\nstatus=running\n' "${FAKE_TIMESTAMP:?}"
    else
      printf 'timestamp=20260724_120000\nstatus=running\n'
    fi ;;
  "-s 192.168.52.52:45678 exec-out run-as io.github.ninbyo02.lami.gpunoconstraint cat files/litert_lm_gpu_benchmark_marker.txt") printf 'timestamp=%s\nstage=prompt_started\nwall_time_ms=%s\n' "${FAKE_TIMESTAMP:?}" "${FAKE_WALL_MS:?}" ;;
  "-s 192.168.52.52:45678 shell dumpsys power") echo 'mWakefulness=Awake' ;;
  "-s 192.168.52.52:45678 shell dumpsys thermalservice") echo 'Thermal Status: 0' ;;
  "-s 192.168.52.52:45678 shell dumpsys meminfo io.github.ninbyo02.lami.gpunoconstraint") echo 'TOTAL PSS: 100' ;;
  "-s 192.168.52.52:45678 exec-out run-as io.github.ninbyo02.lami.gpunoconstraint cat files/litert_lm_gpu_benchmark_marker_history.txt")
    if [[ "${FAKE_CLOSE_MODE:-}" == missing ]]; then
      printf 'timestamp=%s\nstage=prompt_started\nwall_time_ms=%s\n' "${FAKE_TIMESTAMP:?}" "${FAKE_WALL_MS:?}"
    elif [[ -n "${FAKE_TIMESTAMP:-}" ]]; then
      printf 'timestamp=%s\nstage=close_finished\ndetail=target=conversation\n\ntimestamp=%s\nstage=close_finished\ndetail=target=engine\n' "$FAKE_TIMESTAMP" "$FAKE_TIMESTAMP"
    else
      printf 'timestamp=20260724_120000\nstage=prompt_started\nwall_time_ms=1784894699000\n'
    fi ;;
  *) echo "unexpected fake adb command: $*" >&2; exit 91 ;;
esac
ADB
chmod +x "$TMP/bin/adb"
export FAKE_ADB_LOG="$TMP/adb.log"
: >"$FAKE_ADB_LOG"
export PATH="$TMP/bin:$PATH"
export ANDROID_HOME="$TMP/no-android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

run_controller() {
  env HOME="$TMP/home" PATH="$TMP/bin:$PATH" SSH_ORIGINAL_COMMAND="$1" \
    bash "$ROOT/scripts/lami_build_remote_control_full.sh"
}

output="$(run_controller 'debug-token-ui-live-state 192.168.52.52 45678')"
[[ "$output" == *"debug_token_ui_live_state=begin"* ]]
[[ "$output" == *"debug_token_ui_pid=1234"* ]]
[[ "$output" == *"debug_token_ui_benchmark_pid=2234"* ]]
grep -Fxq -- '-s 192.168.52.52:45678 get-state' "$FAKE_ADB_LOG"
grep -Fxq -- '-s 192.168.52.52:45678 shell getprop ro.product.model' "$FAKE_ADB_LOG"

PATH="$TMP/bin:$PATH"
source "$ROOT/scripts/debug_token_observer_policy.sh"
debug_token_single_nx733j_device_gate
[[ "$DEBUG_TOKEN_NX733J_SERIAL" == "192.168.52.52:45678" ]]

before="$(wc -l <"$FAKE_ADB_LOG")"
if run_controller 'debug-token-ui-live-state 8.8.8.8 45678' >/dev/null 2>&1; then
  echo "invalid host unexpectedly accepted" >&2
  exit 1
fi
if run_controller 'debug-token-ui-live-state 192.168.52.52 70000' >/dev/null 2>&1; then
  echo "invalid port unexpectedly accepted" >&2
  exit 1
fi
after="$(wc -l <"$FAKE_ADB_LOG")"
[[ "$before" == "$after" ]] || { echo "invalid endpoint reached adb" >&2; exit 1; }

controller_source="$(cat "$ROOT/scripts/lami_build_remote_control_full.sh")"
[[ "$controller_source" != *observer_deadline_epoch* ]] || { echo "obsolete wall-clock deadline reference remains" >&2; exit 1; }
[[ "$controller_source" == *'observer_deadline_ms="$(($(debug_token_monotonic_ms) + observer_max_seconds * 1000))"'* ]]
[[ "$controller_source" != *'observer_deadline_seconds='* ]]
[[ "$controller_source" == *'debug_token_observer_dual_running_process_gate "$initial_pid" "$current_pid" "$benchmark_pid_before" "$benchmark_pid_current" "$top_resumed" "$debug_component"'* ]]
[[ "$controller_source" == *'debug_token_observer_dual_running_process_gate "$pid_before" "$pid_after" "$benchmark_pid_before" "$benchmark_pid_after" "$resumed" "$debug_component"'* ]]
[[ "$controller_source" == *'run_debug_token_ui_case_bounded_adb'* ]]
[[ "$controller_source" == *'debug_token_observer_dual_terminal_cleanup_gate "$initial_pid" "$final_pid" "$benchmark_pid_before" "$benchmark_pid_after"'* ]]
[[ "$controller_source" == *'test-dirty-gpu-30400-cases)'* ]]
[[ "$controller_source" == *'bash scripts/tests/debug_token_observer_policy_test.sh'* ]]
[[ "$controller_source" == *'bash scripts/tests/debug_token_controller_device_gate_test.sh'* ]]
[[ "$controller_source" == *"--tests 'io.github.ninbyo02.lami.gpu.DebugTokenBenchmarkUiSourceContractTest'"* ]]
[[ "$controller_source" == *"--tests 'io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkRunSummaryTest'"* ]]
[[ "$controller_source" == *'initial_pid='* ]]
[[ "$controller_source" == *'final_pid='* ]]
[[ "$controller_source" == *'conversation_close_finished='* ]]
[[ "$controller_source" == *'engine_close_finished='* ]]
[[ "$controller_source" == *'benchmark_process="$package:gpu_benchmark_probe"'* ]]
[[ "$controller_source" == *'benchmark_pid_before='* ]]
[[ "$controller_source" == *'benchmark_pid_after='* ]]
[[ "$controller_source" == *'terminal_cleanup_gate='* ]]
[[ "$controller_source" == *'expected_csv_file="litert_lm_gpu_benchmark_${timestamp}.csv"'* ]]
[[ "$controller_source" == *'expected_md_file="litert_lm_gpu_benchmark_${timestamp}.md"'* ]]
for function_name in force_stop_debug_token_ui_benchmark stop_debug_token_ui_benchmark read_debug_token_ui_live_state; do
  function_body="$(printf '%s\n' "$controller_source" | sed -n "/^${function_name}() (/ , /^)/p")"
  [[ -n "$function_body" ]] || { echo "$function_name is not an isolated bounded subshell" >&2; exit 1; }
  [[ "$function_body" == *'observer_deadline_ms="$(($(debug_token_monotonic_ms) + observer_max_seconds * 1000))"'* ]]
  [[ "$function_body" == *'adb() { run_debug_token_ui_case_bounded_adb'* ]]
done

export FAKE_TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
export FAKE_WALL_MS="$(( $(date +%s) * 1000 ))"
export FAKE_STATE_COUNT="$TMP/state.count"
export FAKE_PID_COUNT="$TMP/pid.count"
export FAKE_BENCH_PID_COUNT="$TMP/bench-pid.count"
export FAKE_OBSERVER_MODE=success
: >"$FAKE_STATE_COUNT"
observer_output="$(run_controller 'debug-token-ui-observe 192.168.52.52 45678')"
[[ "$observer_output" == *"marker_running_no_rerun=true"* ]]
[[ "$observer_output" == *"timestamp_matched_terminal=true"* ]]
[[ "$observer_output" == *"final_pid=1234"* ]]
[[ "$observer_output" == *"benchmark_pid_after=2234"* ]]
[[ "$observer_output" == *"terminal_cleanup_gate=closed"* ]]
[[ "$observer_output" == *"conversation_close_finished=true"* ]]
[[ "$observer_output" == *"engine_close_finished=true"* ]]
rm -rf "$ROOT/artifacts/litert_gpu_token_ui_observer/$FAKE_TIMESTAMP"

export FAKE_OBSERVER_MODE=pid_replace FAKE_PID_MODE=replace FAKE_BENCH_PID_MODE=
: >"$FAKE_PID_COUNT"
if run_controller 'debug-token-ui-observe 192.168.52.52 45678' >"$TMP/pid-replace.out" 2>&1; then
  echo "PID replacement unexpectedly accepted" >&2
  exit 1
fi
grep -Fq 'failure_class=harness_lifecycle_failure reason=main_pid_replaced' "$TMP/pid-replace.out"
rm -rf "$ROOT/artifacts/litert_gpu_token_ui_observer/$FAKE_TIMESTAMP"

export FAKE_OBSERVER_MODE=success FAKE_PID_MODE=absent-terminal FAKE_BENCH_PID_MODE=absent-terminal FAKE_TOP_MODE= FAKE_CLOSE_MODE=
: >"$FAKE_STATE_COUNT"
: >"$FAKE_PID_COUNT"
: >"$FAKE_BENCH_PID_COUNT"
cleanup_output="$(run_controller 'debug-token-ui-observe 192.168.52.52 45678')"
[[ "$cleanup_output" == *"timestamp_matched_terminal=true final_pid=missing benchmark_pid_after=missing process_stable=false benchmark_process_stable=false terminal_cleanup_gate=processes_absent"* ]]
grep -Fxq 'observer_process_gate=processes_absent' \
  "$ROOT/artifacts/litert_gpu_token_ui_observer/$FAKE_TIMESTAMP/host_observer-final.txt"
rm -rf "$ROOT/artifacts/litert_gpu_token_ui_observer/$FAKE_TIMESTAMP"

export FAKE_OBSERVER_MODE=success FAKE_PID_MODE= FAKE_BENCH_PID_MODE=absent-terminal FAKE_TOP_MODE= FAKE_CLOSE_MODE=missing
: >"$FAKE_STATE_COUNT"
: >"$FAKE_PID_COUNT"
: >"$FAKE_BENCH_PID_COUNT"
benchmark_cleanup_output="$(run_controller 'debug-token-ui-observe 192.168.52.52 45678')"
[[ "$benchmark_cleanup_output" == *"timestamp_matched_terminal=true final_pid=1234 benchmark_pid_after=missing process_stable=true benchmark_process_stable=false terminal_cleanup_gate=benchmark_process_terminated"* ]]
[[ "$benchmark_cleanup_output" == *"benchmark_process_stable=false"* ]]
grep -Fxq 'observer_process_gate=benchmark_process_terminated' \
  "$ROOT/artifacts/litert_gpu_token_ui_observer/$FAKE_TIMESTAMP/host_observer-final.txt"
rm -rf "$ROOT/artifacts/litert_gpu_token_ui_observer/$FAKE_TIMESTAMP"

export FAKE_OBSERVER_MODE=success FAKE_PID_MODE= FAKE_BENCH_PID_MODE=error-terminal FAKE_CLOSE_MODE=
: >"$FAKE_STATE_COUNT"
: >"$FAKE_PID_COUNT"
: >"$FAKE_BENCH_PID_COUNT"
if run_controller 'debug-token-ui-observe 192.168.52.52 45678' >"$TMP/pid-query-error.out" 2>&1; then
  echo "terminal PID query error unexpectedly accepted" >&2
  exit 1
fi
grep -Fq 'reason=pid_query_failed' "$TMP/pid-query-error.out"
rm -rf "$ROOT/artifacts/litert_gpu_token_ui_observer/$FAKE_TIMESTAMP"

export FAKE_OBSERVER_MODE=success FAKE_PID_MODE= FAKE_BENCH_PID_MODE= FAKE_TOP_MODE=lost
: >"$FAKE_STATE_COUNT"
if run_controller 'debug-token-ui-observe 192.168.52.52 45678' >"$TMP/foreground-lost.out" 2>&1; then
  echo "terminal foreground loss unexpectedly accepted" >&2
  exit 1
fi
grep -Fq 'reason=initial_foreground_lost' "$TMP/foreground-lost.out"
rm -rf "$ROOT/artifacts/litert_gpu_token_ui_observer/$FAKE_TIMESTAMP"

echo "debug_token_controller_device_gate_test=pass"

# Reviewed no-install candidate security and isolation behavior.
CONTROLLER="$ROOT/scripts/lami_build_remote_control_full.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FUNC="$TMP/reviewed-function.sh"
{
  sed -n '/^run_git_apply_sealed_patch() {/,/^}/p' "$CONTROLLER"
  sed -n '/^run_assemble_reviewed_gpu_30400_candidate() (/,/^)[[:space:]]*$/p' "$CONTROLLER"
} >"$FUNC"
[[ -s "$FUNC" ]] || { echo 'missing reviewed candidate function' >&2; exit 1; }
function_source="$(cat "$FUNC")"
controller_source="$(cat "$CONTROLLER")"
for required in 'umask 077' 'memfd_create' 'MFD_ALLOW_SEALING' 'F_ADD_SEALS' 'F_SEAL_WRITE' 'close_fds=True' 'git diff --cached --raw' '100644' 'mktemp' 'cleanup_error' 'device_operations=none' 'candidate diff sha256 mismatch' 'debug_token_validate_terminal_artifacts'; do
  [[ "$controller_source" == *"$required"* ]] || { echo "missing hardened reviewed-candidate contract: $required" >&2; exit 1; }
done
[[ "$function_source" == *'run_git_apply_sealed_patch'* ]] || { echo 'reviewed candidate does not use sealed patch application' >&2; exit 1; }
[[ "$function_source" != *'"$apk.tmp.$$"'* ]] || { echo 'predictable artifact temp path remains' >&2; exit 1; }
tracked=(
  app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt
  app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkContract.kt
  app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt
  app/src/test/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkUiSourceContractTest.kt
  app/src/test/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkRunSummaryTest.kt
  scripts/lami_build_remote_control_full.sh
)
added=(scripts/debug_token_observer_policy.sh scripts/tests/debug_token_controller_device_gate_test.sh scripts/tests/debug_token_observer_policy_test.sh)
all_expected=("${tracked[@]}" "${added[@]}")
make_fixture() {
  local name="$1" home repo p candidate evil
  home="$TMP/$name/home"; repo="$home/repos/lami-android"
  mkdir -p "$repo" "$home/incoming-patches" "$TMP/$name/bin"
  git init -q -b future "$repo"
  git -C "$repo" config user.email test@example.invalid
  git -C "$repo" config user.name test
  for p in "${tracked[@]}"; do mkdir -p "$repo/$(dirname "$p")"; printf 'base:%s\n' "$p" >"$repo/$p"; done
  cat >"$repo/gradlew" <<'GRADLE'
#!/usr/bin/env bash
set -euo pipefail
[[ "${FAIL_GRADLE:-0}" == 0 ]] || exit 42
grep -Fq 'reviewed-original' app/src/debug/java/io/github/ninbyo02/lami/gpu/DebugTokenBenchmarkActivity.kt
if [[ "$*" == *':app:testStandardDebugUnitTest'* ]]; then
  report=app/build/reports/tests/testStandardDebugUnitTest
  mkdir -p "$report"
  printf '<div class="counter">1408</div>\n' >"$report/index.html"
  exit 0
fi
out=app/build/outputs/apk/standardGpuNoConstraintProvider/debug
mkdir -p "$out"
printf 'reviewed-apk\n' >"$out/app-standardGpuNoConstraintProvider-debug.apk"
GRADLE
  chmod +x "$repo/gradlew"
  git -C "$repo" add "${tracked[@]}" gradlew
  git -C "$repo" commit -qm base
  git init -q --bare "$TMP/$name/origin.git"
  git -C "$repo" remote add origin "$TMP/$name/origin.git"
  git -C "$repo" push -q -u origin future
  candidate="$TMP/$name/candidate"; git clone -q "$repo" "$candidate"
  for p in "${tracked[@]}"; do printf 'reviewed-original:%s\n' "$p" >"$candidate/$p"; done
  for p in "${added[@]}"; do mkdir -p "$candidate/$(dirname "$p")"; printf 'reviewed-original:%s\n' "$p" >"$candidate/$p"; done
  git -C "$candidate" add "${all_expected[@]}"
  git -C "$candidate" diff --cached --binary >"$home/incoming-patches/t57-publication-candidate.patch"
  evil="$TMP/$name/evil"; git clone -q "$repo" "$evil"
  for p in "${tracked[@]}"; do printf 'reviewed-replaced:%s\n' "$p" >"$evil/$p"; done
  for p in "${added[@]}"; do mkdir -p "$evil/$(dirname "$p")"; printf 'reviewed-replaced:%s\n' "$p" >"$evil/$p"; done
  git -C "$evil" add "${all_expected[@]}"
  git -C "$evil" diff --cached --binary >"$TMP/$name/evil.patch"
  printf '%s\n' "$home"
}
invoke_reviewed() {
  local home="$1" base="$2" sha="$3" out="$4"
  set +e
  HOME="$home" PATH="$(dirname "$home")/bin:$PATH" REPO="$home/repos/lami-android" bash -c 'set -euo pipefail; source "$1"; run_assemble_reviewed_gpu_30400_candidate "$2" "$3"' _ "$FUNC" "$base" "$sha" >"$out" 2>&1
  local rc=$?; set -e; return "$rc"
}
home="$(make_fixture main)"; repo="$home/repos/lami-android"; patch="$home/incoming-patches/t57-publication-candidate.patch"
base="$(git -C "$repo" rev-parse origin/future)"; sha="$(sha256sum "$patch" | awk '{print $1}')"
printf 'shared-wip\n' >"$repo/shared-wip.txt"; status_before="$(git -C "$repo" status --porcelain=v1 --untracked-files=all)"
if invoke_reviewed "$home" bad "$sha" "$TMP/invalid-base.out"; then exit 1; else [[ $? -eq 64 ]]; fi
if invoke_reviewed "$home" "$base" bad "$TMP/invalid-sha.out"; then exit 1; else [[ $? -eq 64 ]]; fi
if invoke_reviewed "$home" "0000000000000000000000000000000000000000" "$sha" "$TMP/base-mismatch.out"; then exit 1; else [[ $? -eq 65 ]]; fi
if invoke_reviewed "$home" "$base" "$(printf '0%.0s' {1..64})" "$TMP/hash-mismatch.out"; then exit 1; else [[ $? -eq 65 ]]; fi
home_fetch="$(make_fixture fetchfail)"; repo_fetch="$home_fetch/repos/lami-android"; base_fetch="$(git -C "$repo_fetch" rev-parse origin/future)"
sha_fetch="$(sha256sum "$home_fetch/incoming-patches/t57-publication-candidate.patch" | awk '{print $1}')"
cat >"$TMP/fetchfail/bin/git" <<'GIT'
#!/usr/bin/env bash
set -euo pipefail
[[ "${1:-}" != fetch ]] || exit 99
exec "${REAL_GIT:?}" "$@"
GIT
chmod +x "$TMP/fetchfail/bin/git"
export REAL_GIT="$(command -v git)"
if invoke_reviewed "$home_fetch" "$base_fetch" "$sha_fetch" "$TMP/fetchfail.out"; then exit 1; else [[ $? -eq 74 ]]; fi
[[ -z "$(find "$home_fetch/lami-clean-worktrees" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]
[[ ! -e "$home_fetch/lami-build-artifacts" || -z "$(find "$home_fetch/lami-build-artifacts" -type f -print -quit)" ]]

assert_no_candidate_residue() {
  local home="$1" repo listing
  repo="$home/repos/lami-android"
  [[ ! -e "$home/lami-clean-worktrees" || -z "$(find "$home/lami-clean-worktrees" -mindepth 1 -print -quit 2>/dev/null)" ]]
  listing="$("${REAL_GIT:-$(command -v git)}" -C "$repo" worktree list --porcelain)" || {
    echo "metadata verification listing failed" >&2
    return 1
  }
  [[ -z "$(sed -n 's/^worktree //p' <<<"$listing" | grep -F "$home/lami-clean-worktrees/" || true)" ]]
  [[ ! -e "$home/reviewed-inputs" || -z "$(find "$home/reviewed-inputs" -mindepth 1 -print -quit 2>/dev/null)" ]]
  [[ ! -e "$home/lami-build-artifacts" || -z "$(find "$home/lami-build-artifacts" -type f -print -quit 2>/dev/null)" ]]
}

assert_no_final_apk() {
  local home="$1"
  [[ ! -e "$home/lami-build-artifacts" || -z "$(find "$home/lami-build-artifacts" -type f -name '*.apk' -print -quit 2>/dev/null)" ]]
}

home_rev="$(make_fixture revparsefail)"; repo_rev="$home_rev/repos/lami-android"; base_rev="$(git -C "$repo_rev" rev-parse origin/future)"
sha_rev="$(sha256sum "$home_rev/incoming-patches/t57-publication-candidate.patch" | awk '{print $1}')"
cat >"$TMP/revparsefail/bin/git" <<'GIT'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == 'rev-parse --verify refs/remotes/origin/future^{commit}' ]]; then exit 88; fi
exec "${REAL_GIT:?}" "$@"
GIT
chmod +x "$TMP/revparsefail/bin/git"
export REAL_GIT="$(command -v git)"
if invoke_reviewed "$home_rev" "$base_rev" "$sha_rev" "$TMP/revparsefail.out"; then exit 1; else [[ $? -eq 65 ]]; fi
assert_no_candidate_residue "$home_rev"

home_status="$(make_fixture statusfail)"; repo_status="$home_status/repos/lami-android"; base_status="$(git -C "$repo_status" rev-parse origin/future)"
sha_status="$(sha256sum "$home_status/incoming-patches/t57-publication-candidate.patch" | awk '{print $1}')"
cat >"$TMP/statusfail/bin/git" <<'GIT'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == 'status --porcelain --untracked-files=all' && "$PWD" == *'/lami-clean-worktrees/'* ]]; then exit 89; fi
exec "${REAL_GIT:?}" "$@"
GIT
chmod +x "$TMP/statusfail/bin/git"
if invoke_reviewed "$home_status" "$base_status" "$sha_status" "$TMP/statusfail.out"; then exit 1; else [[ $? -eq 65 ]]; fi
assert_no_candidate_residue "$home_status"

for mode in before after after-list-fail; do
  home_add="$(make_fixture "worktree-add-$mode")"; repo_add="$home_add/repos/lami-android"; base_add="$(git -C "$repo_add" rev-parse origin/future)"
  sha_add="$(sha256sum "$home_add/incoming-patches/t57-publication-candidate.patch" | awk '{print $1}')"
  cat >"$TMP/worktree-add-$mode/bin/git" <<'GIT'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == worktree\ add\ * ]]; then
  if [[ "${WORKTREE_ADD_FAILURE_MODE:?}" != before ]]; then "${REAL_GIT:?}" "$@"; fi
  exit 93
fi
if [[ "${WORKTREE_ADD_FAILURE_MODE:-}" == after-list-fail && "$*" == *' worktree list --porcelain' ]]; then
  printf 'injected\n' >"${WORKTREE_LIST_FAIL_SENTINEL:?}"
  exit 98
fi
exec "${REAL_GIT:?}" "$@"
GIT
  chmod +x "$TMP/worktree-add-$mode/bin/git"
  export WORKTREE_ADD_FAILURE_MODE="$mode"
  export WORKTREE_LIST_FAIL_SENTINEL="$TMP/worktree-add-$mode/list-fail.triggered"
  if invoke_reviewed "$home_add" "$base_add" "$sha_add" "$TMP/worktree-add-$mode.out"; then exit 1; else [[ $? -eq 74 ]]; fi
  if [[ "$mode" == after-list-fail ]]; then
    [[ -s "$WORKTREE_LIST_FAIL_SENTINEL" ]] || { echo 'worktree list failure fixture did not trigger' >&2; exit 1; }
    grep -Fq 'cleanup_error=reviewed_candidate_temporary_state_remains' "$TMP/worktree-add-$mode.out"
  fi
  assert_no_candidate_residue "$home_add"
done
unset WORKTREE_ADD_FAILURE_MODE WORKTREE_LIST_FAIL_SENTINEL

home_rmdir="$(make_fixture rmdirfail)"; repo_rmdir="$home_rmdir/repos/lami-android"; base_rmdir="$(git -C "$repo_rmdir" rev-parse origin/future)"
sha_rmdir="$(sha256sum "$home_rmdir/incoming-patches/t57-publication-candidate.patch" | awk '{print $1}')"
cat >"$TMP/rmdirfail/bin/rmdir" <<'RMDIR'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == *'/lami-clean-worktrees/t57-gpu-30400-candidate.'* ]]; then exit 97; fi
exec "${REAL_RMDIR:?}" "$@"
RMDIR
chmod +x "$TMP/rmdirfail/bin/rmdir"
export REAL_RMDIR="$(command -v rmdir)"
if invoke_reviewed "$home_rmdir" "$base_rmdir" "$sha_rmdir" "$TMP/rmdirfail.out"; then exit 1; else [[ $? -eq 74 ]]; fi
assert_no_candidate_residue "$home_rmdir"

home_cleanup="$(make_fixture cleanupfail)"; repo_cleanup="$home_cleanup/repos/lami-android"; base_cleanup="$(git -C "$repo_cleanup" rev-parse origin/future)"
sha_cleanup="$(sha256sum "$home_cleanup/incoming-patches/t57-publication-candidate.patch" | awk '{print $1}')"
cat >"$TMP/cleanupfail/bin/git" <<'GIT'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == 'rev-parse HEAD' ]]; then exit 88; fi
if [[ "$*" == *' worktree remove --force '* ]]; then
  "${REAL_GIT:?}" "$@" >/dev/null 2>&1 || true
  exit 97
fi
if [[ "$*" == *' worktree prune --expire now' ]]; then
  "${REAL_GIT:?}" "$@" >/dev/null 2>&1 || true
  exit 99
fi
exec "${REAL_GIT:?}" "$@"
GIT
chmod +x "$TMP/cleanupfail/bin/git"
if invoke_reviewed "$home_cleanup" "$base_cleanup" "$sha_cleanup" "$TMP/cleanupfail.out"; then exit 1; else [[ $? -eq 74 ]]; fi
grep -Fq 'cleanup_error=reviewed_candidate_temporary_state_remains' "$TMP/cleanupfail.out"
assert_no_candidate_residue "$home_cleanup"

export REAL_REALPATH="$(command -v realpath)" REAL_STAT="$(command -v stat)" REAL_SHA256SUM="$(command -v sha256sum)"
for artifact_failure_mode in realpath stat sha-command-fail sha-mismatch; do
  home_artifact="$(make_fixture "artifact-$artifact_failure_mode")"; repo_artifact="$home_artifact/repos/lami-android"
  base_artifact="$(git -C "$repo_artifact" rev-parse origin/future)"
  sha_artifact="$(sha256sum "$home_artifact/incoming-patches/t57-publication-candidate.patch" | awk '{print $1}')"
  cat >"$TMP/artifact-$artifact_failure_mode/bin/realpath" <<'REALPATH'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${INJECT_ARTIFACT_FAILURE:?}" == realpath && "$*" == *'/lami-build-artifacts/'*'.apk'* ]]; then exit 97; fi
exec "${REAL_REALPATH:?}" "$@"
REALPATH
  cat >"$TMP/artifact-$artifact_failure_mode/bin/stat" <<'STAT'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${INJECT_ARTIFACT_FAILURE:?}" == stat && "$*" == *'/lami-build-artifacts/'*'.apk'* ]]; then exit 97; fi
exec "${REAL_STAT:?}" "$@"
STAT
  cat >"$TMP/artifact-$artifact_failure_mode/bin/sha256sum" <<'SHA'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$*" == *'/lami-build-artifacts/'*'.apk'* ]]; then
  case "${INJECT_ARTIFACT_FAILURE:?}" in
    sha-command-fail) exit 97 ;;
    sha-mismatch) printf '%064d  %s\n' 0 "${!#}"; exit 0 ;;
  esac
fi
exec "${REAL_SHA256SUM:?}" "$@"
SHA
  chmod +x "$TMP/artifact-$artifact_failure_mode/bin/realpath" "$TMP/artifact-$artifact_failure_mode/bin/stat" "$TMP/artifact-$artifact_failure_mode/bin/sha256sum"
  export INJECT_ARTIFACT_FAILURE="$artifact_failure_mode"
  expected_artifact_rc=74
  [[ "$artifact_failure_mode" != sha-mismatch ]] || expected_artifact_rc=65
  if invoke_reviewed "$home_artifact" "$base_artifact" "$sha_artifact" "$TMP/artifact-$artifact_failure_mode.out"; then
    echo "artifact failure fixture unexpectedly succeeded: $artifact_failure_mode" >&2
    exit 1
  else
    actual_artifact_rc=$?
    if [[ "$actual_artifact_rc" -ne "$expected_artifact_rc" ]]; then
      echo "artifact failure fixture status mismatch: mode=$artifact_failure_mode actual=$actual_artifact_rc expected=$expected_artifact_rc" >&2
      cat "$TMP/artifact-$artifact_failure_mode.out" >&2 || true
      exit 1
    fi
  fi
  assert_no_final_apk "$home_artifact"
  assert_no_candidate_residue "$home_artifact"
done
unset INJECT_ARTIFACT_FAILURE

real_sha="$(command -v sha256sum)"
real_git="$(command -v git)"
cat >"$TMP/main/bin/git" <<'GIT'
#!/usr/bin/env bash
set -euo pipefail
real_git="${REAL_GIT:?}"
if [[ "$*" == "apply --check --index "* ]]; then
  patch_arg="${!#}"
  count="$(cat "${PATCH_RACE_COUNT:?}" 2>/dev/null || echo 0)"
  count=$((count + 1)); printf '%s\n' "$count" >"$PATCH_RACE_COUNT"
  printf 'triggered path=%s\n' "$patch_arg" >"${PATCH_RACE_SENTINEL:?}"
  if cp -- "${EVIL_PATCH:?}" /proc/self/fd/0 2>"${PATCH_RACE_REJECTION:?}"; then
    echo 'reviewed patch stream was writable at apply boundary' >&2
    exit 98
  fi
  cp -- "${EVIL_PATCH:?}" "${ORIGINAL_PATCH:?}"
fi
exec "$real_git" "$@"
GIT
chmod +x "$TMP/main/bin/git"
: >"$TMP/adb.log"
printf '#!/usr/bin/env bash\nprintf "%%s\\n" "$*" >>"${ADB_LOG:?}"\nexit 99\n' >"$TMP/main/bin/adb"; chmod +x "$TMP/main/bin/adb"
export REAL_GIT="$real_git" EVIL_PATCH="$TMP/main/evil.patch" ORIGINAL_PATCH="$patch" ADB_LOG="$TMP/adb.log"
export PATCH_RACE_COUNT="$TMP/patch-race.count" PATCH_RACE_SENTINEL="$TMP/patch-race.triggered" PATCH_RACE_REJECTION="$TMP/patch-race.rejected"
invoke_reviewed "$home" "$base" "$sha" "$TMP/success.out" || { cat "$TMP/success.out" >&2; exit 1; }
[[ "$(cat "$PATCH_RACE_COUNT")" == 1 ]]
grep -Fxq 'triggered path=-' "$PATCH_RACE_SENTINEL"
[[ -s "$PATCH_RACE_REJECTION" ]]
grep -Fxq 'device_operations=none' "$TMP/success.out"; [[ ! -s "$TMP/adb.log" ]]
[[ "$(git -C "$repo" status --porcelain=v1 --untracked-files=all)" == "$status_before" ]]
apk="$(sed -n 's/^apk=//p' "$TMP/success.out")"; [[ -f "$apk" && ! -L "$apk" && -s "$apk" ]]
reported_sha="$(sed -n 's/^apk_sha256=//p' "$TMP/success.out")"; [[ "$reported_sha" == "$($real_sha "$apk" | awk '{print $1}')" ]]
[[ "$(sed -n 's/^candidate_diff_sha256=//p' "$TMP/success.out")" == "$sha" ]]
candidate_tree="$(sed -n 's/^candidate_tree_sha=//p' "$TMP/success.out")"
[[ "$apk" == "$home/lami-build-artifacts/t57-gpu-30400/$candidate_tree/app-standardGpuNoConstraintProvider-debug.apk" ]]
[[ -z "$(find "$home/lami-clean-worktrees" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]
[[ -z "$(find "$home/lami-build-artifacts" -name '*.tmp.*' -o -name '.apk.*' 2>/dev/null)" ]]
home_link="$(make_fixture link)"; repo_link="$home_link/repos/lami-android"; base_link="$(git -C "$repo_link" rev-parse origin/future)"
patch_link="$home_link/incoming-patches/t57-publication-candidate.patch"; sha_link="$($real_sha "$patch_link" | awk '{print $1}')"
mkdir -p "$TMP/link/outside"; ln -s "$TMP/link/outside" "$home_link/lami-build-artifacts"
[[ -L "$home_link/lami-build-artifacts" ]] || { echo 'symlink attack fixture did not trigger' >&2; exit 1; }
if invoke_reviewed "$home_link" "$base_link" "$sha_link" "$TMP/symlink.out"; then exit 1; else [[ $? -eq 65 ]]; fi
[[ -z "$(find "$TMP/link/outside" -mindepth 1 -print -quit)" ]]
[[ -z "$(find "$home_link/lami-clean-worktrees" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]
home_mode="$(make_fixture mode)"; repo_mode="$home_mode/repos/lami-android"; base_mode="$(git -C "$repo_mode" rev-parse origin/future)"
mode_candidate="$TMP/mode/candidate"; rm -f "$mode_candidate/scripts/debug_token_observer_policy.sh"
ln -s ../lami_build_remote_control_full.sh "$mode_candidate/scripts/debug_token_observer_policy.sh"
git -C "$mode_candidate" add scripts/debug_token_observer_policy.sh
[[ "$(git -C "$mode_candidate" ls-files -s scripts/debug_token_observer_policy.sh | awk '{print $1}')" == 120000 ]] || { echo 'mode attack fixture did not create a symlink' >&2; exit 1; }
git -C "$mode_candidate" diff --cached --binary >"$home_mode/incoming-patches/t57-publication-candidate.patch"
sha_mode="$($real_sha "$home_mode/incoming-patches/t57-publication-candidate.patch" | awk '{print $1}')"
if invoke_reviewed "$home_mode" "$base_mode" "$sha_mode" "$TMP/mode.out"; then exit 1; else [[ $? -eq 65 ]]; fi
[[ -z "$(find "$home_mode/lami-clean-worktrees" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]
home_fail="$(make_fixture buildfail)"; repo_fail="$home_fail/repos/lami-android"; base_fail="$(git -C "$repo_fail" rev-parse origin/future)"
patch_fail="$home_fail/incoming-patches/t57-publication-candidate.patch"; sha_fail="$($real_sha "$patch_fail" | awk '{print $1}')"
export FAIL_GRADLE=1
if invoke_reviewed "$home_fail" "$base_fail" "$sha_fail" "$TMP/buildfail.out"; then exit 1; else [[ $? -eq 70 ]]; fi
unset FAIL_GRADLE
[[ -z "$(find "$home_fail/lami-clean-worktrees" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]
[[ -z "$(find "$home_fail/reviewed-inputs" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]
[[ ! -e "$home_fail/lami-build-artifacts" || -z "$(find "$home_fail/lami-build-artifacts" -type f -print -quit)" ]]
echo 'assemble_reviewed_candidate_security_test=pass'
