# CP thread count fixed-input host probe

Verified grouped INT8 CP cache16 model, saved actual host position15 input,
16 traced executions per process, three alternating trials at 1, 2 and 4 CPU
threads. Median Method::execute milliseconds: {"1": 2.0740888125000003, "2": 1.9842408125, "4": 2.4522135625}.

This short host x86 probe does not establish Android speed or output equality.
Scheduling and cache state are uncontrolled; tracing overhead is included.
Four threads did not improve this sample. Most time remains in XNNPACK
(delegate calls), with roughly 0.3–0.4 ms in native operations. Changing the
Android thread count without measuring exact outputs and end-to-end timing
is not justified. Next: inspect Android runtime thread configuration and
prepare a fixed-input device probe with thread-local/runtime controls only
if the API supports it. Keep app defaults unchanged.

The reusable script pins the model hash and records input hashes. It refuses
an existing output directory. No model or Android code is modified.

## Android runtime source inspection

Pinned ExecuTorch v1.4 source at 3dd7ccd1d863fad22639dd2d918ae34a41ce45f0:
`extension/android/jni/jni_layer.cpp` receives numThreads at module creation.
With ET_USE_THREADPOOL, zero selects logical processors / 2. Without the
EXECUTORCH_HAS_THREADPOOL_USE_N_THREADS_GUARD build flag, construction calls
_unsafe_reset_threadpool on the shared pool. With that flag, execute instead
uses UseNThreadsThreadPoolGuard. The OSS threadpool guard header supplies
NoThreadPoolGuard; the N-thread guard is referenced from an fb-only path.
This source inspection does not establish the packaged AAR build flags.
Therefore a per-CP thread override is not yet safe to promote; inspect the
actual AAR API/build before any device experiment. A controlled serial
whole-pipeline thread test remains possible after that verification.

## Next model pilot

CP stateless export updates all five KV caches with index_copy and stacks
whole-cache outputs. Prior host trace attributed about 0.17–0.24 ms/step to
index_put, not including all associated copies. At 16 calls/frame, eliminating
that operation alone would save about 2.7–3.8 ms on the host trace, not enough
by itself to close the device gap. Investigate a stateful/delta-cache isolated
export that returns only current K/V and uses owned cache storage. Gate on
exact hidden/KV outputs at positions 0 and 15 and full feedback code/PCM
agreement. Do not assume output-buffer aliasing is safe. Most CP time remains
in delegated compute, so cache work must be measured rather than presumed.
