# Edge Gallery Runtime Config Static Trace

Scope: static investigation only. No runtime behavior change is proposed or
implemented by this document.

## Current Comparison Baseline

Known behavior:

| Route | Result |
| --- | --- |
| Edge Gallery official GPU | Long Japanese output is normal on the same device/model class. |
| Lami CPU | Long Japanese output is normal after holder identity separation. |
| Lami `standardDebug` GPU | Fails before first token with compiled-model invoke error. |
| Lami `standardGpuMinimalRuntimeCandidateDebug` GPU | Invokes and short output can pass, but long raw callback corrupts. |

Current Lami corruption signatures:

- `callback_corruption_earliest_stage=raw_callback`
- `gpu_output_source_corruption_stage=raw_callback`
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`
- `edge_gallery_parity_difference_summary=edge_gallery_gpu_ok_lami_gpu_raw_callback_decode_fragmentation`
- `edge_gallery_final_response_probe_result=pass`
- `edge_gallery_final_response_probe_difference_summary=last_non_empty_callback_is_delta_not_final_response`

## Runtime / Config Surface Matrix

| Area | Edge Gallery static evidence | Lami current surface | Current read |
| --- | --- | --- | --- |
| Public backend request | Allowlist model declares `accelerators=gpu,cpu`; app can request GPU. | Public `Backend.GPU()` is used for GPU. | Public backend request can be aligned. |
| Backend labels | Native strings list `CPU, GPU, NPU, GPU_ARTISAN, CPU_ARTISAN, GOOGLE_TENSOR_ARTISAN`. | Reflection/diagnostics expose `CPU,GPU,NPU` only. | Lami cannot currently select artisan backends directly. |
| EngineConfig | Edge Gallery app code uses `EngineConfig` with model config values. | Lami builds `EngineConfig(...)` and records `engine_config_fingerprint`. | Public engine config can be compared and mostly mimicked. |
| ConversationConfig | Edge Gallery normal chat uses `ConversationConfig` with sampler. | Lami creates `ConversationConfig` when public class is available and records `conversation_config_fingerprint`. | Public conversation config can be mimicked. |
| SamplerConfig | Allowlist: `topK=64`, `topP=0.95`, `temperature=1.0`. | Lami Gallery-like sampler uses the same values. | Sampler-only root cause is currently weak. |
| max tokens | Edge Gallery allowlist says `maxTokens=4000`. | Lami matrix supports `128,256,512,1024,4000`; Gallery-like default constant is `1024` unless the candidate flavor/profile overrides. | Token budget affects test shape but is not sufficient to explain raw source corruption. |
| cacheDir | Edge Gallery appears to use app-private model path behavior; previous static diff indicates null cache for normal app-private model paths. | Lami exposes cache-dir parity modes and diagnostics. | Cache remains testable but lower-ranked after parity failures. |
| RuntimeConfig | Native strings include `GetRuntimeConfig not implemented for backend:` and runtime config references. | No stable public Lami setter/getter found. | RuntimeConfig is a leading hidden delta. |
| Backend constraints | Native strings include matched/mismatch/model requires messages. | Lami has no public backend constraint setter. | Backend constraint-driven selection remains plausible. |
| Preferred engine type | Native strings mention preferred engine types in prior extraction. | No public preferred engine type setter found. | Possible hidden selector delta. |
| Artisan executor | Native strings include `LlmGpuArtisanExecutor::Create` and `Prefill`. | No public `GPU_ARTISAN` route. | Leading executor mismatch candidate. |
| GPU KV cache | Native strings include `tflite_gpu_kv_cache` and `tflite_opencl_kv_cache`. | No direct public KV-cache selector. | Plausible decode fragmentation contributor. |
| Callback API | Visible JNI includes `nativeGenerateContent`, `nativeGenerateContentStream`, `nativeRunPrefill`, `nativeRunDecode`, `nativeSendMessage`, `nativeSendMessageAsync`. | Lami uses public conversation send/generate paths and callback streaming probes. | Public entry points exist, but internal callback source may differ by executor/config. |

## What Lami Can Configure Today

From current code and diagnostics, Lami can safely vary these in DEV-only GPU
candidate modes:

- public backend: `Backend.CPU()` / `Backend.GPU()`
- text/vision/audio backend fields in `EngineConfig`
- `cacheDir`
- `maxNumTokens`
- `ConversationConfig` presence
- `SamplerConfig(topK, topP, temperature)` presence
- callback streaming vs collect-only/final-commit app behavior
- held-engine reuse vs no-reuse parity mode
- raw callback artifact and quality instrumentation

## What Looks Internal To Edge Gallery / LiteRT-LM

These are visible statically but not controllable through Lami's current public
API:

- `GPU_ARTISAN` / `CPU_ARTISAN` / `GOOGLE_TENSOR_ARTISAN`
- `LlmGpuArtisanExecutor`
- model backend constraint matching
- preferred engine type selection
- internal `RuntimeConfig`
- GPU KV-cache implementation selection
- model-resource path that distinguishes `GPU_ARTISAN` from
  `TF_LITE_PREFILL_DECODE`

These should not be accessed by reflection hacks or production route changes.
They should be treated as static evidence for the next diagnostic step.

## Interpretation

The Lami parity modes have already reduced these candidates:

- UI append / Markdown repair: lower priority because raw callback is corrupt.
- Callback accumulated-vs-delta semantics: lower priority because final response
  probe indicates last non-empty callback is delta, not a complete response.
- Sampler-only: lower priority because baseline, collect-only, and
  no-sampling-acceleration all fail.
- Model file identity: lower priority because Lami CPU and Edge Gallery GPU can
  use the same model family successfully.

The remaining high-priority gap is internal runtime path selection:

```text
public Backend.GPU + public EngineConfig/ConversationConfig/SamplerConfig
may not be equivalent to Edge Gallery's actual GPU executor/backend/runtime
selection.
```

## Do Not Implement Yet

Do not implement the following from this document without a separate explicit
task:

- GPU_ARTISAN reflection or hidden API routing
- RuntimeConfig reflection hacks
- native library replacement in standardDebug
- callback text repair to mask raw source corruption
- production GPU enablement

## Device-Side Confirmation Needed Later

Use `edge_gallery_executor_probe` and copy compact/details output:

```bash
adb shell setprop debug.lami.gpu_generate_probe_mode normal
adb shell setprop debug.lami.gpu_normal_route_use_callback_streaming true
adb shell setprop debug.lami.gpu_probe_use_held_engine false
adb shell setprop debug.lami.gpu_prefill_probe false
adb shell setprop debug.lami.gpu_output_quality_matrix_mode edge_gallery_executor_probe
adb shell setprop debug.lami.gpu_output_quality_max_tokens 512
adb shell monkey -p io.github.ninbyo02.lami.gpustandardminimal 1
```

Prompt:

```text
カレーの材料をお願いします。
```

Then compare fingerprints:

```bash
scripts/compare_runtime_fingerprints.sh --baseline <known-good-or-prior-diag.txt> --probe <executor-probe-diag.txt>
```
