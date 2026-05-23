# QAIRT 2.44 UI Integration Safety Plan

Date: 2026-05-23

Scope: safety gates for moving from isolated QAIRT 2.44 NPU probes toward an
NPU Diagnostic Chat screen. This is not approval to connect NPU to the normal
chat UI.

## Hard Boundaries

- `customBuildExperimentDebug` only.
- No normal `ChatScreen` inference path changes.
- No `selectedPath=npu` in the normal route.
- No high-level `generateResponse`.
- No streaming generation.
- No long prompt or long output.
- No `standard`, `galleryStackExperiment`, `npuExperiment`, or `release`
  behavior changes.
- No `app/src/main/jniLibs` changes.
- No packaged or tracked vendor `libcdsprpc.so`.

## Diagnostic Screen Gates

The diagnostic screen may display existing artifacts immediately, but any
future run action must pass all gates:

1. The current flavor is exactly `customBuildExperiment`.
2. The entrypoint is not reachable from normal navigation.
3. Prompt length is bounded before native code is called.
4. `maxOutputTokens` is hard-capped in native code.
5. The runner has a host-side timeout.
6. Timeout kills only the `io.github.ninbyo02.lami.customnpu` process.
7. `Engine.close` or native cleanup is recorded.
8. Result and diagnostic files are pulled after success and failure.
9. Tombstones are classified as fresh or stale by run id.
10. The result is never written to normal chat messages, DB, TTS, or Markdown
    rendering.

## Timeout And Recovery

Recommended first diagnostic-chat run policy:

- host timeout: 15 to 30 seconds
- app-side timeout: shorter than host timeout if implemented
- on host timeout: kill `io.github.ninbyo02.lami.customnpu`
- always collect:
  - result file
  - native diag file
  - stage file
  - logcat tail
  - tombstone diagnostics
  - package dump
- stale tombstones must not fail a successful current run unless the current
  run id is present in the tombstone.

## Diagnostic Chat Guarded Button

2026-05-23 update: `NpuDiagnosticChatActivity` now exposes a
`customBuildExperimentDebug`-only guarded `Run 3-token smoke` control. The
default launch remains read-only because the button is disabled until the
developer checks `DEV confirm isolated 3-token NPU smoke`.

The button is constrained to:

- prompt `Hi`
- `maxOutputTokens=3`
- isolated lower-level `Qairt244ShortMultitokenSmoke.run(...)`
- app-side running lock
- app-side 30 second timeout marker
- app-private result file only

It still does not use `ChatScreen`, normal message DB writes, TTS, Markdown,
`selectedPath=npu`, or high-level `generateResponse`. Host-side timeout,
process kill, logcat, and tombstone artifact collection remain the authoritative
path for evidence-producing runs.

Guarded UI smoke verification:

- artifact:
  `artifacts/qairt244_npu_diagnostic_chat_guarded_ui_run/20260523_100701/`
- result: `success`
- output: `! How Hi`
- hard cap: `max_output_tokens=3`
- native evidence: `QNN_HTP_V79_FastRPC_native_diag`
- tombstone classification: `stale-tombstone-ignored`

The UI path remains diagnostic-only. It must not be promoted to the normal chat
route without a separate integration review and host-side recovery plan.

Automated guarded UI runner:

- script:
  `scripts/run_qairt244_npu_diagnostic_chat_ui_smoke.sh`
- artifact:
  `artifacts/qairt244_npu_diagnostic_chat_ui_smoke/20260523_102810/`
- result: `success`
- output: `! How Hi`
- hard cap: `max_output_tokens=3`
- UI operations: `ui_dev_checkbox_taps=1`, `ui_run_button_taps=1`
- timeout: `false`
- native evidence: `QNN_HTP_V79_FastRPC_native_diag`
- tombstone classification: `stale-tombstone-ignored`

The runner is the preferred reproducible path for Diagnostic Chat UI evidence.
It selects a non-emulator device, builds/installs `customBuildExperimentDebug`,
launches only `NpuDiagnosticChatActivity`, drives the DEV checkbox and guarded
run button once, captures screenshots/window dumps, pulls app-private result
files, and runs the tombstone collector. Timeout handling force-stops only the
`customnpu` package and records the timeout in the artifact.

Multi-run runner update:

- script:
  `scripts/run_qairt244_npu_diagnostic_chat_ui_multirun.sh`
- fixed run count: `2`
- prompt: `Hi`
- hard cap: `maxOutputTokens=3`
- latest attempt artifact:
  `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_110017/`

The first multi-run attempt captured successful NPU outputs for both requested
runs, but it also exposed a host runner wait bug: the script stopped on any
earlier `state=success` marker rather than the last guarded UI marker. The
script now waits on the last marker state and treats `state=started` as still
running. This is a host artifact issue, not a normal UI integration change.
The normal `ChatScreen`, message DB, TTS, Markdown path, and normal
`selectedPath=npu` route remain untouched.

Fixed runner verification:

- artifact:
  `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/`
- run count: `2`
- run1: `result=success`, final guarded marker `state=success`
- run2: `result=success`, final guarded marker `state=success`
- final `state=started`: `false` for both runs
- timeout: `false`
- fresh crash: `false`
- native evidence: `QNN_HTP_V79_FastRPC_native_diag`

This confirms the corrected host runner waits for the actual final guarded
marker state before classifying a Diagnostic Chat UI run as complete.

Result viewer refresh:

- screen: `customBuildExperimentDebug` `NpuDiagnosticChatActivity`
- latest evidence shown:
  `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/`
- read-only fields include run1/run2 result, output, elapsed,
  decode elapsed, final guard marker state, `state=started` final-state
  status, after-10s TOTAL PSS/Native Heap PSS, tombstone classification, and
  fresh crash status
- route guard fields explicitly show normal `ChatScreen` route disabled and
  normal `selectedPath=npu` disabled
- `Refresh result view` only rereads app-private files or committed latest
  verification values
- Refresh does not call the lower-level smoke entrypoint, does not initialize
  the engine, and does not generate tokens
- optional synced source:
  `/data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_diagnostic_runner_summary.txt`
- host sync script:
  `scripts/sync_qairt244_npu_diagnostic_summary_to_app.sh`
- the sync script copies key-value summary data only and records
  `npu_generation=not_run`, `engine_initialize=not_run`, `run_decode=not_run`,
  and `activity_launch=not_run`

The existing DEV checkbox and guarded run button remain the only UI path that
can trigger the isolated 3-token smoke. The normal chat route remains
disconnected.

Read-only summary display verification:

- artifact:
  `artifacts/qairt244_npu_diagnostic_summary_readonly_verify/20260523_122946/`
- displayed source: `app_private_file`
- Activity launch state: `COLD`
- DEV checkbox: not pressed
- guarded run button: not pressed
- NPU generation: not run
- Engine.initialize: not run
- RunDecode: not run
- normal `ChatScreen`: not connected
- normal `selectedPath=npu`: not used

Summary freshness indicator:

- artifact:
  `artifacts/qairt244_npu_diagnostic_summary_freshness/20260523_124234/`
- threshold: `86400` seconds / 24 hours
- displayed fields:
  `synced_at_local`, `source_artifact_timestamp`,
  `source_artifact_age_human`, `freshness_status`, and
  `freshness_warning`
- observed status: `fresh`
- observed warning: `none`
- DEV checkbox: not pressed
- guarded run button: not pressed
- NPU generation: not run
- Engine.initialize: not run
- RunDecode: not run

ChatScreen disabled blocked branch verification:

- artifact:
  `artifacts/qairt244_chat_screen_blocked_branch_disabled_verify/20260523_215825/`
- build/install:
  `customBuildExperimentDebug`
- launch:
  normal `MainActivity` / `ChatScreen`
- toggle:
  `DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED=false`
- blocked branch:
  not fired
- blocked Snackbar / `adapter_not_connected`:
  not observed on normal launch
- normal `selectedPath=npu`:
  not applied
- NPU generation:
  not run
- `Engine.initialize`:
  not run
- `RunDecode`:
  not run

Short prompt DEV guard spec:

- spec:
  `docs/litert_qairt244_diagnostic_short_prompt_guard_spec.md`
- validator implementation:
  `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticPromptValidator.kt`
- implementation status: editable preview only, enabled exclusively by
  `allowEditablePromptPreview=true`
- prompt maximum: 32 characters
- allowed characters: ASCII letters, digits, space, `. , ? ! ' - _`
- rejected: empty prompt, newline, tab/control characters, emoji, non-ASCII
  symbols
- hard generation cap: `maxOutputTokens=3`
- timeout: 30 seconds
- required guards: DEV checkbox, explicit confirmation, running lock, prompt
  disabled while running
- fallback: Diagnostic Chat-local failure only, no normal UI fallback
- normal `ChatScreen`, normal `selectedPath=npu`, high-level
  `generateResponse`, and streaming remain forbidden
- validator is not connected to Run button or native execution in this phase
- default preview-only UI displays the fixed `Hi` validation result with
  `input_enabled=false`
- editable preview launch displays `input_enabled=true` and updates validator
  status on text changes
- route guard display includes `prompt_input_execution=disabled` and
  `editable_prompt_phase=preview_only`
- default Diagnostic Chat launch leaves the guarded Run button disabled and
  disconnected unless `allowGuardedNpuRun=true` is explicitly supplied
- `allowEditablePromptPreview=true` is separate from `allowGuardedNpuRun=true`;
  it does not make the Run button read the prompt field
- verification artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_preview/20260523_133833/`
- verified OK preview: `Hi`, `reasonCode=ok`
- verified NG preview: `Hello/LamiHi`,
  `reasonCode=contains_disallowed_char`
- verified execution guards:
  `prompt_execution_connected=false`, `run_button_uses_fixed_prompt=Hi`,
  `npu_generation=false`, `engine_initialize=false`, `run_decode=false`
- existing Diagnostic Chat UI runner scripts pass this explicit extra when they
  intentionally run guarded smoke tests; read-only launches omit it

Editable prompt execution connection plan:

- plan:
  `docs/litert_qairt244_diagnostic_editable_prompt_connection_plan.md`
- STEP 2B requires a new explicit `allowEditablePromptExecution=true` Activity
  extra
- this extra is separate from `allowEditablePromptPreview=true` and
  `allowGuardedNpuRun=true`
- Run button may enable only when all extras are present, DEV checkbox is
  checked, validator status is valid, prompt length is within 32 characters,
  `maxOutputTokens=3` remains fixed, native editable prompt support exists,
  and no run is active
- invalid prompt keeps Run disabled and starts no native execution
- current native prompt support: absent; Android-side gate reports
  `native_editable_prompt_supported=false` and keeps Run disabled
- preflight artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_175939/`
- stale summary freshness is a visible warning, not a hard execution block
- normal `ChatScreen`, normal `selectedPath=npu`, high-level
  `generateResponse`, DB writes, TTS, and Markdown remain forbidden

## Fallback Policy

Fallback is diagnostic-only:

- If NPU fails, show failure in the diagnostic screen.
- Do not fall back into the normal GPU/CPU chat path.
- Do not silently retry with a different backend.
- Do not automatically increase output tokens.
- Do not run a second generation in the same action.

## Artifact Policy

Each diagnostic run should create a timestamped directory under:

```text
artifacts/qairt244_npu_diagnostic_chat/<timestamp>/
```

Minimum files:

- `summary.md`
- `result.txt`
- `native_diag.txt`
- `stage_file.txt`
- `logcat_tail.txt`
- `stale_tombstone_note.md`
- diagnostics collector output

Large copied APK native libraries should remain untracked unless explicitly
needed for a static comparison artifact.

## 2-3 Token Smoke Gate

Before moving beyond one token:

- one-token verifier remains reproducible after the diagnostic screen is added
- diagnostic screen remains read-only or guarded
- token cap is set to `2` or `3` in a new isolated native marker
- prompt remains fixed and short
- timeout remains unchanged or tighter
- memory and cleanup are recorded
- no normal UI state is touched
- docs identify the exact new risk being tested

## Normal UI Integration Gate

Normal UI integration is a later phase and requires a separate decision. Before
that phase:

- isolated diagnostic screen must complete multiple bounded runs without fresh
  crash evidence
- cleanup must be reliable
- fallback behavior must be explicit and user-visible
- memory pressure must be measured
- normal chat state mutation must be reviewed independently

## Editable Prompt Guarded Run Update - 2026-05-23

STEP 2B is now implemented for the Diagnostic Chat only. It remains isolated
from normal chat.

Execution gates:

- `customBuildExperimentDebug`
- `allowEditablePromptPreview=true`
- `allowGuardedNpuRun=true`
- `allowEditablePromptExecution=true`
- DEV checkbox checked
- `NpuDiagnosticPromptValidator` valid
- native marker `qairt244_editable_prompt_smoke_v1`
- native `DecodeConfig.SetMaxOutputTokens(3)` evidence

The guarded run artifact is:

```text
artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_184901/
```

Result:

- prompt `Hello` was normalized to `Hello`
- `prompt_source=editable_prompt`
- `result=success`
- `max_output_tokens=3`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fresh_crash=false`

Safety note: the initial UI runner observed two success markers in one Activity
session. The Activity now clears the DEV confirmation and leaves the Run button
disabled after completion. Do not proceed to normal `ChatScreen` integration
until this one-shot UI lock is reverified in a separate run.

One-shot reverify:

```text
artifacts/qairt244_npu_diagnostic_editable_prompt_one_shot_verify/20260523_191757/
```

Outcome:

- exactly one guarded success marker
- no residual `state=started`
- duplicate success marker: `false`
- DEV checkbox off after completion
- Run button disabled after completion
- fresh crash: `false`
- normal `ChatScreen` route and normal `selectedPath=npu` route untouched

STEP 3 can proceed as a Diagnostic Chat-only fallback / timeout / recovery
verification. Normal ChatScreen integration remains out of scope.

STEP 3 result:

```text
artifacts/qairt244_npu_diagnostic_fallback_recovery/20260523_193405/
```

Verified:

- invalid prompt does not enable Run
- native unsupported preflight remains blocked before NPU work
- timeout simulation does not call Engine.initialize or RunDecode
- timeout recovery clears DEV confirmation and disables Run
- refresh after timeout does not reconnect normal UI
- fresh crash evidence: none

Normal ChatScreen integration remains a separate design phase.

## Normal ChatScreen NPU Integration Design - 2026-05-23

The normal ChatScreen NPU route remains unimplemented. The design boundary is
now captured in:

```text
docs/litert_qairt244_chat_screen_npu_integration_plan.md
```

Key safety constraints:

- `customBuildExperimentDebug` only
- DEV hidden toggle required
- default `selectedPath` remains CPU/GPU
- normal `selectedPath=npu` is never persisted or auto-restored
- latest Diagnostic Chat success and fallback/recovery artifacts must be fresh
- `maxOutputTokens` starts fixed at 3
- initial normal UI path is one non-streaming short run only
- DB, TTS, Markdown, streaming, and stop button integration are out of scope
  for the first implementation
- timeout, fresh crash, duplicate marker, stale summary, memory warning, UI
  freeze, or unclear cleanup disables the NPU route

This pass is documentation only. It does not connect normal `ChatScreen`, does
not set normal `selectedPath=npu`, and does not run NPU generation.

## DEV-Only NPU Route Adapter Boundary - 2026-05-23

The adapter boundary for any first normal ChatScreen experiment is documented
in:

```text
docs/litert_qairt244_dev_only_npu_route_adapter_plan.md
```

The planned adapter is a `customBuildExperimentDebug`-only `runOnce` API with
`maxOutputTokens=3` and a structured result. Its first candidate call site is
inside the `InferenceTarget.LOCAL` send branch before normal DB persistence.
It must stay detached from message DB writes, TTS, Markdown processing,
streaming partials, stop button ownership, high-level `generateResponse`, and
normal `selectedPath=npu`.

This pass is design only and executed no NPU work.

## DEV-Only NPU Route Adapter Stub - 2026-05-23

The `customBuildExperimentDebug` source set now contains a blocked adapter
schema:

- `DevOnlyNpuRouteAdapter`
- `DevOnlyNpuRouteResult`
- `BlockedDevOnlyNpuRouteAdapter`

The blocked implementation always returns `success=false` and
`reasonCode=adapter_not_connected`. It has no `ChatScreen` call site and does
not call NPU generation, `Engine.initialize`, `RunDecode`, high-level
`generateResponse`, normal `selectedPath=npu`, DB, TTS, Markdown, streaming, or
stop button code.

Unit tests verify the default `maxOutputTokens=3`, nullable fields, timeout and
fresh crash flags, and blocked result mapping.

## DEV-Only NPU Route Gate - 2026-05-23

`DevOnlyNpuRouteGate` now fixes the pure Kotlin gate before any ChatScreen
integration. It requires:

- `customBuildExperiment`
- editable prompt preview allowed
- guarded NPU run allowed
- editable prompt execution allowed
- DEV checkbox checked
- validator valid
- native editable prompt supported
- not already running
- `maxOutputTokens=3`

The gate exposes typed failure reasons and has unit coverage for every
rejection path. It is not connected to `ChatScreen` and runs no NPU work.

## DEV-Only NPU Route Planner - 2026-05-23

`DevOnlyNpuRoutePlanner` now composes the gate and adapter without any
`ChatScreen` call site:

- gate NG returns `reasonCode=gate_blocked:<REASON>`
- gate NG does not call the adapter
- gate OK calls the adapter
- the default adapter remains `BlockedDevOnlyNpuRouteAdapter`
- gate OK therefore still returns `reasonCode=adapter_not_connected`

Unit tests verify that blocked gates do not call the adapter and that the
planner does not call Engine.initialize or RunDecode by itself.

## DEV-Only Planner Result UI Boundary - 2026-05-23

`NpuDiagnosticChatActivity` now renders a small `Planner Preview (blocked)`
section. It is a UI boundary only:

- gate input is synthetic and OK
- adapter is explicitly `BlockedDevOnlyNpuRouteAdapter`
- prompt is `Hello`
- `maxOutputTokens=3`
- result is `success=false`
- reason is `adapter_not_connected`

The preview also renders the safety status:

- normal ChatScreen route is not connected
- normal `selectedPath=npu` is not applied
- NPU generation is false
- `Engine.initialize` is false
- `RunDecode` is false
- high-level `generateResponse` is false
- DB, TTS, Markdown, and streaming are false

Refresh may recompute this blocked planner preview, but it cannot reach native
NPU execution because no real NPU adapter is installed.

## DEV-Only Route Transient Display Model - 2026-05-23

`DevOnlyNpuRouteDisplayModel` now defines the transient result/error shape for
a future DEV-only ChatScreen branch. It is pure Kotlin and has no normal UI
call site.

Classification is fixed before integration:

- success -> `SUCCESS`
- adapter or gate block -> `BLOCKED`
- timeout -> `TIMEOUT`
- fresh crash evidence -> `CRASH`
- other failure -> `ERROR`

The model preserves output, reason code, elapsed/decode timing text, backend
evidence text, and artifact text. It remains detached from DB persistence, TTS,
Markdown, streaming, stop button ownership, selected-path state, and native
NPU execution.

## Display Model Diagnostic Preview - 2026-05-23

The Diagnostic Chat `Planner Preview (blocked)` section now uses the display
model rather than raw planner fields. The current blocked adapter preview is:

- title: `DEV NPU route blocked`
- status: `BLOCKED`
- message: `NPU route adapter is not connected`
- reason: `adapter_not_connected`
- output: `none`

The preview continues to display route isolation flags:

- `ChatScreen route connected=false`
- `selectedPathNpuApplied=false`
- `npuGeneration=false`
- `engineInitialize=false`
- `runDecode=false`

No normal UI route or native NPU execution is introduced by this display-only
change.

## DEV-Only Transient Presenter - 2026-05-23

`DevOnlyNpuTransientPresenter` now provides the ViewModel-independent boundary
between a DEV-only NPU result and any future ChatScreen transient UI display.

It maps `DevOnlyNpuRouteDisplayModel` to `DevOnlyNpuTransientUiState` and
always keeps integration side effects disabled:

- `shouldPersistToDb=false`
- `shouldSpeakTts=false`
- `shouldRenderMarkdown=false`
- `shouldStream=false`

This applies to `SUCCESS`, `BLOCKED`, `TIMEOUT`, `CRASH`, and `ERROR`. The
presenter does not call the planner or adapter, does not run native NPU work,
and has no normal ChatScreen call site.

## Transient Presenter Diagnostic Preview - 2026-05-23

The Diagnostic Chat `Planner Preview (blocked)` section now renders the
presenter output in addition to the display model. The current blocked adapter
state is visible as:

- `transient_visible=true`
- `transient_status=BLOCKED`
- `transient_message=NPU route adapter is not connected`
- `transient_reasonCode=adapter_not_connected`
- `shouldPersistToDb=false`
- `shouldSpeakTts=false`
- `shouldRenderMarkdown=false`
- `shouldStream=false`

This is still a read-only Diagnostic Chat preview and does not introduce a
normal ChatScreen route.

## ChatScreen Disabled Blocked Branch - 2026-05-23

`ChatScreen.kt` now has a disabled DEV-only NPU blocked branch at the future
candidate location:

```text
app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt:2349
```

The branch is deliberately placed after prompt capture and blank validation,
but before input clearing, DB persistence, TTS cleanup, Markdown, streaming,
stop-button ownership, and selected-path persistence.

Runtime behavior remains unchanged because
`DEV_ONLY_NPU_CHATSCREEN_BLOCKED_BRANCH_ENABLED=false`. The disabled true path
uses reflection to a `customBuildExperimentDebug` target and only returns a
blocked transient Snackbar summary:

- `status=BLOCKED`
- `reason=adapter_not_connected`
- `db=false`
- `tts=false`
- `markdown=false`
- `stream=false`

The main `ChatScreen` still has no NPU package import and no real NPU adapter.
