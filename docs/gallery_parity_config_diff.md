# Gallery Parity Config Diff

## Scope

This compares only the debug benchmark receiver configuration against Google AI Edge Gallery normal `llm_chat` configuration. It intentionally excludes QAIRT, QNN, HTP, NPU loading, fallback routing, and production `app/src/main` changes.

Local references:

- Lami receiver: `app/src/debug/java/io/github/ninbyo02/lami/gpu/LiteRtLmGpuBenchmarkReceiver.kt`
- Gallery chat: `/home/sato/project/google-ai-edge-gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt`
- Gallery task module: `/home/sato/project/google-ai-edge-gallery/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatTaskModule.kt`
- Gallery Gemma 4 E2B allowlist: `/home/sato/project/google-ai-edge-gallery/model_allowlists/1_0_12.json`

## Exact Config Differences

| Area | Receiver | Gallery normal chat | Root-cause relevance |
| --- | --- | --- | --- |
| Engine construction | Direct `EngineConfig(...)` at `LiteRtLmGpuBenchmarkReceiver.kt:476-483`. | Direct `EngineConfig(...)` at `LlmChatModelHelper.kt:112-123`. | Same class/API, so failure is not explained by a different engine type. |
| Main backend | Variant-dependent; GPU/default/null-modality variants use `Backend.GPU()` at `LiteRtLmGpuBenchmarkReceiver.kt:877-920`. | Reads model accelerator, defaulting to GPU at `LlmChatModelHelper.kt:80-108`. | Mostly aligned for GPU tests. |
| Vision/audio backends | Most variants set modality backends even for text: GPU variant uses `visionBackend=GPU`, `audioBackend=CPU` at `LiteRtLmGpuBenchmarkReceiver.kt:912-920`; `gpu-null-modalities` uses null/null at `:877-885`. | `llm_chat` passes `supportImage=false`, `supportAudio=false` at `LlmChatTaskModule.kt:86-95`, so `visionBackend=null`, `audioBackend=null` at `LlmChatModelHelper.kt:116-117`. | Not sole cause because `gpu-null-modalities` was tested, but non-null modality variants are not Gallery-chat parity. |
| `maxNumTokens` | Most variants pass benchmark `maxOutputTokens` into engine `maxNumTokens`, for example `:873`, `:884`, `:895`, `:920`. Defaults are `32,64,128,256` at `LiteRtLmGpuBenchmarkReceiver.kt:1079`. `default`, `gpu-null-max`, and `gpu-all` pass null at `:899-909`. | Reads model max tokens at `LlmChatModelHelper.kt:74-75`; Gemma 4 E2B allowlist sets `maxTokens=4000` at `1_0_12.json:14-20`. | High risk for small-token variants. Not sole cause if null-token variants fail the same way. |
| `cacheDir` | Always set to `appContext.cacheDir.absolutePath` for all variants at `LiteRtLmGpuBenchmarkReceiver.kt:855-864`. | Null for normal app-private model paths; non-null only for `/data/local/tmp` at `LlmChatModelHelper.kt:119-122`. | Applies to all tested receiver variants and remains a plausible parity delta. |
| Conversation creation | No-arg `engine.createConversation()` at `LiteRtLmGpuBenchmarkReceiver.kt:558`. | `engine.createConversation(ConversationConfig(...))` at `LlmChatModelHelper.kt:159-175`. | Strongest config delta. Engine/conversation creation can succeed while send uses different native conversation/session config. |
| Sampler config | None in receiver. | Non-NPU gets `SamplerConfig(topK, topP, temperature)` at `LlmChatModelHelper.kt:162-170`; Gemma allowlist defaults are topK 64, topP 0.95, temperature 1.0 at `1_0_12.json:14-19`. | Strong Gallery difference. More likely to affect decode, but it also proves no-arg conversation is not Gallery parity. |
| System/tools/initial state | No explicit system instruction, tools, or initial messages. | `ConversationConfig` carries `systemInstruction` and `tools` at `LlmChatModelHelper.kt:172-173`; reset can include `initialMessages` at `:230-233`. | Medium. Empty system/tools can be valid, but the constructor path differs. |
| Model/options source | Receiver resolves model by intent/settings/first local `.litertlm` at `LiteRtLmGpuBenchmarkReceiver.kt:783-801`. | Gallery uses a `Model` object and per-model config values at `LlmChatModelHelper.kt:63-81`. | Receiver has the file but not the Gallery model metadata surface. |

## Model Match

The current benchmark model size is about `2,583,085,056` bytes. Gallery allowlist `1_0_12.json` lists Gemma 4 E2B as:

```text
modelFile=gemma-4-E2B-it.litertlm
sizeInBytes=2583085056
maxTokens=4000
accelerators=gpu,cpu
llmSupportImage=true
llmSupportAudio=true
```

So the file size aligns with Gallery's Gemma 4 E2B metadata, but the receiver does not apply the same chat-task runtime options.

## Config Root-Cause Ranking

1. **Missing Gallery `ConversationConfig` / `SamplerConfig` path.** Highest config-specific evidence. Gallery normal chat does not use no-arg conversation creation.
2. **Engine token budget mismatch.** High risk for variants that pass `32/64/128/256` as `maxNumTokens`; Gallery uses `4000` for this model. Not sufficient alone because null-token variants were also tested.
3. **Cache directory parity mismatch.** Receiver always uses app cache; Gallery normal app-private model path uses `cacheDir=null`.
4. **Text-only modality mismatch.** Receiver default GPU enables modality backends; Gallery text chat disables them. Not sufficient alone because null-modality variant was tested.
5. **Model metadata not carried into receiver.** The file is present, but Gallery chat behavior depends on model config values beyond the path.

## Config Conclusion

The receiver proves Engine and Conversation can be created, but it does not prove Gallery-equivalent send readiness. The most defensible config conclusion is:

```text
LiteRtLmGpuBenchmarkReceiver is not Gallery-chat parity at conversation/session configuration time.
The first validation run should use null modality backends, cacheDir=null, maxNumTokens=4000,
and createConversation(ConversationConfig(SamplerConfig(64, 0.95, 1.0))).
```
