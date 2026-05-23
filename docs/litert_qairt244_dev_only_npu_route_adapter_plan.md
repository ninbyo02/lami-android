# QAIRT 2.44 DEV-Only NPU Route Adapter Boundary

Date: 2026-05-23

Scope: design only. This document defines the smallest API boundary for a
future DEV-only NPU route adapter. It does not implement the adapter, connect
normal `ChatScreen`, set normal `selectedPath=npu`, run NPU generation,
`Engine.initialize`, `RunDecode`, high-level `generateResponse`, or streaming.

## Current Proven Source

The only approved execution source remains the custom diagnostic path:

- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticChatActivity.kt:321`
  starts guarded short multi-token smoke with timeout and one-shot UI lock.
- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticChatActivity.kt:362`
  simulates timeout without native execution.
- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticChatActivity.kt:372`
  calls the editable prompt wrapper only after UI gates pass.
- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt:46`
  exposes `runEditablePrompt(...)`.
- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt:56`
  validates prompt before native execution.
- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244ShortMultitokenSmoke.kt:68`
  calls the native editable prompt entrypoint.

The adapter must reuse this lower-level shape and must not reuse high-level
`Conversation` / `Session` / `generateResponse` entrypoints for the first
normal UI experiment.

## Normal ChatScreen Entry Candidates

Observed normal send path:

- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt:2214`
  defines the composer send/stop button.
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt:2279`
  logs the send entry and branches by `selectedInferenceTarget`.
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt:2335`
  enters the `InferenceTarget.LOCAL` branch.
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt:2347`
  captures `requestPrompt`.
- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt:2369`
  starts `localInferenceJob`.

Candidate insertion point for a future adapter is after prompt validation in
the `InferenceTarget.LOCAL` branch and before normal local DB insertion at
`ChatScreen.kt:2395`. That keeps the first DEV-only NPU run out of normal
message persistence.

Do not insert the first adapter inside:

- `LocalStreamingRunner`
- held engine acquisition
- official LiteRT conversation flow
- legacy reflection fallback
- TTS/Markdown rendering paths

Those paths are broader than the bounded Diagnostic Chat proof.

## Existing Paths To Avoid Initially

### DB Persistence

- `ChatScreen.kt:2395` inserts the user message.
- `ChatScreen.kt:3085` finalizes assistant output into the chat.
- `ChatScreen.kt:6674` builds the persisted assistant `Message`.
- `OllamaViewModel.kt:163` and `OllamaViewModel.kt:173` insert messages.

Initial adapter result must not call these paths. Store only a DEV artifact
path and transient DEV status.

### Streaming / Markdown

- `ChatScreen.kt:2639`, `ChatScreen.kt:2743`, and `ChatScreen.kt:2845`
  upsert streaming placeholders from local partials.
- `LocalStreamingRunner.kt:3357` starts official flow streaming.
- `LocalStreamingRunner.kt:3436` invokes `sendMessageAsync`.
- `LocalStreamingRunner.kt:3492` appends Markdown streaming chunks.
- `LocalStreamingRunner.kt:3638` starts the official blocking path.
- `LocalStreamingRunner.kt:3700` invokes `sendMessage`.
- `ChatScreen.kt:4074` and `ChatScreen.kt:4082` normalize Markdown for
  streamed/final display.

Initial adapter result must be non-streaming and must not enter Markdown
streaming or repair logic.

### TTS

- `ChatScreen.kt:704` reads streaming sentence TTS settings.
- `ChatScreen.kt:711` creates `AndroidTtsController`.
- `ChatScreen.kt:1635` and `ChatScreen.kt:3103` start TTS after responses.
- `ChatScreen.kt:3711` starts replay TTS for existing messages.

Initial adapter result must not call TTS or set stop-button ownership for TTS.

### Stop Button

- `ChatScreen.kt:2214` uses the same composer button for send and stop.
- `ChatScreen.kt:2223` handles local stop.
- `ChatScreen.kt:2240` stops TTS as part of stop cleanup.

Initial adapter must not connect to the stop button. Timeout is owned by the
adapter, not by the normal stop UI.

## Proposed API

```kotlin
interface DevOnlyNpuRouteAdapter {
    suspend fun runOnce(
        prompt: String,
        maxOutputTokens: Int = 3,
        timeoutMs: Long = 30_000L,
    ): DevOnlyNpuRouteResult
}
```

The adapter should live only in `customBuildExperimentDebug` source sets or be
compiled as a no-op unavailable implementation outside that flavor.

## Result Schema

```kotlin
data class DevOnlyNpuRouteResult(
    val success: Boolean,
    val output: String?,
    val reasonCode: String,
    val elapsedMs: Long?,
    val decodeElapsedMs: Long?,
    val prompt: String,
    val maxOutputTokens: Int,
    val backendEvidence: String?,
    val artifactPath: String?,
    val freshCrash: Boolean,
    val timeout: Boolean,
)
```

Required `reasonCode` values:

- `ok`
- `disabled_flavor`
- `dev_toggle_off`
- `invalid_prompt`
- `native_marker_missing`
- `token_cap_mismatch`
- `stale_diagnostic_summary`
- `timeout`
- `fresh_crash`
- `native_failure`
- `cleanup_unknown`
- `unsupported_device`

## Gate Conditions

All gates must pass:

- `customBuildExperimentDebug`
- DEV hidden toggle ON
- Nubia / SM8750 check
- QAIRT 2.44 aligned runtime stack expected
- `libcdsprpc.so` uses-native-library visibility expected
- `qairt244_editable_prompt_smoke_v1` marker available
- `SetMaxOutputTokens(3)` evidence available
- prompt validator OK
- `maxOutputTokens == 3`
- `timeoutMs <= 30_000`
- latest Diagnostic Chat success summary fresh
- fallback / timeout / recovery artifact pass
- one-shot hardening artifact pass
- no fresh tombstone evidence

## Failure And Timeout Semantics

- Failure returns `success=false`.
- Failure does not write to message DB.
- Failure does not call TTS.
- Failure does not invoke Markdown rendering.
- Failure does not auto retry GPU/CPU.
- Timeout returns `reasonCode=timeout`, `timeout=true`, and disables the NPU
  route for the session.
- Fresh crash returns `freshCrash=true` and requires Diagnostic Chat reverify.
- Unknown cleanup returns `reasonCode=cleanup_unknown` and disables reuse.

## selectedPath=npu Boundary

The adapter may report `backendEvidence=NPU`, but must not write normal
`selectedPath=npu` settings. If a future call site needs to expose backend
choice, the DEV toggle owns it for one run only. On failure, timeout, Activity
recreation, or app start, the normal selected path remains CPU/GPU.

## First Call-Site Design

The first implementation should add a DEV-only branch near
`ChatScreen.kt:2335`, before `ChatScreen.kt:2395` persists the user message.

Pseudo-flow:

```kotlin
if (devOnlyNpuToggle && promptValidator.isValid(requestPrompt)) {
    val result = devOnlyNpuRouteAdapter.runOnce(requestPrompt)
    showDevOnlyTransientResult(result)
    writeDevArtifact(result)
    return@launch
}
```

This deliberately returns before:

- user message insert
- assistant message insert
- streaming placeholder update
- TTS
- Markdown
- stop button ownership

## Initial Implementation Prohibitions

- no normal `selectedPath=npu`
- no persisted backend preference
- no DB writes
- no TTS
- no Markdown pipeline
- no streaming
- no stop button integration
- no high-level `generateResponse`
- no automatic GPU/CPU fallback
- no multi-run reuse
- no prompt longer than 32 ASCII characters

## Implementation Checklist

Before writing code:

- Confirm latest Diagnostic Chat success artifact is fresh.
- Confirm fallback / timeout / recovery artifact passes.
- Confirm one-shot hardening artifact passes.
- Confirm memory cleanup and cold-start force-stop profiles are acceptable.
- Confirm no large binary artifacts are staged.
- Define DEV hidden toggle storage as non-persistent or session-only.
- Define artifact path for adapter results.
- Add tests for result schema mapping and prompt rejection.
- Verify `standard`, `npuExperiment`, `galleryStackExperiment`, and `release`
  remain unaffected.

## Stub Implementation Status

Initial code now exists only in the `customBuildExperimentDebug` source set:

- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteAdapter.kt`
- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/BlockedDevOnlyNpuRouteAdapter.kt`
- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteGate.kt`
- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRoutePlanner.kt`
- `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteAdapterTest.kt`
- `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteGateTest.kt`
- `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRoutePlannerTest.kt`

The current implementation is deliberately blocked:

- `success=false`
- `reasonCode=adapter_not_connected`
- `output=null`
- `elapsedMs=null`
- `decodeElapsedMs=null`
- `backendEvidence=null`
- `artifactPath=null`
- `freshCrash=false`
- `timeout=false`

The blocked adapter does not call NPU generation, `Engine.initialize`,
`RunDecode`, high-level `generateResponse`, `selectedPath=npu`, DB, TTS,
Markdown, streaming, or stop button code. It is not connected to `ChatScreen`.

The route gate is pure Kotlin and currently has no `ChatScreen` call site. It
requires all of the following before a future route may run:

- `customBuildExperiment=true`
- `allowEditablePromptPreview=true`
- `allowGuardedNpuRun=true`
- `allowEditablePromptExecution=true`
- DEV checkbox checked
- validator valid
- native editable prompt support present
- `running=false`
- `maxOutputTokens=3`

The fixed failure reasons are:

- `NOT_CUSTOM_BUILD_EXPERIMENT`
- `EDITABLE_PREVIEW_DISABLED`
- `GUARDED_RUN_DISABLED`
- `EDITABLE_PROMPT_EXECUTION_DISABLED`
- `DEV_CHECKBOX_NOT_CHECKED`
- `VALIDATOR_INVALID`
- `NATIVE_PROMPT_UNSUPPORTED`
- `RUN_ALREADY_IN_PROGRESS`
- `INVALID_MAX_OUTPUT_TOKENS`

The route planner combines the gate and adapter without any ChatScreen call
site:

- gate blocked: returns `success=false` and
  `reasonCode=gate_blocked:<REASON>`
- gate blocked: adapter is not called
- gate OK: adapter `runOnce(...)` is called
- current adapter: `BlockedDevOnlyNpuRouteAdapter`, so gate OK still returns
  `reasonCode=adapter_not_connected`

Planner unit tests use a recording fake adapter to prove gate failures do not
call the adapter and the planner itself does not call Engine.initialize or
RunDecode.

## Planner Result UI Boundary

`NpuDiagnosticChatActivity` now contains a display-only section named
`Planner Preview (blocked)`.

The preview constructs a synthetic all-clear `DevOnlyNpuRouteGateInput` and
calls `DevOnlyNpuRoutePlanner.runIfAllowed(...)` with the default
`BlockedDevOnlyNpuRouteAdapter`:

- prompt: `Hello`
- `maxOutputTokens=3`
- `timeoutMs=30000`
- gate result: OK
- adapter result: `success=false`
- adapter reason: `adapter_not_connected`

The preview is intentionally not a normal ChatScreen integration. It only
renders the structured planner result and safety flags:

- `ChatScreen route connected=false`
- `selectedPathNpuApplied=false`
- `npuGeneration=false`
- `engineInitialize=false`
- `runDecode=false`
- `highLevelGenerateResponse=false`
- `dbSave=false`
- `tts=false`
- `markdown=false`
- `streaming=false`

Refresh re-runs only this blocked planner preview. Because the adapter remains
`BlockedDevOnlyNpuRouteAdapter`, Refresh does not execute NPU generation,
`Engine.initialize`, `RunDecode`, or any normal inference path.

## Transient Result Display Model

The DEV-only route now has a pure Kotlin display mapper for pre-ChatScreen
transient result/error rendering:

- source:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteDisplayModel.kt`
- tests:
  `app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/npu/DevOnlyNpuRouteDisplayModelTest.kt`

The mapper converts `DevOnlyNpuRouteResult` into a UI-facing
`DevOnlyNpuRouteDisplayModel` with:

- `title`
- `message`
- `status`
- `output`
- `reasonCode`
- `elapsedText`
- `backendEvidenceText`
- `artifactText`

Status classification is fixed as:

- `success=true` -> `SUCCESS`
- `timeout=true` -> `TIMEOUT`
- `freshCrash=true` -> `CRASH`
- `reasonCode` starts with `gate_blocked:` -> `BLOCKED`
- `reasonCode=adapter_not_connected` -> `BLOCKED`
- otherwise -> `ERROR`

This is still not connected to normal `ChatScreen`. It does not call the
adapter, NPU generation, `Engine.initialize`, `RunDecode`, high-level
`generateResponse`, DB, TTS, Markdown, streaming, or normal `selectedPath=npu`.

## Remaining Steps To Normal UI

1. Implement adapter as `customBuildExperimentDebug` only, with no ChatScreen
   call site.
2. Unit-test result schema and gate failures.
3. Add a DEV-only ChatScreen branch that returns before DB persistence.
4. Run one short prompt through the branch.
5. Review artifacts before considering any DB/TTS/Markdown/streaming work.

## No-Run Confirmation

This pass implemented only the blocked adapter schema and tests. It did not
connect normal `ChatScreen`, set normal `selectedPath=npu`, run NPU generation,
call `Engine.initialize`, call `RunDecode`, call high-level
`generateResponse`, or run streaming.
