# Fixed-input CP head comparison (2026-10-09)

Debug opt-in `cp_head_fixed_benchmark` compares one real CP head (head 0, 2048 × 1024) with three deterministic input vectors. Seven paired trials alternate variant order; three warmups and sixteen timed calls per variant. All timed outputs checked bit exact after timing. JNI allocation/copy included; packing and output validation excluded. No frequency or thermal control. This isolates kernel layout effects, not full-model realtime or broad head coverage.

| Input | Row-major median ms | Packed4 median ms |
|---|---|---|
| 0 | 1.2209 | 0.8620 |
| 1 | 1.1648 | 0.7926 |
| 2 | 1.1195 | 0.7857 |

All eight full-feedback requests completed with accepted code/PCM hashes. Original APK, manifest, WAV and probes restored and hash verified. Default path remains row-major; packed weights add 120 MiB across all CP heads and per-request packing cost. CPU-only smoke APK; no realtime or NPU acceptance. See adjacent JSON for all paired trials.

Decision: fixed-input head 0 comparison supports a layout speed benefit (about 29–32% lower kernel time), without establishing realtime or all-head benefit. Keep default unchanged. Next candidate: prepack verified head files offline and mmap them directly, avoiding per-request 120 MiB allocations and packing. Require full output hashes and separate first-use/memory evidence before promotion.
