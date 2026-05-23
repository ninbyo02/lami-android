# QAIRT 2.44 Diagnostic Short Prompt DEV Guard Spec

Date: 2026-05-23

Scope: STEP 2 preparation only. This document defines the requirements for the
`customBuildExperimentDebug` Diagnostic Chat short prompt input. STEP 2A enables
editing only when the explicit `allowEditablePromptPreview=true` Activity extra
is supplied. The edited value is used only for validator preview and does not
run NPU generation.

## Current State

Completed:

- STEP 1 summary freshness indicator is implemented and read-only verified.
- Diagnostic Chat can display synced runner results from app-private files.
- NPU initialize, 1-token smoke, 3-token smoke, UI smoke, UI multi-run, memory
  cleanup, and cold-start cleanup have diagnostic evidence.
- The short prompt validator is implemented as a pure Kotlin
  `customBuildExperimentDebug` helper and is covered by unit tests.

Still prohibited in this phase:

- connecting editable short prompt input to execution
- NPU generation
- Engine.initialize
- RunDecode
- normal `ChatScreen` NPU wiring
- normal `selectedPath=npu` route
- high-level `generateResponse`
- streaming generation

## Allowed Surface

The future short prompt input may only exist in:

```text
customBuildExperimentDebug NpuDiagnosticChatActivity
```

It must remain isolated from:

- normal `ChatScreen`
- message database writes
- TTS
- Markdown pipeline
- normal backend selection
- normal `selectedPath`
- release, standard, galleryStackExperiment, and npuExperiment variants

## Prompt Constraints

Initial prompt:

```text
Hi
```

Validation rules:

- maximum length: 32 Unicode scalar values
- minimum length: 1 non-whitespace character
- newline characters: rejected
- tab and other control characters: rejected
- emoji and non-ASCII symbols: rejected for the first editable phase
- leading/trailing whitespace: trim before validation
- internal repeated spaces: allowed, but no semantic normalization
- prompt must be stored in artifact/result files exactly after trimming

Allowed character class for the first editable phase:

```text
A-Z a-z 0-9 space . , ? ! ' - _
```

If validation fails, the UI must show an error in the Diagnostic Chat screen and
must not call the native smoke entrypoint.

Validator implementation:

```text
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticPromptValidator.kt
```

Unit test:

```text
app/src/testCustomBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticPromptValidatorTest.kt
```

The validator returns:

- `isValid`
- `normalizedPrompt`
- `reasonCode`
- `message`

Reason codes:

- `ok`
- `empty`
- `too_long`
- `contains_newline`
- `contains_tab`
- `contains_control_char`
- `contains_non_ascii`
- `contains_disallowed_char`

The validator is connected to the Diagnostic Chat preview field only when the
Activity is launched with `allowEditablePromptPreview=true`. It is not connected
to the guarded Run button or native execution in this phase.

## Preview-Only UI Status

The Diagnostic Chat screen now shows a prompt preview and validation result for
the fixed initial `Hi` prompt.

Default launch:

- section: `Short prompt input preview`
- displayed value: `Hi`
- `input_enabled=false`
- `preview_only=true`
- `isValid=true`
- `reasonCode=ok`
- `normalizedPrompt=Hi`
- `run_button_connected=false`
- `npu_generation=false`
- guarded run button requires `allowGuardedNpuRun=true` intent extra before it
  can be connected

Editable preview launch:

```bash
adb shell am start \
  -n io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity \
  --ez allowEditablePromptPreview true
```

- `input_enabled=true`
- `editable_prompt_preview=true`
- `max length=32`
- `singleLine=true`
- validation result updates after each text change
- valid input shows `reasonCode=ok`
- invalid input shows the specific validator `reasonCode`
- app-private verification mirror:
  `files/qairt244_editable_prompt_preview_state.txt`

This remains display-only. The edited prompt is not read by the guarded Run
button, the guarded Run button still uses fixed `Hi`, and default Activity
launch keeps the input disabled.

## Generation Limits

Hard limits:

- backend: NPU diagnostic-only lower-level isolated path
- `maxOutputTokens=3` fixed in native code
- timeout: 30 seconds
- streaming: disabled
- high-level `generateResponse`: forbidden
- normal fallback to CPU/GPU chat path: forbidden
- automatic retry: forbidden
- multiple generations per button press: forbidden

The UI may pass the validated prompt only to a future isolated native entrypoint
that still enforces `maxOutputTokens=3`. Kotlin-side validation alone is not
sufficient.

## DEV Guard

Required controls:

- DEV checkbox must be checked
- explicit short prompt confirmation must be visible
- Run button disabled while validation fails
- Run button disabled while a run is active
- running lock prevents double execution
- prompt field disabled during execution
- timeout handler records timeout status
- completion re-enables controls only after cleanup state is recorded

The existing read-only Refresh remains separate. Refresh must not trigger
validation, Engine.initialize, RunDecode, or generation.

## Artifact Requirements

Each future short prompt run must create a timestamped artifact directory and
must collect:

- `summary.md`
- result file with prompt, output, `max_output_tokens`, elapsed, and status
- native diag tail
- stage file
- logcat tail
- stale/fresh tombstone note
- package dump extract
- screenshot before and after if UI-driven
- synced runner summary / freshness fields

The app-private files must include enough information to distinguish the run:

- run id
- prompt after trimming
- prompt validation status
- `max_output_tokens=3`
- timeout status
- final guarded marker state
- normal route disabled status
- selectedPath disabled status

## Failure Policy

On validation failure:

- do not call native code
- do not start Engine.initialize
- do not create a native session
- do not run RunDecode
- show a Diagnostic Chat-local error

On runtime failure:

- record failure in app-private result
- collect diagnostics
- do not fallback to normal chat route
- do not silently retry
- do not increase token count
- do not update normal chat state

## Pre-Enable Checklist

Before enabling editable prompt input:

- input length validation implemented
- allowed character validation implemented
- newline/control character rejection implemented
- validator tests pass for accepted and rejected examples
- native path still hard-caps `maxOutputTokens=3`
- timeout remains 30 seconds or tighter
- artifact collection script updated
- stale/fresh tombstone classification preserved
- summary freshness fields preserved
- DEV checkbox guard preserved
- explicit confirmation text present
- Run button lock verified
- prompt field disabled during run
- normal `ChatScreen` untouched
- normal `selectedPath=npu` untouched
- high-level `generateResponse` not referenced
- release and `app/src/main/jniLibs` untouched

STEP 2A completion criteria:

- default Activity launch keeps `input_enabled=false`
- `allowEditablePromptPreview=true` launch sets `input_enabled=true`
- validator OK and NG states are visible in the Diagnostic Chat preview
- `prompt_execution_connected=false`
- `run_button_uses_fixed_prompt=Hi`
- no Engine.initialize, RunDecode, or NPU generation is invoked

STEP 2A verification artifact:

```text
artifacts/qairt244_npu_diagnostic_editable_prompt_preview/20260523_133833/
```

The verification recorded both the OK state for `Hi` and an NG preview state
for `Hello/LamiHi` with `reasonCode=contains_disallowed_char`. The NG check was
preview-only and did not invoke native execution.

## STEP 2B Connection Gate

The final connection plan is fixed in:

```text
docs/litert_qairt244_diagnostic_editable_prompt_connection_plan.md
```

Editable prompt execution remains disabled until a later implementation adds and
checks all of these explicit gates:

- `allowEditablePromptPreview=true`
- `allowGuardedNpuRun=true`
- `allowEditablePromptExecution=true`
- DEV checkbox checked
- validator `isValid=true`
- normalized prompt length `<=32`
- `running=false`
- native hard cap `maxOutputTokens=3`
- native editable prompt support

When invalid input is present, the Run button must remain disabled, the
validator `reasonCode` must be displayed, and no Engine.initialize, native
session, RunDecode, or NPU generation may start.

Current STEP 2B implementation status:

- Android-side extra `allowEditablePromptExecution=true` is recognized.
- The current native short multi-token entrypoint is fixed to `Hi`.
- `supportsEditablePromptExecution()` returns `false`.
- Run remains disabled in editable execution mode until a rebuilt native
  entrypoint can accept an editable prompt parameter.
- preflight artifact:
  `artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_175939/`

## Non-Goals

This spec does not authorize:

- normal ChatScreen integration
- selectedPath-based NPU route
- high-level generation APIs
- streaming generation
- long prompts
- token limits above 3
- fallback to CPU/GPU normal chat
- release flavor changes
