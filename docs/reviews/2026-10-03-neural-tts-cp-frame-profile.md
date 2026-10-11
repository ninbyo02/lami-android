# Retained-context CP frame profile

The trained FP16 five-layer CP backbone was run on SM8750 with QNN SDK 2.47 and a retained context. A captured CPU teacher-forced frame supplied 16 distinct positions, inputs and KV caches. `prepare_mtp_frame_qnn_inputs.py` validates the position, tensor shape, dtype and finiteness, and writes one QNN input-list row per CP position. The NPU outputs are not fed back into later positions; this is not free generation or an integrated realtime implementation.

| Run | Input rows | Inferences | Mean NetRun | Min | Max | NetRun IPS, including IO |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Short | 16 | 16 | 4.263 ms | 2.925 ms | 9.141 ms | 67.54 |
| Sustained | 16, repeated | 160 | 10.002 ms | 7.546 ms | 22.204 ms | 86.99 |

The first short run implies 68.2 ms for 16 CP executions, but the longer run implies 160.0 ms before CPU main, heads, decoder and scheduling. QNN NetRun IPS includes input/output file operations and is not the same as graph execution latency. The 16 output hidden vectors were finite, had 1024 elements, and differed between every adjacent position. This confirms distinct rows were executed, not generated-audio correctness.

The run used `high_performance` but did not control the device's concurrent LLM use, temperature, clock, or memory pressure. Input-list file IO and teacher-forced CPU caches differ from a persistent app implementation. The adjacent repeated-input experiment also ranged from 8.875 to 17.468 ms per step at different times; this frame run does not remove that variability. Neither the short run nor the sustained run establishes a causal change from the 16-slot cache pilot.

A stable 50–60 ms total per 80 ms of audio needs CP work comfortably below that budget. With 16 serial CP calls, 10 ms each is already too slow. Reducing static cache length alone did not solve this. The next viable model-level experiment needs fewer serial CP predictions or a substantially smaller trained/distilled backbone; fidelity and audible quality must be rechecked before app integration.
