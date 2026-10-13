# LAMI Resident CP Rust core (host prototype)

Implements the CP resident context lifecycle, 16-position ordering, reusable typed
tensor storage and an optional SDK-backed driver. No phone inference or latency
improvement is established by host tests or cross-compilation.

Run: `cargo test --offline && cargo clippy --offline --all-targets -- -D warnings`

## SDK-backed prototype

The `qnn-sdk` feature compiles a narrow C bridge directly against the existing
official QAIRT 2.47 headers (core API 2.36, System API 1.11). No QNN structure
layout is re-created in Rust. The bridge rejects a different runtime API/build,
graph name, tensor signature, dtype, shape, dynamic dimensions or buffer alias.

`QnnBackend` retains one context, graph, context binary and boxed tensor storage.
Each successful step calls synchronous `graphExecute`, checks finite outputs
and unchanged cache positions, then copies the produced KV into existing input
allocations. A new frame clears KV without loading the model again. The graph
returns one 1024-element hidden tensor and ten KV tensors, not fifteen logits
heads. Sampling and the original heads remain the caller's responsibility.

RAW client buffers remain the default. The optional registered RPC/ION mode
uses the exact allocator signatures/heap/flags of SDK 2.47's shared-buffer sample
and `QnnMem_register` for 24 buffers once per context. Its allocations/handles
are reused and deregistered during close. Explicit input/output copies remain;
zero-copy is **not** implemented or claimed. No ADB, subprocess, model load or file
transfer occurs in the per-step Rust driver path. Android JNI and ordinary chat
voice routing are not connected or enabled.

`fixed_probe` is a silent, bounded diagnostic, not a voice generator. It freezes
each supplied input/KV state, then repeats real graph execution without context
reload. CPU-origin FP32 cache rounding outside the current position is recorded
in this mode, rather than silently adopted by the strict normal-step path.
`LAMI_CP_RPC_LIBRARY` selects registered buffers before open. RSS, registration
count and separate SDK/copy/validation timings are printed; no device permissions
or global frequency settings are changed.

The caller must hash and verify the pinned context bytes before calling
`from_verified_bytes`; this constructor does not implement SHA-256 itself.
Production adoption still requires matched input/KV numerical tests, real phone
execution, full utterance/EOS and voice-quality review.

Set `QAIRT_SDK_ROOT` to the existing 2.47 installation and use
`cargo test --offline --locked --features qnn-sdk`. Android compilation also
requires the existing Android Rust target and NDK compiler supplied as `CC`,
`AR` and Cargo's target linker. No SDK download or installation is performed.

`metadata_probe VERIFIED_CONTEXT LIB_QNN_SYSTEM` (with `qnn-sdk`) validates the
actual binary's 13/11 tensor signature through the System library only. It does
not load an HTP backend or execute a graph; `graph_executions=0` is intentional.

## Debug JNI/FFI Diagnostic

The optional `ffi` feature exports token-handle operations from
`native/resident_cp_ffi.h`. QNN objects are created and retained on one native
worker; no unsafe Send implementation or QNN pointer is exposed to Java.
Commands are synchronous and serialized. Reset clears KV and abandons partial
frames. Cancel operates between steps, not during an in-flight SDK call. Failed
execution poisons the worker until release; recovery requires a fresh context.
Caller arrays/context bytes are copied and never retained as borrowed pointers.

`ResidentCpRustNativeApi` implements the existing Android NativeApi in the debug
source set only. It verifies the same immutable bytes passed to native, checks
1024/16/32 shape contracts and owns/reaps its tokens. No production routing,
model, CPU fallback, Activity or manifest registration is changed. Driver/Session
calls remain caller-serialized; the low-level native adapter serializes access.
The library is not automatically built, packaged, installed or loaded by the app.

`ResidentCpRustJniProbe` is an explicit command-line diagnostic. Existing SDK
libraries and a verified context are prerequisites. It checks reuse, partial
frame cancellation, stale-driver rejection, release and fresh-context recovery
against a frozen first-frame output, without audio. APK/app namespace behavior
and the complete Main/head/PCM Android pipeline remain separate gates.

Host verification needs no vendor SDK:

```bash
cargo test --offline --locked --features ffi-host-test
cargo clippy --offline --locked --features ffi-host-test --all-targets -- -D warnings
```

`ffi-host-test` is compile-time prohibited on Android. Its fake backend echoes
input and is not a hardware or voice-quality test. After the normal standardDebug
unit test build, `python3 scripts/check_resident_cp_jni.py` from the repository
root invokes real JNI with `-Xcheck:jni` using that fake backend, then removes all
temporary model/library/PCM-free fixture artifacts. Hosted CI does not need QNN
headers or download a Qualcomm SDK. Optional real SDK compilation continues to
require `QAIRT_SDK_ROOT` and the `qnn-sdk` feature.

### Current Evidence And Limits

The original native ELF route generated two 19-frame utterances with one context,
24 registered buffers and 608 calls; each backend's first/reused PCM matched
bitwise. Main and PCM decoder were on the host, not in Android JNI. The hybrid
NPU generation wall times 13.995/13.581 s contain SDK graph 4.380/4.223 s,
native sampling 2.617/2.708 s, Main prefill 1.439/1.427 s and measured copies.
Main continuation/head/outer-loop residual is 2.315/2.295 s; pipe/guard/Rust
validation/reset residual is 2.950/2.797 s. Network RTT alone was not measured.
CPU's 8.589/8.546 s similarly combines CP/sampling (4.850/4.854 s), prefill
(1.458/1.447 s) and unseparated continuation. Decoder was a separate phase.
These numbers are not intrinsic NPU latency or Android CPU/NPU speed parity.

The app_process JNI attempt reached nativeCreate but initially failed at
deviceCreate with SDK error 14001 (QNN_DEVICE_ERROR_INVALID_CONFIG). Explicit
FastRPC loading exposed an unresolved libhidlbase.so dependency in the Java
native-library namespace. No permission change or additional library was used
to bypass this restriction. Real-device JNI graph execution and installed-app
namespace validation have not passed; this diagnostic remains opt-in and must
not be promoted. Saved comparison WAVs and raw device traces remain local-only.
