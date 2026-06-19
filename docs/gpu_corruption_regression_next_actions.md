# GPU Corruption Regression Next Actions

Scope: decision guide for interpreting the regression matrix. This document does
not authorize production GPU promotion or route changes.

## If Results Point To Runtime Stack Difference

Evidence:

- GPU fails across short/medium/long prompts.
- CPU passes the same prompts.
- `loaded_native_runtime_stack_fingerprint` differs from a known-good probe.
- `gpu_sampler_root_cause_candidate=runtime_decode_fragmentation`.

Next action:

- Compare loaded library SHA-256 values.
- Keep `standardDebug` unchanged.
- Use only isolated DEV flavors for runtime alignment experiments.

## If Results Point To Executor Difference

Evidence:

- CPU passes.
- GPU short prompts may pass but medium/long fail at raw callback.
- `edge_gallery_executor_probe_result=same_sampler_different_executor` or
  executor fingerprints differ.
- Edge Gallery official GPU remains clean.

Next action:

- Prioritize internal executor/backend selection investigation:
  - `GPU_ARTISAN`
  - backend constraints
  - `RuntimeConfig`
  - preferred engine types
  - GPU KV-cache selection
- Do not use reflection hacks or hidden APIs in standard builds.

## If Results Point To Callback Difference

Evidence:

- Raw callback artifacts are clean but UI/final text corrupts.
- `gpu_output_source_corruption_stage` is not `raw_callback`.
- `gpu_output_ui_append_changed_text=true`.

Next action:

- Investigate callback join/aggregation separately.
- Current known issue is raw-callback corruption, so this would be a change in
  evidence.

## If Results Point To Model Difference

Evidence:

- CPU and GPU fail the same prompts with the same model.
- Edge Gallery model and Lami selected model differ by size/SHA.
- Corruption reproduces outside GPU-specific route.

Next action:

- Re-check model identity, file size, SHA-256, and metadata.
- Do not treat model difference as current leading cause unless CPU also fails.

## If Results Are Length-Dependent

Evidence:

- Short prompts pass.
- `long_300chars.txt` or `long_500chars.txt` fails.
- Fragmentation score rises with output length.
- Tail corruption ratio increases.

Next action:

- Treat runtime decode fragmentation as strengthened.
- Compare `maxTokens=128,256,512,1024,4000` only as diagnostics.
- Do not mask quality failure by reducing max tokens for promotion.

## If Results Are Markdown-Dependent

Evidence:

- `markdown_bullets.txt`, `markdown_numbered.txt`, or `markdown_table.txt`
  fails while non-Markdown prompts pass.
- Repeated markdown fragment detection or tail marker density rises.

Next action:

- Investigate token pattern sensitivity.
- Keep promotion blocked until both prose and Markdown/list outputs pass.

## If Results Are Mixed-Language Or Symbol-Dependent

Evidence:

- `mixed_ja_en.txt` or `mixed_symbols.txt` fails selectively.
- Numeric/unit fragments corrupt while prose remains normal.

Next action:

- Investigate tokenizer/decode boundary behavior.
- Compare raw callback chunk size distribution and first suspicious callback
  index with `scripts/analyze_gpu_callback_raw_stream.sh`.

## Current Default Decision

Until a regression run shows clean long, Markdown, numeric, and multi-turn GPU
results, keep:

```text
gpu_output_quality_promotion_blocker=true
```

as a standard GPU promotion blocker.
