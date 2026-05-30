# QAIRT244 Phase S4-A Pseudo Streaming UI Connection Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, change native code, connect real token streaming, connect
TTS, or persist `Backend.NPU`.

## Baseline

The NPU standard route currently reaches ChatScreen through the gated S1 path:

```text
InferenceTarget.LOCAL
-> image input rejection
-> requestPrompt blank check
-> S1 gate
-> NpuStandardRouteS1Bridge().run()
-> transient NPU STANDARD ROUTE S1 display
-> optional S2 DB save
-> optional S3 Markdown final text
-> return before the normal local route
```

S2 and S3 are still gated off by default:

```text
ENABLE_NPU_STANDARD_ROUTE_S2_DB=false
ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN=false
```

Existing local/Ollama streaming uses `localStreamingResponseText`,
`streamingResponseTextForRender`, `streamingAssistantMessageId`,
`upsertStreamingAssistantPlaceholder(...)`, and
`finalizeStreamingAssistantMessage(...)`. Those helpers are tied to placeholder
rows, final DB persistence, streaming sentence playback, stop-button ownership,
and Markdown finalization for the normal local route.

S4-A must not use the normal local/Ollama streaming route as real token
streaming.

## S4-A Gate Position

Recommended minimal gate:

```text
inside existing S1 gate
  after s1Result is available
  after S2 saveCandidate is available when S2 is enabled
  after S3 finalized text is resolved when S3 is enabled
  before user/assistant DB insert
```

The first S4-A UI connection should be evaluated only when:

- `NpuStandardRouteS1GateConfig.enabled=true`;
- `s1Result.successCriteriaMet=true`;
- the final display text is non-empty;
- failure flags are absent through the S1 success criteria;
- S2, if enabled, has a valid `saveCandidate`;
- S3, if enabled, has produced or fallen back to a final text.

Introduce a separate gate:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING = false
```

Gate off must preserve the current S3/S2/S1 behavior exactly.

## UI State Target

Do not route S4-A through the existing streaming placeholder path initially.

Recommended state:

```kotlin
var npuStandardRouteS4PseudoStreamingText by remember(effectiveChatId) {
    mutableStateOf<String?>(null)
}
var npuStandardRouteS4PseudoStreamingActive by remember(effectiveChatId) {
    mutableStateOf(false)
}
```

Use cumulative chunks from `NpuStandardRouteS4PseudoStreamingBridge`:

```text
chunk[0] = first visible prefix
chunk[1] = larger prefix
...
chunk[last] = final full text
```

The rendered surface should stay NPU-specific, for example below or near the
existing `NPU STANDARD ROUTE S1` debug block:

```text
NPU STANDARD ROUTE S4-A PSEUDO STREAMING
<current cumulative chunk>
```

This keeps pseudo streaming separate from:

- `localStreamingResponseText`;
- `remoteStreamingResponseText`;
- `streamingResponseTextForRender`;
- `streamingAssistantMessageId`;
- `upsertStreamingAssistantPlaceholder(...)`;
- `finalizeStreamingAssistantMessage(...)`.

## DB Persistence

DB persistence remains final-only.

S4-A chunks must not be inserted or updated as DB rows. If S2 is enabled, keep
the existing final persistence policy:

```text
user row: request prompt
assistant row: final full assistant text
```

The assistant row body should be the same final text that S4-A reaches in its
last chunk. The final chunk must equal:

```text
pseudoStreamingCandidate.finalText
```

and, when S2 persists:

```text
assistant Message.message
```

No partial chunk should appear in `Message.message`.

## Markdown Policy

Markdown remains final-only.

S4-A should chunk the already-finalized text:

```text
S1 sanitized/display text
-> S3 finalizedText when S3 gate is on
-> S4-A chunking
```

Do not run Markdown repair per chunk. Partial Markdown repair can corrupt code
fences, lists, and headings because chunks are incomplete by design.

When S3 is off, S4-A should use the same fallback text that S2 would persist:

```text
saveCandidate.assistantMessage.text
```

When S3 is on, S4-A should use:

```text
markdownCandidate.finalizedText
```

## Failure Policy

Failure must not pseudo stream.

Do not create or display pseudo chunks when:

- `s1Result.successCriteriaMet=false`;
- `NpuStandardRouteS4PseudoStreamingBridge` returns no candidate;
- final text is blank;
- S2 is enabled but `saveCandidate=null`;
- fallback, timeout, or fresh crash prevents S1 success.

Failure UI remains the existing transient S1 diagnostics. Failure must not:

- insert DB rows;
- update streaming placeholder rows;
- start TTS;
- run `LocalStreamingRunner`;
- persist `Backend.NPU`.

## Ordering

Recommended S4-A gate-on ordering:

```text
S1 success
-> compute S2 saveCandidate when S2 gate is on
-> resolve S3 final text when S3 gate is on
-> S4-A Bridge prepares pseudoStreamingCandidate
-> display cumulative chunks in NPU-specific transient state
-> after final chunk, persist S2 DB rows if S2 gate is on
-> return before the normal local route
```

For the first implementation, keep the staged display and final DB save in the
same coroutine launched from the S1 gate. Use short deterministic delays only if
needed for manual UI confirmation.

If S2 is off, S4-A remains display-only and returns after the final staged text.

## Existing Local/Ollama Streaming Isolation

S4-A must not affect:

- `InferenceTarget.SERVER`;
- normal `InferenceTarget.LOCAL` when S1 gate is off;
- custom/dev hidden QAIRT route outside the S1 gate;
- `LocalStreamingRunner`;
- `runWithHeldEngine(...)`;
- local/remote streaming partial callbacks;
- streaming placeholder insert/update/finalize helpers;
- streaming sentence playback buffers;
- stop-button owner state;
- TTS controllers.

Rollback triggers:

- S4-A writes partial chunks to DB;
- a streaming placeholder row appears for NPU S4-A;
- `localStreamingResponseText` is used by S4-A;
- final DB text differs from the final chunk;
- pseudo streaming starts for a failure result;
- normal local/Ollama streaming behavior changes with the S4-A gate off;
- TTS starts from pseudo chunks.

## Rollback

Rollback is gate-only:

```text
ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=false
```

Fallback sequence:

- S4-A gate off: return to current S3/S2/S1 behavior.
- S3 gate off: use S2 sanitized text.
- S2 gate off: return to S1 display-only.
- S1 gate off: return to normal local/Ollama route.

No DB migration is required because S4-A should persist only the same final
assistant row as S3/S2.

## Test Items

Pure/unit tests:

- S4-A gate off returns the S3/S2 final text without chunk display.
- S4-A gate on uses `NpuStandardRouteS4PseudoStreamingBridge`.
- failure S1 result produces no pseudo streaming candidate.
- empty final text produces no pseudo streaming candidate.
- chunks are cumulative.
- final chunk equals final text.
- `realTokenStreaming=false`.
- `tts=false`.
- `backendNpuPersisted=false`.

ChatScreen-level tests:

- gate off preserves current S2/S3 behavior.
- gate on writes only NPU-specific transient display state.
- gate on does not touch `localStreamingResponseText`.
- gate on does not set `streamingAssistantMessageId`.
- S2 enabled plus S4-A enabled inserts exactly one user row and one assistant row
  after final chunk.
- assistant DB body equals the final chunk.
- failure path writes no DB rows and no pseudo chunks.
- existing local/Ollama route is unchanged when S1 or S4-A gate is off.

Manual runtime checklist for later:

- use `customBuildExperimentDebug` only;
- temporarily enable S4-A gate;
- send a prompt through Local;
- confirm `NPU STANDARD ROUTE S4-A PSEUDO STREAMING` appears;
- confirm final text remains `こんにちは。` or the selected final text;
- if S2 is enabled, confirm DB updates only after final display;
- confirm no TTS and no streaming placeholder diagnostics;
- roll back S4-A gate to false and reinstall.
