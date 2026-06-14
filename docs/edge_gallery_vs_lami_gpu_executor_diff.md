# Edge Gallery vs Lami GPU Executor Difference

Scope: investigation notes for runtime/executor/callback differences. No
production route, CPU route, NPU route, runtime stack, or callback joining change
is implied by this document.

## Observed Behavior Split

| Route | Long Japanese generation | Raw callback quality | Notes |
|---|---|---|---|
| Edge Gallery official GPU | OK | No observed corruption | Same device and same model family. |
| Lami CPU | OK | OK | Both generic and Edge Gallery E2B model variants are usable on CPU. |
| Lami `standardDebug` GPU | Fails before quality can be assessed | `cc:735` invoke failure | Runtime stack is not aligned enough to invoke GPU compiled model. |
| Lami `standardGpuMinimalRuntimeCandidateDebug` GPU | Fails quality gate on long output | corrupt at `raw_callback` | Minimal pair invokes GPU, but decode/callback stream fragments semantically. |

## Edge Gallery Static Executor Evidence

The Edge Gallery APK native strings contain the following relevant evidence:

| Evidence | Location / class of artifact | Meaning |
|---|---|---|
| `GPU_ARTISAN`, `CPU_ARTISAN`, `GOOGLE_TENSOR_ARTISAN` | `liblitertlm_jni.so`, dex strings | LiteRT-LM runtime supports more backend/executor labels than Lami public reflection exposes. |
| `Artisan model detected. Switching backend from GPU to GPU_ARTISAN.` | `liblitertlm_jni.so` | Runtime can switch executor selection based on model/runtime detection. |
| `LlmGpuArtisanExecutor`, `LlmGpuArtisanExecutor::Create`, `LlmGpuArtisanExecutor::Prefill` | `liblitertlm_jni.so` | Native GPU artisan executor exists. |
| `backend constraint is matched`, `backend constraint mismatch. Model requires one of [` | `liblitertlm_jni.so` | Model/runtime backend constraints participate in executor selection. |
| `No preferred engine types defined`, `preferred engine types` | `liblitertlm_jni.so` | Preferred engine/runtime config exists internally. |
| `tflite_gpu_kv_cache`, `tflite_opencl_kv_cache` | `liblitertlm_jni.so` | GPU KV-cache/decode path may vary by executor/runtime selection. |
| `GPU sampler unavailable. Falling back to CPU sampling.` | `liblitertlm_jni.so` | Runtime has sampler fallback behavior, but Lami no-sampling/no-acceleration experiments did not fix corruption. |

The Lami minimal runtime pair also contains many of these native strings,
including `GPU_ARTISAN`, `LlmGpuArtisanExecutor`, backend constraints, callback
utility references, tokenizer/BPE messages, and decode/prefill timing strings.
Therefore the decisive question is not whether the symbols/strings are linked;
it is whether the Edge Gallery app and Lami public route select the same internal
executor/configuration at runtime.

Static evidence does not prove the Edge Gallery run selected `GPU_ARTISAN`. It
does prove the working app ships a runtime capable of internal executor and
backend selection not visible through Lami's current public reflection.

## Lami Runtime Access Pattern

| Area | Lami current evidence |
|---|---|
| Public backend reflection | `CPU,GPU,NPU` only. No public `GPU_ARTISAN`, `CPU_ARTISAN`, or `GOOGLE_TENSOR_ARTISAN` API observed. |
| GPU route selection | Uses public `Backend.GPU` route with callback streaming diagnostics. |
| Runtime stack variants | `standardDebug` fails GPU invoke; minimal pair invokes GPU but corrupts long raw callbacks. |
| Callback stage | `callback_corruption_earliest_stage=raw_callback`; `gpu_output_source_corruption_stage=raw_callback`. |
| UI/join experiments | `collect_only`, no sampling acceleration, and baseline all fail quality; `gpu_output_ui_append_changed_text=false` has been observed. |

## Public JNI Surface

Edge Gallery and Lami minimal pair both expose the same visible JNI generate
surface:

- `nativeGenerateContent`
- `nativeGenerateContentStream`
- `nativeRunPrefill`
- `nativeRunDecode`
- `nativeSendMessage`
- `nativeSendMessageAsync`

The raw callback corruption is therefore unlikely to be explained by a missing
public JNI entry point. The investigation should focus on app-level API choice
(`GenerateContent` vs stream/send-message), hidden runtime configuration,
executor selection, and callback text semantics.

## Generate / Callback Semantics Candidates

| Candidate | Evidence | Current status |
|---|---|---|
| Delta chunks | Lami receives many small callbacks and appends them. Short prompts can pass. | Plausible but insufficient: corrupted tokens already appear in raw callback artifacts. |
| Accumulated callback text | If callback text were accumulated full text, appending all chunks would corrupt output. | Weaker after raw artifact analysis, but final-response/last-non-empty probes remain useful to verify. |
| Final-only response | Edge Gallery may use a final response or internal adapter before UI display. | Unproven. If Edge Gallery buffers internally, Lami raw callback artifacts should still be compared with final candidate text. |
| Hidden Edge Gallery aggregation layer | Edge Gallery UI displays normal output while Lami raw callback corrupts. | Possible, but cannot explain semantic corruption already visible in Lami raw callback unless Edge Gallery uses a different native callback source/executor. |

## Executor Path Hypothesis

The strongest runtime-level difference is not simple UI append behavior. It is
that Edge Gallery likely reaches a different native executor/configuration path
than Lami's public `Backend.GPU` path.

Possible concrete differences:

1. Edge Gallery runtime selects `GPU_ARTISAN` or another internal executor while
   Lami selects `LlmLiteRtCompiledModelExecutor` through public `Backend.GPU`.
2. Edge Gallery applies hidden `RuntimeConfig`, preferred engine type, or backend
   constraint handling before GPU generation.
3. Edge Gallery uses a GPU KV-cache/decode path that is not selected by Lami's
   public route.
4. Edge Gallery callback stream may be post-processed by a native/runtime adapter
   before app code observes text.

## Model Metadata Position

Edge Gallery allowlist identifies:

- model id: `litert-community/gemma-4-E2B-it-litert-lm`
- file: `gemma-4-E2B-it.litertlm`
- size: `2588147712`
- commit: `6e5c4f1e395deb959c494953478fa5cec4b8008f`
- accelerators: `gpu,cpu`
- config: `topK=64`, `topP=0.95`, `temperature=1.0`, `maxTokens=4000`

Because Lami CPU succeeds with the Edge Gallery model and Lami GPU short output
can succeed, model identity alone is not the root cause. Model metadata may
still influence executor selection, especially if Edge Gallery runtime honors
backend constraints or preferred engine types that Lami public API cannot reach.

## Current Comparison Summary

| Question | Current answer |
|---|---|
| Is Lami corruption caused by Markdown/UI append? | Unlikely. Raw callback artifacts already contain corrupted text. |
| Is sampler acceleration alone the root cause? | Unlikely. Baseline, collect-only, and no-sampling-acceleration matrix results all fail. |
| Is max token budget alone the root cause? | Unlikely. Shorter max-token probes still show quality issues on longer prompts. |
| Is model identity the root cause? | Unlikely. Edge Gallery model works in Edge Gallery GPU and Lami CPU. |
| Is runtime/executor selection the leading cause? | Yes. Edge Gallery runtime has internal executor evidence not reachable from Lami public reflection, and Lami minimal pair still differs from Edge Gallery. |
