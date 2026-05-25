# QAIRT244 NPU Hidden-To-UI Handoff Plan

Date: 2026-05-25

Scope: documentation-only design for a future handoff from the hidden
experimental qairt244 SM8750 NPU result to normal ChatScreen UI surfaces. This
plan does not implement normal UI promotion, does not connect the standard
route, does not run NPU, does not call `Engine.initialize` or `RunDecode`, does
not change native code, and does not change release behavior.

## Baseline Inputs

The only accepted display-quality baseline for this handoff design is:

```text
case=sanitizer_only
max_output_tokens=128
baseline=enhanced_sanitizer_only_128
artifact=artifacts/qairt244_npu_turn_stop_quality_compare/20260525_211810
```

Native stop sequence / stop token comparison is not required for this handoff.
The static investigation at
`artifacts/qairt244_npu_stop_api_investigation/20260525_214513/` found no
public Android/JNI per-run stop sequence, stop token, EOS, or `<end_of_turn>`
API for the qairt244 editable path. `stop_sequence_end_of_turn` therefore
remains `not_run/native_stop_not_exposed`.

## Handoff Phases

### Phase H0: Hidden Experimental Only

Current state. The hidden experimental route produces result files and
artifacts only.

- `normal_ui_route_connected=false`
- `standard_route_connected=false`
- no ChatScreen assistant message handoff
- no DB persistence
- no TTS
- no Markdown rendering
- no streaming
- no persisted `selectedPath=npu`

### Phase H1: Transient UI Preview

First allowed UI handoff candidate. ChatScreen may show only a DEV-only
transient preview, such as a guarded card, banner, or snackbar.

- detailed surface spec:
  `docs/litert_qairt244_npu_phase_h1_transient_ui_surface.md`
- display `sanitized_output` only
- keep `raw_output` in artifacts only
- no DB persistence
- no TTS
- no Markdown rendering
- no streaming
- no persisted `selectedPath=npu`
- no standard route connection
- no normal assistant message insertion
- reload or navigation may discard the preview

Phase H1 must be blocked unless the promotion gate in this document passes.

The first Phase H1 code step is state/display-model/presenter test coverage
only. It fixes sanitized-output-only display, raw-output exclusion,
reason-only failure display, rollback hiding, and side-effect flags remaining
false before any ChatScreen connection is attempted.

The second Phase H1 code step is artifact metadata mapper test coverage only.
It converts hidden result key-value metadata into `DevOnlyNpuPhaseH1UiInput`,
keeps `raw_output` out of UI input, and converts promotion-gate mismatches into
rollback/failure input before any ChatScreen connection is attempted.

The third Phase H1 code step is freshness and state-transition test coverage
only. It accepts artifact epoch milliseconds from `artifact_timestamp_ms`,
`artifact_timestamp`, `synced_at`, or `created_at`, treats only artifacts within
24 hours as fresh, blocks missing/future/stale timestamps, and fixes clear
events for new input, navigation away, toggle OFF, failure/rollback, and app
restart. Refresh is artifact metadata re-read only; it does not run NPU,
initialize an engine, run decode, retry, fallback, persist DB rows, call TTS,
render Markdown, or stream.

The fourth Phase H1 code step is the artifact metadata input boundary. It
accepts key-value text, maps, or already-read file content; retains only the
minimum display/gate fields; drops `raw_output`, model paths, token dumps, full
native diagnostics, and unknown keys; validates boolean and numeric fields
before mapper handoff; and fixes duplicate key behavior as last-value-wins.
If `dev_enable_npu_chatscreen_route=false`, future ChatScreen wiring must not
read or parse metadata at all.

### Phase H2: Assistant-Message-Style Temporary Display

Second candidate after Phase H1 proves stable. The sanitized output may be
rendered in an assistant-message-like visual surface, but only in memory for the
current session.

- session-local display only
- reload may remove the message
- no DB persistence
- no TTS
- no Markdown rendering
- no streaming
- no persisted `selectedPath=npu`
- raw output remains artifact-only

### Phase H3: DB Persistence Evaluation

DB persistence may be evaluated only after repeated Phase H1/H2 passes with the
same sanitizer gate and no route-boundary regressions.

- DB persistence gets its own gate
- TTS, Markdown, and streaming remain disconnected
- persisted content must still be sanitized natural Japanese
- raw output must not be persisted as user-visible assistant content

### Phase H4: TTS / Markdown / Streaming Evaluation

TTS, Markdown, and streaming must each be gated independently. They must not be
connected as a bundle.

- TTS gate is separate from DB gate
- Markdown gate is separate from DB and TTS gates
- streaming gate is separate from DB, TTS, and Markdown gates
- each gate must preserve rollback on artifact residue, repetition,
  multilingual drift, timeout, crash, fallback, or route leakage

## Promotion Gate For Any Handoff

Every accepted handoff candidate must satisfy all of the following:

- `sanitizer_only + max_output_tokens=128` baseline passes
- sanitized `quality_classification=natural_japanese`
- sanitized output contains no `<end_of_turn>` or `<start_of_turn>`
- no template artifact after sanitize
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
- `db=false`
- `tts=false`
- `markdown=false`
- `streaming=false`
- standard route disconnection regression test passes
- staged binary check passes
- latest baseline artifact timestamp is fresh; stale, missing, or future
  metadata cannot be used as handoff evidence
- artifact metadata input boundary passes: minimum fields present, booleans and
  numbers valid, `raw_output` not propagated, and DEV toggle false blocks
  metadata read/parse

Raw native output may contain `template_artifact` only as diagnostic evidence.
It is acceptable only when the displayed sanitized output remains meaningful
natural Japanese and artifact-free.

## Rollback Conditions

Rollback the handoff candidate if any of these occur:

- sanitized output is empty
- template artifact remains after sanitize
- repetition remains after sanitize
- multilingual drift remains after sanitize
- timeout
- fresh crash
- `fallback_used=true`
- QNN / HTP / FastRPC NPU evidence is missing
- `selectedPath=npu` or equivalent normal-route NPU setting is saved
- DB, TTS, Markdown, or streaming ingress appears before its own gate
- standard route or normal UI route is connected
- stale artifact is used as promotion evidence
- generic or QCS8275 model is selected for NPU

## Implementation Pre-Checklist

Before any code implementation for Phase H1:

- latest baseline artifact is fresh and named in the implementation note
- stop API is documented as not required for Phase H1
- sanitizer unit tests pass
- standard route disconnection regression test passes
- staged binary check passes
- DEV/hidden toggle defaults remain false
- hidden route is off after a guarded run
- handoff target is transient only
- DB, TTS, Markdown, and streaming are explicitly out of scope
- `selectedPath=npu` persistence remains blocked

## Non-Goals

- no normal UI promotion in this documentation pass
- no standard route connection
- no DB, TTS, Markdown, or streaming connection
- no NPU execution
- no native change
- no model change
- no release or standard behavior change
- no `app/src/main/jniLibs` change
