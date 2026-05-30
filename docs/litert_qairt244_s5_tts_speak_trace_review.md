# QAIRT244 Phase S5 TTS Speak Trace Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, change native code, change `ttsController.speak(...)`, or
persist `Backend.NPU`.

## Baseline

The S5 speak path is gated by:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S5_TTS = false
```

When temporarily enabled, the current S2 path does:

```text
insert user row
insert assistant row -> assistantId
NpuStandardRouteS5TtsBridge.prepareTtsCandidate(...)
shouldSpeakNpuStandardRouteS5Tts(...)
maybeReleaseHeldEngineForTtsPlayback()
ttsController.speak(ttsCandidate.speakText)
```

The first speak runtime check needs explicit trace points because the final UI
text alone cannot prove whether:

- a candidate was created;
- the speak call was invoked;
- a skip reason prevented speech.

## Trace Surface

Use logcat plus the existing local trace file path via
`appendLocalReflectionTrace(...)`.

Recommended helper:

```kotlin
fun traceNpuS5Tts(message: String) {
    val line = "NPU_S5_TTS $message"
    Log.i("ChatScreen", line)
    appendLocalReflectionTrace(context.applicationContext, line)
}
```

Do not add a visible UI debug block for the first trace pass.

Reasoning:

- the UI already has S1/S4-A debug blocks;
- TTS trace is operational state, not user-facing result text;
- logcat/file trace can be pulled after the run;
- avoiding another UI block reduces layout risk before runtime verification.

If later needed, a compact copyable debug block can be added after the logging
contract is stable.

## ttsCandidate_created

Record immediately after `NpuStandardRouteS5TtsBridge.prepareTtsCandidate(...)`
returns.

Recommended fields:

```text
ttsCandidate_created=true|false
failure_reason=<mapping.failureReason or none>
speak_text_length=<candidate.speakText.length or 0>
final_text_length=<assistantTextForPersist.length>
tts_enabled=<ttsEnabled>
streaming_active=<npuStandardRouteS4PseudoStreamingActive>
assistant_id=<assistantId or null>
backend_npu_persisted=false
```

This trace should happen before `shouldSpeakNpuStandardRouteS5Tts(...)` so that
candidate failures are visible.

## tts_speak_invoked

Record immediately before:

```kotlin
ttsController.speak(ttsCandidate.speakText)
```

Recommended fields:

```text
tts_speak_invoked=true
speak_text_length=<ttsCandidate.speakText.length>
assistant_id=<assistantId>
cooldown=false
stop_suppressed=false
streaming_active=false
backend_npu_persisted=false
```

Do not log full speech text by default. If content confirmation is required,
log only a short sanitized preview:

```text
speak_text_first_40=<first 40 chars>
```

Avoid logging raw output or diagnostic labels.

## tts_skipped_reason

Define one normalized reason for every no-speak path:

```text
gate_off
candidate_null
tts_disabled
streaming_active
assistant_id_null
cooldown
stop_suppressed
empty_after_sanitize
```

Recommended classification order:

1. `gate_off`
2. `candidate_null`
3. `tts_disabled`
4. `streaming_active`
5. `assistant_id_null`
6. `cooldown`
7. `stop_suppressed`
8. `empty_after_sanitize`
9. `none`

The ordering keeps broad gate and candidate failures ahead of UI ownership
checks. `empty_after_sanitize` should map from
`NpuStandardRouteS5TtsContract.FAILURE_EMPTY_SPEAK_TEXT`.

Recommended pure helper:

```kotlin
internal fun classifyNpuStandardRouteS5TtsSkipReason(
    enabled: Boolean,
    mapping: NpuStandardRouteS5TtsMapping,
    ttsEnabled: Boolean,
    streamingActive: Boolean,
    assistantId: Int?,
    suppressedForAssistant: Boolean,
    inCooldown: Boolean,
): String
```

This helper should be tested independently from `ttsController`.

## Where To Emit Skip Trace

Emit skip trace after candidate creation and before return from the S5 block:

```text
tts_skipped_reason=<reason>
tts_speak_invoked=false
...
```

For `gate_off`, the current code does not enter the S5 block. If gate-off trace
is desired, it should be a separate lightweight log at the S1/S2 boundary, but
the first implementation can avoid logging gate-off on every normal run to keep
noise low.

First trace implementation recommendation:

- when S5 gate is true, always log candidate and skip/speak result;
- when S5 gate is false, no trace.

The docs can still define `gate_off` for tests and later explicit diagnostics.

## File Versus UI

Preferred first trace surface:

```text
logcat + appendLocalReflectionTrace(...)
```

Do not write a new dedicated result file in the first implementation.

Reasoning:

- existing traces already support ADB pull/cat workflows;
- S5 state is transient and tied to the current ChatScreen run;
- no artifact/binary git add is needed;
- UI remains focused on S1/S4-A route output.

Consider a dedicated file only if logcat/trace file is insufficient on device.

## Rollback

Rollback should be trace-gate-only or code-revert-only:

- remove/disable S5 trace calls;
- keep `ENABLE_NPU_STANDARD_ROUTE_S5_TTS=false`;
- no DB migration required;
- no TTS state migration required;
- no `Backend.NPU` persistence involved.

Rollback triggers:

- trace throws or blocks TTS;
- trace logs raw sensitive prompt/output unexpectedly;
- UI rendering changes due to trace state;
- trace file grows aggressively;
- normal local/Ollama TTS logs change unexpectedly.

## Runtime Check Commands

Build/install/launch for a later trace runtime check:

```bash
./gradlew :app:assembleCustomBuildExperimentDebug
adb -s 192.168.52.52:34437 install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk
adb -s 192.168.52.52:34437 shell am start -n io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.MainActivity
```

Logcat filter:

```bash
adb -s 192.168.52.52:34437 logcat -d | grep 'NPU_S5_TTS'
```

Existing trace file inspection can use the same app package as the
customBuildExperimentDebug runtime:

```bash
adb -s 192.168.52.52:34437 shell run-as io.github.ninbyo02.lami.customnpu ls files
```

If the existing local reflection trace file name is needed, inspect the app
files directory and cat the matching trace file.

## Pass Criteria

For a successful speak run:

```text
ttsCandidate_created=true
tts_skipped_reason=none
tts_speak_invoked=true
assistant_id=<non-null>
speak_text_length>0
backend_npu_persisted=false
```

For a candidate-only or skip run:

```text
ttsCandidate_created=false or true
tts_skipped_reason=<expected reason>
tts_speak_invoked=false
backend_npu_persisted=false
```

## Stop Conditions

Stop and roll back if:

- no S5 trace appears with S5 gate true;
- trace says `tts_speak_invoked=true` but no speak call should be possible;
- trace says `tts_speak_invoked=false` while speech occurs;
- `tts_skipped_reason` is ambiguous or missing;
- raw prompt/output is logged beyond a short sanitized preview;
- trace causes crash/ANR;
- trace changes S1/S2/S3/S4-A behavior;
- `Backend.NPU` persistence appears.
