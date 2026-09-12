# GPU accelerator isolation with NPU retained — 2026-09-12

## Result

A single accelerator substitution resolves the observed short-prompt GPU invoke failure while retaining successful NPU diagnostic inference. Starting from the controlled StandardDebug APK, remove `libLiteRtClGlAccelerator.so` and add `libLiteRtOpenClAccelerator.so` from the previously successful GPU candidate. Every common ZIP entry outside signing metadata is byte-identical, including libLiteRt.so, liblitertlm_jni.so, Qualcomm/QNN/NPU libraries and DEX/resources. This is a controlled accelerator-provider substitution, not an attribution to one internal function.

The two accelerator binaries export `LiteRtAcceleratorImpl@@VERS_1.0` as a 200-byte object. That alone does not establish ABI compatibility; fresh device runs below are the functional evidence.

## Validation

Isolated package: io.github.ninbyo02.lami.gpucontrolled, NX733J. Candidate APK SHA-256: `7c74b1485518ebcc3b94a80234dbd2fa2d11636f7c7faae79bda7ad65bdba70e`.

- APK signature verification passed.
- All 13 required NPU-ready native libraries exactly match verified staging; patched Conversation API marker retained.
- GPU: current user generic model, prompt こんにちは, typed Contents callback, null modalities/cache, Gallery sampler. Actual Engine context 4096: success; 1024: success. Each has 12 text callbacks, onDone=1, onError=0, no fallback and no timeout. Actual context is from engine-construction markers; the old report formatter's parity-default field is not authoritative for overrides.
- NPU: same APK, SM8750 model, Kotlin Conversation Product Route durability receiver. 18/18 route successes and 18/18 correct arithmetic answers, engine reused 15 times, conversation reused 13 times. Simulated brief background/foreground, chat switching, background expiry and low-memory recreation checks all passed.
- NPU diagnostic uses synchronous responses and simulated lifecycle calls. GPU uses streaming callbacks. These do not establish ordinary ChatScreen/TTS/stop/long-output reliability or simultaneous GPU/NPU execution in one process.

## Host observation caveat

The NPU receiver ends with status=success. The host polling script originally looked for completed/failed/failure and therefore waited to its observation deadline despite all 18 turns having finished. The result above is taken from the receiver's complete persisted result (including route_pass=true and lifecycle_pass=true), not the host deadline label. No NPU failure was observed in this batch.

## Interpretation and next gate

The earlier whole-stack difference is now narrowed to the GPU accelerator provider selection/package contents. This experiment retains the working NPU stack and demonstrates a viable combined candidate for the two tested inference routes. It does not establish why the ClGl implementation fails internally; further native tracing would be needed for that upstream-level root cause.

Implement reproducible staging/packaging for the pinned OpenCL provider, preserve the existing NPU packaging gates, and qualify the resulting normal app through GPU/NPU ChatScreen streaming, TTS, stop/recovery and longer conversation tests before promotion. Do not use the old GPU-only minimal APK as a production replacement.

The standard user's APK, preferences, models and chat history remain unchanged. Only the separate diagnostic package and additional diagnostic model files were used. All tests ended and ChatGPT was returned to foreground.
