# NPU Classifier Design

Scope: design only. This document defines the intended classifier behavior for
copied NPU diagnostics. It does not implement Android runtime behavior.

## Input

The classifier should read copied `[NPU diagnostic keys]` text or an equivalent
key-value diagnostics file.

Important input keys:

```text
status
fallback_used
fresh_crash
timeout
npu_backend_evidence
backend_evidence
quality_classification
selected_path_npu_saved
normal_ui_route_connected
standard_route_connected
conversation_created
generate_response
db
tts
markdown
streaming
cleanup_status
engine_close_evidence
fresh_tombstone_status
```

## Output

Use machine-readable output:

```text
NPU_CLASSIFICATION=npu_promotion_candidate
NPU_PROMOTION_BLOCKER=false
NPU_ROOT_CAUSE=none
NEXT_ACTION=promote_to_next_gate
```

## Classification Values

```text
npu_promotion_candidate
npu_route_not_connected
npu_fallback_detected
npu_quality_failure
npu_timeout
npu_crash_detected
npu_cleanup_failure
npu_backend_missing
unknown
```

## Rule Order

Evaluate hard blockers first:

1. If `fallback_used=true`, classify as `npu_fallback_detected`.
2. If `fresh_crash=true` or `fresh_tombstone_status` indicates a fresh crash,
   classify as `npu_crash_detected`.
3. If `timeout=true`, classify as `npu_timeout`.
4. If NPU/QNN/HTP backend evidence is missing, classify as
   `npu_backend_missing`.
5. If `standard_route_connected` is not true for a standard-route probe, or
   `conversation_created` / `generate_response` is false, classify as
   `npu_route_not_connected`.
6. If `quality_classification` is not `natural_japanese`, classify as
   `npu_quality_failure`.
7. If `cleanup_status` is not `success` or `engine_close_evidence` is missing,
   classify as `npu_cleanup_failure`.
8. If all required gate keys pass, classify as `npu_promotion_candidate`.
9. Otherwise classify as `unknown`.

## Promotion Blocker Mapping

| Classification | NPU_PROMOTION_BLOCKER | NPU_ROOT_CAUSE |
| --- | --- | --- |
| `npu_promotion_candidate` | `false` | `none` |
| `npu_route_not_connected` | `true` | `route_not_connected` |
| `npu_fallback_detected` | `true` | `fallback_used` |
| `npu_quality_failure` | `true` | `output_quality_failure` |
| `npu_timeout` | `true` | `timeout` |
| `npu_crash_detected` | `true` | `fresh_crash` |
| `npu_cleanup_failure` | `true` | `cleanup_failure` |
| `npu_backend_missing` | `true` | `backend_evidence_missing` |
| `unknown` | `true` | `unknown` |

## Next Actions

Recommended `NEXT_ACTION` values:

```text
promote_to_next_gate
fix_route_connection
investigate_fallback
investigate_output_quality
investigate_timeout
investigate_crash
fix_cleanup_lifecycle
collect_backend_evidence
collect_more_diagnostics
```

## Test Design

Future script self-tests should include fixtures for:

- fully passing candidate
- fallback used
- timeout
- fresh crash
- missing backend evidence
- non-natural output quality
- cleanup failure
- incomplete route connection
