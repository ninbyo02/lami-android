# QAIRT244 NPU Phase H1 Transient UI Surface

Date: 2026-05-25

Scope: documentation-only pre-implementation design for the Phase H1 transient
UI preview. This document does not implement UI code, does not run NPU, does
not call `Engine.initialize` or `RunDecode`, does not connect the standard
route, does not persist DB messages, does not call TTS, does not invoke
Markdown rendering, does not enable streaming, and does not change release or
standard behavior.

## Baseline

Phase H1 depends on the already-adopted hidden experimental baseline:

```text
case=sanitizer_only
max_output_tokens=128
baseline=enhanced_sanitizer_only_128
quality=natural_japanese
artifact=artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810
```

Native stop sequence / stop token is not required for Phase H1. The static
investigation at
`artifacts/qairt244_npu_stop_api_investigation/20260525_214513/` found no
public Android/JNI per-run stop API for this qairt244 path, so the UI surface
continues to rely on sanitized output.

## Display Location

Phase H1 may display a DEV-only transient preview inside ChatScreen.

- surface type: transient card, banner, or snackbar-like preview
- label: `DEV NPU transient preview`
- source: hidden experimental NPU result metadata
- placement: outside the persisted assistant message list
- lifetime: in-memory only
- reload behavior: may disappear on screen reload or app restart

The preview must not create or mutate a normal assistant message.

## Displayed Data

The UI surface may display only these fields:

- `sanitized_output`
- `status=success` or failure status
- `reasonCode`
- `decode_ms`
- short backend evidence label, for example `QNN_HTP_V79_FastRPC`
- `maxOutputTokens=128`
- short artifact path or artifact basename
- `DEV NPU transient preview` label

`sanitized_output` is the only model text allowed in the preview.

## Hidden Or Persisted-Only Data

These values must not be displayed as user-facing assistant text:

- `raw_output`
- `raw_native_output`
- full prompt template
- internal native diagnostics
- stack traces
- tombstone text
- full model path

`raw_output` and native diagnostics remain artifact-only evidence.

## Save And Routing Prohibitions

Phase H1 must not:

- save `selectedPath=npu` or equivalent normal-route NPU settings
- insert into conversation or message DB
- call TTS
- call Markdown renderer or repair logic
- call streaming renderer
- enter standard route selection
- enter normal local inference fallback
- connect release or standard behavior

## State Model

Recommended implementation shape:

- reuse or adapt `DevOnlyNpuTransientUiState`
- map native/result files through an explicit display model or presenter
- keep the state in debug / hidden experimental scope
- default state is not visible
- default toggle remains false

State fields should be limited to:

- `visible`
- `status`
- `reasonCode`
- `sanitizedOutput`
- `decodeMs`
- `backendEvidenceLabel`
- `maxOutputTokens`
- `artifactShortPath`
- `rollback`
- `stale`

## Visibility Rules

`visible=true` is allowed only after a successful hidden NPU run whose metadata
passes the promotion gate:

- sanitized `quality_classification=natural_japanese`
- sanitized output is non-empty
- sanitized output has no template artifact
- no repetition after sanitize
- no multilingual drift after sanitize
- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `selected_path_npu_saved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`
- artifact is fresh

If any gate field is missing or stale, the preview remains hidden.

## Clear Conditions

Clear the transient preview on:

- new user input
- navigation away from ChatScreen
- hidden / DEV NPU toggle OFF
- failure or rollback state
- app restart
- stale artifact detection
- selected-path or standard-route leakage detection

Clear means removing the in-memory preview state only. It must not delete the
diagnostic artifact unless a separate cleanup routine already owns that file.

## Refresh Rules

Refresh may read artifact metadata only.

- no NPU run
- no `Engine.initialize`
- no `RunDecode`
- no retry
- no auto fallback
- no DB reload into assistant message list

If refreshed metadata is stale, incomplete, or fails the gate, the preview is
hidden.

## Failure Display

Failure display is allowed only as a DEV-only transient diagnostic summary.

- no `sanitized_output`
- show `reasonCode`
- show status
- show short artifact path if available
- `rollback=true` should use warning/error treatment
- no retry button
- no auto fallback
- no assistant-message insertion
- no DB, TTS, Markdown, or streaming handoff

Failure text must not present raw model output as assistant content.

## Implementation Pre-Checklist

Before Phase H1 code work starts:

- DisplayModel / Presenter tests pass
- sanitizer tests pass
- standard route regression test passes
- latest baseline artifact is fresh
- staged binary check passes
- docs gate is up to date
- default toggle remains false
- hidden route turns off after guarded run
- target surface is transient only
- DB, TTS, Markdown, and streaming remain separate gates
- no native stop API dependency is introduced

## State Test Baseline - 2026-05-25

The first implementation step is limited to state/display-model/presenter tests.
It does not connect ChatScreen, does not run NPU, and does not promote the
standard route.

The H1 test baseline fixes these code-level rules:

- success with non-empty sanitized output maps to `visible=true`
- `devLabel=DEV NPU transient preview`
- `outputPreview` is populated only from `sanitizedOutput`
- `rawOutput` is accepted only as input evidence and is not copied into UI
  state
- failure maps to reason-only display with `outputPreview=null`
- rollback/gate failure maps to warning/error state with `outputPreview=null`
- empty sanitized output is hidden rollback
- standard route connection blocks preview
- side-effect flags are always false:
  `shouldPersistToDb=false`, `shouldSpeakTts=false`,
  `shouldRenderMarkdown=false`, `shouldStream=false`
- `maxOutputTokens=128`, short backend evidence, decode time, and short
  artifact path are display metadata only

This test layer is the implementation precondition for any later ChatScreen
transient surface wiring.

## Artifact Mapper Baseline - 2026-05-25

The second implementation step maps artifact result key-value metadata into
`DevOnlyNpuPhaseH1UiInput`. It still does not connect ChatScreen, run NPU,
call `Engine.initialize`, call `RunDecode`, or promote the standard route.

Mapper rules:

- accept `Map<String, String>` or key-value text
- read `sanitized_output` as the only displayable model text
- read `raw_output` only as artifact evidence and discard it before UI input
- require `result=success` for success input
- require `quality_classification=natural_japanese`
- require `npu_backend=NPU`
- require `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- require `fallback_used=false`, `timeout=false`, `fresh_crash=false`
- require `selected_path_npu_saved=false`
- require `standard_route_connected=false` and `normal_ui_route_connected=false`
- require `db=false`, `tts=false`, `markdown=false`, `streaming=false`
- require `max_output_tokens=128`

Gate failures map to rollback input with `outputPreview=null` after presenter
mapping. Failure results map to reason-only failure display. The mapper tests
cover empty sanitized output, template residue after sanitize, fallback,
timeout, fresh crash, standard route connection, DB ingress, missing NPU
evidence, max-output display, decode-ms display, and short artifact-path
display.

## Non-Goals

- no normal UI promotion
- no standard route connection
- no message DB write
- no TTS
- no Markdown
- no streaming
- no selected-path NPU persistence
- no NPU execution
- no native change
- no release or standard behavior change
