# NPU Return To Standard Route Plan

Scope: planning and documentation only. This document does not change Android
runtime behavior, UI, GPU/NPU route implementation, native libraries, hidden
configuration, or inference flow.

## Why This Plan Exists

GPU investigation is closed for now as experimental / diagnostics-only:

```text
GPU_PROMOTION_DECISION=blocked
GPU_ROOT_CAUSE_CANDIDATE=runtime_decode_fragmentation
PUBLIC_API_GAP_SUMMARY=public_selector_api_absent_native_executor_symbols_present
```

The practical route decision is:

- CPU route is the stable usable local route candidate.
- GPU remains DEV-only / experimental because long output corrupts at raw
  callback source.
- NPU work should return to the standard-route safety and promotion track.

## NPU Docs Inventory

| Document | Current role | Key reading |
| --- | --- | --- |
| `docs/litert_lm_npu_readiness.md` | High-level readiness and dependency state | Public `Backend.NPU` exists, QNN/HTP libraries are visible, but LiteRT Qualcomm dispatch API packaging / CLI proof remains a blocker for app-side NPU. |
| `docs/litert_qairt244_npu_turn_stop_quality_compare.md` | Hidden route display-quality baseline | Hidden QAIRT244 path has NPU evidence and a sanitizer-only display baseline, with strict safety invariants and no standard route connection. |
| `docs/litert_qairt244_ui_integration_safety_plan.md` | Diagnostic UI and normal UI safety boundary | Diagnostic Chat is isolated; normal `ChatScreen`, DB, TTS, Markdown, streaming, and normal `selectedPath=npu` remain disconnected. |
| `docs/litert_qairt244_dev_only_npu_route_adapter_plan.md` | DEV-only adapter boundary | Defines the smallest future adapter shape and required gate fields before any normal route experiment. |
| `docs/litert_qnn_qairt_coupling_findings.md` | QNN / QAIRT / dispatch coupling evidence | Earlier initialize-only custom stack hit dispatch coupling failures; QNN generation and library versions must be treated as aligned units. |
| `docs/litert_custom_build_plan.md` | Custom QAIRT244 build and hidden lifecycle history | Later hidden experiments narrow lifecycle, max token, cleanup, and process-boundary risks; they do not authorize standard route promotion. |

## Current NPU Progress

What is already proven or structured:

- Device class is viable for investigation: Nubia Z70S Ultra / SM8750 / V79.
- QNN/HTP/FastRPC evidence has been observed in hidden NPU runs.
- Public LiteRT-LM `Backend.NPU` API is detectable.
- Hidden diagnostic / custom route has produced successful bounded NPU output.
- Hidden route safety invariants exist:
  - no normal `ChatScreen` route connection
  - no normal `selectedPath=npu` persistence
  - no DB / TTS / Markdown / streaming ingress
  - explicit timeout and fresh crash handling
  - artifact-based evidence collection
- Display quality gate exists for sanitized output, especially
  `quality_classification=natural_japanese`.
- Lifecycle / cleanup / process-boundary artifacts exist for later hidden
  sequential experiments.

Still blocked or not promoted:

- Standard app-side NPU route is not implemented.
- Public readiness still records dispatch API / runtime packaging and CLI proof
  as blockers.
- Generic `.litertlm` models are not assumed to be NPU-compatible.
- QAIRT/QNN/LiteRT dispatch and model artifacts must be treated as aligned
  units, not as independent `.so` swaps.
- Hidden-route display cleanup is not enough to authorize normal UI promotion.
- 512-token hidden experiments remain isolated / review-only unless a later
  artifact proves clean lifecycle and quality under the same gate.

## Relationship To CPU Stable Route

CPU should remain the stable local route while NPU promotion is staged:

- CPU success is the baseline fallback / usability path.
- NPU work must not silently fallback to CPU and report success as NPU.
- If NPU fails, diagnostics must report NPU failure and preserve CPU route
  behavior separately.
- CPU route tests should remain part of every NPU promotion preflight.

## GPU Diagnostics To Reuse For NPU

The GPU investigation produced reusable diagnostic patterns:

- focused copy actions for route-specific keys
- compact diagnostics with prioritized key ordering
- route-specific key whitelist / key groups
- classifier scripts that turn copied diagnostics into decisions
- report generator sections for promotion blockers and next actions
- explicit `*_PROMOTION_DECISION`, `*_PROMOTION_BLOCKER`, and safe stop lines
- raw artifact summaries and artifact-driven investigation notes

NPU equivalents worth adding later:

```text
NPU_PROMOTION_DECISION
NPU_PROMOTION_DECISION_REASON
NPU_PROMOTION_BLOCKER
NPU_SAFE_NEXT_ACTION
NPU_BACKEND_EVIDENCE
NPU_ROUTE_ISOLATION_STATUS
NPU_OUTPUT_QUALITY_CLASSIFICATION
NPU_LIFECYCLE_CLEANUP_STATUS
```

Do this only as diagnostics/reporting work first. Do not connect standard
`ChatScreen` route behavior until the gate below passes.

## NPU Standard Route Promotion Gate

Minimum evidence before any standard-route experiment:

- `status=success`
- `fallback_used=false`
- `fresh_crash=false`
- `timeout=false`
- backend evidence shows real NPU / QNN / HTP execution, for example
  `QNN_HTP_V79_FastRPC_native_diag`
- no CPU/GPU route contamination
- no normal `selectedPath=npu` persistence before explicit promotion gate
- output quality is `natural_japanese`
- no visible template residue after display cleanup
- no prompt echo, repeated completion classification, or multilingual drift in
  displayed output
- validation matrix `short` may pass as `short_template_cleanup_pass` only when
  `output_quality_candidate_status=quality_candidate_pass` and sanitized /
  display text is natural Japanese
- validation matrix `quality_gate` may pass as
  `quality_gate_expected_rejection` only to prove unsafe template output is
  rejected; it is not a normal output success
- callback / tokenizer / stats diagnostics do not fail or go missing
- lifecycle evidence shows cleanup / close or safe isolation after each run
- DB, TTS, Markdown, and streaming remain disconnected until isolated route
  stability is proven
- model identity is NPU-compatible or SoC-specific, not inferred from a generic
  model name
- native stack / QAIRT / QNN / dispatch / model artifacts are aligned and
  documented

Standard route remains blocked if any of these are true:

- `fallback_used=true`
- `fresh_crash=true`
- `timeout=true`
- missing NPU/QNN/HTP backend evidence
- `quality_classification` is not `natural_japanese`
- normal route side effects appear before promotion
- `selected_path_npu_saved=true` before explicit approval
- dispatch/runtime/model provenance is unknown
- hidden route needs process force-stop to be safe for the tested mode
- `VALIDATION_WARNINGS=quality_gate_output_must_not_reach_ui_tts_db` appears
  without proof that rejected output is suppressed before UI/TTS/DB/Markdown /
  streaming ingress

Detailed standard-route DEV gate planning is tracked in:

- `docs/npu_standard_route_dev_gate_integration_plan.md`
- `docs/npu_quality_gate_output_suppression_plan.md`
- `docs/npu_settings_display_consolidation_plan.md`

Phase 1 diagnostic-only connection is complete when:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=1
npu_standard_route_phase=1
npu_standard_route_phase_name=1_route_entry_diagnostic
npu_standard_route_connected=true
conversation_created=false
generate_response=false
npu_standard_route_ui_append_allowed=false
npu_standard_route_tts_allowed=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
```

This phase must not create a conversation, call generate, append UI text, speak
TTS, save DB messages, render Markdown, or start streaming.

Phase 2 conversation-created diagnostic gate is complete when:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=2
npu_standard_route_phase=2
npu_standard_route_phase_name=2_conversation_created_diagnostic
npu_standard_route_connected=true
conversation_created=true
generate_response=false
npu_standard_route_ui_append_allowed=false
npu_standard_route_tts_allowed=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
```

Phase 2 remains diagnostic-only. It must not call native generate/decode, append
UI text, speak TTS, save DB messages, render Markdown, or start streaming. If
the quality candidate fails, output suppression and rollback diagnostics must
remain active before any later standard-route connection work proceeds.

Phase 3 generate-response diagnostic gate is complete when:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=3
npu_standard_route_phase=3
npu_standard_route_phase_name=3_generate_response_diagnostic
npu_standard_route_connected=true
conversation_created=true
generate_response=true
npu_standard_route_generate_diagnostic_only=true
npu_standard_route_output_delivery_allowed=false
npu_standard_route_ui_append_allowed=false
npu_standard_route_tts_allowed=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
```

Phase 3 may inspect the existing S1 generate result in diagnostics, but output
delivery remains closed. If `output_quality_candidate_status=quality_candidate_fail`,
then `npu_standard_route_output_suppressed=true`,
`npu_standard_route_suppression_reason=<output_quality_candidate_reason>`, and
`npu_standard_route_rollback_required=true` are required. Template artifact text
such as `_turn>` must not reach UI/TTS/DB/Markdown/Streaming.

Phase 4 UI append gate is complete when:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=4
npu_standard_route_phase=4
npu_standard_route_phase_name=4_ui_append_gate
npu_standard_route_connected=true
conversation_created=true
generate_response=true
npu_standard_route_generate_diagnostic_only=false
npu_standard_route_quality_gate_passed=true
npu_standard_route_output_suppressed=false
npu_standard_route_output_delivery_allowed=true
npu_standard_route_ui_append_allowed=true
npu_standard_route_ui_append_executed=true
npu_standard_route_output_delivery_executed=true
npu_standard_route_delivery_path=phase4_in_memory_ui_append
npu_standard_route_tts_allowed=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
```

Phase 4 opens only UI append for `quality_candidate_pass`. Rejected output must
remain suppressed with `npu_standard_route_output_delivery_allowed=false`,
`npu_standard_route_ui_append_allowed=false`, and
`npu_standard_route_rollback_required=true`. TTS, DB save, Markdown, and
streaming remain closed until later explicit phases. Phase 4 is not complete if
`npu_standard_route_ui_append_allowed=true` appears without
`npu_standard_route_ui_append_executed=true`.

Phase 5 TTS gate is complete when:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=5
npu_standard_route_phase=5
npu_standard_route_phase_name=5_tts_gate
npu_standard_route_connected=true
conversation_created=true
generate_response=true
npu_standard_route_quality_gate_passed=true
npu_standard_route_output_suppressed=false
npu_standard_route_output_delivery_allowed=true
npu_standard_route_ui_append_allowed=true
npu_standard_route_tts_allowed=true
npu_standard_route_tts_source=tts_text
npu_standard_route_ui_append_executed=true
npu_standard_route_tts_requested=true
npu_standard_route_tts_started=true
npu_standard_route_output_delivery_executed=true
npu_standard_route_delivery_path=phase5_in_memory_ui_append_and_tts
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
```

Phase 5 opens only TTS after UI append for `quality_candidate_pass`. Rejected
output must keep `npu_standard_route_tts_allowed=false` and
`npu_standard_route_tts_block_reason=quality_candidate_fail`. DB save, Markdown,
and streaming remain closed until later explicit phases. Phase 5 is not complete
if `npu_standard_route_tts_allowed=true` appears without
`npu_standard_route_tts_started=true`.

Phase 6 DB save gate is complete when:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=6
npu_standard_route_phase=6
npu_standard_route_phase_name=6_db_save_gate
npu_standard_route_connected=true
conversation_created=true
generate_response=true
npu_standard_route_quality_gate_passed=true
npu_standard_route_output_suppressed=false
npu_standard_route_output_delivery_allowed=true
npu_standard_route_ui_append_allowed=true
npu_standard_route_ui_append_executed=true
npu_standard_route_ui_append_target=db_backed_assistant_message
npu_standard_route_tts_allowed=true
npu_standard_route_tts_started=true
npu_standard_route_db_save_allowed=true
npu_standard_route_db_save_executed=true
npu_standard_route_db_save_target=assistant_message
npu_standard_route_db_assistant_id_present=true
npu_standard_route_db_saved_text_length=<length>
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
```

Phase 6 opens only DB save after UI append and TTS for
`quality_candidate_pass`. The assistant row should be DB-backed rather than a
duplicate transient row. Rejected output must keep
`npu_standard_route_db_save_allowed=false`,
`npu_standard_route_db_save_executed=false`, and
`npu_standard_route_db_save_block_reason=quality_candidate_fail`. Phase 7 must
not start until Phase 6 confirms DB save success and confirms `_turn>` /
`raw_unexpected_start_turn` output never reaches UI, TTS, or DB.

Phase 7A Markdown gate is complete when:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=7
npu_standard_route_phase=7
npu_standard_route_phase_name=7_markdown_gate
npu_standard_route_connected=true
conversation_created=true
generate_response=true
npu_standard_route_quality_gate_passed=true
npu_standard_route_ui_append_executed=true
npu_standard_route_tts_started=true
npu_standard_route_db_save_executed=true
npu_standard_route_markdown_allowed=true
npu_standard_route_markdown_executed=true
npu_standard_route_markdown_mode=<mode>
npu_standard_route_streaming_allowed=false
npu_standard_route_streaming_executed=false
npu_standard_route_rollback_required=false
```

Phase 7A opens only Markdown after UI/TTS/DB for `quality_candidate_pass`.
Rejected output must keep `npu_standard_route_markdown_allowed=false` and
`npu_standard_route_markdown_executed=false`. Streaming remains closed until
Phase 7B. Phase 7B must not start until Phase 7A confirms Markdown execution and
confirms `_turn>` / `raw_unexpected_start_turn` output never reaches Markdown.

Phase 7B pseudo streaming gate is complete when:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=8
npu_standard_route_phase=8
npu_standard_route_phase_name=7b_pseudo_streaming_gate
npu_standard_route_quality_gate_passed=true
npu_standard_route_ui_append_executed=true
npu_standard_route_tts_started=true
npu_standard_route_db_save_executed=true
npu_standard_route_markdown_executed=true
npu_standard_route_streaming_allowed=true
npu_standard_route_streaming_executed=true
npu_standard_route_streaming_mode=pseudo_final_text
npu_standard_route_streaming_source=markdown_finalized_text
npu_standard_route_native_streaming_used=false
npu_standard_route_streaming_text_matches_db=true
npu_standard_route_streaming_text_matches_markdown=true
npu_standard_route_rollback_required=false
```

Phase 7B does not implement native token streaming. It streams only safe final
text that has already passed the quality gate and Markdown finalization. Rejected
output must keep `npu_standard_route_streaming_executed=false` and
`npu_standard_route_streaming_block_reason=quality_candidate_fail`. Native
streaming remains deferred until LiteRT-LM exposes reliable chunk/finish
telemetry for this NPU route.

Final promotion readiness is reviewed by
`scripts/review_npu_standard_route_final_promotion.sh` and documented in
`docs/npu_standard_route_final_promotion_review.md`. A Phase 7B success artifact
can produce `PROMOTION_DECISION=go`; a `quality_candidate_fail` artifact can
produce `NPU_STANDARD_ROUTE_FINAL_REVIEW=suppression_pass`, which confirms safe
suppression but is not itself promotion-ready.

Settings should treat S1-S8 as NPU standard-route phases, not separate hardware
backends. Existing preference keys remain compatible while labels are redesigned
around `NPU Experimental / DEV` plus a developer phase selector.

Settings consolidation rollout readiness is reviewed by
`scripts/review_npu_rollout_readiness.sh` and documented in
`docs/npu_settings_consolidation_rollout_plan.md`. It consumes either raw Phase
7B diagnostics or final promotion review output. A positive final promotion
review can produce `NPU_ROLLOUT_READY=true`, but rollout risk remains `medium`
until the Settings UI consolidation, developer phase selector, and monitoring
work are implemented in later UI-only tasks.

## Next Device Confirmation Keys

When the device is available, collect or preserve:

```text
status
npu_backend
npu_backend_evidence
backend_evidence
fallback_used
fresh_crash
timeout
quality_classification
selected_path_npu_saved
selected_path_npu_normal_route
normal_ui_route_connected
standard_route_connected
conversation_created
generate_response
db
tts
markdown
streaming
max_output_tokens
native_max_output_tokens_limit
decode_elapsed_ms
artifact_path
run_id
cleanup_status
engine_close_evidence
fresh_tombstone_status
```

The key list should be copied from artifacts or diagnostics, not inferred from
UI behavior alone.

## Next Codex Tasks

Keep the next tasks small and evidence-producing:

1. Add an NPU diagnostics key list document or fixture that mirrors the GPU
   focused-copy key grouping.
2. Add a docs-only NPU classifier design:
   `NPU_PROMOTION_DECISION=blocked/ready_for_hidden_soak/ready_for_standard_probe`.
3. Add a report-generator TODO section for NPU artifacts without implementing
   Android route behavior.
4. Add tests or script fixtures only if they operate on copied diagnostics or
   committed text fixtures.
5. Re-run static dispatch/package checks before any new NPU runtime attempt:
   `scripts/check_litert_npu_dispatch.sh`.
6. Prepare a device-run checklist for hidden route confirmation; do not connect
   normal `ChatScreen` yet.

## Implementation Priority

Use this order when moving from planning into implementation:

| Priority | Work item | Scope |
| --- | --- | --- |
| A | NPU diagnostic key copy | Implemented as `NPU診断キーをコピー`; captures backend evidence, route isolation, quality, cleanup, and promotion-gate inputs. |
| B | NPU classifier | Implemented as `scripts/classify_npu_diagnostic_result.sh`; classifies copied diagnostics and DEV NPU compact/details into promotion candidate / blocker categories. |
| C | NPU report generator | Implemented as `scripts/render_npu_investigation_report.sh`; summarizes device runs, classifier output, backend evidence, gate status, cleanup, blockers, and next actions. |
| D | NPU promotion readiness review | Implemented as `scripts/review_npu_promotion_readiness.sh`; aggregates repeatability runs into readiness, score, passed gates, failed gates, remaining blockers, and next action. |
| D1 | NPU quality alignment review | Implemented as `scripts/review_npu_quality_alignment.sh`; reviews `quality_classification` against candidate/display output without relaxing the promotion gate. |
| D2 | NPU standard route connection review | Implemented as `scripts/review_npu_standard_route_connection.sh`; converts readiness / classifier / device-run evidence into a go/no-go review for a future DEV-only standard route connection. |
| D3 | NPU promotion final review | Implemented as `scripts/review_npu_promotion_final.sh`; combines readiness, quality alignment, and connection review into the final pre-connection go/no-go decision. |
| E | ChatScreen standard route integration | DEV-only route connection after hidden route and classifier gates pass. |
| F | DB / TTS / Markdown / Streaming integration | Add one integration boundary at a time after route stability. |
| G | Full promotion | Only after repeated standard-route success, quality, cleanup, and regression evidence. |

Priority A through D should remain diagnostics/reporting work. Priority E and
later require a separate approval because they change Android route behavior.

Use the Priority A copy action for first-pass device evidence capture. GPU
diagnostic copy focuses on executor / callback corruption; NPU diagnostic copy
captures `fallback_used`, `fresh_crash`, `timeout`, `npu_backend_evidence`,
`standard_route_connected`, `quality_classification`, `cleanup_status`, and
`engine_close_evidence` as stable promotion-gate inputs.

Use the Priority B classifier on copied diagnostics or DEV compact/details:

```bash
scripts/classify_npu_diagnostic_result.sh --input artifacts/device_runs/npu_s1_latest.txt
```

Current S1 engine-create-failed diagnostics should classify as:

```text
NPU_CLASSIFICATION=npu_engine_create_failed
NPU_PROMOTION_BLOCKER=true
NPU_PROMOTION_DECISION=blocked
NPU_PROMOTION_DECISION_REASON=engine_create_failed
NPU_ROOT_CAUSE_CANDIDATE=litert_npu_compiled_model_executor_failure
NEXT_ACTION=inspect_qairt_qnn_model_runtime_alignment_and_recreate_guard
```

If `output_quality_candidate_status=quality_candidate_pass` but
`quality_classification=template_artifact` or `unknown`, classify it as an
intermediate blocker:

```text
NPU_CLASSIFICATION=npu_quality_candidate_pass_with_template_cleanup
NPU_PROMOTION_BLOCKER=true
NPU_PROMOTION_DECISION=blocked
NPU_PROMOTION_DECISION_REASON=quality_candidate_pass_but_primary_classification_not_natural_japanese
NEXT_ACTION=run_repeatability_matrix_and_align_quality_classification_with_candidate_gate
```

This preserves the full promotion requirement that the primary
`quality_classification` must be `natural_japanese`.

If the candidate output passes but the primary classification is
`mixed_language`, classify it as a success-leaning intermediate blocker:

```text
NPU_CLASSIFICATION=npu_quality_candidate_pass_with_mixed_language_terms
NPU_PROMOTION_BLOCKER=true
NPU_PROMOTION_DECISION=blocked
NPU_PROMOTION_DECISION_REASON=mixed_language_classification_with_quality_candidate_pass
NEXT_ACTION=run_repeatability_matrix_and_review_mixed_language_gate
```

Use this for outputs where English proper nouns such as `Google DeepMind` or
`Gemma 4` trigger mixed-language detection while the Japanese response is
otherwise natural. It does not relax the full promotion gate.

Use the Priority C report generator after saving copied NPU diagnostics:

```bash
scripts/render_npu_investigation_report.sh \
  --device-runs artifacts/device_runs \
  --output artifacts/npu_investigation_report/NPU_INVESTIGATION_REPORT.md
```

The report embeds the classifier output, promotion gate summary, failure layer,
crash/tombstone status, cleanup evidence, root cause ranking, and next action.

Use the Priority D readiness review for multi-run promotion assessment:

```bash
scripts/review_npu_promotion_readiness.sh --device-runs artifacts/device_runs
```

Current repeatability results should evaluate as:

```text
NPU_PROMOTION_READINESS=near_candidate
NPU_PROMOTION_READINESS_SCORE=80
PASSED_GATES=status,backend,backend_evidence,decode,native_call_returned,cleanup,no_fallback,no_timeout,no_crash
FAILED_GATES=quality_alignment
REMAINING_BLOCKERS=quality_classification_alignment
NEXT_ACTION=collect_repeatability_matrix_and_review_standard_route_connection
```

This means NPU standard promotion is close but still blocked on quality
classification alignment and standard route connection review. It does not
authorize Android route changes.

Use the D1 quality alignment review to explain the remaining blocker without
relaxing the gate:

```bash
scripts/review_npu_quality_alignment.sh --device-runs artifacts/device_runs
```

For the current three-prompt repeatability set, expected output is:

```text
NPU_QUALITY_ALIGNMENT=classifier_alignment_needed
QUALITY_ALIGNMENT_SCORE=86
PASSED_ALIGNMENTS=template_cleanup_candidate,mixed_language_proper_noun_candidate,natural_japanese
FAILED_ALIGNMENTS=primary_quality_classification_alignment
QUALITY_MISMATCHES=template_artifact_vs_candidate_pass,mixed_language_vs_candidate_pass
NEXT_ACTION=review_quality_classifier_alignment_without_relaxing_promotion_gate
```

This keeps `quality_classification=natural_japanese` as the full promotion
requirement while making template cleanup and mixed-language proper-noun cases
visible as reviewable mismatches.

Use the D2 standard route connection review before any route implementation:

```bash
scripts/review_npu_standard_route_connection.sh --device-runs artifacts/device_runs
```

For the current repeatability set, expected output is:

```text
NPU_STANDARD_ROUTE_REVIEW=needs_quality_alignment
READY_FOR_CONNECTION=false
FAILED_GATES=quality_gate_review,standard_route_connected,conversation_created,generate_response,engine_close_evidence
ROLLBACK_RISKS=none
NEXT_ACTION=finish_quality_alignment_before_standard_route_connection
```

The review deliberately distinguishes NPU DEV route success from standard route
connection readiness. `near_candidate` is not enough to connect normal
`ChatScreen` NPU behavior while `quality_classification_alignment` remains.

See `docs/npu_standard_route_connection_review.md` for the pre-connection
gate, post-connection checklist, stop line, and rollback criteria.

Use the D3 final review as the last stop/go summary before implementation:

```bash
scripts/review_npu_promotion_final.sh --device-runs artifacts/device_runs
```

Current expected output is:

```text
NPU_PROMOTION_FINAL_REVIEW=quality_alignment_pending
READY_FOR_STANDARD_ROUTE=false
PROMOTION_SCORE=83
SAFE_NEXT_ACTION=finish_quality_classification_alignment_before_standard_route_connection
```

This means the next task is quality-classifier alignment review, not standard
route connection. See `docs/npu_promotion_final_review.md`.

Use the alignment decision review to decide whether the remaining quality
alignment blocker is a hard quality failure or a conservative warning:

```bash
scripts/review_npu_quality_alignment_decision.sh --device-runs artifacts/device_runs
```

Current expected output:

```text
NPU_ALIGNMENT_DECISION=review_warning
ALIGNMENT_IS_HARD_BLOCKER=false
ALIGNMENT_IS_REVIEW_WARNING=true
CONFIDENCE_SCORE=80
SAFE_NEXT_ACTION=collect_additional_repeatability_data_before_standard_route_connection
```

This does not authorize route connection. It means the current blocker is best
handled as a review warning while repeatability evidence is expanded.

Implementation tasks to defer until separately approved:

- normal `ChatScreen` NPU branch
- standard route backend setting persistence
- high-level `generateResponse` NPU path
- DB / TTS / Markdown / streaming integration
- native library staging or dispatch runtime changes

## Safe Stop Line

Do not proceed with standard route integration while:

- GPU remains blocked and experimental.
- NPU evidence depends on hidden isolated routes only.
- dispatch / QAIRT / QNN / model alignment is unresolved.
- lifecycle safety requires force-stop or per-run isolation for the target mode.
- output quality is not consistently natural Japanese.

The immediate development posture is:

```text
CPU_STABLE_ROUTE=maintain
GPU_ROUTE=experimental_diagnostics_only
NPU_ROUTE=return_to_hidden_gate_and_standard_promotion_plan
```
