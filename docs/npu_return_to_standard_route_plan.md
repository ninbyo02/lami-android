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
| C | NPU report generator | Summarize device runs, backend evidence, quality, cleanup, blockers, and next actions. |
| D | NPU promotion decision | Emit explicit `NPU_PROMOTION_DECISION`, reason, blocker, and safe next action. |
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
