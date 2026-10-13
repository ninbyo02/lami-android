# LAMI cute TTS: resident CP native handoff (PC-only)

## Scope and preservation
- Current voice: Qwen3-TTS 0.6B-derived lami_cute; no model or speaker changes.
- Isolated branch: agent/tts-resident-cp-host-20261013.
- No ADB, phone access, GPU preemption, or modifications to existing generated audio.
- Do not promote this driver or claim a speedup from host-only fake backend tests.

## Existing code found
- `app/src/debug/cpp/lami_npu_persistent_holder_stub.cpp`: test-only native create/close holder with diagnostics and fatal latch. Not a resident CP execution backend.
- `app/src/debug/cpp/qnn_direct_probe_debug.cpp`: QNN dependency/probe loading diagnostics, not the CP 16-position inference driver.
- Existing CP AOT binary: `/home/sato/tmp/lami-tts-npu-pilot/production-plain/aot/mtp-qnn-context.bin`.
- Existing PC-only save-trace parity gate: `/home/sato/lami-tts-daemon/cp-host-validation-20261013/check_paired.py`. Saved traces have only 8/32 identical step inputs and 2/32 identical incoming KV caches; they cannot be used for a CPU/NPU speedup ratio.
- Existing resident CP abstractions: ResidentCpSession, ResidentCpBufferedBackend, ResidentCpQnnDriver, ResidentCpContextSpec, ResidentCpArtifactVerifier and ResidentCpRoutingPolicy.

## Native driver requirements before hardware trial
1. Investigate verified QAIRT SDK headers, graph I/O name/shape/dtype/stride and CP KV tensor ownership for the *exact* AOT context. Do not infer runtime API signatures from a diagnostic stub.
2. Bind NativeApi.loadContext to a single native context plus graph lifetime. Verify actual context file hash immediately before opening, reject stale/mismatched artifacts.
3. Allocate/retain native I/O tensors and KV storage per context. No file I/O, context create/free or model init in per-position forward.
4. Execute positions 0..15 serially with previous CP code/owned cache passed to the next step. Distinguish per-frame KV reset from model reload. Do not reset KV inside a frame.
5. Time context creation, input copy, graph execute, cache update, output extraction, native synchronization separately. Never add nested counters.
6. Use matching text, weights, input tensor, KV cache and sampling draws against CPU control. Hard-stop on divergence; preserve CPU path.
7. On native fatal errors, close/discard context, do not retry a poisoned handle, never silently promote NPU output or overwrite generated WAV.
8. Test cold/warm lifecycle and 16-step parity on the phone only after the weekday 19:30 access restriction, and only in a controlled device test.

## Known blocker
The current Kotlin NativeApi is an interface with fake tests. There is **no actual QNN/HTP NativeApi implementation** and the existing holder is not an inference engine. Resident CP performance is unmeasured.
