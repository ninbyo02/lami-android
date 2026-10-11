# CP cache length 16 pilot

The CP frame uses positions 0 through 15, so a static 16-slot cache can contain the whole frame. The export option `--cache-length 16` changes only the cache and attention shapes; the 32-slot export remains the default. With the trained LAMI checkpoint, the source backbone and original MtpStep agree exactly at position zero. The SM8750 AOT model contains one dispatch op and no remaining CPU ops.

On a captured frame-zero position-eight input, the SM8750 FP16 16-slot output and the existing 32-slot output agreed exactly on the hidden vector and the first 16 slots of all ten KV tensors. This checks one input and does not establish full generated-speech parity.

The same phone ran 100 repeated identical inputs with a retained QNN SDK 2.47 context and `high_performance` profile:

| Graph | Mean NetRun per CP step | Min | Max |
| --- | ---: | ---: | ---: |
| 16-slot cache | 16.915 ms | 7.750 ms | 26.574 ms |
| 32-slot cache, adjacent rerun | 17.468 ms | 9.152 ms | 26.765 ms |

The 0.553 ms (3.2%) difference is too small for the 80 ms frame budget. At 16 CP steps, the 16-slot mean alone is about 271 ms per frame. Both adjacent runs were much slower than the earlier 32-slot 8.875 ms measurement. Concurrent device load, clock and thermal state were not controlled; these results cannot establish a causal speedup. Even applying the observed 3.2% ratio to the earlier 8.875 ms would yield about 137.5 ms for 16 CP steps, before the main model, heads and decoder.

Do not use this as a realtime configuration. A structural reduction of CP invocations or model compute, plus an end-to-end rolling-cache benchmark under concurrent LLM load, remains necessary.
