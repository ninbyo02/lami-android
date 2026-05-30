# QAIRT244 Phase S3 Markdown Gate Runtime Review

Date: 2026-05-30

Scope: static review only. This document does not implement code, run runtime
probes, install APKs, change native code, turn the Markdown gate on, connect
streaming/TTS, or persist `Backend.NPU`.

## Gate Location

`ChatScreen.kt` defines the S3 Markdown gate as a top-level constant next to the
S2 DB gate:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S2_DB = false
private const val ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN = false
```

The S3 call is inside the S1/S2 gated path:

1. S1 gate selects the NPU route.
2. `NpuStandardRouteS1Bridge().run()` returns `s1Result`.
3. S2 DB gate prepares `saveCandidate`.
4. S3 resolves `assistantTextForPersist`.
5. User row is inserted.
6. Assistant row is inserted with `assistantTextForPersist`.

The S3 path remains before the dev hidden QAIRT route and before the normal
local/Ollama route.

## Gate False Behavior

When `ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN=false`,
`NpuStandardRouteS3MarkdownBridge.resolveFinalizedText(...)` returns
`fallbackText` immediately.

Current fallback text is:

```text
saveCandidate.assistantMessage.text
```

That preserves the current S2 behavior:

- no Markdown finalizer runs;
- no repair is applied;
- assistant DB body remains the S2 sanitized text;
- `こんにちは。` remains `こんにちは。`.

This is the rollback state for S3.

## Gate True Conditions

When `ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN=true`, finalized text is used only
if `NpuStandardRouteS3MarkdownBridge.prepareMarkdownCandidate(...)` returns a
candidate.

Candidate creation requires:

- `s1Result.successCriteriaMet=true`;
- `s1Result.sanitizedOutput.trim()` is non-empty;
- `s1Result.displayText.trim()` is non-empty;
- the injected Markdown finalizer returns non-empty text;
- S3 side effects remain disconnected:
  `streaming=false`, `tts=false`, `backendNpuPersisted=false`.

If these conditions fail, `resolveFinalizedText(...)` falls back to the S2 text.

## Failure And Empty Behavior

Markdown processing is skipped when the gate is off.

When the gate is on, the finalizer is not called if:

- `s1Result.successCriteriaMet=false`;
- sanitized input is empty;
- source display text is empty.

If the finalizer returns empty text, S3 returns no candidate and the bridge
falls back to the S2 assistant text.

Failure path remains:

```text
S1/S2 failure -> no S2 saveCandidate -> no DB rows -> no S3 finalized text
```

## DB And UI Consistency

The assistant DB row is created with:

```kotlin
createAssistantMessage(
    chatId = resolvedChatId,
    response = assistantTextForPersist,
    localSourceSummary = saveCandidate.assistantMessage.sourceDisplayText,
)
```

The normal chat UI later renders `Message.message` through
`PlainAssistantMessage`.

Therefore the DB body and UI body are intended to match:

```text
assistant DB body == assistantTextForPersist == visible assistant message
```

The S1 diagnostic display remains separate as `localSourceSummary` and the
transient `NPU STANDARD ROUTE S1` block. It is not the assistant body.

## Disconnected Surfaces

The S3 gate path does not call:

- `LocalStreamingRunner`;
- `runWithHeldEngine`;
- streaming placeholder helpers;
- TTS speak/queue helpers;
- `Backend.NPU` persistence.

The injected finalizer is `buildFinalizedStreamingResponseForPersist(...)`, but
it is used only as a final-text helper. S3 does not connect streaming.

## Rollback

Rollback is one-line:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN = false
```

With S3 gate off, S2 continues to persist sanitized text directly. With S2 gate
off, the path returns to S1 display-only. With S1 gate off, the app returns to
the existing local/Ollama route.

No database migration is required. Existing rows are ordinary assistant text.

## Runtime Check Commands

Do not run these as part of this review. They are proposed commands for a later
manual S3 gate ON verification after temporarily setting the gate to true.

Build and install:

```bash
./gradlew :app:assembleCustomBuildExperimentDebug
adb -s 192.168.52.52:34437 install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk
```

Launch:

```bash
adb -s 192.168.52.52:34437 shell am start -n io.github.ninbyo02.lami.customnpu/.MainActivity
```

After sending a local prompt from ChatScreen, verify:

- normal chat shows user `こんにちは`;
- normal chat shows assistant `こんにちは。`;
- the S1 debug block remains visible and separate;
- DB assistant body and visible assistant body match;
- no streaming placeholder appears;
- no TTS starts;
- rollback restores `ENABLE_NPU_STANDARD_ROUTE_S3_MARKDOWN=false`.

For Markdown-specific behavior, use a later safe prompt-shaping plan before
testing code fences or escaped newlines through real NPU output.

## Test Coverage To Keep

Current static/unit coverage should continue to include:

- S3 gate off returns fallback text and does not call finalizer;
- S3 gate on uses `markdownCandidate.finalizedText`;
- failed S1 returns fallback and does not call finalizer;
- empty final text falls back to S2 text;
- S3 candidate keeps `streaming=false`, `tts=false`,
  `backendNpuPersisted=false`.
