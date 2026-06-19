# NPU Standard Route DEV Gate Integration Plan

Scope: planning only. Do not change Kotlin runtime, Android route behavior,
NPU route selection, DB, TTS, Markdown, streaming, native libraries, hidden
configuration, fallback behavior, or production defaults in this phase.

## Current State

NPU S1 DEV route has passed backend / decode / cleanup / repeatability /
validation coverage:

```text
NPU_VALIDATION_RESULT=pass
VALIDATION_SCORE=100
PASSED_CASES=short_template_cleanup_pass,medium,long,markdown,mixed_language,quality_gate_expected_rejection
FAILED_CASES=none
VALIDATION_WARNINGS=quality_gate_output_must_not_reach_ui_tts_db
PROMOTION_RECOMMENDATION=ready_for_standard_route_review_with_stop_line
NEXT_ACTION=enforce_quality_gate_output_suppression_before_standard_route_connection
```

Settings display consolidation is tracked in
`docs/npu_settings_display_consolidation_plan.md`. S1-S6 are staged NPU standard
route phases, not separate hardware backends.

Confirmed NPU DEV route evidence:

```text
status=success
selected_backend=NPU_S1
requested_backend=NPU
effective_backend=NPU
backend_evidence=QNN_HTP_V79_FastRPC_native_diag
route_family=npu_s1
run_decode_reached=true
timeout=false
fallback=false
fresh_crash=false
native_stage=adapter_success
native_call_returned=true
native_decode_started=true
native_decode_finished=true
native_cleanup_reached=true
```

## Code Inventory

Read-only inspection found these existing boundaries:

- `NpuStandardRouteS1Contract.kt`
  - S1 result, `successCriteriaMet`, `actualDisplayText`, `ttsText`, side-effect
    flags, and `outputQualityCandidateStatus`.
  - `successCriteriaMet` rejects `quality_candidate_fail`.
- `NpuStandardRouteS1Mapper.kt`
  - Maps raw native result into S1 result and computes display text from
    quality candidate / sanitized output.
- `NpuS1PersistentCustomJniDiagnostics.kt`
  - Defines quality candidate constants and prompt/profile evidence.
- `ChatScreen.kt`
  - Has existing staged helpers for S1 display, S2 DB, S3 Markdown, S4 pseudo
    streaming, and S5 TTS.
- `NpuStandardRouteS2DbMapper.kt`
  - Builds DB save candidate only if `s1Result.successCriteriaMet`.
- `NpuStandardRouteS3MarkdownMapper.kt`
  - Builds Markdown candidate only if `s1Result.successCriteriaMet`.
- `NpuStandardRouteS4PseudoStreamingMapper.kt`
  - Builds pseudo-streaming chunks only if `s1Result.successCriteriaMet`.
- `NpuStandardRouteS5TtsMapper.kt`
  - Builds TTS candidate only if `s1Result.successCriteriaMet` and TTS gates
    pass.
- `NpuDiagnosticCopyText.kt`
  - Already exposes stable copied NPU diagnostic keys for device-run review.

This existing shape is compatible with a staged DEV gate. The implementation
plan should preserve the S1-to-S5 separation and must not bypass
`successCriteriaMet`.

## Passed Gates

- NPU backend evidence is present: `QNN_HTP_V79_FastRPC_native_diag`.
- Decode path is reached and completes.
- Native call returns.
- Native cleanup is reached.
- `fallback=false`.
- `timeout=false`.
- `fresh_crash=false`.
- Validation matrix reaches score 100.
- `short_template_cleanup_pass` confirms safe template cleanup candidate.
- `quality_gate_expected_rejection` confirms unsafe template output is rejected
  by the quality candidate gate.

## Remaining Stop Line

The remaining blocker is:

```text
quality_gate_output_must_not_reach_ui_tts_db
```

The validation `quality_gate` case can produce:

```text
raw_output=_turn>\n<end_of_turn>\n<start_of_turn>model_turn>\n<end_of_turn>
sanitized_output=_turn>
actual_display_text=_turn>
tts_text=_turn>
output_quality_candidate_status=quality_candidate_fail
output_quality_candidate_reason=raw_unexpected_start_turn
```

This is an expected rejection for validation matrix purposes, but it must never
flow to UI append, TTS, DB save, Markdown, or streaming in the standard route.

## DEV Gate Property

Use a single explicit opt-in property for any future standard-route candidate:

```text
debug.lami.npu_standard_route_dev_gate=true
```

Phase selection is read only when the DEV gate is enabled:

```text
debug.lami.npu_standard_route_phase=1
debug.lami.npu_standard_route_phase=2
```

Unset, blank, or invalid phase values fall back to Phase 1. This keeps the
diagnostic path conservative if a stale or malformed property remains on the
device.

Default behavior:

```text
debug.lami.npu_standard_route_dev_gate=false
```

When false:

- standard route NPU connection remains disabled
- normal CPU route remains unchanged
- GPU experimental diagnostics remain unchanged
- no DB/TTS/Markdown/Streaming NPU side effects are allowed

## Phase Order

### Phase 0: docs/scripts only

Current phase. No Kotlin behavior changes.

### Phase 1: route entry diagnostics only

Goal: show whether the standard-route DEV gate would select the NPU candidate.

Do not create conversation or generate output.

Expected diagnostics:

```text
npu_standard_route_dev_gate_enabled=true
npu_standard_route_phase=1_route_entry_diagnostic
npu_standard_route_connected=false
conversation_created=false
generate_response=false
```

Implemented Phase 1 diagnostics use:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=1
```

and emit route-decision diagnostics only for NPU backend candidates. CPU and GPU
routes do not emit these keys even when the property is set.

Phase 1 completed criteria:

```text
npu_standard_route_dev_gate_enabled=true
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
npu_standard_route_rollback_required=false
```

If a future diagnostic fixture passes `quality_candidate_fail`, Phase 1 must
still suppress output and require rollback:

```text
npu_standard_route_quality_gate_passed=false
npu_standard_route_output_suppressed=true
npu_standard_route_suppression_reason=quality_candidate_fail
npu_standard_route_rollback_required=true
npu_standard_route_rollback_reason=quality_gate_output_must_not_reach_ui_tts_db
```

### Phase 2: conversation creation only

Goal: validate ChatScreen / route plumbing without native generation.

Phase 2 is enabled with:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=2
```

Do not call generate. In the current diagnostic-only implementation,
`conversation_created=true` means the standard-route connection has advanced to
the conversation-created gate for review; it does not permit native generate,
UI append, TTS, DB, Markdown, or streaming.

Expected diagnostics:

```text
npu_standard_route_dev_gate_enabled=true
npu_standard_route_phase=2
npu_standard_route_phase_name=2_conversation_created_diagnostic
npu_standard_route_connected=true
conversation_created=true
generate_response=false
npu_standard_route_quality_gate_passed=true
npu_standard_route_output_suppressed=false
npu_standard_route_ui_append_allowed=false
npu_standard_route_tts_allowed=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
npu_standard_route_rollback_required=false
npu_standard_route_rollback_reason=none
```

Phase 2 stop line:

- `generate_response` must remain `false`
- all output/persistence surfaces must remain disallowed
- if `output_quality_candidate_status=quality_candidate_fail`, set
  `npu_standard_route_output_suppressed=true`,
  `npu_standard_route_suppression_reason=quality_candidate_fail`, and
  `npu_standard_route_rollback_required=true`
- any evidence that rejected output reached UI/TTS/DB/Markdown/Streaming stops
  the standard-route connection work

Device confirmation command:

```bash
adb shell setprop debug.lami.npu_standard_route_dev_gate true
adb shell setprop debug.lami.npu_standard_route_phase 2
adb shell monkey -p io.github.ninbyo02.lami 1
```

### Phase 3: generate response with output suppression

Goal: reach the generate-response diagnostic gate through the existing S1 DEV
route result, but keep all delivery surfaces closed. This phase may collect and
report the NPU result in compact/full dump diagnostics, but it must not send
text to UI append, TTS, DB, Markdown, or streaming.

Phase 3 is enabled with:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=3
```

Only a candidate pass can be considered safe for later phases:

```text
output_quality_candidate_status=quality_candidate_pass
```

If candidate fails, suppress all user-facing / persistence surfaces.

Expected diagnostics on candidate pass:

```text
npu_standard_route_dev_gate_enabled=true
npu_standard_route_phase=3
npu_standard_route_phase_name=3_generate_response_diagnostic
npu_standard_route_connected=true
conversation_created=true
generate_response=true
npu_standard_route_generate_diagnostic_only=true
npu_standard_route_quality_gate_passed=true
npu_standard_route_output_suppressed=false
npu_standard_route_suppression_reason=none
npu_standard_route_output_delivery_allowed=false
npu_standard_route_candidate_text_present=true
npu_standard_route_ui_append_allowed=false
npu_standard_route_tts_allowed=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
npu_standard_route_rollback_required=false
npu_standard_route_rollback_reason=none
```

Expected diagnostics on candidate fail:

```text
npu_standard_route_phase=3
npu_standard_route_phase_name=3_generate_response_diagnostic
conversation_created=true
generate_response=true
npu_standard_route_quality_gate_passed=false
npu_standard_route_output_suppressed=true
npu_standard_route_suppression_reason=<output_quality_candidate_reason>
npu_standard_route_output_delivery_allowed=false
npu_standard_route_ui_append_allowed=false
npu_standard_route_tts_allowed=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
npu_standard_route_rollback_required=true
npu_standard_route_rollback_reason=quality_candidate_fail_output_suppressed_before_ui_tts_db
```

Phase 3 stop line:

- UI/TTS/DB/Markdown/Streaming allowed keys must remain `false`
- `npu_standard_route_output_delivery_allowed=false` must remain visible
- `quality_candidate_fail` output must be suppressed before every delivery
  surface
- `_turn>` or other template artifact output must remain diagnostic-only
- fallback, timeout, fresh crash, decode-not-reached, or cleanup-not-reached
  evidence requires rollback diagnostics

Device confirmation command:

```bash
adb shell setprop debug.lami.npu_standard_route_dev_gate true
adb shell setprop debug.lami.npu_standard_route_phase 3
adb shell monkey -p io.github.ninbyo02.lami 1
```

### Phase 4: UI append

Only after Phase 3 proves suppression, allow UI append for candidate pass. This
phase remains DEV-gated and still keeps TTS, DB save, Markdown, and streaming
closed.

Phase 4 is enabled with:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=4
```

Expected diagnostics on candidate pass:

```text
npu_standard_route_phase=4
npu_standard_route_phase_name=4_ui_append_gate
npu_standard_route_connected=true
conversation_created=true
generate_response=true
npu_standard_route_generate_diagnostic_only=false
npu_standard_route_quality_gate_passed=true
npu_standard_route_output_suppressed=false
npu_standard_route_suppression_reason=none
npu_standard_route_output_delivery_allowed=true
npu_standard_route_candidate_text_present=true
npu_standard_route_ui_append_allowed=true
npu_standard_route_ui_append_source=actual_display_text
npu_standard_route_ui_append_block_reason=none
npu_standard_route_ui_append_executed=true
npu_standard_route_output_delivery_executed=true
npu_standard_route_delivery_path=phase4_in_memory_ui_append
npu_standard_route_tts_allowed=false
npu_standard_route_tts_started=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
npu_standard_route_rollback_required=false
npu_standard_route_rollback_reason=none
```

Expected diagnostics on candidate fail:

```text
npu_standard_route_phase=4
npu_standard_route_phase_name=4_ui_append_gate
conversation_created=true
generate_response=true
npu_standard_route_quality_gate_passed=false
npu_standard_route_output_suppressed=true
npu_standard_route_suppression_reason=<output_quality_candidate_reason>
npu_standard_route_output_delivery_allowed=false
npu_standard_route_ui_append_allowed=false
npu_standard_route_ui_append_source=blocked_quality_candidate_fail
npu_standard_route_ui_append_block_reason=quality_candidate_fail
npu_standard_route_ui_append_executed=false
npu_standard_route_output_delivery_executed=false
npu_standard_route_tts_allowed=false
npu_standard_route_tts_started=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
npu_standard_route_rollback_required=true
npu_standard_route_rollback_reason=quality_candidate_fail_output_suppressed_before_ui_tts_db
```

Phase 4 stop line:

- `quality_candidate_fail` must keep `npu_standard_route_ui_append_allowed=false`
- `_turn>` or other template artifact display text must remain suppressed
- TTS/DB/Markdown/Streaming allowed keys must remain `false`
- `npu_standard_route_ui_append_executed=true` is required before Phase 4 is
  considered integrated; `allowed=true` alone is only a gate decision
- fallback, timeout, fresh crash, decode-not-reached, or cleanup-not-reached
  evidence requires rollback diagnostics

Device confirmation command:

```bash
adb shell setprop debug.lami.npu_standard_route_dev_gate true
adb shell setprop debug.lami.npu_standard_route_phase 4
adb shell monkey -p io.github.ninbyo02.lami 1
```

### Phase 5: TTS

Only after UI append is stable, allow TTS for candidate pass. This phase remains
DEV-gated and keeps DB save, Markdown, and streaming closed.

Phase 5 is enabled with:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=5
```

Expected diagnostics on candidate pass:

```text
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
npu_standard_route_tts_text_length=<length>
npu_standard_route_tts_block_reason=none
npu_standard_route_ui_append_executed=true
npu_standard_route_tts_requested=true
npu_standard_route_tts_started=true
npu_standard_route_output_delivery_executed=true
npu_standard_route_delivery_path=phase5_in_memory_ui_append_and_tts
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
npu_standard_route_rollback_required=false
```

Expected diagnostics on candidate fail:

```text
npu_standard_route_phase=5
npu_standard_route_phase_name=5_tts_gate
generate_response=true
npu_standard_route_quality_gate_passed=false
npu_standard_route_output_suppressed=true
npu_standard_route_suppression_reason=<output_quality_candidate_reason>
npu_standard_route_output_delivery_allowed=false
npu_standard_route_ui_append_allowed=false
npu_standard_route_tts_allowed=false
npu_standard_route_tts_source=blocked_quality_candidate_fail
npu_standard_route_tts_text_length=0
npu_standard_route_tts_block_reason=quality_candidate_fail
npu_standard_route_ui_append_executed=false
npu_standard_route_tts_requested=false
npu_standard_route_tts_started=false
npu_standard_route_output_delivery_executed=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
npu_standard_route_rollback_required=true
npu_standard_route_rollback_reason=quality_candidate_fail_output_suppressed_before_ui_tts_db
```

Phase 5 stop line:

- `quality_candidate_fail` must keep both UI append and TTS disallowed
- `_turn>` or other template artifact display/TTS text must remain suppressed
- DB/Markdown/Streaming allowed keys must remain `false`
- `npu_standard_route_tts_started=true` is required before Phase 5 is
  considered integrated; `tts_allowed=true` alone is only a gate decision
- fallback, timeout, fresh crash, decode-not-reached, or cleanup-not-reached
  evidence requires rollback diagnostics

Device confirmation command:

```bash
adb shell setprop debug.lami.npu_standard_route_dev_gate true
adb shell setprop debug.lami.npu_standard_route_phase 5
adb shell monkey -p io.github.ninbyo02.lami 1
```

### Phase 6: DB save

Only after UI/TTS behavior is stable. Phase 6 remains DEV-gated and adds DB
save for candidate-pass output only. Markdown and streaming stay closed.

Phase 6 is enabled with:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=6
```

Expected diagnostics on candidate pass:

```text
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
npu_standard_route_tts_requested=true
npu_standard_route_tts_started=true
npu_standard_route_db_save_allowed=true
npu_standard_route_db_save_executed=true
npu_standard_route_db_save_target=assistant_message
npu_standard_route_db_saved_text_length=<length>
npu_standard_route_db_assistant_id_present=true
npu_standard_route_db_message_replaced_transient=true
npu_standard_route_db_conversation_id_present=true
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
npu_standard_route_rollback_required=false
```

Phase 6 uses the same assistant-message persistence path as the existing local
route DB save, but only under the explicit Phase 6 DEV gate. The UI row should be
DB-backed rather than a duplicate transient assistant row. The saved text is the
safe display candidate selected after `quality_candidate_pass`.

Expected diagnostics on candidate fail:

```text
npu_standard_route_phase=6
npu_standard_route_phase_name=6_db_save_gate
generate_response=true
npu_standard_route_quality_gate_passed=false
npu_standard_route_output_suppressed=true
npu_standard_route_suppression_reason=<output_quality_candidate_reason>
npu_standard_route_output_delivery_allowed=false
npu_standard_route_ui_append_allowed=false
npu_standard_route_ui_append_executed=false
npu_standard_route_tts_allowed=false
npu_standard_route_tts_started=false
npu_standard_route_db_save_allowed=false
npu_standard_route_db_save_executed=false
npu_standard_route_db_save_block_reason=quality_candidate_fail
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
npu_standard_route_rollback_required=true
npu_standard_route_rollback_reason=quality_candidate_fail_output_suppressed_before_ui_tts_db
```

Phase 6 stop line:

- `quality_candidate_fail` must keep UI append, TTS, and DB save disallowed
- `_turn>` or other template artifact output must never be saved
- Markdown and streaming allowed keys must remain `false`
- `db_save_allowed=true` is not enough; `db_save_executed=true` and an assistant
  id are required before Phase 6 is considered integrated
- duplicate transient plus DB-backed assistant rows indicate a failed integration

Device confirmation command:

```bash
adb shell setprop debug.lami.npu_standard_route_dev_gate true
adb shell setprop debug.lami.npu_standard_route_phase 6
adb shell monkey -p io.github.ninbyo02.lami 1
```

### Phase 7A: Markdown

Connect Markdown only after Phase 6 confirms DB-backed UI display without
duplicates and confirms `quality_candidate_fail` output never reaches DB.
Streaming remains closed.

Phase 7A is enabled with:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=7
```

Expected diagnostics on candidate pass:

```text
npu_standard_route_phase=7
npu_standard_route_phase_name=7_markdown_gate
npu_standard_route_connected=true
conversation_created=true
generate_response=true
npu_standard_route_ui_append_executed=true
npu_standard_route_tts_started=true
npu_standard_route_db_save_executed=true
npu_standard_route_markdown_allowed=true
npu_standard_route_markdown_executed=true
npu_standard_route_markdown_mode=<mode>
npu_standard_route_markdown_block_reason=none
npu_standard_route_streaming_allowed=false
npu_standard_route_streaming_executed=false
npu_standard_route_rollback_required=false
```

Expected diagnostics on candidate fail:

```text
npu_standard_route_phase=7
npu_standard_route_phase_name=7_markdown_gate
npu_standard_route_output_suppressed=true
npu_standard_route_ui_append_executed=false
npu_standard_route_tts_started=false
npu_standard_route_db_save_executed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_markdown_executed=false
npu_standard_route_markdown_block_reason=quality_candidate_fail
npu_standard_route_streaming_allowed=false
npu_standard_route_streaming_executed=false
npu_standard_route_rollback_required=true
```

Phase 7A uses the existing `NpuStandardRouteS3MarkdownBridge` and
`buildFinalizedStreamingResponseForPersist` path. The finalized Markdown text is
the DB-backed assistant text for this DEV phase. `quality_candidate_fail` output
must never be passed to Markdown rendering/finalization.

Phase 7A stop line:

- `quality_candidate_fail` must keep UI append, TTS, DB, and Markdown execution
  disabled
- `_turn>` or template artifact output must not reach Markdown
- `npu_standard_route_streaming_allowed` and
  `npu_standard_route_streaming_executed` must remain `false`
- Markdown is not complete if `markdown_allowed=true` appears without
  `markdown_executed=true`

Device confirmation command:

```bash
adb shell setprop debug.lami.npu_standard_route_dev_gate true
adb shell setprop debug.lami.npu_standard_route_phase 7
adb shell monkey -p io.github.ninbyo02.lami 1
```

### Phase 7B: Pseudo Streaming

Phase 7B uses property phase `8` because the phase selector is integer based:

```text
debug.lami.npu_standard_route_dev_gate=true
debug.lami.npu_standard_route_phase=8
```

This phase is pseudo streaming only. It does not call native token streaming and
does not change LiteRT-LM / JNI / native code. The NPU generation result is still
collected as complete final text first, then the safe finalized text is split
into cumulative UI chunks with the existing pseudo-streaming mapper.

Expected diagnostics on candidate pass:

```text
npu_standard_route_phase=8
npu_standard_route_phase_name=7b_pseudo_streaming_gate
npu_standard_route_connected=true
conversation_created=true
generate_response=true
npu_standard_route_quality_gate_passed=true
npu_standard_route_output_suppressed=false
npu_standard_route_ui_append_executed=true
npu_standard_route_tts_started=true
npu_standard_route_db_save_executed=true
npu_standard_route_markdown_executed=true
npu_standard_route_streaming_allowed=true
npu_standard_route_streaming_executed=true
npu_standard_route_streaming_mode=pseudo_final_text
npu_standard_route_streaming_source=markdown_finalized_text
npu_standard_route_streaming_chunk_count=<count>
npu_standard_route_streaming_final_text_length=<length>
npu_standard_route_native_streaming_used=false
npu_standard_route_streaming_text_matches_db=true
npu_standard_route_streaming_text_matches_markdown=true
npu_standard_route_rollback_required=false
```

Expected diagnostics on candidate fail:

```text
npu_standard_route_phase=8
npu_standard_route_phase_name=7b_pseudo_streaming_gate
npu_standard_route_quality_gate_passed=false
npu_standard_route_output_suppressed=true
npu_standard_route_ui_append_executed=false
npu_standard_route_tts_started=false
npu_standard_route_db_save_executed=false
npu_standard_route_markdown_executed=false
npu_standard_route_streaming_allowed=false
npu_standard_route_streaming_executed=false
npu_standard_route_streaming_block_reason=quality_candidate_fail
npu_standard_route_native_streaming_used=false
npu_standard_route_rollback_required=true
```

Phase 7B stop line:

- native token streaming remains disabled and
  `npu_standard_route_native_streaming_used=false`
- streaming source must be `markdown_finalized_text` or another safe final text,
  never `raw_output`
- `quality_candidate_fail` must keep UI, TTS, DB, Markdown, and pseudo streaming
  execution disabled
- DB saved text, Markdown finalized text, and pseudo streaming final text should
  match

Device confirmation command:

```bash
adb shell setprop debug.lami.npu_standard_route_dev_gate true
adb shell setprop debug.lami.npu_standard_route_phase 8
adb shell monkey -p io.github.ninbyo02.lami 1
```

## NPU Standard Route Completion Checklist

- Phase 1 route-entry diagnostics visible
- Phase 2 conversation-created gate visible
- Phase 3 generate diagnostics with fail-output suppression
- Phase 4 UI append executed only for `quality_candidate_pass`
- Phase 5 TTS started only for `quality_candidate_pass`
- Phase 6 DB save executed only for `quality_candidate_pass`
- Phase 7A Markdown executed only for `quality_candidate_pass`
- Phase 7B pseudo streaming executed only for `quality_candidate_pass`
- Native token streaming remains deferred
- `quality_candidate_fail` never reaches UI, TTS, DB, Markdown, or streaming

After these checks pass, run
`scripts/review_npu_standard_route_final_promotion.sh` against the Phase 7B
success artifact and the quality-candidate-fail suppression artifact. The final
review is documented in `docs/npu_standard_route_final_promotion_review.md`.

## Fail Output Suppression Rule

Implementation must use this invariant:

```text
output_quality_candidate_status=quality_candidate_fail
```

means:

- no UI append of model text
- no TTS text
- no DB save
- no Markdown rendering
- no streaming / pseudo streaming chunks
- diagnostic-only failure assistant text may be shown only if it is a fixed
  safe app-authored message, not model output

See `docs/npu_quality_gate_output_suppression_plan.md`.

## Diagnostics Key Plan

Add these keys when Kotlin implementation begins:

```text
npu_standard_route_dev_gate_enabled
npu_standard_route_phase
npu_standard_route_connected
npu_standard_route_quality_gate_passed
npu_standard_route_output_suppressed
npu_standard_route_suppression_reason
npu_standard_route_ui_append_allowed
npu_standard_route_tts_allowed
npu_standard_route_db_save_allowed
npu_standard_route_markdown_allowed
npu_standard_route_streaming_allowed
npu_standard_route_rollback_required
npu_standard_route_rollback_reason
```

Also keep existing gate inputs:

```text
status
fallback
fallback_used
timeout
fresh_crash
run_decode_reached
native_cleanup_reached
output_quality_candidate_status
output_quality_candidate_reason
quality_classification
actual_display_text
tts_text
standard_route_connected
conversation_created
generate_response
db
tts
markdown
streaming
```

## Rollback Conditions

Stop the phase and do not advance if any of these occur:

- `fallback=true` or `fallback_used=true`
- `timeout=true`
- `fresh_crash=true`
- fresh tombstone appears
- `run_decode_reached=false`
- `native_cleanup_reached=false`
- `output_quality_candidate_status=quality_candidate_fail` and model output
  reaches UI/TTS/DB/Markdown/Streaming
- `actual_display_text` contains unsafe template fragments such as `_turn>`
- DB save occurs before explicit DB phase
- TTS starts before explicit TTS phase
- Markdown / streaming starts before explicit phase
- CPU/GPU route behavior regresses

## Test Plan

Unit tests for the future Kotlin implementation:

- candidate pass -> output allowed only for the active phase
- candidate fail -> output suppressed for all downstream surfaces
- `actual_display_text=_turn>` -> output suppressed
- DEV gate off -> no standard route connection
- DEV gate on Phase 1 -> diagnostics only
- DEV gate on Phase 2 -> conversation created, no generate
- DEV gate on Phase 3 -> generate response, suppress failure output
- S2 DB mapper remains blocked when S1 success criteria fail
- S3 Markdown mapper remains blocked when S1 success criteria fail
- S4 pseudo streaming mapper remains blocked when S1 success criteria fail
- S5 TTS mapper remains blocked when S1 success criteria fail
- CPU route unaffected
- GPU route unaffected

Device tests after implementation:

- short
- medium
- long
- markdown
- mixed_language
- quality_gate expected rejection

For the quality gate run, expected standard-route diagnostics:

```text
npu_standard_route_quality_gate_passed=false
npu_standard_route_output_suppressed=true
npu_standard_route_ui_append_allowed=false
npu_standard_route_tts_allowed=false
npu_standard_route_db_save_allowed=false
npu_standard_route_markdown_allowed=false
npu_standard_route_streaming_allowed=false
```

## Conditions To Start Implementation

Start Kotlin implementation only after approving:

- DEV gate property name and phase order
- fixed safe failure message policy for candidate fail
- exact diagnostic key list
- test coverage for output suppression
- no DB/TTS/Markdown/Streaming connection before Phase 4+

The first implementation task should be Phase 1 diagnostics only.
