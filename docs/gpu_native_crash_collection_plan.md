# GPU Native Crash Collection Plan

## Scope

This plan covers the debug-only LiteRT-LM GPU benchmark runner:

- Script: `scripts/run_litert_lm_gpu_benchmark.sh`
- Receiver: `io.github.ninbyo02.lami.gpu.LiteRtLmGpuBenchmarkReceiver`
- Route type: `litert_lm_gpu_benchmark`

Production `ChatScreen`, S1-S5 routes, `Backend.NPU`, QAIRT/QNN setup, and fallback settings are out of scope and were not changed.

## Current Failure Signature

Observed GPU failure:

| item | value |
| --- | --- |
| backend variant | `gpu` |
| last app marker | `engine_create_started` |
| app-side report | missing |
| process after probe | not alive |
| likely class | native crash during LiteRT-LM `Engine` creation |

CPU backend behavior differs: it reaches app-side exception handling and report generation with `LiteRtLmJniException`. That makes GPU native crash collection the next required evidence path.

## Collection Additions

The benchmark script now collects both raw artifacts and structured crash fields under:

`artifacts/litert_lm_gpu_benchmark/<timestamp>/`

| required item | artifact |
| --- | --- |
| background logcat | `logcat_probe_threadtime.txt` |
| post-run `adb logcat -d` | `logcat_postrun_threadtime.txt` |
| logcat tail compatibility output | `logcat_tail.txt` |
| dropbox dump | `dropbox_full.txt` |
| tombstone listing | `tombstone_listing.txt` |
| latest tombstone path | `tombstone_latest_path.txt` |
| latest tombstone body | `tombstone_latest.txt` |
| crash summary | `crash_summary.md` |
| structured fields | `crash_fields.txt` |
| host summary | `summary.txt` |

`crash_summary.md`, `summary.txt`, and timeout fallback markdown now expose the same key root-cause fields:

| field | source priority |
| --- | --- |
| `native_crash_suspected` | pid absence, signal, abort message, or backtrace presence |
| `crash_process` | tombstone, post-run logcat, background logcat, dropbox |
| `signal` | tombstone, post-run logcat, background logcat, dropbox |
| `abort_message` | tombstone, post-run logcat, background logcat, dropbox |
| `backtrace_head` | latest tombstone `backtrace:` first 12 frames |
| `build_ids` | tombstone/logcat/dropbox `BuildId` or `Build ID` lines |

## Expected Diagnostic Read

After a failing GPU run, inspect in this order:

1. `summary.txt`
   - Confirm `backend_variant=gpu`.
   - Confirm `latest_stage=engine_create_started`.
   - Read `native_crash_suspected`, `signal`, `abort_message`, `backtrace_head`, and `build_ids`.
2. `crash_summary.md`
   - Use the top key-value block for quick issue filing.
   - Use the logcat/dropbox/tombstone extracts to verify that the selected crash belongs to `io.github.ninbyo02.lami`.
3. `tombstone_latest.txt`
   - Confirm `Cmdline:` or package process name.
   - Confirm the top native frames and Build IDs.
4. `dropbox_full.txt`
   - Cross-check if tombstone access is restricted or stale.

## Freshness Notes

The current script chooses the latest `/data/tombstones/tombstone_*` and records process liveness plus app markers. If future runs show stale tombstone ambiguity, add a freshness classifier that checks:

- timestamp proximity to the benchmark timestamp,
- package name or pid match,
- marker history ending at `engine_create_started`,
- matching `DEBUGGERD`/`tombstoned` logcat entries in `logcat_probe_threadtime.txt`.

## Reproduction Commands

GPU crash path:

```bash
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu --timeout 180
```

Control paths:

```bash
scripts/run_litert_lm_gpu_benchmark.sh --backend cpu --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend default --timeout 180
```

Verification commands:

```bash
bash -n scripts/run_litert_lm_gpu_benchmark.sh
./gradlew testStandardDebugUnitTest
git diff --check
```

## Root-Cause Evidence Target

The next actionable root-cause report should include:

- `summary.txt` root-cause fields,
- `crash_summary.md`,
- `tombstone_latest.txt` top 40 lines around `signal` and `backtrace`,
- `build_ids` for `liblitertlm_jni.so`, `libLiteRt.so`, GPU/OpenCL/Vulkan-related libraries if present,
- whether the crash process is exactly `io.github.ninbyo02.lami`.

