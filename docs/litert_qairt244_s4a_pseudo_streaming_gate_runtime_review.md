# QAIRT244 Phase S4-A Pseudo Streaming Gate Runtime Review

Date: 2026-05-30

Scope: static review only. This document does not implement code, run runtime
probes, install APKs, change native code, turn on the S4-A gate, connect real
token streaming, connect TTS, or persist `Backend.NPU`.

## Gate Location

`ChatScreen.kt` currently defines:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING = false
private const val NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING_CHUNK_DELAY_MS = 120L
```

The S4-A gate is inside the existing S1 ChatScreen gate:

```text
InferenceTarget.LOCAL
-> image input rejection
-> requestPrompt blank check
-> shouldEnterNpuStandardRouteS1(...)
-> NpuStandardRouteS1Bridge().run()
-> optional S2 DB path
-> optional S3 Markdown finalized text
-> S4-A candidate creation only if S4-A gate is true
-> return before existing local/Ollama route
```

The NPU-specific transient state is:

```kotlin
npuStandardRouteS4PseudoStreamingText
npuStandardRouteS4PseudoStreamingActive
```

The display block is separate from the normal streaming display and uses:

```text
NPU STANDARD ROUTE S4-A PSEUDO STREAMING
NPU STANDARD ROUTE S4-A FINAL
```

## Gate False Behavior

With `ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=false`:

- S4-A Bridge is not called in the S2 DB path.
- S4-A Bridge is not called in the S1 display-only path.
- `localStreamingResponseText` is not touched.
- `streamingResponseTextForRender` is not touched.
- `streamingAssistantMessageId` is not touched.
- streaming placeholder/finalize helpers are not called.
- existing S3/S2/S1 behavior remains the active behavior.

Rollback therefore remains a single constant change back to:

```kotlin
ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=false
```

## Gate True Conditions

When the S4-A gate is temporarily true, pseudo chunks are used only when:

- S1 gate is already selected;
- `s1Result.successCriteriaMet=true`;
- final text is non-empty;
- `NpuStandardRouteS4PseudoStreamingBridge` returns a ready candidate;
- `shouldStartNpuStandardRouteS4APseudoStreaming(...)` returns true.

`shouldStartNpuStandardRouteS4APseudoStreaming(...)` is:

```kotlin
enabled && mapping.hasPseudoStreamingCandidate
```

The candidate itself requires:

- nonblank final text;
- nonblank source display text;
- non-empty nonblank cumulative chunks;
- final chunk equals final text;
- `dbPersistedText == finalText`;
- `realTokenStreaming=false`;
- `tts=false`;
- `backendNpuPersisted=false`.

## DB Persistence

DB persistence remains final-only.

In the S2 path, S4-A staged display runs before DB insertion. After staged
display completes, existing S2 insert logic writes:

```text
user row: saveCandidate.userMessage.text
assistant row: assistantTextForPersist
```

`assistantTextForPersist` is the same final full text used to build the S4-A
candidate. No chunk is inserted or updated as a DB row.

If S2 is off, S4-A is display-only and does not create DB rows.

## Markdown Policy

Markdown remains final-only.

When S3 is enabled, S4-A receives the already finalized text:

```text
NpuStandardRouteS3MarkdownBridge.resolveFinalizedText(...)
-> assistantTextForPersist
-> NpuStandardRouteS4PseudoStreamingBridge.preparePseudoStreamingCandidate(...)
```

When S3 is disabled, S4-A receives the S2 assistant fallback text. S4-A does not
run Markdown repair per chunk.

## Failure Policy

S4-A does not pseudo stream on failure.

Failure cases include:

- `s1Result.successCriteriaMet=false`;
- empty final text;
- empty source display text;
- no S2 save candidate when S2 is enabled;
- fallback/timeout/fresh crash conditions that prevent S1 success.

Failure UI remains the existing S1 transient diagnostics. Failure does not
write DB rows through S4-A and does not start pseudo chunk display.

## Local/Ollama Streaming Isolation

S4-A does not connect to:

- `LocalStreamingRunner`;
- `runWithHeldEngine(...)`;
- `localStreamingResponseText`;
- `remoteStreamingResponseText`;
- `streamingResponseTextForRender`;
- `streamingAssistantMessageId`;
- `upsertStreamingAssistantPlaceholder(...)`;
- `finalizeStreamingAssistantMessage(...)`;
- streaming sentence TTS buffers;
- stop-button owner state;
- TTS controllers.

This means S4-A is a full-text staged display path, not real token streaming.

## Runtime Check Plan

The later runtime check should be limited to `customBuildExperimentDebug`.

Temporary local changes for the runtime check:

```text
ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=true
```

Optional if DB persistence confirmation is required:

```text
ENABLE_NPU_STANDARD_ROUTE_S2_DB=true
```

Build/install/launch commands for the later runtime step:

```bash
./gradlew :app:assembleCustomBuildExperimentDebug
adb -s 192.168.52.52:34437 install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk
adb -s 192.168.52.52:34437 shell am start -n io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.MainActivity
```

Manual UI check:

- select Local route;
- send `こんにちは`;
- confirm `NPU STANDARD ROUTE S1`;
- confirm `NPU STANDARD ROUTE S4-A PSEUDO STREAMING` appears while active;
- confirm final title changes to `NPU STANDARD ROUTE S4-A FINAL`;
- confirm final text equals the expected final full output;
- if S2 is on, confirm normal chat rows are still final-only;
- confirm no streaming placeholder diagnostics appear;
- confirm no TTS starts.

Result file check for NPU success remains the existing real provider/dev-only
result surface:

```bash
adb -s 192.168.52.52:34437 shell run-as io.github.ninbyo02.lami.customnpu cat files/qairt244_short_multitoken_smoke_result.txt
```

Rollback commands for the later runtime step:

```text
set ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=false
set ENABLE_NPU_STANDARD_ROUTE_S2_DB=false if it was temporarily enabled
rebuild/reinstall customBuildExperimentDebug
confirm git status -sb is clean
```

## Pass Criteria

- S4-A block appears only with the S4-A gate true.
- S4-A final text equals the final full answer.
- DB row, if S2 is enabled, contains only the final full assistant text.
- `qairt244_short_multitoken_smoke_result.txt` still reports NPU success.
- No local/Ollama streaming placeholder row is created by S4-A.
- No TTS starts.
- Rollback to gate false restores the prior S3/S2/S1 behavior.

## Stop Conditions

Stop and roll back if:

- partial chunks appear in DB;
- `localStreamingResponseText` or streaming placeholder state is involved;
- pseudo streaming starts for an S1 failure;
- final DB text differs from S4-A final text;
- TTS starts;
- normal local/Ollama streaming behavior changes with S4-A gate false;
- crash/ANR/timeout appears.
