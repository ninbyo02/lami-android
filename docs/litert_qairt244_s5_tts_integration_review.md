# QAIRT244 Phase S5 TTS Integration Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, change native code, connect TTS, or persist
`Backend.NPU`.

## Baseline

The promoted NPU route is currently staged as:

```text
S1: real NPU result -> transient display
S2: optional DB save of final user/assistant rows
S3: optional final Markdown text
S4-A: optional pseudo streaming display of final full text
```

S4-A is not real token streaming. It displays cumulative chunks derived from a
known final answer. The existing local/Ollama streaming and TTS paths are still
separate:

- normal final-response TTS uses `sanitizeTextForTts(...)` and
  `ttsController.speak(...)`;
- streaming sentence TTS uses `consumeStreamingSentenceAndSpeak(...)`,
  `speakStreamingTailIfNeeded(...)`, and `ttsController.speakQueued(...)`;
- replay TTS for persisted assistant rows uses `sanitizeTextForTts(message.message)`;
- `AndroidTtsController` additionally runs `SpeechTextBuilder.build(...)`
  before speaking.

S5 should connect only after S1/S2/S3/S4-A behavior is stable.

## Speech Text Source

The TTS source should be the final assistant text, not diagnostics and not
pseudo chunks.

Preferred order:

```text
S3 finalizedText when S3 gate is on
else S2 assistant sanitized/display text
else S1 sanitized/display text for display-only mode
```

When S2 is enabled, the spoken text should match the assistant DB body:

```text
speech source = assistant Message.message = final full assistant text
```

When S4-A is enabled, the spoken text should match:

```text
pseudoStreamingCandidate.finalText
```

Do not speak:

- `NPU STANDARD ROUTE S1` diagnostics;
- `NPU STANDARD ROUTE S4-A ...` labels;
- `localSourceSummary`;
- raw NPU output;
- partial pseudo chunks;
- failure reasons.

## S4-A Interaction

S4-A should not speak while chunks are being staged.

Recommended policy:

```text
S4-A active -> no TTS
S4-A final chunk displayed -> optional final-only TTS
```

Reasoning:

- S4-A chunks are artificial UI chunks, not model token events.
- Speaking chunks would duplicate or truncate the final response.
- Markdown/code-fence text should be sanitized once from final text.
- Stop/replay ownership is easier when there is one final assistant message.

If S4-A is off, S5 can still speak the same final text after the S2/S3 final
path finishes.

## Failure And Empty Policy

TTS must not start when:

- `s1Result.successCriteriaMet=false`;
- `fallbackUsed=true`;
- `timeout=true`;
- `freshCrash=true`;
- final text is blank;
- `sanitizeTextForTts(finalText)` returns empty;
- `SpeechTextBuilder.build(finalText)` would reduce to an empty/unsuitable
  utterance;
- S2 is enabled but no assistant row is saved;
- user stopped the run before final success.

Failure should remain visual diagnostics only. TTS failure must not change NPU
success/failure classification.

## Markdown-To-Speech

Use the existing TTS cleanup layers before speaking:

```text
final assistant text
-> sanitizeTextForTts(...)
-> AndroidTtsController.speak(...)
-> SpeechTextBuilder.build(...)
-> TtsSummaryBuilder.build(...)
```

`SpeechTextBuilder` already strips or summarizes several Markdown features:

- fenced code blocks become code/config/command summaries;
- inline code is converted or shortened;
- URLs become link summaries;
- ATX headings lose `#`;
- list markers are normalized;
- emphasis markers are stripped.

S5 should not add a second Markdown parser unless a concrete mismatch appears.
The first implementation should rely on the existing TTS sanitization and add
tests around the NPU final-text examples.

## Existing TTS Settings

S5 must respect the existing user/dev settings:

- `ttsEnabled` is the top-level enable switch.
- `devEnableStreamingSentenceTts` should not be used for S4-A pseudo chunks.
- `ttsController.isInCooldown()` should still suppress auto-speak.
- `suppressedTtsAssistantMessageId` should still prevent replay/auto speak for
  a stopped assistant row.
- `stopTtsWithCleanup(...)` remains the stop path.

Recommended new gate:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S5_TTS = false
```

S5 auto-speak should require:

```text
ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true
ttsEnabled=true
final text non-empty after sanitization
successCriteriaMet=true
not stopped
not in cooldown
```

## Held Engine And Memory

Existing TTS auto-speak can call:

```kotlin
maybeReleaseHeldEngineForTtsPlayback()
```

For the first S5 connection, this should be treated as a gate condition rather
than automatic behavior. Releasing the held engine for TTS may be useful, but it
changes NPU reuse/lifecycle behavior.

Conservative first policy:

- do not release held engine until final NPU success is complete;
- never release during S4-A chunk display;
- record a diagnostic if TTS requests release;
- rollback if TTS release affects the next NPU run.

## Connection Point

Recommended connection point in `ChatScreen.kt`:

```text
inside S1 gate
  after final text is resolved
  after S4-A chunk display completes when S4-A is enabled
  after assistant row insert when S2 is enabled
  before return@IconButton
```

This mirrors the existing normal local route, where final TTS happens after the
assistant message id is known.

For S2-off display-only mode, S5 may speak after final display if explicitly
allowed, but the first S5 runtime check should prefer S2-on so stop/replay
ownership can attach to an assistant message id.

## Rollback

Rollback should be gate-only:

```text
ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false
```

Rollback must leave:

- S4-A pseudo streaming display unchanged;
- S3 Markdown final text unchanged;
- S2 DB rows unchanged;
- S1 NPU success classification unchanged;
- `Backend.NPU` persistence disabled.

Rollback triggers:

- TTS starts before final chunk;
- TTS starts for failure/fallback/timeout/fresh crash;
- TTS speaks diagnostics or raw output;
- TTS speaks an empty or punctuation-only output;
- TTS release of held engine breaks the next NPU run;
- stop button cannot distinguish NPU generation from TTS playback;
- normal local/Ollama TTS behavior changes with S5 gate off.

## Test Items

Pure/unit tests:

- success final text creates a TTS candidate.
- failure S1 result creates no TTS candidate.
- fallback/timeout/fresh crash create no TTS candidate.
- empty text creates no TTS candidate.
- punctuation-only text creates no TTS candidate.
- Markdown heading/list/code examples sanitize to non-dangerous speech text.
- S4-A active state prevents speaking.
- S4-A final state permits speaking only final text.
- `tts=false` remains the default before S5 gate on.
- `backendNpuPersisted=false` remains unchanged.

ChatScreen-level tests:

- S5 gate off preserves S4-A/S3/S2 behavior.
- S5 gate on speaks once after final assistant text.
- S5 gate on does not call `speakQueued(...)` for pseudo chunks.
- S5 gate on does not use streaming sentence buffers.
- failure path shows diagnostics only and does not speak.
- stop after TTS starts calls `stopTtsWithCleanup(...)`.
- replay button can speak the persisted final assistant row.

Manual runtime checklist for later:

- use `customBuildExperimentDebug`;
- temporarily enable `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true`;
- keep `ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=true` only if verifying
  final-after-pseudo-stream behavior;
- send `こんにちは`;
- confirm visual final response first;
- confirm TTS starts only after final response;
- confirm no speech during S4-A chunk display;
- stop TTS and confirm UI returns to ready state;
- rollback S5 gate to false and reinstall;
- confirm `git status -sb` is clean after rollback.

## Blockers

- No stable S5 gate.
- No dedicated TTS candidate/contract for NPU final text.
- Stop ownership for display-only S1 without DB row is ambiguous.
- Held engine release for TTS may affect next-run NPU stability.
- S4-A pseudo chunks are too short to prove no chunk-level TTS without a longer
  fixed response check.
