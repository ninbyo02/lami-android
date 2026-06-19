# LiteRT-LM GPU Integrated Recommendation

## Scope

This integrates the three GPU benchmark investigations:

- `docs/gpu_receiver_vs_chat_route_delta.md`
- `docs/gpu_native_crash_collection_plan.md`
- `docs/gpu_engineconfig_variant_matrix.md`

Production `ChatScreen`, S1-S5 routes, `Backend.NPU`, QAIRT/QNN setup, and fallback settings are out of scope. The implemented changes are limited to the debug-only GPU benchmark receiver, its host script, and documentation.

## Current Conclusion

The receiver crash boundary is after `engine_create_started` and before `engine_create_finished`, so the process dies inside:

```kotlin
Engine(config)
engine.initialize()
```

The CPU receiver route reaches report generation and catches a `LiteRtLmJniException`, which proves the receiver/report path is alive. The GPU route is a native process death, not a catchable Kotlin exception.

The highest-value route delta is `maxNumTokens`: production ChatScreen's `buildLiteRtEngineConfig(...)` uses `maxNumTokens = null`, while the original benchmark GPU variant used explicit `32/64/128/256`. The next most important deltas are cold Engine creation per benchmark case, receiver execution context, and possible model path mismatch.

## Implemented Diagnostic Surface

The benchmark now supports these `--backend` variants:

| variant | purpose |
| --- | --- |
| `cpu` | Control route; should survive to app-side report with catchable exception. |
| `gpu` | Current crashing baseline. |
| `default` | Production-like GPU backend with `maxNumTokens=null`. |
| `gpu-null-max` | Isolates fixed `maxNumTokens` as the variable. |
| `gpu-null-modalities` | Tests nullable vision/audio modality backends. |
| `gpu-cpu-modalities` | Keeps text GPU, forces non-text modalities to CPU. |
| `gpu-cache-dir` | Explicit cache-dir parity confirmation; expected to match `gpu`. |
| `gpu-all` | Combines `maxNumTokens=null` with null modality backends. |

The runner now records native crash evidence under `artifacts/litert_lm_gpu_benchmark/<timestamp>/`, including background logcat, post-run `adb logcat -d`, dropbox, tombstone listing/body, `crash_fields.txt`, and `crash_summary.md`. Key fields are also copied into `summary.txt` and timeout fallback markdown.

## Recommended Run Order

Use an explicit `--model-path` matching the model that successfully generates in ChatScreen whenever possible. Run one variant per process to keep tombstones and marker history unambiguous.

```bash
scripts/run_litert_lm_gpu_benchmark.sh --backend cpu --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend default --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu-null-max --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu-null-modalities --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu-cpu-modalities --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu-all --timeout 180
```

## Decision Matrix

| Observation | Recommendation |
| --- | --- |
| `cpu` writes report, all GPU variants die natively at `engine_create_started`. | Treat as GPU native initialization/model/runtime issue. Use `crash_summary.md`, `tombstone_latest.txt`, and `build_ids` as primary root-cause evidence. |
| `default` or `gpu-null-max` survives but `gpu` crashes. | `maxNumTokens` is the leading trigger. Keep benchmark Engine creation at `maxNumTokens=null`; compare output token caps later through a safer generation-level control if available. |
| `gpu-null-modalities` or `gpu-cpu-modalities` survives. | Non-text modality backend initialization is implicated. Keep benchmark text backend GPU but avoid GPU vision/audio for this route. |
| `gpu-all` survives while narrower variants crash. | Bisect between null max tokens and null modalities. Apply only the smallest stable debug benchmark delta. |
| GPU succeeds only when explicit `--model-path` is used. | Receiver model discovery was selecting a different `.litertlm`; require explicit model path for benchmark investigations. |
| Native backtrace points into GPU/OpenCL/Vulkan delegate setup. | Prioritize runtime/library compatibility and file an upstream/vendor issue with signal, abort message, top frames, and Build IDs. |

## Next Engineering Steps

1. Run `default` and `gpu-null-max` first after the CPU control. These are closest to ChatScreen's successful `maxNumTokens=null` configuration.
2. If both still crash, stop changing EngineConfig and analyze native artifacts. The root-cause report should include `summary.txt`, `crash_summary.md`, `tombstone_latest.txt` top frames, `signal`, `abort_message`, `crash_process`, and `build_ids`.
3. If a null-max variant succeeds, restructure the benchmark so Engine creation uses the stable production-like config, then measure token limits only after Engine initialization is stable.
4. If all raw receiver GPU variants crash but ChatScreen still works, add a future debug-only holder-parity route that uses the same `LocalInferenceEngineHolder` / `buildLiteRtEngineConfig(...)` path as ChatScreen. Do not change production ChatScreen for this.
5. Keep NPU, QAIRT/QNN, fallback settings, and S1-S5 routes untouched until GPU receiver evidence is conclusive.

## Verification Commands

```bash
bash -n scripts/run_litert_lm_gpu_benchmark.sh
./gradlew testStandardDebugUnitTest
git diff --check
```
