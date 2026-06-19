# NPU Standard Route Final Promotion Review

Scope: review / checklist / scripts only. Do not change Android runtime,
ChatScreen behavior, NPU route implementation, Settings UI, native libraries, or
phase gate behavior in this review step.

## Phase Summary

The staged NPU standard route has been implemented and device-confirmed through
Phase 7B:

- Phase 1: route entry diagnostic
- Phase 2: conversation created diagnostic
- Phase 3: generate diagnostic
- Phase 4: UI append gate
- Phase 5: TTS gate
- Phase 6: DB save gate
- Phase 7A: Markdown gate
- Phase 7B: pseudo streaming gate

Phase 7B uses `debug.lami.npu_standard_route_phase=8`. It is pseudo streaming,
not native token streaming. The route still obtains complete NPU output first,
passes it through the quality gate and Markdown finalization, then uses the safe
final text for cumulative UI chunks. Native token/chunk streaming remains
deferred because the current lower-level route does not expose enough reliable
chunk and finish telemetry.

## Promotion GO Conditions

`scripts/review_npu_standard_route_final_promotion.sh` reports promotion GO only
when the Phase 7B success artifact satisfies all required gates:

```text
status=success
selected_backend=NPU_S1 or another NPU value
effective_backend=NPU
backend_evidence includes QNN/HTP/FastRPC/NPU evidence
fallback=false or fallback_used=false
timeout=false
fresh_crash=false
run_decode_reached=true
native_call_returned=true
native_decode_finished=true
native_cleanup_reached=true
npu_standard_route_phase=8
npu_standard_route_phase_name=7b_pseudo_streaming_gate
npu_standard_route_connected=true
conversation_created=true
generate_response=true
npu_standard_route_quality_gate_passed=true
npu_standard_route_output_suppressed=false
npu_standard_route_output_delivery_allowed=true
npu_standard_route_ui_append_executed=true
npu_standard_route_db_save_executed=true
npu_standard_route_markdown_executed=true
npu_standard_route_streaming_executed=true
npu_standard_route_streaming_mode=pseudo_final_text
npu_standard_route_native_streaming_used=false
npu_standard_route_streaming_text_matches_db=true
npu_standard_route_streaming_text_matches_markdown=true
npu_standard_route_rollback_required=false
```

After R1b, completed-route diagnostics should also be present in the artifact
when the run came from user-facing `NPU Experimental`:

```text
npu_standard_route_selection_mode=user_facing_npu_experimental
npu_standard_route_user_facing_backend=NPU Experimental
npu_standard_route_completed_route_selected=true
npu_standard_route_effective_phase_source=completed_route_default
npu_standard_route_effective_phase=8
npu_standard_route_completed_route_family=npu_standard_route_completed
```

The internal compatibility fields may still read `selected_backend=NPU_S5` and
`route_family=npu_s5`. That is acceptable for final-promotion artifacts as long
as the completed-route summary keys above are present and phase 8 gates pass.

## Rollout Monitor Relationship

Final promotion review judges one artifact. R2 rollout monitoring aggregates
multiple artifacts:

```text
scripts/review_npu_rollout_monitor.sh --device-runs artifacts/device_runs
```

The monitor treats Phase 8 success artifacts as positive rollout samples and
quality-candidate-fail suppression artifacts as safety-pass samples. Suppression
pass is not promotion-ready by itself, but it is required evidence that unsafe
template output remains blocked from UI / TTS / DB / Markdown / pseudo
streaming.

Phase R3 dev-gate removal readiness builds on this review and the R2 monitor:

```text
scripts/review_npu_dev_gate_removal_readiness.sh --device-runs artifacts/device_runs
```

It requires final promotion GO plus multiple clean rollout samples and a rollback
plan. Phase R3b uses that evidence to remove the dev gate requirement for
user-facing `NPU Experimental` completed-route default selection only.

Expected output:

```text
NPU_STANDARD_ROUTE_FINAL_REVIEW=ready
READY_FOR_NPU_STANDARD_ROUTE=true
PROMOTION_DECISION=go
PROMOTION_DECISION_REASON=phase7b_pseudo_streaming_passed
PROMOTION_SCORE=100
FAILED_GATES=none
REMAINING_BLOCKERS=none
SAFE_NEXT_ACTION=prepare_npu_settings_consolidation_and_standard_backend_rollout
```

## Suppression Pass Conditions

A `quality_candidate_fail` artifact is not promotion-ready by itself, but it can
pass the suppression review. This is important because the validation suite
intentionally includes dangerous template-output prompts.

Suppression pass requires:

```text
npu_standard_route_output_suppressed=true
npu_standard_route_ui_append_executed=false
npu_standard_route_tts_started=false
npu_standard_route_db_save_executed=false
npu_standard_route_markdown_executed=false
npu_standard_route_streaming_executed=false
npu_standard_route_native_streaming_used=false
npu_standard_route_rollback_required=true
rollback reason or streaming block reason indicates quality_candidate_fail
```

Expected output:

```text
NPU_STANDARD_ROUTE_FINAL_REVIEW=suppression_pass
READY_FOR_NPU_STANDARD_ROUTE=false
PROMOTION_DECISION=blocked_for_this_artifact
PROMOTION_DECISION_REASON=quality_candidate_fail_suppressed_correctly
SAFE_NEXT_ACTION=continue_using_quality_gate_suppression
```

This is not a regression. It confirms fail-output suppression works. The same
artifact must not be used as the positive promotion proof.

## TTS ON/OFF Handling

TTS is not a hard blocker when the user setting disables speech. The final review
treats this as a warning if:

```text
npu_standard_route_tts_allowed=true
npu_standard_route_tts_started=false
npu_standard_route_tts_execution_block_reason=tts_disabled
```

When TTS is enabled, `npu_standard_route_tts_started=true` is expected. Any other
TTS non-start state is a failed gate until it is explained.

## Rollback Conditions

Promotion remains blocked if any of these appear in a Phase 7B success artifact:

- fallback used
- timeout
- fresh crash
- decode not reached
- native call did not return
- native cleanup did not complete
- phase is not `8`
- rollback required
- output suppressed unexpectedly
- UI, DB, Markdown, or pseudo streaming did not execute
- native streaming used
- pseudo streaming text does not match DB / Markdown finalized text
- backend evidence does not indicate NPU/QNN/HTP/FastRPC

For suppression artifacts, rollback is expected and must be tied to
`quality_candidate_fail`.

## Settings Integration Notes

Before making NPU a normal Settings backend, keep the existing developer phase
keys compatible. The short-term Settings model should present NPU as one
experimental backend with developer phase controls, not as separate hardware
backends for S1-S8.

Phase R1 keeps that compatibility while connecting the user-facing
`NPU Experimental` selection to the completed standard route. Phase R3b removes
the dev gate requirement for the completed route default only: when
`NPU Experimental` is selected and `debug.lami.npu_standard_route_phase` is `0`
or absent, the effective phase is `8` unless the completed-route kill switch is
enabled.

Explicit `debug.lami.npu_standard_route_phase=1..8` values remain developer
overrides and require `debug.lami.npu_standard_route_dev_gate=true`. If the dev
gate is false, the override is blocked and diagnostics should include
`npu_standard_route_developer_phase_override_block_reason=dev_gate_disabled`.

The completed-route kill switch is:

```text
debug.lami.npu_standard_route_completed_route_disabled=true
```

This keeps the rollout reversible without changing CPU / GPU / Automatic
behavior.

Remaining rollout tasks:

- consolidate Settings display around `NPU Experimental / DEV`
- keep CPU as stable fallback candidate
- keep GPU experimental and blocked from promotion
- keep quality-gate suppression active
- run the final promotion review over current Phase 7B success and suppression
  artifacts

## Command

```bash
scripts/review_npu_standard_route_final_promotion.sh \
  --input artifacts/device_runs/npu_phase8_latest.txt
```

or:

```bash
scripts/review_npu_standard_route_final_promotion.sh \
  --device-runs artifacts/device_runs
```

Self-test:

```bash
scripts/review_npu_standard_route_final_promotion.sh --self-test
```
