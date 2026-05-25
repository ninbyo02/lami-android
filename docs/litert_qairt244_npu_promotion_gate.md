# QAIRT244 Hidden NPU Promotion Gate

Date: 2026-05-25

Scope: documentation-only planning for the hidden experimental qairt244 SM8750
NPU route. This gate does not promote `Backend.NPU` into normal selected-path
inference, does not run NPU, and does not change native code.

## Adopted Hidden Baseline

The adopted hidden experimental display-quality baseline is:

```text
case=sanitizer_only
max_output_tokens=128
baseline=enhanced_sanitizer_only_128
```

Lower token caps are not adopted. The recorded rollback-only rows remain:

- `lower_max_tokens_64_sanitizer`: rejected after `empty_after_sanitize`
  evidence.
- `lower_max_tokens_32_sanitizer`: rejected after adapter failure / timeout
  evidence.

`stop_sequence_end_of_turn` remains `not_run/native_stop_not_exposed`. Native
stop sequence or native turn-stop behavior is not required for this hidden
baseline unless a real API surface is exposed and separately gated.

## Promotion Gate

Before any broader hidden experimental promotion, every accepted run must
record all of the following:

- `npu_backend=NPU`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback_used=false`
- `fresh_crash=false`
- `timeout=false`
- `quality_classification=natural_japanese` after sanitize
- sanitized display output is artifact-free: no `<start_of_turn>`,
  `<end_of_turn>`, prompt echo, template residue, repeated completion
  classification, or multilingual drift classification
- `selected_path_npu_saved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `selected_path_npu_normal_route=no`
- `conversation_created=no`
- `generate_response=no`
- `db=false`
- `tts=false`
- `markdown=false`
- `streaming=false`
- standard route disconnection regression test passes
- staged binary check passes
- artifact metadata timestamp is fresh:
  `artifact_timestamp_ms`, `artifact_timestamp`, `synced_at`, or `created_at`
  within 24 hours
- missing, future, or stale artifact metadata is rejected before display
- artifact metadata boundary validation passes before mapper/freshness handoff:
  all minimum fields are present, boolean and numeric fields parse cleanly,
  unknown keys are ignored, duplicate keys use the last value, and `raw_output`
  is not propagated into UI input
- `dev_enable_npu_chatscreen_route=false` blocks metadata read and parse
- metadata-to-presenter integration passes: valid fresh metadata reaches
  `visible=true` sanitized output only, while fallback, timeout,
  non-natural quality, standard route connection, or DB ingress reaches
  `visible=false` rollback
- presenter side-effect flags remain false:
  `shouldPersistToDb=false`, `shouldSpeakTts=false`,
  `shouldRenderMarkdown=false`, `shouldStream=false`

Raw native output may be classified as `template_artifact` only as diagnostic
evidence. That raw artifact is acceptable only when the sanitized display output
is meaningful natural Japanese and is artifact-free after sanitize.

## Promotion Blockers

Promotion remains blocked by any of the following:

- raw artifact remains visible after sanitize
- sanitized output is empty
- repetition or multilingual drift remains after sanitize
- `fresh_crash=true`
- `timeout=true`
- `fallback_used=true`
- missing `QNN_HTP_V79_FastRPC_native_diag` evidence
- `selectedPath=npu` or equivalent normal-route NPU selection is saved
- DB, TTS, Markdown, or streaming ingress appears
- standard or normal UI route is connected
- generic or QCS8275 model is selected for NPU
- stale artifact is used as the basis for promotion
- `stale_or_unknown` or `stale_or_invalid` artifact timestamp is used as the
  basis for promotion

## Standard Route Boundary

The hidden qairt244 route remains separate from normal local inference:

- no normal Settings exposure without developer access
- no production `Backend.NPU` candidate selection
- no automatic NPU to GPU or CPU fallback
- no persisted `selectedPath=npu`
- no DB persistence, TTS, Markdown repair, or streaming integration

Regression coverage should continue to prove the standard route is disconnected,
including the blocked-branch coverage referenced by
`DevOnlyNpuChatScreenBlockedBranchTest`.

## Staged Binary Check

Promotion review requires an explicit staged-binary check before any merge or
release-facing handoff:

- `git status --short` must not show newly staged `.so`, `.litertlm`, `.apk`,
  `.aar`, `.zip`, `.tar`, or `.gz` artifacts.
- Native artifact provenance must be recorded by path and SHA-256 in the
  relevant run document or in `docs/qairt244_native_artifact_reproducibility.md`.
- Any native artifact used by the hidden run must remain reproducible from
  documented source/build steps or a pinned external checkout; do not rely on an
  untracked local binary as promotion evidence.

## Existing Coverage

The current supporting docs already cover the main pieces:

- `docs/litert_qairt244_npu_hidden_to_ui_handoff_plan.md` defines the staged
  hidden-to-UI handoff. The first allowed UI candidate is Phase H1 transient
  preview only: sanitized output display, no DB, no TTS, no Markdown, no
  streaming, no selected-path NPU persistence, and no standard route connection.
- `docs/litert_qairt244_npu_phase_h1_transient_ui_surface.md` defines the Phase
  H1 surface: DEV-only transient card/banner/snackbar, `sanitized_output` only,
  raw output artifact-only, clear on input/navigation/toggle-off/failure/app
  restart/stale artifact, and no retry or auto fallback on failure.
  The first code step is state/display-model/presenter tests only; ChatScreen
  remains disconnected until those tests pin raw-output exclusion and
  side-effect flags false.
  The second code step maps artifact key-value metadata into H1 input while
  discarding `raw_output`; gate mismatches become rollback/failure input before
  any ChatScreen connection.
  The third code step fixes artifact freshness and clear/refresh state
  transitions in pure Kotlin. Refresh is metadata-only and records no NPU,
  engine, or decode execution flags.
  The fourth code step fixes the metadata input boundary: key-value text,
  `Map<String, String>`, and file-content text converge into validated metadata
  while raw/native-only fields are dropped before UI input.
  The fifth code step fixes metadata-to-presenter integration from key-value
  text through `UiState` without adding any ChatScreen call site.
- `docs/litert_qairt244_npu_turn_stop_quality_compare.md` records the
  `sanitizer_only + max_output_tokens=128` adoption, raw/sanitized output
  policy, and native stop API limitation.
- `artifacts/qairt244_npu_stop_api_investigation/20260525_214513/` records the
  read-only native stop API investigation. The result is
  `native_stop_not_exposed`; no native stop comparison is implemented.
- `docs/litert_qairt244_chat_screen_npu_integration_plan.md` records the same
  sanitizer baseline and no DB/TTS/Markdown/streaming boundary.
- `docs/qairt244_standard_hidden_experimental_plan.md` records the standard
  hidden route gate, default-OFF developer access boundary, and standard-user
  exclusion rules.
- `docs/qairt244_native_artifact_reproducibility.md` records native artifact
  provenance and the no-large-binary Git policy.
