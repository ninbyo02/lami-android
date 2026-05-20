# LiteRT Qualcomm Dispatch Capability Source Trace

Date: 2026-05-21

Scope: source-only trace of the QAIRT 2.44 / Qualcomm NPU dispatch failure path. No source was modified, no build was run, and no inference or dry-run was run.

## Finding

`No usable Dispatch runtime found` is emitted by LiteRT's TFLite dispatch delegate after dispatch API initialization fails before delegate kernel creation. It does not mean only "missing `libLiteRtDispatch_Qualcomm.so`". A dispatch `.so` can be present and can export `LiteRtDispatchGetApi`, but still be unusable if the loaded dispatch API cannot initialize QNN/HTP, cannot satisfy LiteRT/dispatch/QNN version checks, cannot create an HTP device for the detected SoC, or cannot later consume the compiled model's dispatch bytecode.

The exact fatal site is:

- `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/dispatch_delegate.cc:112-120`: `Initialize()` calls `InitializeDispatchApi()`. On any failure it logs `Failed to initialize Dispatch API: ...` and sets `has_dispatch_runtime_ = false`.
- `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/dispatch_delegate.cc:125-132`: `CreateDelegateKernelInterface()` checks `has_dispatch_runtime_`; if false, it calls `LITERT_FATAL("Failed to create a dispatch delegate kernel: No usable Dispatch runtime found")`.

So the actionable root cause is upstream of the fatal: inspect the `Failed to initialize Dispatch API` log and the Qualcomm/QNN logs it wraps.

## Call Chain

1. LiteRT-LM selects NPU:
   - `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_compiled_model_executor_factory.cc:164-176` routes `Backend::NPU` to `LlmLiteRtNpuCompiledModelExecutor::Create`.
   - `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_litert_npu_compiled_model_executor.cc:486-505` creates LiteRT options with `HwAccelerators::kNpu | kCpu`; on Android it creates Qualcomm options, sets QNN log level off, and HTP performance mode `kBurst`.

2. LiteRT builds the compiled model and applies registered accelerators:
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/compiled_model.cc:764-803` initializes the model and runtime.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/compiled_model.cc:809-820` injects dispatch opaque options containing the model allocation base and fd.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/compiled_model.cc:831-887` creates and applies accelerator delegates with `ModifyGraphWithDelegate`.

3. The NPU accelerator creates the dispatch delegate:
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/accelerators/dispatch/dispatch_accelerator.cc:138-149` requires `kLiteRtEnvOptionTagDispatchLibraryDir` unless dispatch is statically linked.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/accelerators/dispatch/dispatch_accelerator.cc:83-90` creates and wraps `CreateDispatchDelegatePtr`.

4. Dispatch delegate initializes the dispatch API:
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/dispatch_delegate.cc:174-181` calls `LiteRtDispatchInitialize(...)` and then `LiteRtDispatchCheckRuntimeCompatibility(...)`.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/dispatch_delegate.cc:183-215` reads vendor id, build id, dispatch API version, capabilities, and creates the dispatch device context.

5. Dynamic dispatch runtime loading:
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/litert_dispatch.cc:97-116` discovers dispatch libraries under `kLiteRtEnvOptionTagDispatchLibraryDir`; no library returns runtime failure.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/litert_dispatch.cc:151-164` loads the selected `.so`, resolves `LiteRtDispatchGetApi`, and calls it.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/litert_dispatch.cc:171-180` checks the dispatch API struct version and calls the vendor `initialize` function.

6. Qualcomm dispatch API initialization:
   - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/dispatch/dispatch_api.cc:70-138` parses Qualcomm options, uses dispatch library dir as QNN shared library dir, creates `QnnManager`, then reads QNN API/build IDs.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/dispatch/dispatch_api.cc:176-179` reports only `kLiteRtDispatchCapabilitiesBasic` after successful initialization.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/dispatch/dispatch_api.cc:344-367` checks LiteRT API compatibility; newer LiteRT caller than dispatch build is rejected.

7. QNN/HTP backend setup:
   - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/qnn_manager.cc:392-418` sets/extends `ADSP_LIBRARY_PATH` and adds `libQnnHtp.so` to the loader path when a dispatch library dir is supplied.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/qnn_manager.cc:420-428` loads `libQnnSystem.so`, resolves system API, loads `libQnnHtp.so`, and checks the expected HTP backend API version.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/qnn_manager.cc:154-253` rejects missing providers, wrong QNN core API major, too-old QNN core minor, wrong backend API major, and too-old backend API minor.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/core/backends/htp_backend.cc:397-420` obtains platform SoC info on Android, maps SoC model to LiteRT's table, and fails if no DSP arch is configured.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/core/backends/htp_backend.cc:423-499` configures and creates the QNN HTP device handle for that SoC.

8. Model dispatch bytecode is required after the runtime is accepted:
   - `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/dispatch_delegate_kernel.cc:495-545` reads dispatch custom op initial data, expects nonzero `bytecode_offset`, constructs a `LiteRtMemBuffer` pointing into the model allocation, and calls `LiteRtDispatchInvocationContextCreate`.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/core/dispatch_op_schema.cc:74-86` defines the custom options as flexbuffer keys `bytecode_size`, `bytecode_offset`, and `name`.
   - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/dispatch/litert_dispatch_invocation_context.cc:114-180` parses the QNN context binary, finds the graph by function name, creates a QNN context from the binary, and retrieves the graph.

## Why Symbol Presence Is Not Enough

`LiteRtDispatchGetApi` only proves that the vendor dispatch shim can return a `LiteRtDispatchApi` struct. It does not prove any of these:

- The dispatch API struct version matches the LiteRT runtime (`litert_dispatch.cc:171-174`).
- The vendor `initialize` function can create `QnnManager` (`dispatch_api.cc:129-138`).
- `libQnnSystem.so` and `libQnnHtp.so` can be found from the app namespace and dispatch library dir (`qnn_manager.cc:392-428`).
- QNN provider counts and QNN core/backend API versions satisfy the build-time QAIRT headers (`qnn_manager.cc:154-253`).
- Android HTP platform info maps to a supported SoC with DSP arch (`htp_backend.cc:397-420`).
- HTP device creation and performance-mode setup succeeds for that SoC (`htp_backend.cc:423-520`).
- The `.litertlm` model contains LiteRT dispatch custom ops whose options point to valid embedded QNN context binary bytecode (`dispatch_delegate_kernel.cc:495-545`).
- The context binary is compatible with the device SoC/HTP arch/QNN runtime and contains the requested graph/function (`litert_dispatch_invocation_context.cc:121-180`).

Therefore a packaged `libLiteRtDispatch_Qualcomm.so` with `LiteRtDispatchGetApi` can still produce `No usable Dispatch runtime found` when the failure happens during `LiteRtDispatchInitialize`, `LiteRtDispatchCheckRuntimeCompatibility`, or `LiteRtDispatchDeviceContextCreate`.

## Inferred Requirements For This Case

For SM8750 / V79 / QAIRT 2.44, the runtime likely requires all of the following to line up:

- A LiteRT runtime, LiteRT-LM JNI, and `libLiteRtDispatch_Qualcomm.so` built against compatible LiteRT dispatch API struct and C API versions.
- Qualcomm QNN libraries from the same expected QAIRT generation as the dispatch build, at minimum:
  - `libQnnSystem.so`
  - `libQnnHtp.so`
  - `libQnnHtpPrepare.so` where required by Android HTP/QNN loading
  - V79 stub/skel pair, typically `libQnnHtpV79Stub.so` plus `libQnnHtpV79Skel.so`, reachable through normal dynamic loading and `ADSP_LIBRARY_PATH`.
- Android app namespace access to the dispatch/QNN library directory passed via `Backend.NPU(nativeLibraryDir)` / LiteRT environment dispatch library dir.
- A device whose QNN HTP `deviceGetPlatformInfo` reports a supported SoC. In this source tree SM8750 is mapped to DSP arch V79 and 8 MB VTCM:
  - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/core/schema/soc_table.cc:42-44`
  - `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/supported_soc.csv:4`
- A `.litertlm` containing TFLite model sections with `model_type` metadata and, for NPU execution, dispatch custom ops whose custom options point to nonzero embedded QNN context bytecode offsets/sizes. LiteRT-LM maps sections from metadata in `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/util/litert_lm_loader.cc:66-113` and parses model sections in `/home/sato/project/litert-custom-build/LiteRT-LM/runtime/components/model_resources_litert_lm.cc:58-75`.
- QNN context binaries compiled for the target HTP arch/SoC and compatible QNN SDK version. Source evidence: runtime uses `contextCreateFromBinary` (`qnn_manager.cc:538-554`) and retrieves the named graph (`litert_dispatch_invocation_context.cc:165-176`); if the binary was generated for another SoC, arch, or QAIRT generation, the runtime can fail after the dispatch API was loaded successfully.

## Conditions That Collapse To The Observed Fatal

These conditions can all end as `No usable Dispatch runtime found` because `Initialize()` catches the earlier error, logs it, and sets `has_dispatch_runtime_ = false`:

- No dispatch library under the configured dispatch directory.
- Multiple dispatch libraries where the first selected one is the wrong vendor/build.
- Dispatch `.so` load failure from Android namespace, missing transitive dependencies, or missing `LiteRtDispatchGetApi`.
- `LiteRtDispatchGetApi` returns a struct whose version fails `IsSameVersionAsRuntime`.
- Qualcomm `Initialize` cannot parse or use options and cannot create `QnnManager`.
- `libQnnSystem.so` or `libQnnHtp.so` missing/unloadable, or `QnnInterface_getProviders` / `QnnSystemInterface_getProviders` missing/failing.
- QNN core/backend API major mismatch or too-old minor version relative to the dispatch build headers.
- HTP backend cannot map platform SoC to a supported DSP arch, or cannot create backend/device handles.
- Basic dispatch capability bit is not set, or `LiteRtDispatchDeviceContextCreate` fails.

Failures after kernel creation have different messages, for example `Failed to create invocation context`, `Failed to create context from context binary`, `Function name not found`, or `Failed to create QNN context`; those point more directly at model bytecode/graph incompatibility.

## Useful Logging Points

Without source changes, the most useful existing logs are:

- `Failed to initialize Dispatch API: ...` from `dispatch_delegate.cc:112-116`.
- `Loading shared library: ...`, `Unsupported dispatch runtime version`, and `No dispatch library found in ...` from `litert_dispatch.cc:107-180`.
- Dispatch vendor/build/API/capabilities logs from `dispatch_delegate.cc:183-205`.
- Qualcomm `Failed to parse qnn options`, `Failed to set up QNN manager`, `Failed to get QNN API version`, and `Failed to get QNN build ID` from `dispatch_api.cc:120-160`.
- QNN load/version/provider logs from `qnn_manager.cc:120-253`.
- `ADSP_LIBRARY_PATH` from `qnn_manager.cc:399-413`.
- HTP SoC and device creation logs from `htp_backend.cc:397-499`.

If source logging is later allowed, add high-signal logs at:

- `litert_dispatch.cc:115`: selected dispatch runtime path and count of candidates.
- `litert_dispatch.cc:162-180`: `LiteRtDispatchGetApi` status, `TheApi.version`, and vendor interface pointer presence.
- `dispatch_api.cc:129-138`: shared library dir, selected backend, and exact `QnnManager::Create` error status/message.
- `qnn_manager.cc:420-468`: resolved QNN library paths, provider API versions, backend build ID, parsed SDK version.
- `htp_backend.cc:407-420`: raw `socModel`, mapped SoC name, DSP arch, VTCM.
- `dispatch_delegate_kernel.cc:516-545`: dispatch op `bytecode_offset`, `bytecode_size`, `name`, model base/fd.
- `litert_dispatch_invocation_context.cc:121-180`: context binary parse result, graph names/count, selected function name, `contextCreateFromBinary` and `graphRetrieve` QNN status.

## Bottom Line

The observed fatal is a generic late symptom of an earlier dispatch runtime initialization failure. In this codebase, the most plausible source-level explanations for "dispatch `.so` present and `LiteRtDispatchGetApi` exists, but runtime unusable" are QNN/QAIRT generation mismatch, Android library path/namespace failure for QNN HTP/System/V79 artifacts, unsupported or misdetected SM8750/V79 HTP setup, or a compiled model/context-binary mismatch that only becomes visible once the dispatch delegate tries to create invocation contexts.
