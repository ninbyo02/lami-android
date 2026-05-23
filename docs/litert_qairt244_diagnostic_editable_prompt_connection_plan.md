# QAIRT 2.44 Diagnostic Editable Prompt Connection Plan

Date: 2026-05-23

Scope: final design review before STEP 2B. This document fixes the safety
conditions for connecting the Diagnostic Chat editable prompt preview to the
guarded short NPU smoke path. It does not connect the prompt to execution and
does not run NPU generation.

Implementation update: the Android-side STEP 2B gates and runner preflight are
implemented, but editable prompt execution remains blocked because the current
QAIRT 2.44 native short multi-token entrypoint is still fixed to `prompt=Hi`.

## Current State

Completed:

- STEP 1 summary freshness indicator.
- STEP 2A editable prompt preview.
- Default Diagnostic Chat launch keeps `input_enabled=false`.
- `allowEditablePromptPreview=true` launch sets `input_enabled=true`.
- Validator OK preview was verified with `Hi`.
- Validator NG preview was verified with `Hello/LamiHi` and
  `reasonCode=contains_disallowed_char`.
- `prompt_execution_connected=false`.
- The guarded Run button still uses fixed prompt `Hi`.

Still not performed:

- editable prompt execution connection
- NPU generation from editable prompt
- Engine.initialize
- RunDecode
- normal `ChatScreen` integration
- normal `selectedPath=npu` route
- high-level `generateResponse`
- streaming generation

## Connection Enable Conditions

STEP 2B may connect the editable prompt to the guarded smoke path only when all
of these conditions are true:

- build flavor is `customBuildExperimentDebug`
- Activity extra `allowEditablePromptPreview=true`
- Activity extra `allowGuardedNpuRun=true`
- new Activity extra `allowEditablePromptExecution=true`
- DEV checkbox is checked
- `NpuDiagnosticPromptValidator.validate(input).isValid=true`
- normalized prompt length is `<=32`
- `maxOutputTokens=3` remains fixed in the lower-level native path
- `running=false`
- prompt execution status is visible as `prompt_execution_connected=true`
- native editable prompt support is present
- normal `ChatScreen` route remains disconnected
- normal `selectedPath=npu` route remains disabled

Summary freshness policy:

- `freshness_status=stale` must show a visible warning.
- stale summary is not, by itself, a hard execution block.
- `freshness_status=unknown` must also show a visible warning.
- the execution artifact must record the freshness status at run start.

Rationale: freshness describes previous runner evidence, not the current
editable prompt safety state. Blocking on stale summary would not improve native
execution safety, but hiding the warning would be misleading.

## Run Button Enable Conditions

The Run button may become enabled only when every connection condition is met.

It must remain disabled when:

- `allowEditablePromptPreview` is missing or false
- `allowGuardedNpuRun` is missing or false
- `allowEditablePromptExecution` is missing or false
- DEV checkbox is unchecked
- validator result is invalid
- prompt is empty after normalization
- prompt length is greater than 32
- a run is already active
- native artifact preflight fails
- native short multi-token entrypoint is fixed to `Hi`

UI must display:

- `prompt_execution_connected=true` only in the fully armed STEP 2B state
- `prompt_source=editable_prompt`
- validator `reasonCode`
- normalized prompt
- `maxOutputTokens=3`
- stale/unknown freshness warning when applicable

During execution:

- Run button disabled
- input field disabled
- DEV checkbox disabled
- running state visible
- double execution prevented by the existing running lock

## Execution Path

Allowed path:

- Diagnostic Chat Activity
- lower-level isolated short smoke entrypoint only
- normalized editable prompt
- native hard cap `maxOutputTokens=3`
- app-side timeout `30s`
- app-private result/native diag files

Forbidden path:

- normal `ChatScreen` ViewModel
- message database writes
- TTS
- Markdown pipeline
- normal backend selection
- normal `selectedPath` mutation
- high-level `generateResponse`
- streaming generation
- CPU/GPU fallback through normal chat route

## Result Contract

The result file must include:

- `result`
- `run_id`
- `prompt_source=editable_prompt`
- `actual_prompt`
- `normalized_prompt`
- `prompt_validation_is_valid=true`
- `prompt_validation_reasonCode=ok`
- `max_output_tokens=3`
- `output`
- `elapsed_ms`
- `prefill_elapsed_ms` when available
- `decode_elapsed_ms`
- `cleanup_elapsed_ms`
- `npu_backend=NPU`
- QNN/HTP/V79/FastRPC evidence marker when present
- freshness status at run start
- normal route disabled status
- selectedPath disabled status

The native diagnostic file must preserve:

- `SetMaxOutputTokens(3)` evidence
- lower-level RunDecode evidence
- cleanup evidence
- QNN/HTP/V79/FastRPC evidence when present

## Artifact Contract

Each STEP 2B run must write:

```text
artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/<timestamp>/
```

Required files:

- `summary.md`
- `result.txt`
- `native_diag.txt`
- `screenshot_before.png`
- `screenshot_after.png`
- `window_before.xml`
- `window_after.xml`
- `logcat_tail.txt`
- `stale_tombstone_note.md`
- `package_dump_extract.txt`
- synced runner summary snapshot
- editable prompt preview state snapshot

`summary.md` must state:

- run count: `1`
- `prompt_source=editable_prompt`
- actual prompt
- normalized prompt
- validator status
- `max_output_tokens=3`
- timeout status
- fresh/stale tombstone classification
- fresh crash status
- normal UI route status
- selectedPath route status

## Invalid Input Behavior

When input is invalid:

- Run button disabled
- no Engine.initialize
- no native session
- no RunDecode
- no NPU generation
- reason code displayed in Diagnostic Chat
- normalized prompt displayed when applicable
- app-private preview state may be updated
- read-only artifact is sufficient if this is a validation-only check

Invalid input must not create a native execution artifact that looks like a run.

## Current Preflight Classification

Current native artifact state:

- Kotlin wrapper: `Qairt244ShortMultitokenSmoke.nativeRun(...)`
- native entrypoint:
  `Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRun`
- current prompt handling: fixed `constexpr const char* kPrompt = "Hi"`
- editable prompt parameter: not present
- `supportsEditablePromptExecution()`: `false`

Therefore, `allowEditablePromptExecution=true` currently results in:

- `native_editable_prompt_supported=false`
- `prompt_execution_connected=false`
- Run disabled
- no Engine.initialize
- no RunDecode
- no NPU generation

Preflight artifact:

```text
artifacts/qairt244_npu_diagnostic_editable_prompt_guarded_run/20260523_175939/
```

Observed:

- `requested_prompt=Hi`
- `input_enabled=true`
- `editable_prompt_execution_extra=true`
- `native_editable_prompt_supported=false`
- `preflight_result=blocked_native_fixed_hi`
- `prompt_execution_connected=false`
- `run_button_connected=false`
- `run_executed=false`
- `engine_initialize=false`
- `run_decode=false`
- `npu_generation=false`

Required next native change before a real editable prompt run:

- add an editable prompt `jstring` parameter to the custom native entrypoint
- validate/null-check the prompt before native use
- write `actual_prompt`, `normalized_prompt`, and
  `prompt_source=editable_prompt` to the result file
- keep `DecodeConfig.SetMaxOutputTokens(3)` hard-coded
- rebuild the QAIRT 2.44 custom artifact and update expected IDs

## Final Pre-Implementation Checklist

Before implementing STEP 2B:

- confirm `allowEditablePromptExecution=true` is required and independent from
  `allowEditablePromptPreview=true`
- keep default Activity launch read-only
- keep `allowEditablePromptPreview=true` preview-only unless
  `allowEditablePromptExecution=true` is also present
- keep `allowGuardedNpuRun=true` required
- keep DEV checkbox required
- keep `maxOutputTokens=3` hard-capped in native code
- keep timeout at 30 seconds or tighter
- keep stale tombstone classification
- keep summary freshness warning visible
- verify invalid prompt disables Run button
- verify normal `ChatScreen` route remains untouched
- verify normal `selectedPath=npu` route remains untouched

## Non-Goals

This plan does not authorize:

- connecting editable prompt in this commit
- running NPU generation in this commit
- normal chat integration
- high-level generation APIs
- streaming generation
- token limits above 3
- release flavor changes
- `app/src/main/jniLibs` changes
