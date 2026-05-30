# QAIRT244 Phase S3 Markdown UI Connection Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, change native code, connect Markdown UI, connect
streaming/TTS, or persist `Backend.NPU`.

## Baseline

S1/S2 currently work as:

```text
ChatScreen S1 gate
-> NpuStandardRouteS1Bridge
-> S1 transient display
-> optional S2 DB candidate
-> user row
-> assistant row
```

The S2 assistant row currently persists:

```text
saveCandidate.assistantMessage.text
```

That text comes from `s1Result.sanitizedOutput`. The S1 diagnostic text is
stored separately as `localSourceSummary`.

Existing assistant UI already renders saved assistant rows through
`PlainAssistantMessage`. That component parses fenced code segments and inspects
Python code warnings for non-streaming messages. Therefore S3 does not need a
new assistant UI surface for the first integration.

## S3 Gate Position

Recommended minimal gate position:

```text
inside existing S1 gate
  after S2 saveCandidate is available
  before createAssistantMessage(...)
```

The S3 gate should be evaluated only after:

- S1 route is selected;
- `NpuStandardRouteS1Bridge().run()` has returned;
- `s1Result.successCriteriaMet=true`;
- `NpuStandardRouteS2DbBridge` has produced a valid save candidate.

This keeps S3 dependent on a successful S1/S2 candidate and prevents Markdown
processing for failure diagnostics.

## Finalized Text Target

`markdownCandidate.finalizedText` should replace only the assistant body that is
passed to `createAssistantMessage(...)`.

Recommended shape:

```kotlin
val s3MarkdownMapping = NpuStandardRouteS3MarkdownBridge().prepareMarkdownCandidate(
    s1Result = s1Result,
    finalizeMarkdown = { text ->
        buildFinalizedStreamingResponseForPersist(
            response = text,
            markdownStreamingMode = markdownStreamingMode,
            onMarkdownRepair = { ... },
        )
    },
)
val assistantTextForPersist = s3MarkdownMapping.markdownCandidate?.finalizedText
    ?: saveCandidate.assistantMessage.text
```

Then persist:

```kotlin
createAssistantMessage(
    chatId = resolvedChatId,
    response = assistantTextForPersist,
    localSourceSummary = saveCandidate.assistantMessage.sourceDisplayText,
)
```

S3 should not alter the user message.

## DB And UI Consistency

The DB saved body and UI rendered body should match.

Recommended policy:

- persist `markdownCandidate.finalizedText`;
- let normal chat rendering display `Message.message` through
  `PlainAssistantMessage`;
- do not create a separate transient Markdown-only display path;
- do not store diagnostic blocks in `Message.message`.

This ensures the same text is used for:

- DB row body;
- chat list/message body;
- copy-all action;
- future Markdown/code rendering.

For the current minimal Japanese success:

```text
sanitized_output=こんにちは。
finalizedText=こんにちは。
Message.message=こんにちは。
UI text=こんにちは。
repair_applied=false
```

## Code Fence And Repair Use

Use the existing final-text helper:

```kotlin
buildFinalizedStreamingResponseForPersist(...)
```

Do not use streaming partial helpers for S3.

Reasoning:

- S3 is still a non-streaming phase.
- `buildFinalizedStreamingResponseForPersist(...)` already handles final text.
- It can use `MarkdownCodeRepair.repair(...)` for default mode.
- It can use `processEdgeGalleryCompatibleMarkdown(...)` for
  `EDGE_GALLERY_COMPAT`.
- `PlainAssistantMessage` already renders fenced code segments from saved text.

S3 should record repair diagnostics only when a stable metadata surface is
chosen. The first UI connection can keep repair metadata out of the assistant
body.

## Failure Policy

Failure must not enter Markdown processing.

Do not call `NpuStandardRouteS3MarkdownBridge` when:

- S1 gate is not selected;
- `s1Result.successCriteriaMet=false`;
- `NpuStandardRouteS2DbBridge` returns no save candidate;
- `saveCandidate.readyToPersist=false`;
- assistant candidate text is empty.

Failure path remains:

```text
transient S1/S2 display only
no DB rows for failure
no Markdown finalization
```

This avoids converting diagnostic failure text into a normal assistant response.

## S2 DB Save Order

S3 should sit between S2 candidate creation and assistant insert:

```text
S1 success
-> S2 saveCandidate
-> S3 markdownCandidate from S1 result
-> create/resolve chat
-> insert user message
-> insert assistant message with finalizedText
```

The user row should still be inserted before the assistant row.

If S3 fails to produce a candidate but S2 has a valid candidate, the safe first
rollback behavior is to use the S2 sanitized assistant text unchanged. This
keeps S3 gate-off behavior equivalent to S2.

## Rollback

Rollback should be gate-only:

- S3 gate off: persist S2 sanitized text unchanged.
- S2 gate off: return to S1 display-only.
- S1 gate off: return to existing local/Ollama route.

No DB migration is required if S3 stores normal assistant text. Existing S3
processed rows can remain ordinary assistant rows.

Rollback triggers:

- `こんにちは。` changes unexpectedly;
- failure output is persisted;
- S1 diagnostic display is persisted as assistant text;
- streaming placeholder appears;
- TTS starts;
- Markdown processing throws and blocks S2 DB save;
- normal local/Ollama behavior changes when S3 gate is off.

## Existing Route Isolation

S3 must not affect:

- `InferenceTarget.SERVER`;
- normal local/Ollama route;
- dev hidden QAIRT route;
- `RealNpuStandardRouteS1Provider`;
- `NpuStandardRouteS2DbBridge` candidate generation;
- `LocalStreamingRunner`;
- held engine lifecycle;
- TTS controllers;
- `Backend.NPU` persistence.

The implementation should stay inside the S1/S2 gated path and return before
the existing local route, as S1/S2 already do.

## Test Items

Unit tests:

- S3 gate off keeps `saveCandidate.assistantMessage.text`.
- S3 gate on uses `markdownCandidate.finalizedText`.
- `こんにちは。` remains unchanged.
- injected finalizer can transform escaped newlines.
- `markdownCandidate=null` falls back to S2 text or prevents S3 processing,
  depending on the selected first implementation policy.
- failure S1 result does not call finalizer.
- S2 candidate missing does not call finalizer.

ChatScreen-level tests:

- S3 gate off follows current S2 behavior.
- S3 gate on inserts one user row and one assistant row.
- assistant row message equals finalized text.
- assistant row message does not equal S1 diagnostic display text.
- failure writes no DB rows.
- streaming/TTS/Backend persistence are not called.

Manual runtime checks after implementation:

- `customBuildExperimentDebug` with S3 gate ON still shows
  user `こんにちは` and assistant `こんにちは。`.
- DB body and visible assistant body match.
- `NPU STANDARD ROUTE S1` diagnostic block remains separate.
- No streaming placeholder appears.
- No TTS starts.

## Open Decisions

- Whether S3 gate should initially be `false` for both build variants and only
  toggled temporarily for runtime verification.
- Whether repair diagnostics should be recorded in `localSourceSummary` or a
  future stats field.
- Whether `markdownCandidate=null` should hard-fail the S3 save or silently
  fall back to S2 sanitized text when S2 is otherwise valid.
