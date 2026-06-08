# DEV memory diagnostics for guard recovery

## Why this was added

Safety guard activation on the LiteRT / QNN / NPU investigation path can be followed by app instability when the same thread or conversation continues. The new DEV diagnostics record app and system memory snapshots around generation stages so we can compare whether memory pressure returns after a guarded or failed run.

This is diagnostic-only. It does not bypass safety guards, change prompt templates, change sanitizer or stop-sequence behavior, or change the NPU / GPU fallback policy.

After a safety guard activation, the same conversation is treated as blocked. The guard is not a normal stop or user cancel; it is a safety stop on a path that may have left runner, engine, or native context state in an unsafe condition. Continuing the same conversation can repeatedly re-enter the same guard path and can obscure whether memory pressure is recovering.

## What is collected

The snapshots are labeled as App/System memory diagnostics and may include:

- stage and timestamp
- Java heap used / max MB
- native heap allocated / size MB
- total PSS / RSS / Swap PSS MB when Android exposes the value
- Native Heap PSS / RSS / alloc / size MB when Android exposes the value
- Dalvik Heap PSS / RSS / alloc / size MB when Android exposes the value
- total PSS MB
- private dirty / clean MB
- graphics / stack / code / system / unknown PSS MB when Android exposes the value
- available system memory MB
- system memory threshold MB
- Android low-memory flag
- thread name
- GC count when Android exposes it

The values come from Android process and system APIs such as `Debug.getMemoryInfo()`, `Debug.getNativeHeapAllocatedSize()`, `Debug.getNativeHeapSize()`, `Runtime`, and `ActivityManager.MemoryInfo`.

The DEV output uses a dumpsys-comparable key/value shape:

```text
[DEV診断: App/System memory diagnostics]
memory_stage=before_generate
native_heap_alloc_mb=...
total_pss_mb=...
total_rss_mb=unavailable
total_swap_pss_mb=unavailable
source=android_debug_memoryinfo_api
measurement_note=api_derived_approximate_may_not_match_adb_dumpsys_meminfo
adb_compare_hint=compare_with_adb_shell_dumpsys_meminfo_package
```

For each later stage, the diagnostics also show deltas from `before_generate` for total PSS, total RSS, Swap PSS, native heap alloc, native heap PSS, Dalvik heap PSS, and system available memory. Unavailable API values are displayed as `unavailable`.

## App/System memory recovery check

The DEV diagnostics include a manual `メモリ回復確認` button. It is shown only in DEV diagnostics and does not run during normal generation. Pressing the button starts an asynchronous App/System memory recovery check that captures snapshots at:

- `memory_recovery_current` immediately after the button press
- `memory_recovery_delayed_1s`
- `memory_recovery_delayed_3s`
- `memory_recovery_delayed_5s`

The 0 / 1 / 3 / 5 second sequence is intended to distinguish an immediate leak-like increase from delayed accounting or asynchronous release in LiteRT / QNN / Android memory accounting. `generation_finished` or `after_runner_dispose` can be too early to treat as the final post-release value.

Starting a new recovery check cancels the previous recovery-check job. If generation is running, the button is disabled or the UI asks the user to run it after generation completes. The normal generation, TTS, DB save, and UI response paths are not delayed by this check.

The recovery check reports deltas from `memory_recovery_current` for total PSS, native heap PSS, native heap alloc, Dalvik heap PSS, and available system memory. These are still app API-derived approximate values and may not match `adb shell dumpsys meminfo io.github.ninbyo02.lami` exactly.

The DEV diagnostics copy text includes the recovery check section as well as the on-screen diagnostics. This lets repeated conversation runs be pasted into chat, logs, or a spreadsheet without manually transcribing the values. For each run, compare:

- `total_pss_mb`
- `native_heap_pss_mb`
- `native_heap_alloc_mb`
- `system_available_memory_mb`
- the recovery delta section from `memory_recovery_current` to `memory_recovery_delayed_1s`, `memory_recovery_delayed_3s`, and `memory_recovery_delayed_5s`

If the 0 / 1 / 3 / 5 second values stabilize or move back toward the pre-run baseline, the after-run increase may be delayed release or delayed accounting. If the values keep increasing across repeated runs, collect the copied DEV diagnostics together with `adb shell dumpsys meminfo io.github.ninbyo02.lami`, logcat, and any available tombstones or vendor runtime logs.

## NPU S1 repeated run diagnostics

The DEV diagnostics also include an `NPU S1 20回連続テスト` button for NPU Standard Route S1. This is a diagnostic-only sequential runner. It is separate from normal chat send, does not automate normal chat tapping, does not write normal conversation history, and does not run TTS for each diagnostic run.

The default diagnostic run uses:

- prompt: `こんにちは`
- run count: `20`
- max output tokens: `32`, matching the current S1 setting
- one run at a time only
- five-second App/System memory recovery snapshot after each run

The repeated runner has five DEV-only modes:

- `repeated_run_mode=reuse`: default mode. It preserves the current S1 repeated-run behavior and does not request an explicit recreate between runs.
- `repeated_run_mode=reuse_10s`: does not request engine recreate, but waits 10 seconds after each successful run before starting the next run. Use this to test whether QNN / HTP / FastRPC cleanup is simply delayed.
- `repeated_run_mode=reuse_30s`: does not request engine recreate, but waits 30 seconds after each successful run before starting the next run. Use this as the stronger cleanup-delay check.
- `repeated_run_mode=recreate`: after each run, the runner requests the existing safe holder recreate path. This is used to check whether repeated failure is tied to runner / engine / session reuse. Direct S1 native runner, LiteRT-LM session, and QNN session dispose APIs are not exposed through this UI layer, so no unsafe forced release is attempted.
- `repeated_run_mode=recreate_3s`: same as `recreate`, then waits three seconds inside the DEV repeated runner before the next run. This isolates delayed LiteRT / QNN / Android memory/resource release from normal app flow.

The DEV copy text includes:

- `[DEV診断: NPU S1 repeated run summary]`
- `[DEV診断: NPU S1 repeated run details]`
- `repeated_run_mode`
- per-run output, timing, short-output telemetry, and five-second memory recovery values
- stopped reason when the runner stops early
- unavailable finish / tokenizer values as explicit `unavailable` or `not_exposed`

Use these fields to compare repeated S1 behavior:

- `all_outputs_same`
- `most_common_output`
- `npu_s1_output_tokens`
- `npu_s1_token_count_mode`
- `finish_reason`
- `stop_reason`
- `memory_recovery_5s_total_pss_mb`
- `memory_recovery_5s_native_heap_pss_mb`
- `memory_recovery_5s_native_heap_alloc_mb`
- `memory_recovery_5s_system_available_memory_mb`
- `peak_5s_total_pss_mb`
- `peak_5s_native_heap_pss_mb`
- `memory_growth_suspected`
- `repeated_run_wait_ms`
- `total_wait_time_ms`
- `failure_after_total_wait_ms`
- per-run `wait_after_run_ms`
- per-run `wait_started_at_elapsed_realtime_ms`
- per-run `wait_finished_at_elapsed_realtime_ms`

The repeated runner stops early if `fallback_used`, `fresh_crash`, `timeout`, `low_memory`, `safety_guard_triggered`, `run_decode_reached=false`, `status != success`, near-threshold system memory, cancellation, or an abnormally long run is observed.

Interpretation examples:

- If all runs return `こんにちは。` with `all_outputs_same=true`, continue short-output finish reason investigation rather than changing prompt or generation settings.
- If five-second memory values stabilize, the immediate after-run increase may be delayed accounting or delayed release rather than a simple leak.
- If `system_available_memory_mb` trends down while process PSS does not explain it, compare with `adb shell dumpsys meminfo io.github.ninbyo02.lami` and device-level memory pressure logs.
- If `memory_growth_suspected=true`, collect DEV copy text, `dumpsys meminfo`, logcat, and any vendor runtime logs from the same run window.

For a run 7 style `adapter_failure`, inspect the copied diagnostics in this order:

1. `failure_pattern_hint`
2. `first_failure_native_stage`
3. `first_failure_native_error_stage`
4. failing run `native_stage_history`
5. failing run `native_diag_tail`
6. Dropbox / tombstone presence near `first_failure_wall_time_ms`

If there is no new tombstone or Dropbox native crash near the S1 failure time, treat the S1 failure as a JNI exception returned to the app layer rather than a fresh native `SIGABRT`. If `native_call_reached=true` and `native_call_returned=false`, suspect a failure inside the native call before a normal Kotlin return. If `native_call_returned=true` and `native_stage_history` reaches `native_result_parse`, suspect adapter result parsing or result conversion. If `native_cleanup_reached` or `native_session_destroy_reached` remains `unavailable`, cleanup / session destroy is not observable from the current app layer; use `native_diag_tail` and tombstones before assuming a leak.

Mode comparison examples:

- `reuse_30s` still stops around run 6-7 with `engine-create-failed:INTERNAL`: suspect an `EngineFactory::CreateDefault` / process-level cumulative create limit rather than short cleanup delay.
- `reuse_30s` reaches run 20 while `reuse` fails around run 6-7: suspect delayed QNN / HTP / FastRPC cleanup.
- `reuse_10s` improves the failure point but `reuse_30s` completes: suspect cleanup delay with a recovery window between 10 and 30 seconds.
- `reuse` stops around run 7, but `recreate` completes 20 runs: suspect runner / engine / session reuse.
- `reuse` stops around run 7, `recreate` also stops around run 7, but `recreate_3s` completes 20 runs: suspect delayed LiteRT / QNN release or delayed memory accounting.
- all three modes stop around run 7 with `adapter_failure` and `run_decode_reached=false`: suspect LiteRT-LM JNI / QNN internal state rather than app-side chat flow or DB/TTS/markdown behavior.

## What is not collected

The standard Android APIs do not reliably separate QNN / NPU dedicated memory from the app process and system memory totals. These diagnostics must not be interpreted as direct accelerator-dedicated memory readings.

Use the snapshots as app/process/system-pressure evidence. For accelerator-specific native resource ownership, compare with lower-level vendor logs, tombstones, and `adb shell dumpsys meminfo`.

The Android in-app API values may not exactly match `adb shell dumpsys meminfo`. In particular, `TOTAL SWAP PSS`, RSS, System, and accelerator/vendor memory accounting can differ by device, OS version, and API availability. Treat the DEV diagnostics as trend and stage-delta evidence, not as a byte-for-byte replacement for `dumpsys`.

## dumpsys meminfo comparison map

| DEV diagnostic key | Closest `adb shell dumpsys meminfo` area | Notes |
| --- | --- | --- |
| `total_pss_mb` | `TOTAL PSS` / App Summary total PSS | API-derived from `Debug.MemoryInfo`; usually closest process-level comparison. |
| `total_rss_mb` | `TOTAL RSS` | Nullable; not exposed consistently on all Android versions. |
| `total_swap_pss_mb` | `TOTAL SWAP PSS` | Nullable; not exposed consistently on all Android versions. |
| `native_heap_pss_mb` | `Native Heap` PSS | API-derived from `Debug.MemoryInfo.nativePss`. |
| `native_heap_rss_mb` | `Native Heap` RSS | Usually unavailable in app APIs. |
| `native_heap_alloc_mb` | `Native Heap` Alloc | From `Debug.getNativeHeapAllocatedSize()`. |
| `native_heap_size_mb` | `Native Heap` Heap Size | From `Debug.getNativeHeapSize()`. |
| `dalvik_heap_pss_mb` | `Dalvik Heap` PSS | API-derived from `Debug.MemoryInfo.dalvikPss`. |
| `dalvik_heap_rss_mb` | `Dalvik Heap` RSS | Usually unavailable in app APIs. |
| `dalvik_heap_alloc_mb` | `Dalvik Heap` Alloc | Approximation from `Runtime.totalMemory() - Runtime.freeMemory()`. |
| `dalvik_heap_size_mb` | `Dalvik Heap` Heap Size | Approximation from `Runtime.totalMemory()`. |
| `private_dirty_mb` | `Private Dirty` total | API-derived process total. |
| `private_clean_mb` | `Private Clean` total | API-derived process total. |
| `graphics_pss_mb` | App Summary Graphics | Nullable; API-derived summary stat. |
| `stack_pss_mb` | App Summary Stack | Nullable; API-derived summary stat. |
| `code_pss_mb` | App Summary Code | Nullable; API-derived summary stat. |
| `system_pss_mb` | App Summary System | Nullable; API-derived summary stat. |
| `unknown_pss_mb` | Other / Unknown | Approximate; currently from `Debug.MemoryInfo.otherPss`. |
| `system_available_memory_mb` | Device available RAM | From `ActivityManager.MemoryInfo`, not the process meminfo table. |
| `system_memory_threshold_mb` | Low-memory threshold | From `ActivityManager.MemoryInfo`. |
| `low_memory` | System low-memory flag | From `ActivityManager.MemoryInfo`. |

## What to check after guard activation

- Whether native heap returns near the `before_generate` baseline.
- Whether total PSS returns near the `before_generate` baseline.
- Whether available system memory recovers after the guarded or failed run.
- Whether the same conversation/thread keeps increasing native heap or total PSS across repeated runs.
- Whether `low memory` flips to `true` around `safety_guard_triggered`, `generation_failed`, or cleanup stages.
- Whether the DEV diagnostics show `guard state: blocked` and `last safety stage: safety_guard_triggered`.
- Whether `after_cancel` and `after_runner_dispose` or `after_engine_recycle` are recorded after the guard stage.

## Guard blocking and cleanup policy

When `safety_guard_triggered` is detected:

- the conversation is marked blocked in UI/in-memory state;
- generation is not invoked again for that conversation;
- the user is directed to continue in a new conversation;
- local streaming/TTS state is stopped where the UI has control;
- the held local runner/engine path is reset or scheduled for recreation through existing holder APIs;
- memory snapshots are recorded around `safety_guard_triggered`, `after_cancel`, and `after_runner_dispose` or `after_engine_recycle`.

The recycle step uses existing safe APIs only. If a lower-level LiteRT / QNN / native session does not expose a close or dispose hook through the current app layer, this diagnostic cannot prove the native resource was fully released. Use the in-app App/System memory diagnostics together with device logs, tombstones, and `adb shell dumpsys meminfo <package>`.

## Suggested device check sequence

For a guarded run, compare these stages in the DEV diagnostics:

- `before_generate`
- `after_prompt_build`
- `before_engine_call`
- `safety_guard_triggered`
- `after_cancel`
- `after_runner_dispose` or `after_engine_recycle`
- `generation_finished` or `generation_failed`

Then compare the same moment with:

```shell
adb shell dumpsys meminfo io.github.ninbyo02.lami
```

Look for native heap, total PSS, and available system memory returning toward the `before_generate` baseline. If the same conversation is blocked but another new conversation still accumulates memory after each guard, collect the in-app stages and `dumpsys meminfo` output together.

For NPU Standard Route S1:

1. Save the DEV diagnostics and `adb shell dumpsys meminfo io.github.ninbyo02.lami` before generation.
2. Run NPU Standard Route S1.
3. Save the `generation_finished` or `safety_guard_triggered` DEV diagnostics.
4. Save `after_engine_recycle` or `after_runner_dispose` DEV diagnostics when present.
5. Save `adb shell dumpsys meminfo io.github.ninbyo02.lami` again.
6. Compare total PSS, RSS, Swap PSS, Native Heap, Dalvik Heap, and system available memory deltas.

For the manual memory recovery check:

1. Run NPU Standard Route S1.
2. Confirm `generation_finished` and `after_runner_dispose` in DEV diagnostics.
3. Press `メモリ回復確認` in DEV diagnostics.
4. Compare `memory_recovery_current`, `memory_recovery_delayed_1s`, `memory_recovery_delayed_3s`, and `memory_recovery_delayed_5s`.
5. Check whether `native_heap_pss_mb`, `total_pss_mb`, and `system_available_memory_mb` recover over time.
6. Compare with `adb shell dumpsys meminfo io.github.ninbyo02.lami` when external confirmation is needed.

For the NPU S1 repeated run diagnostic:

1. Enable DEV diagnostics.
2. Run NPU Standard Route S1 once and confirm the route is connected.
3. Press `NPU S1 20回連続テスト`.
4. Wait for `repeated_run_status=completed`, `stopped`, or `cancelled`.
5. Copy DEV diagnostics.
6. Compare `all_outputs_same`, `most_common_output`, output token fields, timing min/max/average, and five-second memory recovery values.
7. Run `adb shell dumpsys meminfo io.github.ninbyo02.lami` near the same window when external comparison is needed.

For Android Studio Logcat confirmation and startup probe triage:

1. Open Android Studio Logcat.
2. Select the target device, for example `nubia NX733J`.
3. Set the query to `LamiNpuS1`.
4. Launch Lami and confirm `event=dev_logcat_probe_started`.
5. Open DEV diagnostics and start `NPU S1 20回連続テスト` with `reuse`.
6. Confirm how far the sequence reaches: `event=repeated_run_button_clicked_or_start_invoked`, `event=repeated_runner_entered`, `event=helper_called`, `event=repeated_run_start`, and then `event=adapter_failure` if the adapter fails.
7. If `event=dev_logcat_probe_started` is missing, check build variant, `BuildConfig.DEBUG`, Logcat capture, and install target.
8. If startup appears but button/start is missing, check the UI operation path.
9. If button/start appears but runner-entered is missing, check the ChatScreen to repeated-run connection.
10. If runner-entered appears but repeated-run-start is missing, check runner internal early return or branching.
11. If `event=adapter_failure` appears, inspect its throwable and stack trace for LiteRT / QNN / JNI investigation.
12. Confirm `event=repeated_run_stopped` includes the stop reason, success/fallback/timeout/crash/safety counts, and five-second memory trend fields when the runner stops.

For Engine.initialize / nativeCreateEngine crash triage, use `LamiNpuEngine` in Android Studio Logcat. A broader query for a crash window is:

```text
LamiNpuEngine OR LamiNpuS1 OR AndroidRuntime OR FATAL EXCEPTION OR SIGABRT OR tombstone OR crash_dump
```

The most important marker is `event=engine_initialize_operation_before_native_create`. If this appears but `event=engine_initialize_operation_after_native_create` does not, and a tombstone follows, treat the failure point as inside LiteRT-LM `nativeCreateEngine` / `Engine.initialize`.

CLI Logcat collection:

```bash
adb -s 192.168.52.52:42685 logcat -c
adb -s 192.168.52.52:42685 logcat -v time | tee lami_engine_probe.logcat
```

After the app stops or crashes:

```bash
grep -iE "LamiNpuEngine|LamiNpuS1|AndroidRuntime|FATAL EXCEPTION|SIGSEGV|SIGABRT|tombstone|crash_dump|LiteRt|litertlm|QNN|HTP|FastRPC|io.github.ninbyo02.lami" \
  lami_engine_probe.logcat | tail -400
```

Dropbox and tombstone checks:

```bash
adb -s 192.168.52.52:42685 shell dumpsys dropbox --print | grep -iE "io.github.ninbyo02.lami|native_crash|data_app_crash|system_app_crash|tombstone|crash|SIGABRT|litertlm" -A 100 -B 30 | tail -400

adb -s 192.168.52.52:42685 shell ls -lt /data/tombstones 2>/dev/null | head -20
adb -s 192.168.52.52:42685 shell cat /data/tombstones/tombstone_00 2>/dev/null | head -160
```

If Logcat is not readable on the device, do not rely on Logcat-only diagnosis. Some devices can report `0 B readable` from `adb logcat -g`, and even a direct probe such as `adb shell log -p e -t LamiNpuEngine test` may not appear in `adb logcat -d`. In that state, use the copied DEV diagnostics plus Dropbox/tombstone timestamps instead.

For NPU S1 repeated run versus native tombstone correlation:

1. In Lami, open DEV diagnostics.
2. Start `NPU S1 20回連続テスト` with `reuse`.
3. After it stops, copy and save the DEV diagnostics.
4. In the copied `[DEV診断: NPU S1 repeated run summary]`, find `process_pid`, `first_failure_run_index`, `first_failure_wall_time_ms`, `first_failure_elapsed_realtime_ms`, `first_failure_stage`, `first_failure_reason`, `first_failure_exception_class`, and `tombstone_compare_hint`.
5. In the copied `[DEV診断: NPU S1 repeated run details]`, inspect the failing `run_index` for `run_started_at_wall_time_ms`, `run_finished_at_wall_time_ms`, `failure_detected_at_wall_time_ms`, `engine_request_started_at_elapsed_realtime_ms`, `decode_started_at_elapsed_realtime_ms`, and `failure_stage`.
6. Compare `first_failure_wall_time_ms` and `failure_detected_at_wall_time_ms` with Dropbox and tombstone timestamps. If the failure window matches a `SIGABRT` / `liblitertlm_jni.so` / `nativeCreateEngine` tombstone, treat the repeated-run adapter failure and native crash as correlated.
7. If no new Dropbox entry or tombstone appears near the S1 failure time, treat the S1 failure as a returned app-layer adapter/JNI failure rather than a fresh native process crash.
8. Then inspect `engine_request_count`, `adapter_call_count`, `decode_success_count`, `first_failure_counter_snapshot`, and `failure_pattern_hint`.
9. If run 7 shows `adapter_call_count=7` and `decode_success_count=6`, the seventh adapter/decode handoff is the likely failure point.
10. `engine_create_*` is `not_exposed` when the repeated runner cannot directly observe the LiteRT-LM Engine creation API. Do not read `engine_create_attempt_count=unavailable` as zero attempts.
11. Inspect `first_failure_native_stage`, `first_failure_native_error_stage`, `first_failure_native_error_class`, `first_failure_native_error_source`, `first_failure_native_stage_history`, and `first_failure_native_diag_tail`.
12. In the failing run details, compare `native_call_reached`, `native_call_returned`, `native_decode_started`, `native_cleanup_reached`, and `native_session_destroy_reached`.

Device collection commands for this fallback path:

```bash
adb -s 324451613506 shell dumpsys dropbox --print | grep -a -iE "io.github.ninbyo02.lami|native_crash|tombstone|SIGABRT|litertlm|LiteRt|QNN|HTP" -A 120 -B 40 | tail -500

adb -s 324451613506 shell ls -lt /data/tombstones 2>/dev/null | head -20
adb -s 324451613506 shell cat /data/tombstones/tombstone_00 2>/dev/null | head -180
```

## Future candidates

- Persist guard-blocked conversation state if in-memory state is not enough for the investigation.
- Compare in-app snapshots with `adb shell dumpsys meminfo <package>`.
- Add per-run artifact export for memory snapshots alongside existing NPU diagnostic logs.
- Add native-layer lifecycle evidence if LiteRT / QNN exposes a safe session or interpreter close callback through the current app integration.
