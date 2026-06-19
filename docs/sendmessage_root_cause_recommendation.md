# SendMessage Root Cause Recommendation

## Current Root-Cause Ranking

1. **Receiver is not Gallery-chat parity at conversation/send time. Confidence: 75%.**  
   The receiver creates a no-arg conversation and sends raw `String` prompts through Flow first, then blocking fallback. Gallery normal chat creates `ConversationConfig(SamplerConfig(...))` and sends `Contents.of(Content.Text(...))` through `MessageCallback`.

2. **First-prefill invoke input shape/capacity mismatch. Confidence: 70%.**  
   LiteRT-LM source maps the cited `llm_litert_compiled_model_executor.cc:735` line to `compiled_model_->RunAsync(prefill_signature, input_buffers, output_buffers, async)`. Engine and conversation creation are already complete, so the failure is at first prefill invocation, not load/initialize.

3. **`maxNumTokens` is being used as engine context capacity, not output-token limit. Confidence: 60%.**  
   Receiver variants pass `32/64/128/256` into `EngineConfig.maxNumTokens`; Gallery Gemma 4 E2B config uses `4000`. This is high risk for most receiver variants, but it is not sufficient alone if `default`/null max-token variants fail the same way.

4. **CacheDir and modality config deltas contribute but are unlikely as sole root cause. Confidence: 45%.**  
   Receiver always sets `cacheDir` and often enables vision/audio backends. Gallery normal text chat uses `cacheDir=null`, `visionBackend=null`, `audioBackend=null`. However null-modality variants were already tested and still failed.

5. **Cleanup SIGSEGV is secondary. Confidence: 80%.**  
   The crash stack is in `AudioLiteRtCompiledModelExecutor::Reset()` during `Conversation.close()` / `nativeDeleteConversation()`. Because `engine-only` and `conversation-only` succeed and `skip-*` policies still show the send failure, this should remain a cleanup/lifecycle symptom unless a fresh marker proves it precedes the send exception.

## Evidence

- `LiteRtLmGpuBenchmarkReceiver.kt:476-483`: receiver builds `EngineConfig` directly.
- `LiteRtLmGpuBenchmarkReceiver.kt:558`: receiver uses no-arg `engine.createConversation()`.
- `LiteRtLmGpuBenchmarkReceiver.kt:620-632`: receiver first collects `sendMessageAsync(prompt)`, then falls back to `sendMessage(prompt)`.
- `LiteRtLmGpuBenchmarkReceiver.kt:855-864`: receiver always supplies `appContext.cacheDir.absolutePath`.
- `LiteRtLmGpuBenchmarkReceiver.kt:877-920`: receiver variants differ from Gallery on modality backends and `maxNumTokens`.
- `LlmChatModelHelper.kt:112-123`: Gallery creates `EngineConfig` with model-derived `maxNumTokens`, conditional modality backends, and usually `cacheDir=null`.
- `LlmChatTaskModule.kt:86-95`: Gallery normal `llm_chat` initializes with `supportImage=false`, `supportAudio=false`.
- `LlmChatModelHelper.kt:159-175`: Gallery creates `ConversationConfig` with `SamplerConfig` for non-NPU.
- `LlmChatModelHelper.kt:302-336`: Gallery sends `Contents.of(contents)` via `MessageCallback`.
- `model_allowlists/1_0_12.json:4-20`: Gallery Gemma 4 E2B metadata matches the observed model size and uses `maxTokens=4000`, topK 64, topP 0.95, temperature 1.0.
- `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor.cc:733-735`: Status 13 boundary is prefill `compiled_model_->RunAsync(...)`.

## Exact Code Differences To Test

Current receiver shape:

```kotlin
EngineConfig(
  modelPath = modelPath,
  backend = Backend.GPU(),
  visionBackend = variantDependent,
  audioBackend = variantDependent,
  maxNumTokens = maxOutputTokensOrNull,
  cacheDir = appContext.cacheDir.absolutePath,
)

val conversation = engine.createConversation()
conversation.sendMessageAsync(prompt).collect { ... }
conversation.sendMessage(prompt) // fallback
```

Gallery-parity diagnostic shape:

```kotlin
EngineConfig(
  modelPath = modelPath,
  backend = Backend.GPU(),
  visionBackend = null,
  audioBackend = null,
  maxNumTokens = 4000,
  cacheDir = null,
)

val conversation = engine.createConversation(
  ConversationConfig(
    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
  )
)

conversation.sendMessageAsync(
  Contents.of(listOf(Content.Text(prompt))),
  callback,
  emptyMap(),
)
```

## Minimal Next Experiment

Add one debug-only receiver variant, without touching production code:

```text
backend_variant=gallery-chat-parity
phase=send-message
close_policy=skip-all
prompt=こんにちは
maxNumTokens=4000
backend=GPU
visionBackend=null
audioBackend=null
cacheDir=null
conversation=ConversationConfig(SamplerConfig(64,0.95,1.0))
send=Contents.of(Content.Text(prompt)) + MessageCallback + emptyMap
```

Decision rules:

| Observation | Conclusion |
| --- | --- |
| Gallery-parity variant succeeds | Root cause is receiver-specific API/config parity, not model/runtime load. Then bisect sampler, `Contents` send, cacheDir, and maxNumTokens one at a time. |
| Gallery-parity variant fails with same line 735 | Root cause moves below Java parity into first-prefill compiled-model input shape/capacity. Add native logs for rendered token count, selected prefill signature, tensor names/layouts/packed sizes. |
| Flow fails but callback succeeds | Keep callback path for benchmark diagnostics; Flow wrapper is not Gallery parity. |
| Callback returns error but no cleanup crash under `skip-all` | Treat cleanup SIGSEGV separately from send failure. |

## Recommendation

Do not investigate QAIRT/QNN/NPU loading for this failure. The next useful work is a debug-only Gallery-chat-parity receiver variant and a single-case run. Until that experiment is run, the root cause should be stated as:

```text
LiteRtLmGpuBenchmarkReceiver reaches native send with a non-Gallery chat configuration and send overload.
The native failure occurs at first prefill compiled-model invocation, most likely from the resulting
input shape/capacity/session configuration mismatch.
```

Overall confidence: **72%**.
