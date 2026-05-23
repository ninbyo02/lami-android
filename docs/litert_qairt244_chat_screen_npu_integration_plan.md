# QAIRT 2.44 Guarded ChatScreen NPU Integration Plan

Date: 2026-05-23

Scope: design only. This document defines the conditions for any future
normal `ChatScreen` NPU integration after the Diagnostic Chat milestones. It
does not authorize implementation, NPU generation, `Engine.initialize`,
`RunDecode`, high-level `generateResponse`, or normal `selectedPath=npu`.

## Current Baseline

Proven in `customBuildExperimentDebug` on Nubia Z70S Ultra / SM8750:

- QAIRT 2.44 NPU initialize succeeds.
- QNN HTP / V79 / FastRPC path succeeds.
- lower-level 1-token and 3-token smoke runs succeed.
- Diagnostic Chat guarded UI run succeeds.
- editable prompt `Hello` succeeds with `max_output_tokens=3`.
- one-shot hardening succeeds: DEV checkbox off and Run disabled after
  completion.
- Diagnostic Chat fallback / timeout / recovery passes.
- fresh crash evidence: none.
- normal `ChatScreen`: not connected.
- normal `selectedPath=npu`: not used.

## Integration Phases

### Phase A: DEV Hidden Toggle Only

- Add no visible production UI.
- Gate behind `customBuildExperimentDebug` and a DEV-only hidden toggle.
- Default state remains CPU/GPU behavior.
- NPU route is never selected automatically at app start.
- DEV toggle state is not persisted to normal user settings.

### Phase B: Diagnostic Chat Equivalent Short Prompt

- Keep the same prompt validator used by Diagnostic Chat.
- Prompt limit remains 32 ASCII characters.
- `maxOutputTokens` remains fixed at 3.
- Use the lower-level isolated path only.
- Do not call high-level `generateResponse`.

### Phase C: One Normal ChatScreen Short Run

- Permit one non-streaming short run only after Phase A and Phase B pass.
- Do not save to the normal message DB in the first implementation.
- Record result to a DEV diagnostic artifact first.
- Disable the NPU route immediately after one run until reviewed.

### Phase D: Expansion After Fallback Review

- Consider broader prompts only after timeout, failure, cleanup, memory, and
  rollback behavior are verified in the normal UI shell.
- Automatic CPU/GPU retry remains a later phase.
- Streaming, stop button integration, DB persistence, TTS, and Markdown output
  handling remain separate reviews.

## Enable Conditions

All conditions are required before any normal `ChatScreen` NPU implementation:

- `customBuildExperimentDebug` only.
- Nubia / SM8750 device check passes.
- QAIRT 2.44 aligned runtime libs are staged.
- `libcdsprpc.so` visibility is present through
  `<uses-native-library android:name="libcdsprpc.so" android:required="false" />`.
- native marker `qairt244_editable_prompt_smoke_v1` is present.
- native evidence confirms `DecodeConfig.SetMaxOutputTokens(3)` or a stricter
  cap.
- latest Diagnostic Chat success artifact is fresh.
- Diagnostic Chat fallback / timeout / recovery artifact is present and passes.
- one-shot hardening artifact is present and passes.
- memory cleanup and cold-start force-stop profiles have no fresh crash.
- large binary artifacts are not staged for Git tracking.
- DEV toggle is explicitly enabled for the current run.

## selectedPath=npu Conditions

- Default `selectedPath` remains CPU/GPU.
- `selectedPath=npu` is allowed only inside the DEV-only guarded route.
- It must not be written to normal persisted settings.
- It must not be restored automatically on app launch.
- It must be cleared or ignored after timeout, failure, crash suspicion, or
  Activity recreation.
- If any NPU preflight check fails, the NPU option is disabled for the session.

## Timeout And Failure

- Initial timeout is 30 seconds or less.
- Timeout disables the NPU route for the session.
- Engine/session cleanup is mandatory before the UI leaves running state.
- A timeout must produce a DEV artifact with prompt, cap, elapsed time, and
  cleanup result.
- Crash suspicion or a fresh tombstone disables NPU reuse until a new manual
  Diagnostic Chat verification passes.
- Duplicate success markers or residual `state=started` are treated as failure.

## Fallback Policy

Initial behavior is failure display only:

- Do not automatically retry on GPU/CPU in the same ChatScreen request.
- Do not silently switch backends.
- Show a DEV-visible failure state and artifact path.
- Leave normal chat state unchanged when the NPU route fails before output.
- Automatic GPU retry can be designed only after the failure-display path is
  proven.

## DB / TTS / Markdown / Streaming

Initial normal UI integration must keep these disabled or isolated:

- Message DB: no normal persistence for the first NPU run, or DEV-only log file
  only.
- TTS: disabled.
- Markdown pipeline: do not route NPU output through normal Markdown rendering
  until output shape and escaping are reviewed.
- Streaming: disabled.
- Stop button: not connected in the first implementation.
- Output mode: non-streaming short text only.

## Rollback Conditions

Disable the NPU route and require a new design review if any of the following
occur:

- fresh crash or fresh tombstone
- duplicate success marker
- residual `state=started`
- timeout
- memory growth warning after cleanup
- stale Diagnostic Chat summary used as fresh
- `selectedPath=npu` persists into normal settings
- UI freeze or unresponsive running state
- `Engine.close` / cleanup result is missing or ambiguous
- invalid prompt reaches native execution
- output exceeds the configured token cap

## First Implementation Candidate

Do not wire directly into the existing normal inference path. Add a DEV-only
NPU route adapter with a small surface:

- input: validated 32-character ASCII prompt
- backend: QAIRT 2.44 lower-level isolated NPU path
- token cap: 3 fixed
- output: DEV artifact first, then optional transient UI display
- no DB write
- no TTS
- no Markdown pipeline
- no streaming
- no persisted backend selection

The adapter API boundary is defined separately in:

```text
docs/litert_qairt244_dev_only_npu_route_adapter_plan.md
```

The first candidate call site is the `InferenceTarget.LOCAL` branch in
`ChatScreen.kt`, after prompt capture and before normal message persistence.
The adapter must return before user message insert, assistant message insert,
streaming placeholder updates, TTS, Markdown, and stop button ownership.

Current implementation status:

- `DevOnlyNpuRouteAdapter` schema exists in `customBuildExperimentDebug`.
- `BlockedDevOnlyNpuRouteAdapter` always returns
  `reasonCode=adapter_not_connected`.
- `DevOnlyNpuRouteGate` fixes the DEV hidden toggle and prompt/native support
  gate reasons in pure Kotlin.
- `DevOnlyNpuRoutePlanner` combines the gate and blocked adapter while still
  having no `ChatScreen` call site.
- `NpuDiagnosticChatActivity` shows a display-only
  `Planner Preview (blocked)` section. It calls the planner with a synthetic
  OK gate and the blocked adapter, so the rendered result is
  `adapter_not_connected`.
- Unit tests cover the blocked result and result schema flags.
- Gate tests cover every current failure reason.
- Planner tests cover gate-blocked no-adapter-call behavior and gate-OK
  blocked-adapter behavior.
- `DevOnlyNpuRouteDisplayModel` maps route results into transient UI
  statuses (`SUCCESS`, `BLOCKED`, `TIMEOUT`, `CRASH`, `ERROR`) before any
  normal ChatScreen integration.
- No `ChatScreen` call site exists yet.
- No NPU work is run by the adapter stub.
- The Diagnostic Chat planner preview displays
  `ChatScreen route connected=false`, `selectedPathNpuApplied=false`,
  `npuGeneration=false`, `engineInitialize=false`, and `runDecode=false`.

The transient display model is intentionally not persisted to chat history and
does not enter DB, TTS, Markdown, streaming, stop button, or selected-path
state. It is only a shape for DEV error/result rendering once a guarded
adapter branch exists.

Diagnostic Chat now applies that display model to the existing planner preview.
The current blocked adapter result is shown as:

- `status=BLOCKED`
- `reasonCode=adapter_not_connected`
- `message=NPU route adapter is not connected`

This keeps the future transient error/result appearance fixed without adding a
normal ChatScreen call site.

`DevOnlyNpuTransientPresenter` now defines the next boundary after the display
model. It turns display models into `DevOnlyNpuTransientUiState` for a future
DEV-only branch, while hard-coding:

- `shouldPersistToDb=false`
- `shouldSpeakTts=false`
- `shouldRenderMarkdown=false`
- `shouldStream=false`

Those flags remain false even for `SUCCESS`. This prevents a future initial
ChatScreen experiment from accidentally entering message persistence, TTS,
Markdown, or streaming before the explicit integration phase.

## Implementation Checklist

Before code implementation starts:

- Diagnostic Chat latest success artifact is fresh.
- fallback / timeout / recovery artifact passes.
- one-shot hardening artifact passes.
- memory cleanup profile passes.
- cold-start force-stop cleanup passes.
- native marker and token cap evidence are verified.
- large binary artifacts are not Git-tracked.
- `standard`, `npuExperiment`, `galleryStackExperiment`, and `release` are
  unaffected.
- rollback and artifact locations are documented.

## No-Run Confirmation

This planning pass did not run NPU generation, `Engine.initialize`,
`RunDecode`, high-level `generateResponse`, normal `ChatScreen` NPU routing, or
normal `selectedPath=npu`.
