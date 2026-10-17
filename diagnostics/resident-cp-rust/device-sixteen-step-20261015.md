# SM8750 QNN HTP sixteen-step diagnostic, 2026-10-15 06:28 JST

Isolated io.github.ninbyo02.lami.cpdiagnostic debug APK, opt-in sixteen-step
Activity in its own process. Uses pinned model and SDK, fixed synthetic
embedding repeated at positions 0..15. No real speech/audio emitted and
normal app not modified.

First invocation failed closed at pinned fixture hash gate because the
private diagnostic folder had a newer unmatched input fixture. Correct
original pinned input and associated position-zero reference were then
restored in the isolated diagnostic app, without touching production.

Second invocation:
- status=SIXTEEN_STEPS_RETURNED
- positions_completed=16
- step_durations_ns=18488437,30245417,27303021,20085625,29769218,29529844,29503437,29844896,22680938,17903229,18322864,18165208,17975729,18270677,30178490,18317396
- total_step_ns=376584426 (mean 23.54 ms)
- context_load_ns=440562396
- kv_reset_ns=1366718
- native_stats=1,16,24,1,0,128536094,309331355
- last_output_sha256=2eced53f36b33b91b0f447f1b3020dbb6fa6d5c1526f66ddb68c09b02fd96e44
- reference_checked=false; audio_played=false

NPU execution and single-context reuse are established for one synthetic
16-position test. Accuracy, KV-value correctness, CPU numerical parity,
speech quality, speed under contention and sustained operation remain unproven.

## Cache handling code review, 06:31 JST

The verified 16/16 result exercised the Rust QnnBackend::execute() path,
which calls execute_graph(position, true). After each successful SDK graph run,
validate_cache_write checks that all FP32 K/V elements outside the active
position remain bit-identical (5 K + 5 V tensors, dimensions 32 x 8 x 128).
On a detected unrelated position change the step fails instead of adopting
the output. A passing step copies all output K/V tensors to next-step input
without reallocating the tensors. The native stats receipt reported 16
executions and a single context open.

This confirms that the strict write-boundary invariant did not fail in the
synthetic 16-position run; it does NOT independently prove values written
at the active positions are correct or that the cache affects the subsequent
hidden state correctly. Remaining work: compare same-context independent
QNN 16-step carried-KV rollout per position (hidden and K/V outputs), and
compare to same-input CPU model.
