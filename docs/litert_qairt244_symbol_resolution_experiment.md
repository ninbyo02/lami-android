# QAIRT 2.44 Dispatch Symbol Resolution Experiment

Date: 2026-05-22

Scope: `customBuildExperimentDebug` only. No `Conversation`, `Session`,
`generateResponse`, selected-path NPU routing, normal UI NPU wiring, or
single-token smoke was executed.

## Input Failure

The dlopen trace build showed:

```text
dlopen failed: cannot locate symbol "LiteRtGetEnvironmentOptions"
referenced by libLiteRtDispatch_Qualcomm.so
```

Static evidence:

- `libLiteRt.so` exports `LiteRtGetEnvironmentOptions` and
  `LiteRtGetEnvironmentOptionsValue`.
- `libLiteRtDispatch_Qualcomm.so` has undefined references to those symbols.
- the custom-built dispatch did not initially contain `DT_NEEDED [libLiteRt.so]`.
- Gallery stack dispatch does contain `DT_NEEDED [libLiteRt.so]`.

## Agent A: RTLD_GLOBAL / Preload

Build artifact:

```text
artifacts/qairt244_rtld_global_build/20260522_210118/
```

Dry-run artifact:

```text
artifacts/qairt244_rtld_global_dry_run/20260522_210355/
artifacts/npu_diagnostics/20260522_210355_customnpu/
```

Patch shape:

- preload sibling `libLiteRt.so` from app `nativeLibraryDir`
- load it with `RTLD_NOW | RTLD_GLOBAL`
- load `libLiteRtDispatch_Qualcomm.so` with `RTLD_NOW | RTLD_GLOBAL`
- continue app-private file logging in `qairt244_native_diag.txt`

Result:

- `libLiteRt.so` global preload succeeded.
- dispatch `dlopen` still failed with unresolved
  `LiteRtGetEnvironmentOptions`.
- `LiteRtDispatchGetApi` was not reached.
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached.
- QNN/HTP/skel loading was not reached.

Classification: `RTLD_GLOBAL/preload-not-sufficient`.

## Agent B: Explicit DT_NEEDED

Build artifact:

```text
artifacts/qairt244_dispatch_needed_build/20260522_210902/
```

Dry-run artifact:

```text
artifacts/qairt244_dispatch_needed_dry_run/20260522_211136/
artifacts/npu_diagnostics/20260522_211136_customnpu/
```

Patch shape:

- extend local `litert_dynamic_lib` helper with a `dynamic_deps` pass-through
- set Qualcomm dispatch `dynamic_deps = ["//litert/c:litert_runtime_c_api_so"]`
- preserve the existing diagnostic `RTLD_GLOBAL` load and file logger

`readelf -d` confirmed:

```text
NEEDED Shared library: [libLiteRt.so]
NEEDED Shared library: [libandroid.so]
NEEDED Shared library: [liblog.so]
NEEDED Shared library: [libdl.so]
NEEDED Shared library: [libc.so]
NEEDED Shared library: [libm.so]
```

Result:

- dispatch `dlopen` succeeded.
- `dlsym("LiteRtDispatchGetApi")` succeeded.
- `LiteRtDispatchGetApi` returned API version `0.1.0`.
- dispatch runtime version was accepted.
- dispatch vendor initialization started.
- `libQnnSystem.so` `dlopen` succeeded.
- `dlsym("QnnSystemInterface_getProviders")` succeeded.
- dispatch vendor initialization returned
  `kLiteRtStatusErrorDynamicLoading(502)`.
- `LiteRtDispatchCheckRuntimeCompatibility` was not reached.
- `libQnnHtp.so`, `libQnnHtpPrepare.so`, and V79 stub/skel were not mapped.

Classification: `DT_NEEDED-fixes-LiteRt-symbol-boundary`.

## Build IDs

RTLD_GLOBAL build:

| Library | Build ID |
| --- | --- |
| `libLiteRt.so` | `731b74da505bef341a184b3778d0412d` |
| `libLiteRtDispatch_Qualcomm.so` | `b08e70f378f3b2b3c0354375fa6fb532` |
| `liblitertlm_jni.so` | `8554bcd057031088ad9bb2100f1f8f94` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `e0e6b5ff68ad6db654ac1ac3ef2a7aaa` |

DT_NEEDED build:

| Library | Build ID |
| --- | --- |
| `libLiteRt.so` | `731b74da505bef341a184b3778d0412d` |
| `libLiteRtDispatch_Qualcomm.so` | `81390964c1c2ddf43e4f16b4f84cd605` |
| `liblitertlm_jni.so` | `8554bcd057031088ad9bb2100f1f8f94` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `e0e6b5ff68ad6db654ac1ac3ef2a7aaa` |

## Updated Boundary

The first dynamic loading failure is no longer the unresolved LiteRT symbol when
dispatch is rebuilt with `DT_NEEDED [libLiteRt.so]`. The new boundary is inside
Qualcomm dispatch vendor initialization after QNN System provider loading.

Most likely next step: add file logging inside Qualcomm dispatch/QNN manager
around provider enumeration, QNN System API selection, backend library loading,
and any error returned immediately after `QnnSystemInterface_getProviders`.
