# NPU Promotion Gate Definition

Scope: planning and diagnostics only. This document defines the minimum
evidence needed before promoting NPU from hidden / diagnostic route toward a
standard route candidate.

## Minimum Gate

All of these must be true before a standard-route candidate is considered:

```text
status=success
fallback_used=false
fresh_crash=false
timeout=false
standard_route_connected=true
conversation_created=true
generate_response=true
quality_classification=natural_japanese
cleanup_status=success
engine_close_evidence=present
```

Additional backend evidence must show real NPU execution:

```text
npu_backend_evidence=QNN_HTP_V79_FastRPC
```

Equivalent QNN / HTP / NPU evidence is acceptable if the artifact clearly
proves that CPU/GPU fallback was not used.

## Promotion Blockers

Immediate blockers:

```text
fallback_used=true
fresh_crash=true
timeout=true
quality_classification!=natural_japanese
```

Additional blockers:

- `npu_s1_failure_kind=engine_create_failed`
- `last_failure_was_engine_create_failed=true`
- `NPU_CLASSIFICATION=npu_engine_create_failed`
- `NPU_CLASSIFICATION=npu_compiled_model_failure`
- missing NPU/QNN/HTP backend evidence
- `standard_route_connected=false` when testing a standard-route probe
- `conversation_created=false`
- `generate_response=false`
- `cleanup_status` missing or failed
- `engine_close_evidence` missing
- CPU/GPU route contamination
- `selected_path_npu_saved=true` before explicit promotion approval
- DB / TTS / Markdown / streaming connected before isolated route stability
- unknown QAIRT / QNN / LiteRT dispatch / model provenance

Classifier output should map these blockers to:

```text
NPU_PROMOTION_BLOCKER=true
NPU_PROMOTION_DECISION=blocked
```

For current S1 DEV diagnostics, `npu_engine_create_failed` means route entry and
NPU backend evidence can be present while promotion remains blocked at the
LiteRT NPU compiled model executor layer.

## Gate Levels

### Hidden Route Gate

Allows continued hidden / diagnostic testing only.

Required:

- NPU backend evidence
- no fallback
- no fresh crash
- no timeout
- natural Japanese output
- artifact path and run id captured

### Standard Probe Gate

Allows a DEV-only standard-route candidate probe.

Required:

- Hidden Route Gate passes repeatedly
- route isolation evidence is clean
- cleanup evidence is present
- selected path is not persisted without explicit approval
- CPU route remains stable

### Standard Promotion Gate

Allows discussion of UI-facing NPU route promotion.

Required:

- Standard Probe Gate passes
- multiple prompts pass across short / medium / long outputs
- lifecycle remains stable across restart and repeated runs
- DB / TTS / Markdown / streaming integrations are added and validated one at
  a time

## Safe Stop Line

Stop promotion and keep NPU diagnostic-only if:

- any immediate blocker appears
- backend evidence is ambiguous
- output quality is not natural Japanese
- lifecycle cleanup requires force-stop for the target mode
- route isolation is not provable
