# QAIRT244 Phase S5 TTS Speak Connection Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, change native code, connect `ttsController.speak(...)`,
or persist `Backend.NPU`.

## Baseline

S5 currently has a candidate-only UI path:

```text
S1 result
-> final assistant text
-> NpuStandardRouteS5TtsBridge.prepareTtsCandidate(...)
-> shouldPrepareNpuStandardRouteS5Tts(...)
-> no actual TTS call
```

The candidate-only gate has been smoke-checked with
`ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true`: the standard UI NPU path still
displayed `NPU STANDARD ROUTE S1` and `こんにちは。`, no crash occurred, and no
speech occurred because `ttsController.speak(...)` is not connected.

Existing non-NPU final response TTS uses:

```kotlin
sanitizeTextForTts(response).takeIf { it.isNotEmpty() }?.let { speechText ->
    currentSpeakingAssistantMessageId = assistantId
    stopButtonOwnerAssistantMessageId = assistantId
    stopButtonOwnerSetAtMs = SystemClock.elapsedRealtime()
    maybeReleaseHeldEngineForTtsPlayback()
    ttsController.speak(speechText)
}
```

S5 should follow that pattern only after the NPU final answer is stable.

## Minimal Speak Position

Recommended first implementation target: S2-on path only.

Minimum safe position in `ChatScreen.kt`:

```text
inside existing S1 gate
  after S2 saveCandidate exists
  after S3 final text is resolved
  after S4-A final display completes if S4-A is enabled
  after user row insert
  after assistant row insert returns assistantId
  after S5 TTS candidate is prepared
  call ttsController.speak(ttsCandidate.speakText)
```

This location is preferred because:

- `assistantTextForPersist` is final;
- the assistant row already exists;
- stop/replay ownership can use the assistant id;
- S4-A pseudo chunks have completed;
- failure paths have not persisted an assistant row;
- the path is still inside the NPU S1 gate and returns before the normal
  local/Ollama route.

Do not first connect S2-off display-only TTS. Without an assistant row id, stop
and replay ownership are ambiguous.

## Speak Conditions

Call `ttsController.speak(...)` only if all conditions pass:

```text
ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true
ttsEnabled=true
assistantId != null
s5Mapping.hasTtsCandidate=true
ttsCandidate.speakText is nonblank
!ttsController.isInCooldown()
!isTtsSuppressedForAssistant(assistantId)
npuStandardRouteS4PseudoStreamingActive=false
```

The actual spoken text must be:

```kotlin
ttsCandidate.speakText
```

not raw final text. The candidate already applies the injected sanitizer:

```kotlin
sanitizeForTts = ::sanitizeTextForTts
```

`AndroidTtsController` will then apply `SpeechTextBuilder` and
`TtsSummaryBuilder` before Android TTS playback.

## Text Source

The final assistant text passed to S5 should be:

```text
assistantTextForPersist
```

This is also the text passed to:

```kotlin
createAssistantMessage(... response = assistantTextForPersist ...)
```

If S3 is enabled, `assistantTextForPersist` is the S3 finalized text. If S3 is
disabled, it is the S2 sanitized assistant text.

If S4-A is enabled, S5 must run after:

```text
npuStandardRouteS4PseudoStreamingText = s4PseudoStreamingCandidate.finalText
npuStandardRouteS4PseudoStreamingActive = false
```

so speech starts only after the final pseudo streaming display.

## Failure And Empty Policy

Do not speak when:

- `s1Result.successCriteriaMet=false`;
- `fallbackUsed=true`;
- `timeout=true`;
- `freshCrash=true`;
- S2 did not produce a save candidate;
- assistant insert failed or no `assistantId` is available;
- final text is blank;
- final text is punctuation-only, such as `。`;
- `sanitizeTextForTts(...)` returns empty;
- `ttsEnabled=false`;
- `ttsController.isInCooldown()=true`;
- the assistant id is currently suppressed by stop/replay guard.

These conditions should prevent `ttsController.speak(...)` from being called.

## S4-A Policy

S4-A is pseudo streaming, so it must not use:

```kotlin
ttsController.speakQueued(...)
consumeStreamingSentenceAndSpeak(...)
speakStreamingTailIfNeeded(...)
```

S5 should speak only after the final S4-A chunk. It must not speak intermediate
chunks and must not touch streaming sentence buffers.

The S5 candidate call should continue to pass:

```kotlin
streamingActive = npuStandardRouteS4PseudoStreamingActive
```

If that is true, candidate generation should fail with `streaming_active`.

## Cooldown And Stop Ownership

Before speaking, mirror existing final-response TTS ownership:

```kotlin
currentSpeakingAssistantMessageId = assistantId
stopButtonOwnerAssistantMessageId = assistantId
stopButtonOwnerSetAtMs = SystemClock.elapsedRealtime()
```

Then:

```kotlin
maybeReleaseHeldEngineForTtsPlayback()
ttsController.speak(ttsCandidate.speakText)
```

Stop should continue through:

```kotlin
stopTtsWithCleanup(...)
```

Replay should remain unchanged because replay already reads persisted
`message.message` and sanitizes it through `sanitizeTextForTts(...)`.

## Held Engine Release

`maybeReleaseHeldEngineForTtsPlayback()` should be called only after:

- final NPU result is complete;
- S4-A final display has completed if enabled;
- assistant row has been inserted;
- TTS candidate is valid.

Rollback immediately if held-engine release harms the next NPU run.

## Rollback

Rollback remains gate-only:

```text
ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false
```

With S5 gate off:

- no candidate is used;
- no `ttsController.speak(...)` call occurs;
- S4-A/S3/S2/S1 behavior is unchanged;
- existing local/Ollama TTS behavior is unchanged;
- `Backend.NPU` persistence remains disabled.

## Runtime Check Plan

Temporary local changes for the later speak runtime check:

```text
ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true
ENABLE_NPU_STANDARD_ROUTE_S2_DB=true
```

Optional:

```text
ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=true
```

Build/install/launch:

```bash
./gradlew :app:assembleCustomBuildExperimentDebug
adb -s 192.168.52.52:34437 install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk
adb -s 192.168.52.52:34437 shell am start -n io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.MainActivity
```

Manual check:

- confirm app package `io.github.ninbyo02.lami.customnpu`;
- enable the existing user TTS setting;
- select Local route;
- send `こんにちは`;
- confirm final UI response appears first;
- confirm TTS starts once after final answer;
- confirm no speech occurs during S4-A active display;
- press stop and confirm speech stops;
- replay the assistant row and confirm existing replay path still works;
- rollback S5 gate to false and reinstall;
- confirm `git status -sb` is clean after rollback.

## Test Items

Pure/unit tests:

- S5 speak gate off does not use candidate.
- S5 speak gate on requires candidate.
- `ttsEnabled=false` prevents candidate/speak.
- `streamingActive=true` prevents candidate/speak.
- failure/empty/punctuation-only prevents candidate/speak.
- candidate `speakText` is the text passed to TTS.

ChatScreen-level tests:

- S5 gate off preserves S4-A/S3/S2.
- S5 gate on calls `ttsController.speak(...)` only after assistant insert.
- S5 gate on does not call `speakQueued(...)`.
- S5 gate on does not touch streaming sentence buffers.
- stop ownership uses the assistant id.
- replay remains existing persisted-row behavior.
- failure path does not call TTS.

## Stop Conditions

Stop and roll back if:

- TTS starts before final UI response;
- TTS starts during S4-A active display;
- TTS speaks diagnostics, raw output, or labels;
- TTS speaks punctuation-only text;
- `speakQueued(...)` is used for S4-A;
- stop button cannot stop speech;
- replay behavior regresses;
- TTS failure changes NPU success classification;
- held-engine release causes next-run NPU failure;
- normal local/Ollama TTS changes with S5 gate off;
- `Backend.NPU` persistence is introduced.
