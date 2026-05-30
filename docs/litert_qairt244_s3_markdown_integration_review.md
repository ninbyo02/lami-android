# QAIRT244 Phase S3 Markdown Integration Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, change native code, connect streaming, connect TTS, or
persist `Backend.NPU`.

## Baseline

The current promoted path is:

```text
ChatScreen -> S1 Gate -> RealProvider -> S1 result -> S2 DB candidate -> DB rows
```

S1/S2 status:

- S1 can display the real NPU result in the standard UI.
- S2 can persist one user message and one assistant message on the happy path.
- S2 stores the assistant body from `saveCandidate.assistantMessage.text`.
- `saveCandidate.assistantMessage.text` comes from `s1Result.sanitizedOutput`.
- `saveCandidate.assistantMessage.sourceDisplayText` is stored as
  `localSourceSummary`.
- Streaming remains disconnected.
- TTS remains disconnected.
- `Backend.NPU` persistence remains disconnected.

The current successful minimal output is plain Japanese:

```text
こんにちは。
```

For this output, Markdown processing should be a no-op.

## Current Display Flow

There are two relevant display surfaces:

1. Transient S1 debug block:
   - `npuStandardRouteS1DisplayText = s1Result.displayText`
   - shown as `NPU STANDARD ROUTE S1`
   - diagnostic text, not conversation body

2. Normal chat assistant row after S2:
   - DB stores `Message.message = saveCandidate.assistantMessage.text`
   - assistant UI renders the saved `message.message` through
     `PlainAssistantMessage`
   - `PlainAssistantMessage` parses fenced code segments and inspects Python
     code warnings for non-streaming messages

S3 should apply Markdown shaping only to the normal assistant body. It should
not Markdown-process the S1 diagnostic block.

## Existing Markdown/Repair Entrypoints

`ChatScreen.kt` already has final-response helpers:

- `buildFinalizedStreamingResponseForPersist(...)`
- `normalizeStreamingPartialForRender(...)`

`buildFinalizedStreamingResponseForPersist(...)` is the safer reference for S3
because it handles final text, not partial chunks:

- trims final text;
- uses `processEdgeGalleryCompatibleMarkdown(...)` when
  `MarkdownStreamingMode.EDGE_GALLERY_COMPAT`;
- otherwise uses `MarkdownCodeRepair.repair(...)`;
- records repair through an optional callback;
- returns final persisted text.

`normalizeStreamingPartialForRender(...)` should not be used for S3 because S3
is still non-streaming.

## S3 Integration Position

Recommended S3 position:

```text
s1Result.sanitizedOutput
-> S2 save candidate
-> S3 final Markdown normalization for assistant candidate text only
-> DB assistant insert
-> PlainAssistantMessage renders saved text
```

The smallest implementation point is immediately before `createAssistantMessage`
in the S2 DB branch:

```kotlin
val assistantTextForPersist = buildFinalizedStreamingResponseForPersist(
    response = saveCandidate.assistantMessage.text,
    markdownStreamingMode = markdownStreamingMode,
    onMarkdownRepair = { ... },
)
```

Then persist:

```kotlin
createAssistantMessage(
    chatId = resolvedChatId,
    response = assistantTextForPersist,
    localSourceSummary = saveCandidate.assistantMessage.sourceDisplayText,
)
```

This keeps DB and UI consistent because the saved DB body is exactly the body
rendered by `PlainAssistantMessage`.

## Code Fence / Repair Policy

Use the existing final-text repair path, not a new NPU-specific Markdown engine.

S3 should:

- apply repair only to final successful assistant text;
- use `MarkdownCodeRepair.repair(...)` through
  `buildFinalizedStreamingResponseForPersist(...)` for the default mode;
- use `processEdgeGalleryCompatibleMarkdown(...)` only when the effective
  `MarkdownStreamingMode` is `EDGE_GALLERY_COMPAT`;
- record whether repair changed the text when a diagnostics field is available;
- leave plain Japanese unchanged.

S3 should not:

- run streaming partial normalization;
- create a streaming placeholder;
- repair the S1 debug display block;
- repair failed or fallback NPU output;
- store raw diagnostics as assistant Markdown.

## Failure Policy

Failure should not enter Markdown processing.

If `NpuStandardRouteS2DbBridge` returns no save candidate, or if
`s1Result.successCriteriaMet=false`, S3 must not call Markdown helpers.

Failure path remains:

```text
S1/S2 transient display only -> no DB rows -> no Markdown
```

This preserves the S2 failure policy and avoids transforming diagnostic failure
text into user-visible assistant content.

## DB Consistency

S3 should persist the same text it displays.

For successful S3:

- `Message.message` should be the Markdown-normalized final assistant text.
- `PlainAssistantMessage` should render `Message.message`.
- `localSourceSummary` may keep S1/S2 diagnostics.
- If repair is applied, diagnostics should note that separately; the assistant
  body should not contain diagnostic flags.

For current minimal output:

```text
input sanitized_output=こんにちは。
persisted assistant message=こんにちは。
rendered assistant message=こんにちは。
repair_applied=false
```

## Rollback

Rollback should be gate-only:

- S3 Markdown gate off returns to S2 behavior, storing sanitized plain text
  directly.
- S2 DB gate off returns to S1 display-only behavior.
- S1 gate off returns to the existing local/Ollama route.

No database migration is required for rollback if S3 stores only normal
assistant text. Existing S3-processed rows can remain ordinary assistant rows.

Rollback triggers:

- plain Japanese output changes unexpectedly;
- failure output is Markdown-processed or persisted;
- S1 debug block is Markdown-processed;
- code fence repair corrupts non-code text;
- Markdown processing starts streaming, TTS, or backend persistence side
  effects;
- saved DB text and rendered UI text diverge without diagnostics.

## Test Items

Unit-level tests:

- `こんにちは。` remains unchanged.
- fenced code text is repaired through the final-response helper.
- `EDGE_GALLERY_COMPAT` converts escaped newlines through the existing helper.
- repair callback is invoked only when repair changes final text.
- failed S1/S2 mapping does not call Markdown normalization.
- S3 gate off returns the S2 candidate text unchanged.

ChatScreen-level tests:

- S3 gate off preserves S2 persisted assistant body.
- S3 gate on persists Markdown-normalized assistant body.
- S3 gate on still inserts exactly one user row and one assistant row.
- S3 gate on failure inserts no rows.
- S1 diagnostic block remains plain diagnostic display.
- TTS, streaming, and `Backend.NPU` persistence remain disconnected.

Manual runtime checks after implementation:

- S3 gate ON with `こんにちは` still displays `こんにちは。`.
- DB row body matches the visible assistant text.
- No streaming placeholder appears.
- No TTS starts.
- Code-fence prompt family can be tested only after a safe NPU prompt shaping
  plan exists for code output.

## Open Decisions

- Whether S3 should add a new NPU-specific gate constant or reuse an existing
  Markdown mode gate.
- Where to store `repair_applied` for NPU S3 diagnostics.
- Whether S3 should first ship as a no-op plain-text gate before enabling code
  fence repair for NPU output.
