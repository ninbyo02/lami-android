# NPU Investigation Report Plan

Scope: scripts/docs only. This document describes the implemented NPU
investigation report generator. It does not change Android runtime behavior.

## Script

Implemented script:

```bash
scripts/render_npu_investigation_report.sh \
  --device-runs artifacts/device_runs \
  --output artifacts/npu_investigation_report/NPU_INVESTIGATION_REPORT.md

scripts/render_npu_investigation_report.sh --self-test
```

The script gracefully handles missing input directories and still writes a
report with missing-state sections.

## Purpose

GPU investigation benefited from one Markdown report that combined device runs,
quality status, promotion blockers, root-cause candidates, and next actions.
NPU should use the same reporting pattern once NPU artifacts are stable enough
to summarize.

## Report Sections

```text
Overview
Latest NPU run summary
Backend evidence summary
NPU classifier summary
Promotion gate status
Promotion blocker status
Failure layer summary
Crash / tombstone summary
Cleanup summary
Root cause ranking
Next actions
```

## Section Details

### Overview

Summarize current route posture:

```text
CPU_STABLE_ROUTE=maintain
GPU_ROUTE=experimental_diagnostics_only
NPU_ROUTE=standard_promotion_candidate_under_gate
```

### Latest NPU Run

Includes copied NPU diagnostic keys and artifact metadata:

```text
status
reason
route_family
selected_backend
requested_backend
effective_backend
backend_evidence
npu_backend_evidence
fallback_used
fallback
fresh_crash
timeout
quality_classification
standard_route_connected
conversation_created
generate_response
```

### Backend Evidence

Show whether QNN / HTP / FastRPC / V79 evidence was captured and whether
fallback was avoided.

### Quality Summary

Summarize:

```text
quality_classification
prompt_echo_detected
template_residue_detected
multilingual_drift_detected
```

### NPU Classifier Summary

The report invokes:

```bash
scripts/classify_npu_diagnostic_result.sh --input <latest-device-run>
```

and embeds:

```text
NPU_CLASSIFICATION
NPU_CLASSIFICATION_REASON
NPU_PROMOTION_BLOCKER
NPU_PROMOTION_DECISION
NPU_PROMOTION_DECISION_REASON
NPU_ROOT_CAUSE_CANDIDATE
NPU_BACKEND_EVIDENCE_SUMMARY
NPU_FAILURE_LAYER
NEXT_ACTION
```

### Promotion Gate Status

Render each gate condition from
`docs/npu_promotion_gate_definition.md` as pass / fail / unavailable.

### Promotion Blocker Status

Render:

```text
NPU_PROMOTION_BLOCKER
NPU_PROMOTION_DECISION
NPU_PROMOTION_DECISION_REASON
```

### Cleanup Summary

Include:

```text
cleanup_status
engine_close_evidence
native_cleanup_reached
native_cleanup_finished
```

### Crash / Tombstone Summary

Include:

```text
fresh_crash
timeout
fresh_tombstone_status
native_crash_risk_hint
```

### Failure Layer Summary

Include:

```text
npu_s1_failure_kind
npu_s1_failure_layer
native_stage
failure_stage
native_error_stage
```

### Root Cause Ranking

When blocked, rank likely causes:

1. backend evidence missing
2. fallback used
3. timeout
4. fresh crash
5. output quality failure
6. lifecycle cleanup failure
7. route not connected
8. unknown

### Next Actions

Use classifier output where available:

```text
NEXT_ACTION=promote_to_next_gate
```

## Artifact Layout

Recommended paths:

```text
artifacts/npu_device_runs/
artifacts/device_runs/
artifacts/npu_investigation_report/NPU_INVESTIGATION_REPORT.md
artifacts/npu_classifier/
```

Do not commit generated artifacts unless they are small fixtures explicitly
created for tests.

## Example Classifier Block

For the current engine-create-failed S1 DEV diagnostics, the report should show:

```text
NPU_CLASSIFICATION=npu_engine_create_failed
NPU_PROMOTION_BLOCKER=true
NPU_PROMOTION_DECISION=blocked
NPU_PROMOTION_DECISION_REASON=engine_create_failed
NPU_ROOT_CAUSE_CANDIDATE=litert_npu_compiled_model_executor_failure
NPU_BACKEND_EVIDENCE_SUMMARY=qnn_htp_fastrpc_present
NPU_FAILURE_LAYER=litert_npu_compiled_model_executor
NEXT_ACTION=inspect_qairt_qnn_model_runtime_alignment_and_recreate_guard
```
