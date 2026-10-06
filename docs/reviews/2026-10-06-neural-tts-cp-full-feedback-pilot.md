# CP full candidate feedback and later-frame evaluation (2026-10-06)

## Implementation and scope

An isolated host generator now accepts a CP candidate PTE, feeds its sampled codes into subsequent CP embeddings and the main transformer, and saves generated codes for diagnosis. PCM WAV is saved only after EOS. It checks the original frontend against the source model, verifies bundle hashes, enforces a bounded generation limit, and withholds audio if EOS is missing. Production Android playback remains unchanged.

The capture tool accepts up to 64 generated frames and can skip saving an initial span while still executing its full token feedback. A new argument guard rejects invalid spans before model loading; a cache-capacity guard prevents overflowing the main cache. Two new sentences, absent from the earlier train/validation lists, are fixed in `cp_later_frame_texts.jsonl`. They are now validation examples and must not be reused as an untouched final acceptance set.

## Full-feedback results

| CP model | First sentence frames / seconds | Second sentence frames / seconds | EOS and finite PCM |
|---|---|---|---|
| fp32 | 47 / 3.76 | 56 / 4.48 | passed |
| mlp | 55 / 4.40 | 48 / 3.84 | passed |
| ptq | 49 / 3.92 | 45 / 3.60 | passed |

All six generations reach EOS within the 96-frame limit. The WAVs are retained for listening review; EOS, finite PCM and changed duration do not establish intelligibility, pronunciation or equivalent cute voice quality. No listening acceptance or Android performance claim is made. Host jobs overlapped, so elapsed times are not compared as acceleration evidence.

## Later numerical screen

Both spans contain 256 actual CP inputs / 240 selected-head comparisons: frames 16–23 and 32–39 (zero-based). Every selected teacher code exactly matches the independently generated full FP32 baseline at the same sentence, frame and head. This checks that skipping capture storage preserves the token feedback trajectory. Baseline runtime hidden outputs also match each stored teacher input inside the checker.

| Span | Candidate | Rolling mean relative L2 | Rolling selected-code mismatches | Rolling top-1 mismatches |
|---|---|---|---|---|
| 16–23 | mlp | 0.062147 | 145/240 | 31/240 |
| 16–23 | ptq | 0.077366 | 174/240 | 42/240 |
| 32–39 | mlp | 0.062972 | 138/240 | 23/240 |
| 32–39 | ptq | 0.078618 | 155/240 | 36/240 |

The numerical checker rolls candidate KV caches with fixed teacher embeddings; the separate full-feedback test removes that limitation for its generated sentences. These checks complement each other. Sampled-token identity is sensitive to small distribution shifts and is not by itself an audio quality metric. Neither candidate is promoted.

## Validation and next action

Five capture guard tests passed, Python compilation and whitespace checks passed, actual bundle/runtime generation completed, and later-frame teacher token parity passed. Models, raw captures, WAV/code files and reports remain under the voice dataset `qat/cp-int8-qat-pilot-20261006` directory. Only code and summarized evidence are committed.

Next: listen to the retained full-feedback WAVs and evaluate intelligibility with broader sentence coverage before selecting a quantization/training route. Realtime remains unmet; the main transformer and 16 serial CP steps still require substantial acceleration or a trained architecture change.
