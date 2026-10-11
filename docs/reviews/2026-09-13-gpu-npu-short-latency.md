# GPU/NPU short-turn latency decomposition on NX733J

Date: 2026-09-13  
Base: `ab7c72d56d0e0c1a3aa716918f55f15fca22761d` (merged PR #2609)  
Device: NX733J / SM8750 / arm64, battery 80%, charging, 31.0 C before and after

## Scope

This is a read-only debug receiver comparison. It does not change chat history, settings, databases, the installed APK, production routing, tokenizer selection, TTS, or fallback behavior.

Both backends used the same two-turn prompt sequence, Conversation API ownership, model-metadata templates, `lami_stable_v1` sampling (top-k 40, top-p 0.9, temperature 0.3, seed 42), persistent conversation state, and a requested 32-token limit:

1. `こんにちは`
2. `赤だけ答えてください。`

GPU used the generic 2,583,085,056-byte model. NPU used the SM8750-specific 3,016,294,400-byte model. The binaries differ, so this is a product-route comparison, not a hardware-only benchmark.

## Results

| Backend/run | Total | Engine create | Conversation create | Turn 1 send | Turn 2 send |
|---|---:|---:|---:|---:|---:|
| GPU, fresh process | 15,229 ms | 13,705 ms | 13 ms | 1,204 ms | 233 ms |
| GPU, warm process/new engine | 8,056 ms | 6,493 ms | 11 ms | 1,221 ms | 254 ms |
| NPU, fresh process | 2,525 ms | unavailable | unavailable | 736 ms | 150 ms |
| NPU, warm process | 2,085 ms | unavailable | unavailable | 279 ms | 125 ms |

GPU engine construction accounts for about 90% of the fresh total and 81% of the warm-process total. Once the engine and conversation exist, the one-word GPU turn completes in roughly 0.23-0.25 seconds. This supports preserving the existing held-engine reuse path; it does not show a decode bottleneck for this short answer.

NPU is substantially faster for total startup in these samples. Its current A/B probe does not expose separate engine and conversation construction durations, so the residual 1.6-1.7 seconds cannot be assigned more precisely.

## Output quality

GPU returned the expected `赤` in both runs. NPU returned `承知いたしました。` in both runs. The greeting matched exactly across both backends. The stable second-turn divergence means latency and instruction-following quality must be reviewed separately.

## Decision

Do not enable automatic production GPU prewarming from this evidence alone. Prewarming would move a measured 6.5-13.7-second engine build earlier rather than eliminate it, while resident-memory, energy, foreground contention, background lifecycle, and cancellation costs remain unmeasured.

Keep the existing GPU held-engine policy (10-minute foreground idle window and 5-minute confirmed-background window). The next bounded experiment should be debug-only idle prewarming with:

- explicit selected-GPU and validated-model gates;
- cancellation on navigation, model/backend change, low memory, and confirmed background;
- no inference or database side effects;
- cold first-send, process RSS/PSS, temperature, energy proxy, and UI responsiveness comparison;
- automatic fallback to the existing on-demand path.

The NPU benchmark should also gain engine/conversation phase timings before making a backend-selection policy from startup latency.

## Verification and restoration

Both receivers completed successfully. No `FATAL EXCEPTION`, `AndroidRuntime`, package crash, or `UnsatisfiedLinkError` appeared in the final log scan. LAMI was force-stopped after the test to release accelerator resources, and ChatGPT was restored to the foreground. Device temperature remained 31.0 C.
