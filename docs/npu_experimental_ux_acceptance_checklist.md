# NPU Experimental UX Acceptance Checklist

Scope: R5a review / docs / scripts only. This does not change Android runtime,
ChatScreen, Settings UI, NPU route behavior, native libraries, DB schema,
quality gates, suppression behavior, or Automatic backend selection.

## Purpose

R5a checks whether user-selected `NPU Experimental` is usable enough to treat as
nearly complete before any later formal backend promotion work. The completed
route has already passed Phase 8 pseudo streaming, final promotion review,
rollout monitoring, dev-gate removal readiness, and R3b dev-gate removal for
the explicit `NPU Experimental` selection.

R5a is not:

- NPU formal backend promotion
- Automatic backend enrollment
- legacy S1-S5 removal
- native token streaming implementation

## Device Preconditions

Use the consolidated Settings UI:

```text
Inference backend: NPU Experimental
Developer NPU phase override: none
```

For the default UX run:

```text
adb shell setprop debug.lami.npu_standard_route_dev_gate false
adb shell setprop debug.lami.npu_standard_route_phase 0
adb shell setprop debug.lami.npu_standard_route_completed_route_disabled false
adb shell monkey -p io.github.ninbyo02.lami 1
```

`debug.lami.npu_standard_route_phase=0` means no explicit developer override.
Do not use `setprop NAME ""`; Android `setprop` does not accept an empty value.

## Prompt Checklist

Collect at least these artifacts:

```text
こんにちは
カレーの材料をお願いします。
箇条書きで旅行計画を作成してください。
template cleanup が出やすい短文
```

Optional but recommended:

```text
こんにちは
```

with the kill switch enabled:

```text
adb shell setprop debug.lami.npu_standard_route_completed_route_disabled true
```

Use the manifest helper:

```text
scripts/create_npu_experimental_ux_manifest.sh --date YYYYMMDD
```

## Artifact Names

```text
artifacts/device_runs/npu_ux_short_success_YYYYMMDD.txt
artifacts/device_runs/npu_ux_medium_success_YYYYMMDD.txt
artifacts/device_runs/npu_ux_markdown_success_YYYYMMDD.txt
artifacts/device_runs/npu_ux_suppression_pass_YYYYMMDD.txt
artifacts/device_runs/npu_ux_kill_switch_block_YYYYMMDD.txt
```

Save the NPU diagnostic key copy or compact/full dump into each artifact.

## Expected Diagnostics

Positive UX success should include:

```text
status=success
effective_backend=NPU
backend_evidence=QNN_HTP_V79_FastRPC_native_diag
npu_standard_route_phase=8
npu_standard_route_dev_gate_required=false
npu_standard_route_completed_route_selected=true
npu_standard_route_completed_route_rollout_state=enabled
output_quality_candidate_status=quality_candidate_pass
npu_standard_route_output_delivery_allowed=true
npu_standard_route_ui_append_executed=true
npu_standard_route_db_save_executed=true
npu_standard_route_markdown_executed=true
npu_standard_route_streaming_executed=true
npu_standard_route_native_streaming_used=false
npu_standard_route_streaming_text_matches_db=true
npu_standard_route_streaming_text_matches_markdown=true
npu_standard_route_rollback_required=false
fallback=false
timeout=false
fresh_crash=false
```

TTS ON should normally include:

```text
npu_standard_route_tts_started=true
```

TTS OFF is acceptable when diagnostics explain it:

```text
npu_standard_route_tts_started=false
npu_standard_route_tts_execution_block_reason=tts_disabled
```

## User-Visible Behavior

For positive UX success:

- assistant response appears in the chat UI
- DB-backed conversation state is preserved
- Markdown rendering is active for Markdown-formatted answers
- pseudo streaming uses the safe finalized text
- TTS starts when the user setting enables speech
- no rollback is required

Pseudo streaming is still not native token streaming. The expected diagnostic is:

```text
npu_standard_route_native_streaming_used=false
```

## Suppression Check

For the dangerous prompt, expected behavior is safe rejection:

```text
output_quality_candidate_status=quality_candidate_fail
npu_standard_route_output_suppressed=true
npu_standard_route_ui_append_executed=false
npu_standard_route_tts_started=false
npu_standard_route_db_save_executed=false
npu_standard_route_markdown_executed=false
npu_standard_route_streaming_executed=false
npu_standard_route_rollback_required=true
```

This is a safety pass, not a positive UX success artifact.

## Kill Switch Check

The completed route can be disabled with:

```text
adb shell setprop debug.lami.npu_standard_route_completed_route_disabled true
```

Expected diagnostics:

```text
npu_standard_route_completed_route_disabled_by_property=true
npu_standard_route_completed_route_selected=false
npu_standard_route_completed_route_block_reason=kill_switch_disabled
npu_standard_route_output_delivery_allowed=false
npu_standard_route_ui_append_executed=false
npu_standard_route_tts_started=false
npu_standard_route_db_save_executed=false
npu_standard_route_markdown_executed=false
npu_standard_route_streaming_executed=false
```

The kill switch sample is desirable for low-risk UX review, but lack of a kill
switch artifact can be treated as medium risk when all minimum runtime UX gates
pass and this rollback plan is documented.

## Review Script

Run:

```text
scripts/review_npu_experimental_ux_acceptance.sh --device-runs artifacts/device_runs
```

When `artifacts/device_runs` contains files named `npu_ux_*.txt`, the script
uses only those R5a UX artifacts so older rollout, DEV, or pre-R3b experiments
do not affect the UX acceptance result. If no `npu_ux_*.txt` files exist, it
falls back to all NPU-looking artifacts for ad hoc review.

Expected low-risk output:

```text
NPU_EXPERIMENTAL_UX_REVIEW=ready
NPU_EXPERIMENTAL_UX_READY=true
UX_SUCCESS_COUNT>=3
UX_SUPPRESSION_PASS_COUNT>=1
UX_FAILURE_COUNT=0
UX_TTS_STARTED_COUNT>=1
UX_KILL_SWITCH_SAMPLE_COUNT>=1
UX_RISK_LEVEL=low
```

Medium-risk output is acceptable for continued evidence collection when minimum
success and suppression gates pass but a TTS ON or kill-switch artifact is still
missing.

## Pass / Fail

Pass minimum:

- positive UX success count is at least 3
- suppression pass count is at least 1
- failure count is 0
- fallback / timeout / fresh crash are absent
- DB / Markdown / pseudo streaming text consistency passes
- at least one R3b sample has `npu_standard_route_dev_gate_required=false`
- kill switch behavior is documented

Fail:

- any unsafe delivery of `quality_candidate_fail`
- fallback / timeout / fresh crash
- rollback outside expected quality-candidate suppression
- native streaming used
- DB / Markdown / pseudo streaming text mismatch
- kill switch fails to block the completed route

## Next Phases

R5b can review formal `NPU Experimental` rollout wording and device evidence.
R5c can consider whether any remaining developer-only UI cleanup is appropriate.
Neither phase should add NPU to Automatic until a separate explicit review says
so.
