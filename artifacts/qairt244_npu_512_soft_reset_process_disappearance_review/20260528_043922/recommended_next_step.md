# Recommended next step

Do not promote 512 sequential and do not proceed to 1024.

The next single-axis task should be instrumentation-only runner hardening
before any further NPU run:

1. Capture `pidof io.github.ninbyo02.lami` before prompt, immediately after
   prompt, after 10 seconds, and before the next broadcast.
2. Clear logcat before the sequence and save a dedicated process-lifecycle
   logcat slice containing ActivityManager, ActivityTaskManager, lmkd, crash,
   ANR, and package process markers.
3. Capture `dumpsys activity processes io.github.ninbyo02.lami` or equivalent
   process state snapshots around prompt boundaries.
4. If the process disappears while `next_prompt_allowed=true`, stop before
   dispatching the next prompt and classify as `PROCESS_DISAPPEARED_SUSPECT`.

Only after that instrumentation is committed should a separately approved
runtime rerun be considered. Until then, keep 512 as
`hidden_per_run_isolated_512` only.
