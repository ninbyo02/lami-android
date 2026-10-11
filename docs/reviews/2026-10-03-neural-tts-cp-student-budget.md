# CP student size feasibility gate

The approved trained five-layer CP uses 16 serial transformer executions per 80 ms codec frame. Before training a smaller student, this diagnostic measured the execution budget for the last two and last one trained layers on SM8750. These are suffix graphs, **not trained students**. Their input embeddings are the original captured CP inputs, not the missing prefix activation; only graph throughput is meaningful. This experiment makes no audio-quality claim.

The two-layer suffix (start_layer=3) was exported from the trained checkpoint, checked against an exact CPU source split at a synthetic position-zero input, and compiled to a single fully delegated SM8750 dispatch op. The existing one-layer suffix (start_layer=4) was compiled the same way. The 16 distinct captured input positions were each repeated across a 160-inference QNN SDK 2.47 retained-context run, with high_performance selected and the same device. The output file policy kept one result. CPU caches were supplied for their respective layers, so no rolling model state was generated.

| Graph | Context size | Mean NetRun | Min | Max | 16 × mean |
| --- | ---: | ---: | ---: | ---: | ---: |
| Five layers, prior frame run | 157.7 MB | 10.002 ms | 7.546 ms | 22.204 ms | 160.0 ms |
| Last two layers, this run | 63.1 MB | 7.818 ms | 3.442 ms | 13.810 ms | 125.1 ms |
| Last one layer, this run | 31.6 MB | 3.828 ms | 1.873 ms | 6.751 ms | 61.2 ms |

The device's concurrent load, clock and thermals were not controlled; these runs are not a normalized per-layer speedup claim. Even the one-layer proxy consumes about 61 ms for CP alone, with no time for CPU main, selected-head projection, speech decoding or scheduling within the 50–60 ms target. The prior CPU frame budget test also found 259–283 ms per complete 80 ms frame. A one-layer distillation effort by itself is therefore unlikely to meet realtime playback on this hardware.

The current captures contain only two short Japanese prompts and at most two frames each, far too little to train or assess a natural voice. The next architecture experiment should target fewer serial code predictions per codec frame and main-model acceleration, with teacher-generated data and held-out listening review. No layer-pruned speech is being presented as acceptable audio.
