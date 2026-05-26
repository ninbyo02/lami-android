# QAIRT244 NPU Phase H1 Transient UI Surface

## Max Output Tokens 512 Force-Stop Between Prompts - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_force_stop_between_prompts/20260527_074002/`

The per-run force-stop comparison produced complete sanitized responses for
all three hidden prompts, including `useful_code` for the Python calculator
prompt with indentation preserved and a closed code fence. The run also
recorded cleanup/`Engine.close` evidence and no after-10s retained process
after each post-run force-stop.

H1 impact: no change. This artifact is hidden experimental evidence for a
possible per-run isolated 512 mode only. H1 remains pinned to
`sanitizer_only + max_output_tokens=128`, and 512 output must not feed the
transient card, Compose adapter, PreviewHost, normal ChatScreen, assistant
message list, DB, TTS, Markdown renderer, streaming, or selected-path
persistence.

## Max Output Tokens 512 Repeated Code Timeout Review - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_code_timeout_root_cause_review/20260527_065926/`

The repeated 512 code prompt timeout confirms there is no new H1 display input.
The code-aware sanitizer is necessary for code display quality, but the
sequential 512 code prompt did not return a completed sanitized response to
display. The issue is now tracked as 512 code-prompt instability in sequential
hidden runs.

H1 impact: no change. H1 remains pinned to
`sanitizer_only + max_output_tokens=128`. The 512 artifacts must not feed the
transient card, Compose adapter, PreviewHost, normal ChatScreen, or assistant
message list.

## Max Output Tokens 512 Code-Aware Rerun - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_three_prompt_codeaware_compare/20260527_014523/`

The code-aware 512 rerun is not H1 display input. The code prompt timed out and
did not produce a completed sanitized code response, even though the two
Japanese prompts succeeded. Raw output remains diagnostic-only and the normal
ChatScreen, assistant list, DB, TTS, Markdown renderer, streaming, and
selected-path persistence remain disconnected.

H1 impact: no change. H1 remains pinned to
`sanitizer_only + max_output_tokens=128`. 512 cannot feed the transient card,
Compose adapter, PreviewHost, or normal ChatScreen from this artifact.

## Code-Aware Sanitizer Update - 2026-05-27

Artifact:
`artifacts/qairt244_code_aware_sanitizer_review/20260527_012650/`

The hidden-route sanitizer now preserves indentation inside fenced code blocks
and completes an unclosed fence in derived sanitized text when truncation cuts a
response before the closing fence. Raw output remains diagnostic-only and must
not be used as H1 display input.

H1 impact: no change. This is a prerequisite display-quality fix, not a
promotion. H1 stays pinned to `sanitizer_only + max_output_tokens=128` until a
fresh full 512 hidden comparison and a separate H1 gate revision pass.

## Max Output Tokens 512 Code Display Quality Review - 2026-05-27

Artifact:
`artifacts/qairt244_npu_512_code_output_quality_review/20260527_011217/`

The 512 bounded retry remains single-prompt evidence only. It proves the code
prompt can return with QNN evidence and cleanup under a 60 second bound, but it
does not provide H1 display-ready text. The sanitized output loses Python
indentation and has an unclosed code fence due to token-limit truncation.

H1 impact: no change. H1 stays pinned to
`sanitizer_only + max_output_tokens=128`. The 512 code output must not feed the
transient card, Compose adapter, PreviewHost, normal ChatScreen, or assistant
message list. A future H1 gate revision must include a code display quality
gate before any 512 artifact can be considered display input.

## Max Output Tokens 512 Three-Prompt Hidden Comparison - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_three_prompt_compare/20260527_003429/`

The 512 three-prompt hidden comparison is rollback-only for H1 purposes. The
Japanese greeting and short NPU explanation prompts completed as sanitized
`natural_japanese`, but the Python calculator prompt timed out and produced no
completed sanitized code output.

H1 impact: no change. H1 remains pinned to
`sanitizer_only + max_output_tokens=128`. The 512 artifact must not feed the
transient card, Compose adapter, PreviewHost, normal ChatScreen, or assistant
message list. A later H1 gate revision still requires a fully passing hidden
baseline artifact before any display contract can change.

Timeout review:
`artifacts/qairt244_npu_max_output_512_code_timeout_review/20260527_005112/`
classifies the Python code prompt as native no-return/no-callback before the
bounded runner deadline, with cleanup unknown. This reinforces the H1 decision:
512 must not be displayed or surfaced. The H1 contract stays 128-only until a
separate fully passing baseline and H1-specific gate revision exist.

Bounded retry:
`artifacts/qairt244_npu_max_output_512_code_bounded_retry/20260527_010116/`
shows the same Python prompt can return at 512 with a 60 second bounded timeout
and cleanup evidence. H1 impact remains unchanged: the retry is single-prompt
evidence only, the code response is long/truncated with indentation loss after
sanitize, and it must not feed the transient card, Compose adapter, PreviewHost,
normal ChatScreen, or assistant message list.

## Max Output Tokens 512 Single Prompt - 2026-05-27

Artifact:
`artifacts/qairt244_npu_max_output_512_single_prompt/20260527_002303/`

The first 512 hidden runtime verification passed for the single prompt
`こんにちは`: `RunDecode` reached, QNN/HTP/FastRPC evidence present,
`fallback_used=false`, `timeout=false`, `fresh_crash=false`, and sanitized
quality `natural_japanese`. Sanitized output was
`こんにちは！何かお手伝いできることはありますか？`.

H1 impact: no change. H1 remains pinned to the existing
`sanitizer_only + max_output_tokens=128` metadata contract. The 512 artifact is
evidence only for the next hidden three-prompt comparison and must not be used
as H1 display input until a later H1-specific gate explicitly changes the
contract.

## Max Output Tokens 256 Guard Preflight - 2026-05-26

Status: 256 guard-only patch built; run not executed.

Artifacts:

- build/static artifact:
  `artifacts/qairt244_editable_prompt_max256_entrypoint_build/20260526_204155/`
- preflight artifact:
  `artifacts/qairt244_npu_max256_guard_preflight/20260526_205300/`

This is a static guard check only: it does not run NPU generation,
`Engine.initialize`, or `RunDecode`, and it does not connect ChatScreen, DB,
TTS, Markdown, streaming, or selected-path persistence.

H1 remains pinned to `sanitizer_only + max_output_tokens=128` until a separate
256 runtime artifact passes the normal quality, memory, crash, fallback, and
NPU evidence gates.

## Max Output Tokens 256 Single Prompt - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_single_prompt/20260526_211046/`

The first single-prompt 256 hidden runtime verification passed for
`こんにちは`, but H1 remains pinned to the 128 baseline. The sanitized output was
safe natural Japanese, while raw output still contained turn artifacts that
were removed by sanitizer. This is enough to proceed to a 256 three-prompt
hidden comparison, not enough to change H1 display eligibility.

## Max Output Tokens 256 Three-Prompt Hidden Comparison - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/`

The hidden 256 comparison passed all three prompts once each with sanitized
quality `natural_japanese`, `useful_code`, and `natural_japanese`. Safety flags
remained false for fallback, timeout, fresh crash, selected-path persistence,
standard route connection, normal UI route connection, assistant-list
insertion, DB, TTS, Markdown, and streaming.

H1 impact: no change. The transient card, metadata mapper, PreviewHost, and
Compose adapter remain pinned to fresh 128-token metadata. The 256 artifact is
only evidence for future hidden token expansion; it must not be used as an H1
display input until the H1 gate is explicitly revised.

Result commit decision: 256 is a hidden experimental baseline candidate, not an
H1 display baseline. The Python code prompt passed as `useful_code`, but
indentation/display formatting must be reviewed before any UI-facing baseline
change. The H1 card must continue to reject non-128 metadata unless a later
H1-specific gate revision explicitly changes that rule.

## Max Output Tokens 256 Compare Note - 2026-05-26

Phase H1 remains pinned to the adopted `sanitizer_only + max_output_tokens=128`
metadata contract.

The 256-token hidden comparison artifact
`artifacts/qairt244_npu_max_output_256_quality_compare/20260526_201129/`
failed before generation because the native editable-prompt entrypoint still
reports `native_max_output_tokens_limit=128`. The 256 attempts produced empty
sanitized output and are rollback-only.

Do not feed 256 metadata into the H1 transient card, Compose adapter, or
PreviewHost. H1 display remains read-only, Diagnostic-only, and valid only for
fresh 128-token sanitized natural Japanese metadata with DB/TTS/Markdown/
streaming and selected-path persistence all false.

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

## Freshness And State Transition Baseline - 2026-05-25

The third implementation step adds pure Kotlin freshness and state transition
coverage before any ChatScreen connection.

Freshness rules:

- artifact metadata may provide epoch milliseconds through
  `artifact_timestamp_ms`, `artifact_timestamp`, `synced_at`, or `created_at`
- artifacts are fresh only when the timestamp is within 24 hours of the current
  metadata read
- missing timestamp maps to `stale_or_unknown`
- future timestamp maps to `stale_or_invalid`
- older-than-24-hour timestamp maps to `stale_artifact`
- stale, unknown, or invalid artifacts must not become visible preview content
- only the latest named baseline artifact may be used as Phase H1 evidence

State transition rules:

- initial state is `visible=false`
- valid artifact load may produce `visible=true`
- new user input clears to hidden state
- navigation away clears to hidden state
- DEV toggle OFF clears to hidden state
- failure or rollback clears to hidden state
- app restart clears to hidden state
- refresh re-reads artifact metadata and reapplies the mapper only when fresh
- stale refresh returns hidden/rollback with no output preview

Refresh remains metadata-only:

- `runsNpu=false`
- `initializesEngine=false`
- `runsDecode=false`
- no retry
- no auto fallback
- no DB, TTS, Markdown, or streaming handoff

## Artifact Metadata Input Boundary - 2026-05-26

The fourth implementation step fixes the metadata input boundary before any
ChatScreen connection. The boundary accepts key-value text, `Map<String,
String>`, or already-read file content, then emits only validated H1 metadata
and `DevOnlyNpuPhaseH1UiInput`.

Minimum accepted fields:

- `artifact_timestamp_ms`
- `result`
- `sanitized_output`
- `quality_classification`
- `npu_backend`
- `npu_backend_evidence`
- `fallback_used`
- `timeout`
- `fresh_crash`
- `selected_path_npu_saved`
- `standard_route_connected`
- `normal_ui_route_connected`
- `db`
- `tts`
- `markdown`
- `streaming`
- `decode_ms`
- `max_output_tokens`
- `artifact_path`

Boundary exclusions:

- `raw_output` is not retained by boundary metadata and is not copied into UI
  input
- model path, token dumps, full native diagnostics, and unknown keys are not
  retained for UI display
- `selectedPath=npu` is never saved by this boundary

Parser rules:

- blank lines and `#` comment lines are ignored
- unknown keys are ignored
- duplicate keys use the last value
- missing required fields produce rollback input
- invalid boolean values produce rollback input
- invalid numeric values produce rollback input
- valid metadata is then passed through freshness and mapper gates

DEV toggle wiring rule:

- `dev_enable_npu_chatscreen_route=false` means do not read metadata and do
  not parse metadata
- `true` still requires fresh artifact metadata and all promotion gates before
  the transient preview can be visible
- refresh remains metadata re-read only, with no run, retry, fallback,
  `Engine.initialize`, or `RunDecode`

## Metadata-To-Presenter Integration Baseline - 2026-05-26

The fifth implementation step adds pure Kotlin integration coverage for the
read-only transient card input/output path:

```text
key-value metadata text
  -> artifact metadata boundary
  -> DevOnlyNpuPhaseH1UiInput
  -> DevOnlyNpuPhaseH1Presenter
  -> DevOnlyNpuPhaseH1UiState
```

Valid baseline metadata with a fresh timestamp, `result=success`,
`quality_classification=natural_japanese`, NPU evidence, no fallback, no
timeout, no fresh crash, no standard/normal UI route connection, and no
DB/TTS/Markdown/streaming ingress maps to:

- `visible=true`
- `status=SUCCESS`
- `reasonCode=ok`
- `outputPreview=sanitized_output`
- `decode_ms` display text
- `maxOutputTokens=128`
- short `QNN_HTP_V79_FastRPC` backend evidence
- short artifact path
- all side-effect flags false

The same integration test fixes rollback output for gate failures:

- `fallback_used=true` -> `fallback_used`
- `timeout=true` -> `timeout`
- non-natural quality classification -> `quality_not_natural_japanese`
- `standard_route_connected=true` -> `standard_route_connected`
- `db=true` -> `db_connected`

`raw_output` remains boundary-only diagnostic evidence and is not propagated
into `UiInput`, `UiState`, or state string output. Toggle false still means the
metadata provider is not called and the state remains hidden.

## Card View Model Contract - 2026-05-26

The sixth implementation step adds the read-only display contract that a future
H1 transient card may consume. This is still pure Kotlin only: no ChatScreen
call site, no UI component, no NPU run, no engine initialization, and no decode.

`DevOnlyNpuPhaseH1CardViewModel` is mapped from `DevOnlyNpuPhaseH1UiState` and
fixes these display fields:

- `visible`
- `title`
- `subtitle`
- `body`
- `statusLabel`
- `reasonLabel`
- `detailLines`
- `warningLines`
- `devBadge`

Success contract:

- `visible=true`
- `title=DEV NPU transient preview`
- `subtitle=Read-only sanitized output`
- `body=sanitized_output`
- `statusLabel=SUCCESS`
- `reasonLabel=reasonCode=ok` for the baseline success path
- `devBadge=DEV ONLY`

Rollback/failure contract:

- `visible=false`
- `subtitle=Hidden by promotion gate`
- `body=null`
- `statusLabel=ROLLBACK` or `FAILURE`
- `reasonLabel` carries the reason code
- `warningLines` carries the reason summary
- retry remains unavailable

Hidden contract:

- `visible=false`
- `subtitle=Hidden until a fresh gated artifact passes`
- `body=null`
- no warning lines
- no retry or side-effect controls

The view model detail contract includes:

- `maxOutputTokens=128`
- `decode_ms`
- short backend evidence
- short artifact path
- `selectedPathSaved=false`
- `db=false`
- `tts=false`
- `markdown=false`
- `streaming=false`

All unsafe display/action affordances are fixed false:

- `showRawOutput=false`
- `showRetryButton=false`
- `showPersistButton=false`
- `showTtsButton=false`
- `showMarkdownButton=false`
- `showStreamingIndicator=false`

Snapshot contract tests fix `toContractText()` for success and rollback. The
contract text must not contain `raw_output` or template tokens such as
`<end_of_turn>`.

## Preview Renderer Contract - 2026-05-26

The seventh implementation step adds a ChatScreen-independent preview renderer
for the read-only card view model. This is still formatter-only: no Compose UI,
no ChatScreen call site, no NPU run, no `Engine.initialize`, and no
`RunDecode`.

Renderer API:

- `DevOnlyNpuPhaseH1PreviewRenderer.renderLines(model)`
- `DevOnlyNpuPhaseH1PreviewRenderer.renderContractText(model)`

Visible success output order is fixed as:

1. `DEV ONLY - DEV NPU transient preview`
2. `Status: SUCCESS`
3. subtitle
4. `Output:`
5. sanitized output body
6. reason label
7. `Details:`
8. detail lines in source order
9. `Warnings:` and warning lines, only when present

`visible=false` is fixed as no rendered preview lines:

- rollback model -> `emptyList()`
- hidden model -> `emptyList()`
- contract text -> empty string

The renderer contract keeps the same safety boundary:

- raw output is never rendered
- `<end_of_turn>` and `<start_of_turn>` are never rendered
- retry, persist, TTS, Markdown button, and streaming indicator labels are not
  rendered
- detail lines keep `maxOutputTokens`, `decode_ms`, backend evidence, artifact,
  selected-path false, and DB/TTS/Markdown/streaming false in stable order

## Minimal Diagnostic Wiring - 2026-05-26

The eighth implementation step wires the H1 read-only preview into
`NpuDiagnosticChatActivity` only. This is not a normal ChatScreen promotion and
does not insert into the assistant message list.

Wiring flow:

```text
artifact metadata text
  -> DevOnlyNpuPhaseH1ArtifactMetadataParser
  -> DevOnlyNpuPhaseH1UiState
  -> DevOnlyNpuPhaseH1CardViewModel
  -> DevOnlyNpuPhaseH1PreviewRenderer
  -> Diagnostic/DEV read-only text section
```

Activation remains explicit:

- intent extra `dev_enable_npu_chatscreen_route=false` by default
- when false, metadata provider is not called
- when true, metadata is read from app-private
  `qairt244_phase_h1_transient_preview_metadata.txt` if present
- if no app-private metadata file exists, the Diagnostic/DEV screen uses the
  already adopted committed baseline metadata for
  `artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810`
- the fallback metadata is read-only display evidence and does not run NPU

Display is allowed only when the H1 metadata freshness and promotion gates pass.
Rollback, hidden, stale, or gate-failed states render no preview lines.

The section records safety lines:

- `selectedPathNpuSaved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `db=false`
- `tts=false`
- `markdown=false`
- `streaming=false`
- `retry=false`
- `auto_fallback=false`
- `npu_generation=false`
- `engine_initialize=false`
- `run_decode=false`

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

## UI Capture Supplement - 2026-05-26

The initial wiring artifact
`artifacts/qairt244_phase_h1_transient_preview_wiring/20260526_062814` proved
the Diagnostic-only metadata-to-renderer path but did not capture a
representative screenshot/window because another interactive app took
foreground after launch.

Supplemental artifact
`artifacts/qairt244_phase_h1_transient_preview_ui_capture/20260526_064732`
captures the same Diagnostic-only wiring with the Phase H1 section visible:

- `screenshot.png`: representative connected-device screenshot
- `window.xml`: representative UI hierarchy dump
- visible section: `Phase H1 transient preview`
- rendered header: `DEV ONLY - DEV NPU transient preview`
- rendered status: `Status: SUCCESS`
- rendered body: sanitized Japanese output only
- `metadata_read=true`
- `preview_visible=true`
- `raw_output`, `<end_of_turn>`, and `<start_of_turn>` are absent from UI

The capture remains read-only: no retry, no fallback, no DB, no TTS, no
Markdown, no streaming, no selected-path NPU persistence, no `Engine.initialize`,
no `RunDecode`, and no additional NPU execution.

## Read-Only Card Wiring - 2026-05-26

The next implementation step adds a dedicated read-only transient card to the
Diagnostic H1 preview surface in `NpuDiagnosticChatActivity`.

The card is still customBuildExperimentDebug-only and uses the existing H1 chain:

```text
artifact metadata text
  -> DevOnlyNpuPhaseH1ArtifactMetadataParser
  -> DevOnlyNpuPhaseH1UiState
  -> DevOnlyNpuPhaseH1CardViewModel
  -> DevOnlyNpuPhaseH1PreviewRenderer
  -> Diagnostic read-only card
```

Card visibility is tied to the renderer result:

- fresh, gate-passing metadata: card visible
- rollback, hidden, stale, missing, or gate-failed metadata: card hidden
- refresh: metadata reread only, no run/retry/fallback

The card renders only:

- `DEV ONLY`
- `DEV NPU transient preview`
- `Status: SUCCESS`
- sanitized output
- `decode_ms`
- short backend evidence
- `maxOutputTokens=128`
- short artifact path
- side-effect flags false

The card does not render raw output, template tokens, model paths, selected-path
details, retry controls, persist controls, TTS controls, Markdown controls, or a
streaming indicator.

## Read-Only Card Hidden-State Regression - 2026-05-26

Artifact:
`artifacts/qairt244_phase_h1_readonly_card_hidden_state_regression/20260526_074740/`

The Diagnostic-only read-only card was captured across four metadata states:

- success baseline: card visible, sanitized output displayed
- stale metadata: card hidden, `reasonCode=stale_artifact`
- rollback metadata: card hidden, `reasonCode=fallback_used`
- toggle false: card hidden, `metadata_read=false`, `reasonCode=initial`

The hidden-state captures keep renderer line count at `0` and do not display
`DEV ONLY`, sanitized output, raw output, `<start_of_turn>`, or `<end_of_turn>`.
All captures keep `selectedPathNpuSaved=false`, `standard_route_connected=false`,
`normal_ui_route_connected=false`, `db=false`, `tts=false`, `markdown=false`,
`streaming=false`, `npu_generation=false`, `engine_initialize=false`, and
`run_decode=false`.

## Compose Adapter Contract - 2026-05-26

`DevOnlyNpuPhaseH1ComposeAdapter` defines the next pre-UI boundary:

```text
DevOnlyNpuPhaseH1CardViewModel
  -> DevOnlyNpuPhaseH1ComposeModel
```

The adapter is a contract only. It does not implement a Compose component and
does not connect to ChatScreen.

Contract:

- `card.visible=true` maps to `shouldShowSurface=true`
- `card.visible=false` maps to `shouldShowSurface=false` and `body=null`
- body contains sanitized output only
- `insertIntoAssistantList=false`
- `persistToDb=false`
- `speakTts=false`
- `renderMarkdown=false`
- `stream=false`
- `showRetryButton=false`
- `showFallbackButton=false`

The unit tests verify raw output, `<start_of_turn>`, and `<end_of_turn>` do not
appear in the Compose model or contract text.

## Diagnostic Preview Host Contract - 2026-05-26

`DevOnlyNpuPhaseH1PreviewHost` defines the last pre-ChatScreen boundary:

```text
DevOnlyNpuPhaseH1ComposeModel
  -> DevOnlyNpuPhaseH1PreviewHostState
```

The host is Diagnostic-only and contract-only. It does not implement a formal
Compose UI and does not connect to ChatScreen.

Host contract:

- success compose model maps to `visible=true`, `showCard=true`, and non-empty
  renderer text
- hidden, stale, rollback, and toggle-false models map to `visible=false`,
  `showCard=false`, and empty renderer text
- `showAssistantInsertion=false`
- `showDbPersistence=false`
- `showTts=false`
- `showMarkdown=false`
- `showStreaming=false`
- `showRetry=false`
- `showFallback=false`
- `readsMetadata=false`
- `runsNpu=false`
- `engineInitialize=false`
- `runDecode=false`

The host render text is sanitized preview output only. It does not contain raw
output or turn template tokens.

## XML Card / Preview Host Consistency - 2026-05-26

`DevOnlyNpuPhaseH1XmlCardContract` is the shared pure text contract for the
existing Diagnostic XML/read-only card and `DevOnlyNpuPhaseH1PreviewHost`.

The XML card now uses the helper-generated display lines instead of local string
assembly. Contract tests verify:

- success XML card text equals preview host render text
- display order is stable: badge, title, status, subtitle, output, reason,
  details
- hidden, stale, rollback, and toggle-false cases render an empty XML card and
  an empty host render text
- raw output, `<start_of_turn>`, and `<end_of_turn>` are absent
- assistant insertion, DB, TTS, Markdown, streaming, retry, fallback, metadata
  read, NPU run, engine initialize, and decode flags remain false

## Preview Consistency Snapshot - 2026-05-26

`DevOnlyNpuPhaseH1PreviewConsistency` captures the final Diagnostic-only
read-only preview alignment before any formal Compose UI or ChatScreen handoff.

The snapshot compares:

- XML/read-only card helper text
- `DevOnlyNpuPhaseH1PreviewRenderer.renderContractText()`
- `DevOnlyNpuPhaseH1PreviewHostState.renderText`
- Compose adapter render text
- Compose adapter and host contract text safety flags

Success text is identical across XML, renderer, host, and Compose render paths.
Hidden, rollback, stale, and toggle-false paths are empty/hidden across all
render paths. Raw output, turn template tokens, assistant insertion, DB, TTS,
Markdown, streaming, retry/fallback, metadata read, NPU run, engine initialize,
and decode are all fixed to the safe side.
