# Decoder input reuse pilot

CP performs 16 forward calls per codec frame and repeats positions 0..15. Cache immutable RoPE, mask and position tensors per decoder and reuse an owned direct hidden buffer. Preserve values, cache output copies, cancellation checks and EOS enforcement. Add main/cp input_prepare metrics including lazy position initialization. The forward metric now excludes input tensor construction, so compare whole frame and combined preparation + forward timings rather than forward alone.

Main cache128 device comparison completed successfully for all eight utterances. Weighted main forward means were 71.017ms for cache256 and 45.228ms for cache128. Frame p50 ranges were 148–173ms and 121–145ms respectively. Every frame exceeded 80ms. All code and PCM hashes matched across capacities and repeats. Original APK, manifest, last WAV and both probe files were restored and hash verified. Model pilots were removed. Full measured rows are in 2026-10-08-neural-tts-main-cache128-device.json. These serial frame timings exclude PCM decode and do not establish realtime performance.

Input reuse device comparison completed: all eight utterances reached completion, codes and PCM were bit-identical across both versions and repeats, and original APK/manifest/WAV/probes were restored with verified hashes. No duplicate Voice Lab clips were needed. This change is debug-only and does not alter model weights or sampling. Reducing Java allocations alone may yield a small gain; the 16 serial model calls remain.

Validation: JDK21 `:app:compileStandardDebugKotlin` passed (1m47s); `git diff --check` passed. Device output consistency passed; a speed improvement was not established.

## Device timing

Baseline frame p50 values: 109, 137, 122, 145ms. Input reuse: 128, 122, 142, 119ms. Both ranges overlap; do not claim a speed improvement. Main preparation-plus-forward means were 41.066 vs 44.281ms/call; CP 3.309 vs 3.375ms/call. Baseline forward excludes RoPE parsing whereas the new preparation metric includes it, so these combined figures are not equivalent scopes. Whole-frame timing is the primary comparison. Temperature/frequency were uncontrolled. PCM decode is outside the frame budget.

The change reduces repeated allocations and provides preparation metrics, but does not remove the 16 serial CP model calls. The next investigation should address model-internal cache operations or per-frame native execution, with output and sampling equivalence verified before promotion.
