# Preliminary CPU-vs-NPU CP KV-cache comparison — 2026-10-15 06:45 JST

Source: same fixed 1024-element embedding, position 0..15 with carried KV,
ExecuTorch host CPU reference cp-stateless-fp32-cache32-et14.pte vs on-device
production-fp32 Qualcomm QAIRT QNN 2.47 16-step qnn-net-run output.
CPU cache layout [5,1,8,32,128] transposed into QNN [1,32,8,128].
Causal masked values were -10000 for both pipelines. All 160 position/layer/K-or-V
comparisons completed without malformed shapes.

- Worst absolute difference: 0.42852783203125 at position 9, K, layer 3
  (relative L2 of that tensor 0.0059185582)
- Worst relative L2: 0.0061813286 at position 1, K, layer 4
  (maximum absolute error there 0.0779476166)
- At position 15 worst relative L2: 0.0060173655, K, layer 4
  (maximum absolute error there 0.1169042587)

Numerical differences are NONZERO, and quality/acceptance threshold has not
been established. Investigate CPU-vs-QNN weight and arithmetic provenance,
as well as impact on head/code sampling and decoded audio. This is neither
a speech-quality verdict nor a speed benchmark. The independent QNN capture
and model binaries remain local, untracked; do not commit large model files.
