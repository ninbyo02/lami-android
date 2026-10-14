# Resident CP context-open probe: 2026-10-14 JST

- Target: nubia NX733J SM8750, Android 16, authorized ADB 192.168.52.52:43225
- Package: isolated io.github.ninbyo02.lami.cpdiagnostic, separate :cp_context_probe process
- Model: production-fp32/aot/mtp-qnn-context.bin; SHA-256 1ebaf036a376c886fe135afbf5f87dfb651be2804dc8675f1035be9ca7a92136
- Runtime: isolated QAIRT 2.47 aarch64-android HTP/System/V79 Stub, APK hashes validated.
- JNI and QNN libraries had loaded in previous library probe.
- Context probe failed with IllegalStateException, Rust FFI status=3 (BACKEND).
- Exact underlying SDK API stage is unknown. No successful QNN context, CP steps, audio or NPU acceleration observed.
- Three bounded attempts were unsuccessful. Do not retry without a more informative error channel.
- No ordinary LAMI app changes. Isolated diagnostic process force-stopped.
- Rust host ffi-host-test: 17 passed. Android diagnostic build succeeded.
- Next: report exact QNN API stage and SDK error through a bounded diagnostic channel, review RPC namespace and graph metadata before more device testing.
