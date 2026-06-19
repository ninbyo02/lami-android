# GPU Runtime Decode Fragmentation Root Cause Ranking

Scope: diagnostic ranking only. Do not treat this as approval for GPU
promotion, native library replacement, callback text repair, or production route
changes.

## Known Facts

- Edge Gallery official GPU generates long Japanese text normally on the same
  device and model family.
- Lami CPU generates long Japanese text normally with both generic and Edge
  Gallery E2B model variants.
- Lami `standardDebug` GPU fails at compiled model invoke:
  `llm_litert_compiled_model_executor.cc:735`.
- Lami minimal/alignment GPU runtime invokes successfully, but long output fails
  quality at raw callback source:
  - `callback_corruption_earliest_stage=raw_callback`
  - `gpu_output_source_corruption_stage=raw_callback`
  - `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`
  - high 1-2 character callback chunk ratio
  - tail semantic corruption such as broken Japanese ingredient words and
    malformed numeric/unit fragments
- Baseline, collect-only, and no-sampling-acceleration matrix runs all fail.

## Ranked Root Cause Candidates

| Rank | Candidate | Confidence | Evidence | Counter-evidence / gap |
|---:|---|---:|---|---|
| 1 | Executor/backend selection mismatch: Lami public `Backend.GPU` reaches a different native decode path than Edge Gallery, possibly missing `GPU_ARTISAN` or hidden runtime config selection. | 68% | Edge Gallery native runtime contains `GPU_ARTISAN`, `LlmGpuArtisanExecutor`, backend constraint, preferred engine type, and GPU KV-cache strings. Lami minimal runtime also contains these strings, so the issue is runtime selection, not simple symbol presence. Lami reflection exposes only `CPU,GPU,NPU`. Edge Gallery GPU is normal, Lami CPU is normal, Lami GPU raw callback corrupts. | Static strings do not prove Edge Gallery's observed run selected `GPU_ARTISAN`. Lami minimal pair can invoke GPU and produce short valid output. |
| 2 | Runtime decode fragmentation inside Lami's GPU compiled-model decode path. | 62% | Raw callback stage is already corrupt; artifact chunks are abnormally tiny; `collect_only` and UI append changes do not fix it; sampler experiments do not fix it. | This describes the failure mode more than the underlying selector/config reason. |
| 3 | Hidden Edge Gallery callback aggregation or buffering layer. | 35% | Edge Gallery UI output is normal while Lami raw callback stream is fragmented; Edge Gallery may consume final/adapter text instead of exposing every raw chunk. | Lami accumulated raw callback text is itself semantically corrupt, so a pure app-layer aggregation difference is unlikely to fully explain the issue. |
| 4 | Callback delta vs accumulated semantics mismatch. | 25% | If callbacks are accumulated full text, Lami append-all behavior would corrupt output. The many tiny chunks make semantics worth checking. | Raw callback artifacts include malformed token/text pieces before UI append; collect-only matrix still fails. |
| 5 | Tokenizer/decode text boundary mismatch. | 20% | Corruption resembles token boundary or BPE decode fragmentation; Edge runtime includes tokenizer/BPE related messages. | Same model and Lami CPU route decode normally; issue appears GPU-long-output specific. |
| 6 | Model import or model artifact difference. | 12% | Edge Gallery uses a known downloaded model with allowlist metadata; Lami imported copies may differ. | Edge Gallery E2B model works in Lami CPU; model identity has been validated strongly enough that it is no longer the leading cause. |

## Sampler Root Cause Position

Current matrix evidence:

| Mode | Result |
|---|---|
| baseline | quality fail |
| collect-only | quality fail |
| no-sampling-acceleration | quality fail |

This supports `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`
rather than `sampler_related`. Sampler config may still affect how quickly
corruption appears, but it is not the primary current explanation.

## Top Additional Experiments

| Priority | Experiment | Expected evidence | Estimated effort | Expected confidence gain |
|---:|---|---|---|---|
| 1 | Determine Edge Gallery's actual GPU executor selection for the same prompt/model using static log strings, app data, or non-invasive logcat filtering for backend/executor messages. | Direct evidence of `GPU_ARTISAN`, compiled-model executor, KV-cache path, or preferred engine selection. | Medium | High |
| 2 | Run the final-response parity probe and compare `append_all_chunks`, `last_non_empty_callback`, and final candidate SHA/quality for the corrupt prompt. | If last/final text is clean while append-all corrupts, callback semantics is implicated. If all are corrupt, native decode source remains leading. | Low | High |
| 3 | Build a DEV-only isolated flavor with the full Edge Gallery runtime pair, keeping it separate from standard and respecting licensing/provenance review before use. | If Edge Gallery runtime pair fixes raw callback quality, runtime stack/executor implementation is confirmed. If not, app-level API/config remains suspect. | High | High |
| 4 | Add non-invasive diagnostics for RuntimeConfig/backend constraint/KV-cache selection strings emitted by LiteRT-LM when GPU generation starts. | Evidence for public compiled executor vs artisan/internal executor path in Lami. | Medium | Medium |
| 5 | Inspect `.litertlm` metadata and graph names around prefill/decode/backend constraints for both Edge Gallery and Lami-imported files, then correlate with runtime executor strings. | Shows whether model metadata requests a backend/executor Lami public API cannot expose. | Medium | Medium |

## Current Promotion Gate

Standard GPU promotion remains blocked while any of these are observed:

- `gpu_output_quality_candidate_result=quality_candidate_fail`
- `callback_corruption_earliest_stage=raw_callback`
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`
- Edge Gallery GPU succeeds on the same class of prompt while Lami GPU raw
  callback corrupts.

## Current Most Likely Root Cause

The most likely root cause is an executor/backend selection mismatch: Edge
Gallery appears to reach a native LiteRT-LM GPU execution/decode path that Lami's
public `Backend.GPU` route does not reach, likely involving internal runtime
configuration, backend constraints, GPU KV-cache selection, or `GPU_ARTISAN`.
The existence of `GPU_ARTISAN` strings in Lami's minimal pair means the next
proof point must be runtime selection evidence, not another static string grep.

Confidence: 68%.
