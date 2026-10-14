# Preliminary CPU/NPU CP-head greedy selection comparison

2026-10-15 06:47 JST, single repeated synthetic embedding through position 0..15.
Compared CPU ExecuTorch and SM8750 independent QNN hidden outputs with the
same original cp.head.0 .. cp.head.14 weight matrices (2048 x 1024 FP32).
Across 15 heads, CPU and NPU **greedy argmax agreed 15/15**.
No audio was decoded or played. The production sampling is temperature 0.9,
top-k 50 with deterministic pseudorandom draws; argmax-only agreement does
not establish identical sampled code choices, good voice quality, or end-to-end
speech latency. This is a narrow synthetic-case result, not an acceptance test.
Reproduction: scripts/voice/compare_cp_head_selection_16step.py, which expects
the existing local non-versioned model weights and QNN reference outputs.
