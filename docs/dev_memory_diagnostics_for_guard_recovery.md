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

## Future candidates

- Persist guard-blocked conversation state if in-memory state is not enough for the investigation.
- Compare in-app snapshots with `adb shell dumpsys meminfo <package>`.
- Add per-run artifact export for memory snapshots alongside existing NPU diagnostic logs.
- Add native-layer lifecycle evidence if LiteRT / QNN exposes a safe session or interpreter close callback through the current app integration.
