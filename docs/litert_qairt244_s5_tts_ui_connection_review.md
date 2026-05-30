# QAIRT244 Phase S5 TTS UI Connection Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, change native code, connect TTS, or persist
`Backend.NPU`.

## Baseline

The NPU standard route currently reaches ChatScreen through the gated S1 path
and can optionally pass through:

```text
S2 DB final user/assistant row save
S3 final Markdown text
S4-A pseudo streaming final display
```

Existing TTS paths are already present in `ChatScreen.kt`:

- final response auto-speak uses `sanitizeTextForTts(...)` and
  `ttsController.speak(...)`;
- streaming sentence TTS uses `sanitizeStreamingTextForTts(...)` and
  `ttsController.speakQueued(...)`;
- replay TTS for persisted messages uses `sanitizeTextForTts(message.message)`;
- `AndroidTtsController` runs `SpeechTextBuilder.build(...)` and
  `TtsSummaryBuilder.build(...)` before calling Android TTS.

S5 must not reuse streaming sentence TTS for S4-A pseudo chunks.

## S5 Gate Position

Recommended first gate:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S5_TTS = false
```

The gate should live beside the existing NPU phase gates in `ChatScreen.kt`.

Recommended S2-on connection point:

```text
inside existing S1 gate
  after S1 result success
  after S2 saveCandidate exists
  after S3 final text is resolved
  after S4-A chunk display completes if S4-A is enabled
  after assistant row insert returns assistantId
  before return@IconButton
```

Reasoning:

- the final assistant text is known;
- the assistant DB row exists for stop/replay ownership;
- S4-A has already completed, so no chunk-level speech can occur;
- failure paths have already returned or skipped persistence.

S2-off display-only speech should not be the first S5 runtime check because
there is no assistant message id for replay/stop ownership.

## Final Text Source

Pass the exact final assistant text to `NpuStandardRouteS5TtsBridge`.

Source priority:

```text
S3 on: markdownCandidate.finalizedText / assistantTextForPersist
S3 off + S2 on: saveCandidate.assistantMessage.text
S2 off: s1Result.displayText only for later display-only experiments
```

When S2 is enabled, the TTS input should match the assistant DB row:

```text
finalAssistantText == createAssistantMessage(... response ...)
```

When S4-A is enabled, the TTS input should match the final S4-A candidate:

```text
finalAssistantText == pseudoStreamingCandidate.finalText
```

Do not pass:

- `NPU STANDARD ROUTE S1` debug text;
- `NPU STANDARD ROUTE S4-A` labels;
- raw output;
- `localSourceSummary`;
- pseudo chunks before the final chunk;
- failure reason text.

## S4-A Interaction

S4-A is pseudo streaming, not token streaming. S5 must not call
`ttsController.speakQueued(...)` for S4-A chunks.

Recommended behavior:

```text
npuStandardRouteS4PseudoStreamingActive=true  -> no TTS candidate
npuStandardRouteS4PseudoStreamingActive=false -> final-only TTS candidate
```

`NpuStandardRouteS5TtsBridge.prepareTtsCandidate(...)` should receive:

```kotlin
streamingActive = npuStandardRouteS4PseudoStreamingActive
```

That keeps the S5 contract aligned with the rule that speech starts only after
the final answer is stable.

## Failure, Empty, And Punctuation Policy

Do not speak when:

- `s1Result.successCriteriaMet=false`;
- fallback, timeout, or fresh crash prevents S1 success;
- `finalAssistantText` is blank;
- `sanitizeTextForTts(finalAssistantText)` returns empty;
- final text is punctuation-only, such as `。`;
- S2 is enabled but no assistant row was created;
- user stopped the run before final success.

The existing `sanitizeTextForTts(...)` already returns empty for short,
punctuation-only, or symbol-only text. S5 should use that function as the
sanitizer injection:

```kotlin
sanitizeForTts = ::sanitizeTextForTts
```

`AndroidTtsController` will still run `SpeechTextBuilder` before actual speech,
so the first S5 connection should avoid adding another Markdown parser.

## Existing TTS Settings

S5 should require all of:

```text
ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true
ttsEnabled=true
!ttsController.isInCooldown()
!isTtsSuppressedForAssistant(assistantId)
ttsCandidate != null
```

It should preserve existing stop/replay semantics:

- set `currentSpeakingAssistantMessageId=assistantId` before speaking;
- set `stopButtonOwnerAssistantMessageId=assistantId`;
- set `stopButtonOwnerSetAtMs=SystemClock.elapsedRealtime()`;
- use `stopTtsWithCleanup(...)` for stop;
- leave replay button behavior unchanged because replay already reads the
  persisted assistant row.

`devEnableStreamingSentenceTts` should not affect S5 NPU final-only TTS.

## Held Engine Release

Existing final auto-speak paths call:

```kotlin
maybeReleaseHeldEngineForTtsPlayback()
```

For S5, this call should occur only after final NPU success and after S4-A final
display if S4-A is enabled. It must not run during pseudo chunk display.

Rollback triggers for this area:

- held engine release causes the next NPU run to fail;
- held engine release occurs before final S4-A display;
- TTS memory release changes S1/S2/S3/S4-A result classification.

## Rollback

Rollback is gate-only:

```text
ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false
```

With S5 gate off:

- S4-A display remains unchanged;
- S3 final text remains unchanged;
- S2 DB insert remains unchanged;
- S1 result classification remains unchanged;
- `Backend.NPU` persistence remains disabled;
- normal local/Ollama TTS behavior remains unchanged.

## Test Items

Pure/unit tests:

- S5 gate off does not start TTS candidate use.
- S5 gate on calls `NpuStandardRouteS5TtsBridge` with final assistant text.
- `ttsEnabled=false` produces no candidate.
- `streamingActive=true` produces no candidate.
- failure S1 result produces no candidate.
- empty final text produces no candidate.
- punctuation-only final text produces no candidate after `sanitizeTextForTts`.
- Markdown final text is sanitized through injected sanitizer.
- candidate side effects keep `ttsInvoked=false` until UI layer calls TTS.
- `backendNpuPersisted=false`.

ChatScreen-level tests:

- gate off preserves S4-A/S3/S2 behavior.
- gate on speaks only after final S4-A display.
- gate on does not call `speakQueued(...)`.
- gate on does not update streaming sentence buffers.
- gate on uses assistant row id when S2 is enabled.
- failure path does not call TTS.
- stop button calls `stopTtsWithCleanup(...)`.
- replay still speaks the persisted assistant row through existing replay path.

Manual runtime checklist for later:

- use `customBuildExperimentDebug`;
- temporarily set `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true`;
- keep S5 check S2-on first so assistant id ownership exists;
- optionally keep S4-A on to confirm final-after-pseudo-stream timing;
- send `こんにちは`;
- confirm final UI response appears before speech;
- confirm no speech during S4-A active display;
- confirm speech starts once after final display;
- press stop and confirm TTS stops without changing stored assistant text;
- rollback S5 gate to false and reinstall;
- confirm `git status -sb` is clean after rollback.

## Stop Conditions

Stop and roll back if:

- TTS starts for S1 failure/fallback/timeout/fresh crash;
- TTS starts while `npuStandardRouteS4PseudoStreamingActive=true`;
- TTS speaks debug labels or raw output;
- TTS speaks punctuation-only text such as `。`;
- `speakQueued(...)` is used for S4-A chunks;
- streaming sentence buffers are touched by S5;
- stop button cannot stop TTS cleanly;
- normal local/Ollama TTS behavior changes with S5 gate off;
- `Backend.NPU` persistence is introduced.
