# QAIRT 2.44 Dispatch dlopen Trace Result

Date: 2026-05-22

## Purpose

The previous native file logger showed that `LiteRtDispatchInitialize` fails
with `kLiteRtStatusErrorDynamicLoading(502)` before
`LiteRtDispatchCheckRuntimeCompatibility` and before visible QNN/HTP/skel
initialization.

This pass adds a lower-level app-private file trace for dispatch dynamic
loading, including dispatch library candidates, `dlopen`, raw `dlerror`, and
`dlsym`.

## Logger Locations

Custom LiteRT source checkout:

```text
/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/litert_dispatch.cc
/home/sato/project/litert-custom-build/LiteRT/litert/cc/internal/litert_shared_library.cc
```

Marker:

```text
qairt244_dlopen_trace_v1
```

Diagnostic file:

```text
/data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_native_diag.txt
```

The trace records:

- `LiteRtDispatchInitialize` entry
- `dispatch_lib_dir`
- dispatch candidate count and selected path
- before/after `SharedLibrary::Load`
- before/after raw `dlopen`
- `errno` and raw `dlerror()` on `dlopen` failure
- before/after `dlsym("LiteRtDispatchGetApi")`
- raw `dlerror()` on `dlsym` failure
- `LiteRtDispatchGetApi` result and API version if reached

## Linker Debug Property

The custom probe script now sets this property only for the
`customBuildExperimentDebug` package before an explicit dry-run:

```text
debug.ld.app.io.github.ninbyo02.lami.customnpu=dlerror,dlopen,dlsym
```

The script clears the property on exit.

## Build Artifact

```text
artifacts/qairt244_dlopen_trace_build/20260522_083658/
```

Build IDs:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `bf8a68f5348fc68674f1e659da138f71` | `a4b9ec1ab6b4c2d94680d3f63f4fa768dfa0151d6b2012629b1f44b0697290b0` |
| `libLiteRtDispatch_Qualcomm.so` | `b08e70f378f3b2b3c0354375fa6fb532` | `9cc63a7c5d441c969a6733373db7dd1c189e6679470b0f7a6ed8d4d378c67ca1` |
| `liblitertlm_jni.so` | `0097e57889e5b09095d791a62b0a2506` | `cf059d354b473d73d35324a02795e804325ab3cd982d278b559e14fbfc3eda48` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `e0e6b5ff68ad6db654ac1ac3ef2a7aaa` | `d68b13f99852ddf1ee68f97b4f2f66a97375fedf7a8ea8885614a3f1f1d1fa30` |

`strings` confirmed `qairt244_dlopen_trace_v1` in the rebuilt stack.

## Dry-Run Status

The connected-device dry-run was not executed because no adb device was
connected:

```text
List of devices attached
```

Attempt artifact:

```text
artifacts/qairt244_dlopen_trace_dry_run/20260522_083818_no_device/
```

The one allowed connected-device dry-run for this build remains unused.

## Current Classification

Still unchanged from the previous file logger result:

```text
LiteRtDispatchInitialize failure status=kLiteRtStatusErrorDynamicLoading(502)
```

The next connected-device run should classify whether the dynamic loading
failure is:

- wrong or empty dispatch path
- basename-only linker namespace lookup failure
- absolute path lookup failure
- missing transitive dependency
- Android linker namespace restriction
- `LiteRtDispatchGetApi` dlsym failure
- or later vendor initialization failure after successful `dlopen` / `dlsym`

## Next Step

Connect the Nubia device and run the existing probe command once. Do not run
generation, `Conversation`, `Session`, or a single-token smoke.
