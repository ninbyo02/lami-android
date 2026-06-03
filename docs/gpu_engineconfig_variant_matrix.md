# LiteRT-LM GPU EngineConfig Variant Matrix

## Scope

This document covers the debug-only LiteRT-LM GPU benchmark receiver route:

- `app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt`
- `scripts/run_litert_lm_gpu_benchmark.sh`

No production ChatScreen, S1-S5, `Backend.NPU`, QAIRT, QNN, or fallback setting is changed by this work.

## Variant Matrix

| backend_variant | EngineConfig.backend | visionBackend | audioBackend | cacheDir | maxNumTokens | maxNumImages | Purpose |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `gpu` | `Backend.GPU()` | `Backend.GPU()` | `Backend.CPU()` | `context.cacheDir.absolutePath` | requested `max_output_tokens` | constructor default | Preserve the currently crashing GPU benchmark baseline. |
| `cpu` | `Backend.CPU()` | `Backend.CPU()` | `Backend.CPU()` | `context.cacheDir.absolutePath` | requested `max_output_tokens` | constructor default | Control route; expected to fail as catchable `LiteRtLmJniException` based on current observation. |
| `default` | `Backend.GPU()` | `Backend.GPU()` | `Backend.CPU()` | `context.cacheDir.absolutePath` | `null` | constructor default | Approximate production ChatScreen config shape while keeping the debug receiver route. |
| `gpu-null-modalities` | `Backend.GPU()` | `null` | `null` | `context.cacheDir.absolutePath` | requested `max_output_tokens` | constructor default | Tests whether explicit non-text modalities trigger GPU engine initialization crash. |
| `gpu-cpu-modalities` | `Backend.GPU()` | `Backend.CPU()` | `Backend.CPU()` | `context.cacheDir.absolutePath` | requested `max_output_tokens` | constructor default | Keeps text on GPU while forcing non-text modalities away from GPU. |
| `gpu-cache-dir` | `Backend.GPU()` | `Backend.GPU()` | `Backend.CPU()` | `context.cacheDir.absolutePath` | requested `max_output_tokens` | constructor default | Names the cacheDir axis explicitly. This is intentionally equivalent to current `gpu` for regression confirmation. |
| `gpu-null-max` | `Backend.GPU()` | `Backend.GPU()` | `Backend.CPU()` | `context.cacheDir.absolutePath` | `null` | constructor default | Tests whether fixed `maxNumTokens` contributes to native GPU initialization failure. |
| `gpu-all` | `Backend.GPU()` | `null` | `null` | `context.cacheDir.absolutePath` | `null` | constructor default | Combines the two most likely EngineConfig optional-value mitigations. |

## Expected Behavior

| backend_variant | Expected result | Crash likelihood | Interpretation |
| --- | --- | --- | --- |
| `gpu` | Reproduces current native crash after `engine_create_started`. | High | Confirms unchanged baseline. |
| `cpu` | Receiver survives and writes a failure report with Java/Kotlin exception. | Low native-crash risk | Confirms report path and model path are usable outside GPU. |
| `default` | If successful or catchable, `maxNumTokens=null` is relevant. If it still crashes, fixed max token count is unlikely as sole cause. | Medium | Closest existing receiver approximation of production ChatScreen config. |
| `gpu-null-modalities` | If it avoids native crash, explicit `visionBackend=GPU` or `audioBackend=CPU` interaction is suspicious. | Medium | Isolates modality backend arguments. |
| `gpu-cpu-modalities` | If it avoids native crash, `visionBackend=GPU` is suspicious. | Medium | Keeps all modalities explicit but removes GPU vision path. |
| `gpu-cache-dir` | Expected to match `gpu`. | High | Confirms cacheDir is not silently omitted in the named variant. |
| `gpu-null-max` | If it avoids native crash, fixed `maxNumTokens` is suspicious. | Medium | Production ChatScreen uses `maxNumTokens=null`. |
| `gpu-all` | Best chance among EngineConfig-only variants to avoid crash while staying on GPU text backend. | Lower than baseline | If this still crashes, suspect native GPU initialization, model/runtime compatibility, or receiver process context rather than these optional EngineConfig fields. |

## Implementation

The debug receiver now parses the same `backend_variant` extra used by the script and supports:

```text
gpu
cpu
default
gpu-null-modalities
gpu-cpu-modalities
gpu-cache-dir
gpu-null-max
gpu-all
```

The receiver resolves these variants in `resolveEngineConfigParts()` before constructing `EngineConfig`. The `backend_selected` and `engine_create_started` markers include:

```text
backend_variant=<variant>
engine_backend=<GPU|CPU>
vision_backend=<GPU|CPU|null>
audio_backend=<CPU|null>
config_style=<style>
cache_dir=<path|null>
max_output_tokens=<requested>
config_max_num_tokens=<value|null>
max_num_images=constructor_default
```

The host script only changed the `--backend` allowlist/help text. It still sends `backend_variant` through the existing debug-only benchmark receiver and still writes `backend_variant` into fallback reports.

### Code Shape

The implementation is intentionally local to the debug receiver:

```kotlin
val configParts = resolveEngineConfigParts(
    appContext = appContext,
    backendVariant = backendVariant,
    maxOutputTokens = maxOutputTokens,
)

val config = EngineConfig(
    modelPath = modelPath,
    backend = configParts.backend,
    visionBackend = configParts.visionBackend,
    audioBackend = configParts.audioBackend,
    maxNumTokens = configParts.maxNumTokens,
    cacheDir = configParts.cacheDir,
)
```

`resolveEngineConfigParts()` is the only new config switch. It returns nullable `visionBackend`, nullable `audioBackend`, nullable `maxNumTokens`, and the existing `context.cacheDir.absolutePath` cache directory according to the matrix above.

## Suggested Run Order

Run one variant per process to keep tombstones and markers easy to correlate:

```bash
scripts/run_litert_lm_gpu_benchmark.sh --backend cpu --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend default --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu-null-max --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu-null-modalities --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu-cpu-modalities --timeout 180
scripts/run_litert_lm_gpu_benchmark.sh --backend gpu-all --timeout 180
```

## Decision Rules

| Observation | Next conclusion |
| --- | --- |
| All GPU variants crash at `engine_create_started` before `Engine(config)` returns or `Engine.initialize()` completes. | EngineConfig optional values are unlikely to be the primary cause. Prioritize native crash collection and GPU runtime/model compatibility. |
| Only variants with `visionBackend=GPU` crash. | Investigate vision backend initialization even for text-only model loading. |
| Only variants with fixed `maxNumTokens` crash. | Align benchmark receiver with production `maxNumTokens=null` and rerun token-count comparisons after engine creation is stable. |
| `gpu-all` succeeds but `gpu` crashes. | Narrow by rerunning `gpu-null-max`, `gpu-null-modalities`, and `gpu-cpu-modalities`; apply only the smallest successful EngineConfig delta to the debug benchmark route. |
| `default` succeeds and `gpu` crashes. | Production-like `maxNumTokens=null` is the leading receiver-specific delta. |
