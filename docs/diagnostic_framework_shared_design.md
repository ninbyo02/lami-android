# Diagnostic Framework Shared Design

Scope: shared design only. This document identifies reusable diagnostic
patterns from GPU investigation that can be applied to NPU without changing
runtime behavior.

## Shared Components

### Copy Key Formatter

Reusable shape:

```text
[<ROUTE> diagnostic keys]
key=value
```

Common rules:

- stable key order
- one key per line
- missing values render as `unavailable`
- generated from diagnostics map, not rendered details text
- route-specific key groups

### Classifier Framework

Reusable outputs:

```text
<ROUTE>_CLASSIFICATION=...
<ROUTE>_PROMOTION_BLOCKER=...
<ROUTE>_ROOT_CAUSE=...
NEXT_ACTION=...
```

The classifier should be deterministic and fixture-testable from copied
diagnostics.

### Promotion Gate Framework

Common gate fields:

```text
status
fallback_used
fresh_crash
timeout
quality_classification
cleanup_status
promotion_blocker
promotion_decision
promotion_decision_reason
```

Route-specific fields should be additive, not replacements.

### Report Generator Framework

Common sections:

```text
Overview
Latest run
Backend evidence
Quality summary
Promotion gate status
Promotion blocker status
Cleanup summary
Root cause ranking
Next actions
```

The report should gracefully skip missing inputs.

### Artifact Layout

GPU artifacts already use route-specific directories. NPU should mirror that:

```text
artifacts/device_runs/
artifacts/npu_device_runs/
artifacts/npu_investigation_report/
```

Generated artifacts stay out of source control unless they are intentional
small fixtures.

## Route-Specific Key Whitelists

Each route should own a priority key list:

- GPU: executor / runtime decode / callback quality keys
- NPU: backend evidence / fallback / crash / timeout / quality / cleanup keys
- CPU: stable route and failure classification keys

Do not mix route-specific success semantics. CPU fallback must not become NPU
success, and GPU invoke success must not bypass GPU output quality blockers.

## Safe Stop Line Pattern

Every route promotion plan should end with explicit stop lines:

```text
<ROUTE>_PROMOTION_DECISION=blocked
<ROUTE>_PROMOTION_DECISION_REASON=...
<ROUTE>_SAFE_NEXT_ACTION=...
```

This keeps diagnostics useful without silently changing production behavior.
