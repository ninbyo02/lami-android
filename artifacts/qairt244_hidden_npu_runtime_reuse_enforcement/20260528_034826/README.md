# QAIRT244 hidden NPU runtime reuse enforcement

Date: 2026-05-28

Scope: tests, documentation, and artifact text only. No NPU execution, adb,
native change, or rebuild was performed.

## Lifecycle to runtime mapping

`DevOnlyNpuLifecycleWrapper` is the hidden lifecycle decision source for this
phase. `DevOnlyNpuRuntimeReusePolicy` exposes that decision as the
`next_prompt_allowed` gate.

| Evidence outcome | Classification | Runtime policy | `next_prompt_allowed` |
| --- | --- | --- | --- |
| run-id scoped files, current callback/result/native/cleanup ids, terminal success, cleanup timing, `Engine.close=unique_ptr_cleanup`, side effects clear | `SUCCESS_CLEAN` | accept current run and allow reuse | `true` |
| timeout after start or pre-decode evidence | `TIMEOUT_SUSPECT` | suspect session; require isolated next run | `false` |
| missing terminal result, missing cleanup timing, or missing engine close evidence | `CLEANUP_MISSING_SUSPECT` | suspect session; require isolated next run | `false` |
| result timestamp predates current run or stale flag is present | `STALE_RESULT_REJECTED` | reject artifact; require isolated next run | `false` |
| state/result/native_diag/cleanup/callback run id mismatch or unscoped file names | `RUN_ID_MISMATCH_REJECTED` | reject artifact; require isolated next run | `false` |

## Enforcement notes

- Suspect or rejected outcomes set `reuse_allowed=false`.
- Suspect or rejected outcomes set `hidden_per_run_isolated_required=true`.
- 512 remains allowed only for `hidden_per_run_isolated_512` with force-stop
  before and after each prompt.
- Sequential 512 and Activity-restart-only 512 remain rollback modes.
- H1 remains pinned to `max_output_tokens=128`.
- 256 remains the hidden experimental baseline candidate.
- 1024+ remains blocked.

## Test coverage

Added `DevOnlyNpuRuntimeReusePolicyTest` to pin:

- `SUCCESS_CLEAN` opens the next-prompt gate.
- `TIMEOUT_SUSPECT`, `CLEANUP_MISSING_SUSPECT`, `STALE_RESULT_REJECTED`, and
  `RUN_ID_MISMATCH_REJECTED` close the next-prompt gate.
- `hidden_per_run_isolated_required` is enforced through the reuse decision.
- H1/256/512/1024 policy remains unchanged.
