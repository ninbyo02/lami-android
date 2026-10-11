# CP MLP-only INT8 pilot (2026-10-06)

## Scope

Dynamic signed INT8 per-channel weights are limited to the 15 MLP projections in the five-layer code predictor. Attention projections, norms, and heads retain FP32. The exporter checks exactly 15 INT8 weight constants; the default full INT8 mode now checks exactly 35. No production bundle is changed.

## Validation

The same four validation sentences and 128 captured steps used by the QAT pilot are evaluated, including 120 selected-head comparisons. These are the first two audio frames only. Candidate KV caches roll forward, but embeddings remain fixed teacher inputs. This is a bounded numerical screen, not an audio quality acceptance test. The validation set has already informed previous pilot choices and is not an untouched final test.

- teacher: mean relative hidden L2 0.063696; maximum 0.150834; selected-code mismatches 85/120; top-1 mismatches 17/120.
- rolling: mean relative hidden L2 0.065083; maximum 0.132026; selected-code mismatches 97/120; top-1 mismatches 20/120.

Host median per step: FP32 15.048 ms, MLP INT8 11.420 ms (1.32x). Seven alternating trials; these Python runtime bridge timings do not establish Android performance.

The full INT8 PTQ control on this validation set had teacher/rolling selected-code mismatches 85/120 and 90/120, and maximum relative hidden L2 0.175882 and 0.140728. Exact sampled-token identity is sensitive to small distribution shifts and is not by itself a voice quality metric. None of these pilots is approved for normal playback.

Next: compare selective quantization on later frames and an untouched sentence set, then validate full candidate-token feedback and audio before a device performance trial. Realtime remains unachieved: accelerating CP alone does not remove the main transformer cost or its 16 serial CP calls per audio frame.

Checks: actual export and runtime comparison completed; capture split guard unit tests (4) passed; git diff whitespace check passed. No Android source changed.
