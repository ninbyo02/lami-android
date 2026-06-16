# NPU Classifier Design

Scope: script/docs only. This document defines the classifier behavior for
copied NPU diagnostics. It does not change Android runtime behavior.

## Script

Implemented script:

```bash
scripts/classify_npu_diagnostic_result.sh --input artifacts/device_runs/npu_s1_latest.txt
scripts/classify_npu_diagnostic_result.sh --self-test
```

The parser accepts one-key-per-line diagnostics and long lines containing many
`key=value` tokens, including `[DEV診断: NPU S1 compact]` and `[NPU diagnostic
keys]` copied text.

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
NPU_PROMOTION_DECISION=eligible_candidate
NPU_PROMOTION_DECISION_REASON=all_required_gates_passed
NPU_ROOT_CAUSE_CANDIDATE=none
NPU_BACKEND_EVIDENCE_SUMMARY=qnn_htp_fastrpc_present
NPU_FAILURE_LAYER=unavailable
NEXT_ACTION=run_repeatability_matrix_before_standard_route_promotion
```

## Classification Values

```text
npu_promotion_candidate
npu_route_not_connected
npu_fallback_detected
npu_quality_failure
npu_timeout
npu_crash_detected
npu_engine_create_failed
npu_compiled_model_failure
npu_cleanup_failure
npu_backend_missing
npu_decode_not_reached
unknown
```

## Rule Order

Evaluate hard blockers first:

1. If selected/effective backend and `route_family` do not indicate NPU,
   classify as `npu_route_not_connected`.
2. If NPU/QNN/HTP backend evidence is missing, classify as
   `npu_backend_missing`.
3. If `fallback_used=true` or `fallback=true`, classify as
   `npu_fallback_detected`.
4. If `timeout=true`, classify as `npu_timeout`.
5. If `fresh_crash=true` or `fresh_tombstone_status` indicates a fresh crash,
   classify as `npu_crash_detected`.
6. If `npu_s1_failure_kind=engine_create_failed`,
   `last_failure_was_engine_create_failed=true`, or an error message mentions
   engine-create-failed, classify as `npu_engine_create_failed`.
7. If an error message mentions LiteRT compiled model failure, classify as
   `npu_compiled_model_failure`.
8. If `run_decode_reached=false` and status is not success, classify as
   `npu_decode_not_reached`.
9. If cleanup evidence is failed or missing for a terminal failure, classify as
   `npu_cleanup_failure`. `native_cleanup_reached=true` is accepted as cleanup
   evidence for current S1 DEV diagnostics.
10. If `status=success` but `quality_classification` is not
    `natural_japanese`, classify as `npu_quality_failure`.
11. If all required gate keys pass, classify as `npu_promotion_candidate`.
12. Otherwise classify as `unknown`.

## Promotion Blocker Mapping

| Classification | NPU_PROMOTION_BLOCKER | NPU_ROOT_CAUSE |
| --- | --- | --- |
| `npu_promotion_candidate` | `false` | `none` |
| `npu_route_not_connected` | `true` | `route_not_connected` |
| `npu_fallback_detected` | `true` | `fallback_used` |
| `npu_quality_failure` | `true` | `output_quality_failure` |
| `npu_timeout` | `true` | `timeout` |
| `npu_crash_detected` | `true` | `fresh_crash` |
| `npu_engine_create_failed` | `true` | `litert_npu_compiled_model_executor_failure` |
| `npu_compiled_model_failure` | `true` | `litert_compiled_model_failure` |
| `npu_cleanup_failure` | `true` | `cleanup_failure` |
| `npu_backend_missing` | `true` | `backend_evidence_missing` |
| `npu_decode_not_reached` | `true` | `npu_decode_not_reached` |
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
inspect_qairt_qnn_model_runtime_alignment_and_recreate_guard
inspect_compiled_model_dispatch_delegate_and_model_constraints
inspect_stage_history_before_decode
```

## Engine Create Failed Example

Input evidence:

```text
status=failure
selected_backend=NPU_S1
effective_backend=NPU
backend_evidence=QNN_HTP_V79_FastRPC_native_diag
route_family=npu_s1
npu_s1_failure_kind=engine_create_failed
npu_s1_failure_layer=litert_npu_compiled_model_executor
run_decode_reached=false
timeout=false
fallback=false
fresh_crash=false
native_cleanup_reached=true
```

Expected classifier output:

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

Interpretation: NPU route entry and backend evidence are present, but standard
promotion is blocked because engine creation failed near the LiteRT NPU compiled
model executor layer.

## Self-Test Fixtures

The script self-test covers:

- fully passing candidate
- GPU/non-NPU route
- timeout
- fresh crash
- engine create failure
- non-natural output quality
