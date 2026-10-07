# Decoder input reuse pilot

CP performs 16 forward calls per codec frame and repeats positions 0..15. Cache immutable RoPE, mask and position tensors per decoder and reuse an owned direct hidden buffer. Preserve values, cache output copies, cancellation checks and EOS enforcement. Add main/cp input_prepare metrics including lazy position initialization. The forward metric now excludes input tensor construction, so compare whole frame and combined preparation + forward timings rather than forward alone.

Main cache128 device comparison completed successfully for all eight utterances. Weighted main forward means were 71.017ms for cache256 and 45.228ms for cache128. Frame p50 ranges were 148–173ms and 121–145ms respectively. Every frame exceeded 80ms. All code and PCM hashes matched across capacities and repeats. Original APK, manifest, last WAV and both probe files were restored and hash verified. Model pilots were removed. Full measured rows are in 2026-10-08-neural-tts-main-cache128-device.json. These serial frame timings exclude PCM decode and do not establish realtime performance.

Input reuse device comparison remains pending. This change is debug-only and does not alter model weights or sampling. Reducing Java allocations alone may yield a small gain; the 16 serial model calls remain.

Validation: JDK21 `:app:compileStandardDebugKotlin` passed (1m47s); `git diff --check` passed. Device output/latency comparison is pending.
