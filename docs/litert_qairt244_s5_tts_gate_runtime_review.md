# QAIRT244 Phase S5 TTS Gate Runtime Review

Date: 2026-05-30

Scope: static review only. This document does not implement code, run runtime
probes, install APKs, change native code, turn on the S5 gate, connect actual
TTS playback, or persist `Backend.NPU`.

## Gate Location

`ChatScreen.kt` currently defines:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S5_TTS = false
```

The S5 gate is inside the existing S1 ChatScreen route:

```text
InferenceTarget.LOCAL
-> requestPrompt blank check
-> S1 gate
-> NpuStandardRouteS1Bridge().run()
-> optional S2 DB candidate
-> optional S3 final Markdown text
-> optional S4-A pseudo streaming final display
-> S5 TTS candidate evaluation only if S5 gate is true
-> return before existing local/Ollama route
```

S5 currently evaluates only:

```kotlin
NpuStandardRouteS5TtsBridge().prepareTtsCandidate(...)
shouldPrepareNpuStandardRouteS5Tts(...)
```

It does not call:

```kotlin
ttsController.speak(...)
ttsController.speakQueued(...)
maybeReleaseHeldEngineForTtsPlayback()
```

## Gate False Behavior

With `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false`:

- `NpuStandardRouteS5TtsBridge` is not called.
- no TTS candidate is evaluated.
- S4-A behavior is unchanged.
- S3 final text behavior is unchanged.
- S2 DB insert behavior is unchanged.
- S1 display behavior is unchanged.
- existing local/Ollama TTS behavior is unchanged.
- `Backend.NPU` persistence remains disconnected.

Rollback remains:

```kotlin
ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false
```

## Gate True Candidate Conditions

When the S5 gate is temporarily true, candidate generation requires:

- S1 gate is already selected;
- `s1Result.successCriteriaMet=true`;
- `ttsEnabled=true`;
- `streamingActive=false`;
- final assistant text is nonblank;
- `sanitizeTextForTts(finalAssistantText)` returns nonblank speak text.

The ChatScreen call uses:

```kotlin
sanitizeForTts = ::sanitizeTextForTts
streamingActive = npuStandardRouteS4PseudoStreamingActive
```

`shouldPrepareNpuStandardRouteS5Tts(...)` is:

```kotlin
enabled && mapping.hasTtsCandidate
```

The candidate keeps side effects disconnected:

```text
ttsInvoked=false
streaming=false
backendNpuPersisted=false
```

This is still a candidate path only; it is not actual speech playback.

## Text Source

S2/S3 path:

```text
assistantTextForPersist
```

is passed to S5 after DB final insert. This is the same final text used for the
assistant row.

S4-A display-only path:

```text
s4DisplayOnlyCandidate.finalText ?: s1Result.displayText
```

is passed after S4-A final display completes.

The S5 path must not speak or prepare diagnostics text:

- `NPU STANDARD ROUTE S1`;
- `NPU STANDARD ROUTE S4-A ...`;
- `localSourceSummary`;
- raw NPU output;
- failure reason text.

## Failure, Empty, And Punctuation Policy

Candidate generation returns no candidate when:

- `s1Result.successCriteriaMet=false`;
- `ttsEnabled=false`;
- `streamingActive=true`;
- final text is empty;
- sanitizer output is empty.

Because ChatScreen injects `sanitizeTextForTts(...)`, punctuation-only output
such as:

```text
。
```

returns empty and therefore produces no candidate.

Failure/fallback/timeout/fresh crash do not pass S1 success criteria and should
therefore produce no TTS candidate.

## Streaming Active Policy

S4-A pseudo streaming must not speak while active.

The S5 call uses:

```kotlin
streamingActive = npuStandardRouteS4PseudoStreamingActive
```

If this value is true, `NpuStandardRouteS5TtsMapper` returns:

```text
failureReason=streaming_active
ttsCandidate=null
```

The current UI path evaluates S5 after S4-A final display, so the expected
runtime candidate check should occur with `streamingActive=false`.

## Relationship With ttsEnabled

S5 respects the existing user TTS setting:

```text
ttsEnabled=false -> ttsCandidate=null
ttsEnabled=true  -> candidate may be created if other conditions pass
```

`devEnableStreamingSentenceTts` is not part of S5. It must remain specific to
existing local/Ollama streaming sentence playback and must not be used for
S4-A pseudo chunks.

## Runtime Check Plan

The next runtime check should remain candidate-only unless a separate commit
connects actual TTS playback.

Temporary local changes for candidate-path check:

```text
ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true
```

Recommended optional context:

```text
ENABLE_NPU_STANDARD_ROUTE_S2_DB=true
ENABLE_NPU_STANDARD_ROUTE_S4A_PSEUDO_STREAMING=true
```

This checks the intended final ordering:

```text
S1 success
-> S2 DB final insert
-> S4-A final display
-> S5 candidate evaluation
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
- if S4-A is enabled, confirm `NPU STANDARD ROUTE S4-A FINAL`;
- confirm final text `こんにちは。`;
- confirm no actual speech starts because `ttsController.speak(...)` is not
  connected in this phase;
- confirm no TTS stop/replay ownership change is required;
- confirm no `Backend.NPU` persistence.

Optional result file check:

```bash
adb -s 192.168.52.52:34437 shell run-as io.github.ninbyo02.lami.customnpu cat files/qairt244_short_multitoken_smoke_result.txt
```

Rollback commands:

```text
set ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false
restore any temporary S2/S4-A gate changes
rebuild/reinstall customBuildExperimentDebug
confirm git status -sb is clean
```

## Pass Criteria

- With S5 gate false, S4/S3/S2/S1 behavior is unchanged.
- With S5 gate true, app still reaches the final NPU UI result.
- Candidate path does not call actual TTS playback.
- Punctuation-only output would be rejected by `sanitizeTextForTts`.
- `ttsEnabled=false` would reject candidate generation.
- `streamingActive=true` would reject candidate generation.
- No `Backend.NPU` persistence is introduced.

## Candidate Gate ON Runtime Result

Temporary runtime check:

- temporarily set `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true`;
- installed and started `customBuildExperimentDebug`;
- confirmed the ChatScreen UI displayed `NPU STANDARD ROUTE S1`;
- confirmed the final visible answer `こんにちは。`;
- no actual speech occurred because this phase is candidate-only and
  `ttsController.speak(...)` is not connected;
- no crash occurred;
- rolled back by restoring `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false` and
  reinstalling;
- `git status -sb` was clean after rollback.

Interpretation:

- The S5 candidate gate can be enabled without breaking the current standard
  UI NPU path.
- This result does not validate actual TTS playback.
- TTS playback remains unconnected.
- `Backend.NPU` persistence remains disconnected.

## Speak Gate ON Runtime Result

Temporary runtime check:

- temporarily set `ENABLE_NPU_STANDARD_ROUTE_S2_DB=true`;
- temporarily set `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=true`;
- installed and started `customBuildExperimentDebug`;
- confirmed the ChatScreen UI displayed the normal chat exchange:
  user `こんにちは` and assistant `こんにちは。`;
- confirmed the ChatScreen UI displayed `NPU STANDARD ROUTE S1`;
- confirmed the visible S1 text `こんにちは。`;
- confirmed TTS speech by listening on the device;
- `logcat` grep for `NPU_S5_TTS` returned no lines, so trace visibility needs
  improvement;
- no crash occurred;
- rolled back by restoring both S2 and S5 gates to false and reinstalling;
- `git status -sb` was clean after rollback.

Interpretation:

- The gated S5 `ttsController.speak(...)` path succeeded on device.
- S1 through S5 are complete for the current gated integration roadmap.
- `Backend.NPU` persistence remains disconnected.
- Remaining work: improve S5 trace visibility, verify S4-A long-text chunking,
  and decide the permanent gate-on policy.

## Stop Conditions

Stop and roll back if:

- actual TTS playback starts in this candidate-only phase;
- `ttsController.speak(...)` or `speakQueued(...)` is invoked;
- S5 candidate evaluation changes S4/S3/S2 output;
- candidate is generated for failure/empty/punctuation-only output;
- candidate is generated while `streamingActive=true`;
- normal local/Ollama TTS behavior changes with S5 gate false;
- `Backend.NPU` persistence appears.
