# NPU Investigation Report Plan

Scope: report design only. This document does not implement scripts or Android
runtime changes.

## Purpose

GPU investigation benefited from one Markdown report that combined device runs,
quality status, promotion blockers, root-cause candidates, and next actions.
NPU should use the same reporting pattern once NPU artifacts are stable enough
to summarize.

## Proposed Report Sections

```text
Overview
Latest NPU run
Backend evidence
Quality summary
Promotion gate status
Promotion blocker status
Cleanup summary
Crash summary
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

Include copied NPU diagnostic keys and artifact metadata:

```text
run_id
artifact_path
status
route_family
selected_backend
effective_backend
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
fresh_tombstone_status
```

### Crash Summary

Include:

```text
fresh_crash
timeout
fresh_tombstone_status
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

Recommended future paths:

```text
artifacts/npu_device_runs/
artifacts/npu_investigation_report/NPU_INVESTIGATION_REPORT.md
artifacts/npu_classifier/
```

Do not commit generated artifacts unless they are small fixtures explicitly
created for tests.
