# Preliminary CPU ExecuTorch vs SM8750 QNN 16-step comparison

2026-10-15 06:41 JST. CPU model prepared-hai-android-v1/cp-stateless-fp32-cache32-et14.pte, host lami-executorch environment. NPU reference: independent QAIRT qnn-net-run 16-step carried-cache outputs with fixed identical synthetic embedding and input position sequence, production-fp32 AOT. Both ran 16 positions with carried K/V values.

Measured hidden-vector differences (1024 f32 values each): at position 15 max_abs=0.0763134956, rmse=0.0250873026, relative_l2=0.007209722. Across the 16 positions max_abs is approximately 0.09, and relative_l2 around 0.007. This is preliminary and is NOT an acceptance result. CPU mask uses -1e9 where QNN uses -10000, and arithmetic/backend/checkpoint parity must be independently confirmed. There is no subjective listening evaluation or actual speech latency measurement in this test.

Reproduction code: scripts/voice/compare_cp_cpu_npu_16step.py. Local raw baseline QNN captures live in diagnostics/resident-cp-rust/target/qnn-reference-16step-20261015 and are intentionally untracked.

## Mask-alignment sensitivity test, 2026-10-15 06:43 JST

Repeated all 16 ExecuTorch CPU steps with causal masked entries changed from -1e9 to -10000.0, matching the QNN context's diagnostic mask. The hidden tensor metrics were unchanged at the displayed precision for every step. At position 15: maximum absolute difference 0.0763134956, RMSE 0.0250873026, relative L2 0.007209722. Worst over the 16 positions: maximum absolute difference 0.0899554491, RMSE 0.0263598487, relative L2 0.0075754309. Hence mask sentinel alone is not the observed discrepancy driver on these synthetic samples. Next assess whether the CPU reference program and NPU export share exactly the same weight checkpoint and operations, and evaluate active position K/V cache tensor parity and downstream sampling/audio quality before imposing an acceptance threshold. No main assistant application change.
