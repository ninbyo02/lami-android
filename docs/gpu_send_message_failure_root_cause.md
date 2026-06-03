# LiteRT-LM GPU SendMessage Failure Root Cause

## Scope

This note covers the debug-only LiteRT-LM GPU benchmark receiver route and the current device logs.

Out of scope:

- production `app/src/main`
- `ChatScreen`
- S1-S5 standard NPU route code
- `Backend.NPU`
- QAIRT/QNN staging
- fallback behavior

## Current Boundary

The important boundary has moved past earlier Engine initialization failures.

Observed:

| phase | result | read |
| --- | --- | --- |
| `engine-only` | success | `Engine(config)` and `engine.initialize()` can complete for the selected model/config. |
| `conversation-only` | success | `engine.createConversation()` can complete. |
| `send-message` | failure | The first generation call reaches `nativeSendMessage` and fails inside compiled model invocation. |

The selected model is present:

```text
model_path=/data/user/0/io.github.ninbyo02.lami/files/local_models/1780356866149_gemma-4-E2B-it.litertlm
model_exists=true
model_length=2583085056
```

The common send failure is:

```text
Failed to call nativeSendMessage: INTERNAL: ERROR:
[runtime/executor/llm_litert_compiled_model_executor.cc:735]
Failed to invoke the compiled model
```

This makes model existence, Engine construction, and Conversation construction unlikely as the primary blocker.

## Crash Signature Read

The native crash signature seen in collected artifacts is:

```text
SIGSEGV SEGV_MAPERR fault addr 0x0
AudioLiteRtCompiledModelExecutor::Reset()
SessionBasic::~SessionBasic()
Conversation::~Conversation()
Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeDeleteConversation()
```

Treat this as a cleanup/lifecycle crash signature unless a fresh run proves it occurs in the same send-message process after `nativeSendMessage` failure. Recent `conversation-only --close-policy skip-all` runs reached `report_written`, and some crash collectors can pick stale tombstones. The send-message blocker itself is the `nativeSendMessage` compiled-model invoke failure.

## Priority Root-Cause Candidates

| priority | candidate | evidence | next discriminator |
| --- | --- | --- | --- |
| P0 | Text send path is not Gallery-aligned for Gemma 4 E2B | Gallery sends `Contents.of(listOf(Content.Text(input)))` with callback API and a `ConversationConfig` sampler. The receiver sends `sendMessageAsync(prompt)` first, then falls back to `sendMessage(prompt)`. Both wrap text, but they exercise different overloads and Flow/callback behavior. | Add a Gallery-parity send variant using `ConversationConfig(SamplerConfig(...))` and `sendMessageAsync(Contents.of(Content.Text(prompt)), callback, emptyMap())`. |
| P1 | EngineConfig modality backends still differ from Gallery chat | Gallery `llm_chat` initializes with `supportImage=false` and `supportAudio=false`, so `visionBackend=null` and `audioBackend=null`. Some receiver runs use `visionBackend=GPU` and `audioBackend=CPU`; `gpu-null-modalities` is closer. | Keep first probe on `gpu-null-modalities` or a dedicated `gallery-chat-parity` variant. |
| P2 | `maxNumTokens` differs from Gallery Gemma 4 default | Gallery Gemma 4 E2B default `maxTokens` is `4000`, while benchmark runs often use `32/64/128` or `null`. For LiteRT-LM this is KV-cache size, not output-token count. Too-small values can make prefill+decode impossible. | Run a single-prompt parity case with `maxNumTokens=4000` and compare to `null`. Do not use small values as EngineConfig token limits for root-cause probes. |
| P3 | Sampler config is missing in the receiver | Gallery creates `ConversationConfig(samplerConfig=SamplerConfig(topK=64, topP=0.95, temperature=1.0))` for GPU/CPU. Receiver no-arg `createConversation()` leaves sampler defaults to native/model defaults. | Add a sampler-config variant before changing prompt text or model. |
| P4 | Cache-dir behavior differs | Gallery chat passes `cacheDir=null` for normal app-private model paths; LiteRT-LM then uses the model directory. Receiver passes `context.cacheDir.absolutePath`. Gallery benchmark uses a per-run cache dir, but Gallery chat does not. | Add `cacheDir=null` in the Gallery-parity variant. |
| P5 | Cleanup after failed send hits an audio executor null-reset bug | Backtrace points to `AudioLiteRtCompiledModelExecutor::Reset()` during `Conversation.close()`. Gemma 4 is multimodal; a text-only conversation may still own an audio executor object in a partial state. | Use `skip-all` to isolate send failure from close failure; separately run close-only after successful conversation creation with fresh tombstone filtering. |
| P6 | Model/version mismatch with Gallery allowlist | The model length `2583085056` matches Gallery allowlist `1_0_12` Gemma 4 E2B, while newer allowlist `1_0_15` has a larger original and an updatable file. Native stack is `litertlm-android 0.11.0`. | Record allowlist version, model commit hash, and `liblitertlm_jni.so` Build ID in every probe report. |

## Current Conclusion

The most likely blocker is not NPU decode. The Standard Route S1-S4A successes prove the NPU route can decode with `fallback_used=false`.

For the GPU benchmark receiver, the leading hypothesis is:

```text
The receiver reaches nativeSendMessage with a Gemma 4 E2B configuration that is not Gallery-chat aligned.
The strongest config deltas are text-only modality backends, maxNumTokens, ConversationConfig sampler,
cacheDir, and send overload/callback shape.
```

The cleanup SIGSEGV is important, but it should not be treated as the primary send failure unless a fresh timestamp proves the same run dies natively after the send exception.
