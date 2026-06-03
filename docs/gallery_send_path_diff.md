# Gallery Send Path Diff

## Scope

This compares the actual LiteRT-LM send path in `LiteRtLmGpuBenchmarkReceiver` with Google AI Edge Gallery normal chat. It intentionally excludes QAIRT, QNN, HTP, NPU loading, and production chat-screen changes.

## Exact Send Differences

| Area | Receiver | Gallery normal chat | Root-cause relevance |
| --- | --- | --- | --- |
| First send API | `collectStreamingResponse(...)` calls `conversation.sendMessageAsync(prompt).collect` at `LiteRtLmGpuBenchmarkReceiver.kt:620-623` and `:748-762`. | `runInference(...)` calls `conversation.sendMessageAsync(Contents.of(contents), MessageCallback, extraContext ?: emptyMap())` at `LlmChatModelHelper.kt:314-336`. | Strongest send-path delta. Receiver uses raw `String` Flow; Gallery uses typed `Contents` callback. |
| Blocking path | On any streaming exception, receiver falls back to `conversation.sendMessage(prompt)` at `LiteRtLmGpuBenchmarkReceiver.kt:624-632`. | Gallery normal chat does not use blocking fallback; errors go to `MessageCallback.onError` at `LlmChatModelHelper.kt:325-332`. | Receiver may obscure the first failing path by reporting blocking failure with async failure suppressed. |
| Prompt/content construction | Receiver passes the prompt string directly at `LiteRtLmGpuBenchmarkReceiver.kt:621` and `:627`. | Gallery builds a `MutableList<Content>`, adds image/audio first, then adds `Content.Text(input)` at `LlmChatModelHelper.kt:302-312`, and wraps it with `Contents.of(contents)` at `:314-315`. | Important for Gemma 4 multimodal processors. Text-only chat is still typed content in Gallery. |
| Callback vs Flow lifecycle | Receiver collects Kotlin Flow under `runBlocking` at `LiteRtLmGpuBenchmarkReceiver.kt:754-762`. | Gallery provides `MessageCallback.onMessage`, `onDone`, and `onError` at `LlmChatModelHelper.kt:316-334`. | Different Java wrapper around the same native send can change error timing and lifecycle. |
| Output extraction | Receiver prefers `message.contents.toString()` then `message.toString()` at `LiteRtLmGpuBenchmarkReceiver.kt:758-760`. | Gallery passes `message.toString()` to UI at `LlmChatModelHelper.kt:317-318`. | Not likely to cause native failure, but affects blank-output and token accounting conclusions. |
| Conversation state | Receiver creates and closes a fresh Engine/Conversation per case at `LiteRtLmGpuBenchmarkReceiver.kt:476-558` and `:926-958`. | Gallery holds `LlmModelInstance(engine, conversation)` at `LlmChatModelHelper.kt:177` and resets conversation explicitly at `:185-236`. | Medium. It matters for cleanup SIGSEGV and repeated-case diagnostics, less for first-send Status 13. |
| Prompt templates | Receiver sends benchmark prompt strings. | Gallery does not add explicit app-side role tags before send; chat templates are plain prompt text selected by the UI. | Low evidence for app-side prompt-template root cause. Native conversation rendering still applies model prompt templates. |

## Does Gallery Use A Different API Path?

Yes for normal LiteRT-LM chat:

- Gallery uses LiteRT-LM `Engine`, `Conversation`, `ConversationConfig`, `Content`, `Contents`, and `MessageCallback`.
- It does not route normal LiteRT-LM chat through MediaPipe `LlmInferenceSession`.
- It does not use the receiver's raw `String` Flow path for normal chat.

The most relevant Gallery code is:

```text
contents.add(Content.Text(input))
conversation.sendMessageAsync(
  Contents.of(contents),
  object : MessageCallback { ... },
  extraContext ?: emptyMap(),
)
```

from `LlmChatModelHelper.kt:302-336`.

## Send-Path Root-Cause Ranking

1. **Raw `String` send path vs `Contents.of(Content.Text(...))` callback path.** Highest send-path evidence. This applies directly to the observed boundary: only send fails.
2. **No explicit `ConversationConfig` on the receiver conversation.** Gallery send occurs on a conversation constructed with sampler/system/tool config; receiver send occurs on no-arg conversation defaults.
3. **Async Flow plus blocking fallback obscures original failure.** The report may show blocking `nativeSendMessage` while the first failure was from Flow `sendMessageAsync`.
4. **Typed multimodal content mismatch.** Even text-only Gallery sends typed `Content.Text`; receiver relies on `String` overload wrapper behavior.
5. **Response extraction mismatch.** Relevant for result classification, not for native Status 13.
6. **Prompt formatting/template mismatch.** Low evidence as an app-side issue; no Gallery code path adds manual chat role tags before LiteRT-LM `Conversation`.

## Send-Path Conclusion

The benchmark receiver is not exercising the same send path as Gallery. The minimal send-path discriminator is:

```text
Use the same conversation config as Gallery and call
sendMessageAsync(Contents.of(listOf(Content.Text(prompt))), MessageCallback, emptyMap()).
```

If that succeeds while raw `String` Flow fails, the root cause is receiver send API parity. If it still fails with the same line-735 Status 13, focus shifts from Java send overload to first-prefill input shape/capacity.
