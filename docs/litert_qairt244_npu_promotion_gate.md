# QAIRT244 Hidden NPU Promotion Gate

## Max Output Tokens 512 Guard Preflight - 2026-05-26

Status: 512 guard-only patch built; run not executed.

Artifacts:

- build/static artifact:
  `artifacts/qairt244_editable_prompt_max512_entrypoint_build/20260526_235239/`
- preflight artifact:
  `artifacts/qairt244_npu_max512_guard_preflight/20260527_000522/`

The max512 runner is guard/preflight-only in this phase. It records summary,
native marker scan, `SetMaxOutputTokens(512)` evidence, build artifact path,
grep-safety, and staged-binary check, then exits before device selection, app
launch, NPU generation, `Engine.initialize`, or `RunDecode`.

Promotion remains blocked from any 512 runtime use unless supplied evidence
continues to prove `qairt244_editable_prompt_max512_v1`,
`native_max_output_tokens_limit=512`, `SetMaxOutputTokens(512)`, and SM8750
model selection. ChatScreen, DB, TTS, Markdown, streaming, selected-path
persistence, release behavior, and standard behavior remain disconnected.

The staged-binary check records the rebuilt `liblitertlm_jni.so`:

```text
build_id=82cf5b24f5b2897edf3b4b8a6970cf8e
sha256=7db8f0d6674822627cd2877f7eaa6e3a4d89e13a3449708af6629f5d6a800105
```

Promotion decision: no normal UI or H1 baseline change; the next 512 step
still requires separately approved single-prompt hidden safety execution.

## Max Output Tokens 256 Guard Preflight - 2026-05-26

Status: 256 guard-only patch built; run not executed.

Artifacts:

- build/static artifact:
  `artifacts/qairt244_editable_prompt_max256_entrypoint_build/20260526_204155/`
- preflight artifact:
  `artifacts/qairt244_npu_max256_guard_preflight/20260526_205300/`

The 256 quality runner is now guarded before execution. `--preflight-only`
records summary, marker, evidence, grep-safety, and staged-binary checks, then
exits before device selection, app launch, NPU generation, `Engine.initialize`,
or `RunDecode`.

Promotion remains blocked from 256 runtime use unless the supplied native
artifact/static metadata proves `qairt244_editable_prompt_max256_v1`,
`native_max_output_tokens_limit=256`, `SetMaxOutputTokens(256)`, and SM8750
selection evidence. ChatScreen, DB, TTS, Markdown, streaming, and selected-path
persistence remain disconnected from this preflight.

The rebuilt JNI artifact records:

```text
build_id=c42e4438f1b39e384ab075b9392831ca
sha256=3767332f97ffee57b635fc13e2741714c994f7a2cc94d0fde5d4fbbce9c731ba
```

Promotion decision: still no normal UI or H1 baseline change. 256 may proceed
only to a separately approved hidden experimental single-run phase.

## Max Output Tokens 256 Single Prompt - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_single_prompt/20260526_211046/`

The single approved hidden experimental prompt `こんにちは` passed with
`max_output_tokens=256`. `RunDecode` was reached, QNN/HTP/FastRPC evidence was
present, and the sanitized output was natural Japanese with no template
artifact remaining after sanitize.

Gate status:

- `fallback_used=false`
- `timeout=false`
- `fresh_crash=false`
- `selected_path_npu_saved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`

Promotion decision: allow a next-phase 256 three-prompt hidden comparison.
Do not adopt 256 as baseline and do not feed 256 output into normal UI/H1.

## Max Output Tokens 256 Three-Prompt Hidden Comparison - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_three_prompt_compare/20260526_211856/`

The three approved hidden prompts passed once each at
`max_output_tokens=256`:

- `こんにちは`: `natural_japanese`
- `Pythonで簡単な電卓コードを書いて`: `useful_code`
- `ラミィのNPU推論について短く説明して`: `natural_japanese`

Gate status for all rows: `RunDecode` reached, QNN/HTP/FastRPC evidence
present, `fallback_used=false`, `timeout=false`, `fresh_crash=false`,
`selected_path_npu_saved=false`, `standard_route_connected=false`,
`normal_ui_route_connected=false`, `assistant_message_list_inserted=false`,
and `db=false`, `tts=false`, `markdown=false`, `streaming=false`.

Memory after 10 seconds returned to `TOTAL PSS=224993 KB`,
`Native Heap=34500 KB`, so no retained-memory rollback was recorded.

Promotion decision: 256 may be treated as a hidden experimental candidate for
further token expansion. It is not adopted into the H1 display baseline or
normal UI gate. H1 remains pinned to `sanitizer_only + max_output_tokens=128`
until a separate UI-facing decision is made.

Result commit decision: 256 is fixed as a hidden experimental baseline
candidate only. The promotion gate still rejects H1 or normal UI use of 256
until a later explicit gate revision. The next expansion target is 512 and
requires a separate native guard/build/preflight and single-prompt safety run
before any three-prompt comparison.

## Max Output Token Limit Investigation - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_token_limit_investigation/20260526_202629/`

Static finding: the 128-token ceiling is currently enforced by the custom
qairt244 editable-prompt JNI guard in external LiteRT-LM
`kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`. The guard rejects
`max_output_tokens > 128` and reports
`invalid_max_output_tokens ... native_max_output_tokens_limit=128` before
`DecodeConfig::SetMaxOutputTokens(max_output_tokens)` and before `RunDecode`.

Classification: `A. custom_safety_guard_only` for the observed 256 rejection.
No static evidence in the inspected `DecodeConfig` setter path shows a 128 API
limit, but model/runtime/memory safety above 128 remains unproven.

Promotion decision: keep `sanitizer_only + max_output_tokens=128` as the only
accepted hidden display baseline. 256/512/1024/2048/4096 require staged native
guard review, rebuild, one-shot runs, memory-after-10s evidence, cleanup
evidence, fresh crash checks, and sanitizer quality gates before they can be
considered.

## Max Output Tokens 256 Compare - 2026-05-26

Artifact:
`artifacts/qairt244_npu_max_output_256_quality_compare/20260526_201129/`

Result: keep `sanitizer_only + max_output_tokens=128` as the hidden
experimental display baseline.

The compare-only Java gate accepted the explicit
`allow_max_output_tokens_compare=true` request, but the lower native editable
prompt entrypoint rejected `max_output_tokens=256` with
`invalid_max_output_tokens value=256 native_max_output_tokens_limit=128`.
All three requested prompts therefore returned empty sanitized output and
`quality_classification=empty_after_sanitize`.

Safety invariants remained intact: `npu_backend=NPU`,
`npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`,
`fallback_used=false`, `timeout=false`, `fresh_crash=false`,
`selected_path_npu_saved=false`, `standard_route_connected=false`,
`normal_ui_route_connected=false`, `db=false`, `tts=false`, `markdown=false`,
and `streaming=false`.

Promotion blocker: `max_output_tokens=256` is rollback-only until native
`native_max_output_tokens_limit` is deliberately raised and separately gated.
H1 UI promotion remains blocked from using 256; H1 metadata/card contracts
continue to require `max_output_tokens=128`.

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
- card view model contract passes before UI wiring:
  success displays sanitized output only, rollback/hidden display no body,
  detail lines include token/decode/backend/artifact and side-effect false
  metadata, and all raw/retry/persist/TTS/Markdown/streaming controls are false
- preview renderer contract passes before UI wiring:
  success lines render in stable order, rollback/hidden render no lines, raw
  output and template tokens are absent, and action labels are absent
- Diagnostic/DEV minimal wiring passes before any ChatScreen promotion:
  `dev_enable_npu_chatscreen_route=false` defaults to no read/parse, true reads
  metadata only, fresh gate-passing sanitized output is rendered, and
  stale/rollback/hidden output is not rendered

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
  The sixth code step fixes the read-only transient-card view model contract
  and snapshot text without adding any UI component.
  The seventh code step fixes the preview renderer/formatter contract without
  adding Compose UI or ChatScreen wiring.
  The eighth code step wires a read-only metadata-to-renderer preview into
  `NpuDiagnosticChatActivity` only, still outside the normal ChatScreen route.
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

## Phase H1 UI Capture Evidence - 2026-05-26

The promotion gate evidence set now includes
`artifacts/qairt244_phase_h1_transient_preview_ui_capture/20260526_064732`,
which supplements the earlier logic-only wiring artifact
`artifacts/qairt244_phase_h1_transient_preview_wiring/20260526_062814`.

The UI capture passes the pre-promotion H1 display checks:

- Diagnostic-only screen: `NpuDiagnosticChatActivity`
- H1 section visible in `screenshot.png` and `window.xml`
- `DEV ONLY - DEV NPU transient preview`
- `Status: SUCCESS`
- sanitized output rendered as natural Japanese
- no `raw_output`, `<end_of_turn>`, or `<start_of_turn>` in UI
- `selectedPathNpuSaved=false`
- `standard_route_connected=false`
- `normal_ui_route_connected=false`
- `db=false`, `tts=false`, `markdown=false`, `streaming=false`
- `retry=false`, `auto_fallback=false`
- `npu_generation=false`, `engine_initialize=false`, `run_decode=false`

This evidence is not a normal UI promotion and is not a replacement for a fresh
future promotion run. It only closes the missing representative screenshot/window
gap for the already implemented Diagnostic-only H1 wiring.

## Phase H1 Read-Only Card Evidence - 2026-05-26

`NpuDiagnosticChatActivity` now uses the existing H1 metadata gate to show a
dedicated read-only card instead of relying only on a plain diagnostics text
section.

The card is eligible for display only when:

- metadata is fresh
- sanitizer baseline metadata passes
- renderer returns visible lines
- sanitized output is natural Japanese
- raw output and template tokens are excluded
- standard and normal UI route flags remain false
- DB/TTS/Markdown/streaming flags remain false
- `npu_generation=false`
- `engine_initialize=false`
- `run_decode=false`

This still does not satisfy normal UI promotion by itself. It is a
pre-promotion Diagnostic-only surface check.

## Phase H1 Hidden-State Gate Evidence - 2026-05-26

Artifact:
`artifacts/qairt244_phase_h1_readonly_card_hidden_state_regression/20260526_074740/`

The card display gate now has connected-device regression evidence:

- `success`: `metadata_read=true`, `preview_visible=true`,
  `readonly_card_visible=true`
- `stale`: `metadata_read=true`, `preview_visible=false`,
  `readonly_card_visible=false`, `reasonCode=stale_artifact`
- `rollback`: `metadata_read=true`, `preview_visible=false`,
  `readonly_card_visible=false`, `reasonCode=fallback_used`
- `toggle_false`: `metadata_read=false`, `preview_visible=false`,
  `readonly_card_visible=false`, `reasonCode=initial`

The evidence confirms raw output and template tokens are not displayed in the
hidden cases, and all side-effect flags remain false.

## Compose Adapter Gate Contract - 2026-05-26

The H1 Compose adapter contract adds another pre-promotion guard between the
Diagnostic card model and any future Compose surface.

Required adapter outputs:

- `shouldShowSurface=true` only for visible success card models
- `shouldShowSurface=false` for hidden and rollback card models
- `insertIntoAssistantList=false`
- `persistToDb=false`
- `speakTts=false`
- `renderMarkdown=false`
- `stream=false`
- retry/fallback buttons false

This contract is unit-tested and does not connect to ChatScreen or normal UI.

## Diagnostic Host Gate Contract - 2026-05-26

`DevOnlyNpuPhaseH1PreviewHostState` adds a host-level gate after the Compose
adapter contract.

Promotion-relevant guarantees:

- success only: `visible=true`, `showCard=true`
- stale/rollback/hidden/toggle false: `visible=false`, `showCard=false`
- assistant insertion remains false
- DB/TTS/Markdown/streaming remain false
- retry/fallback remain false
- metadata read remains false at host level
- NPU run, `Engine.initialize`, and `RunDecode` remain false at host level

This is still Diagnostic-only and is not a normal UI promotion.

## XML Card / Host Consistency Gate - 2026-05-26

The H1 promotion gate now includes unit coverage that the current Diagnostic
XML/read-only card and the preview host expose the same text contract.

Gate checks added:

- success XML card text equals preview host render text
- hidden, stale, rollback, and toggle-false states produce no card text
- raw output and turn template tokens are absent
- assistant insertion and side-effect routes remain false
- metadata read, NPU run, `Engine.initialize`, and `RunDecode` remain false at
  host level

This prevents a future host/Compose surface from drifting away from the existing
Diagnostic card behavior.

## Preview Consistency Contract Gate - 2026-05-26

The gate now includes `DevOnlyNpuPhaseH1PreviewConsistencyTest`, which compares
the full read-only preview chain:

```text
XML card helper == PreviewRenderer == PreviewHost == Compose render text
```

For success metadata, the snapshot fixes badge/title/status/output/detail order
and confirms sanitized output only. For hidden, rollback, stale, and toggle-false
states, every render layer is empty/hidden. The same test verifies raw output and
turn template artifacts are absent and that assistant insertion, DB, TTS,
Markdown, streaming, retry/fallback, metadata read, NPU run, engine initialize,
and decode stay false.
