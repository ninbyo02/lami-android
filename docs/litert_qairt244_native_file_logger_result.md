# QAIRT 2.44 Native File Logger Result

Date: 2026-05-22

## Purpose

Native `__android_log_print` diagnostics were not visible on the Nubia Z70S
Ultra / Android 16 device, even for app-owned JNI smoke code that definitely
executed. This pass moved the LiteRT-LM / LiteRT dispatch diagnostics to an app
private append-only file.

No generation path was exercised. The only device execution was the explicitly
allowed `customBuildExperimentDebug` `Engine.initialize` dry-run.

## File Logger

Diagnostic file:

```text
/data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_native_diag.txt
```

Marker:

```text
qairt244_native_file_v1
```

Primary source locations patched in the custom native source checkout:

```text
/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc
/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/dispatch_delegate.cc
```

The JNI logger records `nativeCreateEngine` entry, pointer/null state, string
conversion boundaries, model asset creation, engine settings creation, dispatch
library directory setup, and `EngineFactory::CreateDefault` entry. The dispatch
logger records `DispatchDelegate::Initialize`, `InitializeDispatchApi`,
`LiteRtDispatchInitialize`, and the fatal no-runtime boundary.

## Build Artifact

```text
artifacts/qairt244_native_file_logger_build/20260522_074639/
```

Build IDs:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `8e2c846a217663487c7163a3c596688c` | `215ecfebc89f25adae32de7b0189e81264a5e1317160ff327dfb127702c801c0` |
| `libLiteRtDispatch_Qualcomm.so` | `e249453cf79d19c37af2b2019fea71f1` | `ec12f96959b543782d906afc5cc2caa888dc3b29ea2403ff175088d88acdf093` |
| `liblitertlm_jni.so` | `38b795aeb83183c12361f108dc2308bc` | `b4380c0d8ad06ef4bf6951ee66485d94ddb7173c5bad2fc1ede393027e2c06ee` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `696d69bb8a9de9988bc5a24efec61a2e` | `22ce807533dc659c3f482f6943f2a8b7311869e0a2c61ab8629d15bcaf3d496d` |

`strings` confirmed `qairt244_native_file_v1` in `liblitertlm_jni.so` and
`libLiteRt.so`.

## Dry-Run Artifact

Full diagnostics:

```text
artifacts/npu_diagnostics/20260522_074944_customnpu/
```

Curated artifact:

```text
artifacts/qairt244_native_file_logger_dry_run/20260522_074944/
```

The script installed `customBuildExperimentDebug` directly with Gradle. It did
not run `./update.sh update`.

## Native File Result

The native file was created and contains these key boundaries:

```text
qairt244_native_file_v1 nativeCreateEngine ENTRY ...
qairt244_native_file_v1 nativeCreateEngine ModelAssets::Create success
qairt244_native_file_v1 nativeCreateEngine EngineSettings::CreateDefault success
qairt244_native_file_v1 nativeCreateEngine SetLitertDispatchLibDir main length=105
qairt244_native_file_v1 nativeCreateEngine before EngineFactory::CreateDefault
qairt244_native_file_v1 DispatchDelegate::Initialize entry has_dispatch_runtime=0 ...
qairt244_native_file_v1 InitializeDispatchApi ENTRY
qairt244_native_file_v1 InitializeDispatchApi LiteRtDispatchInitialize failure status=kLiteRtStatusErrorDynamicLoading(502)
qairt244_native_file_v1 DispatchDelegate::Initialize InitializeDispatchApi failed status=kLiteRtStatusErrorDynamicLoading(502) message=LiteRtDispatchInitialize failed
qairt244_native_file_v1 DispatchDelegate::CreateDelegateKernelInterface ENTRY has_dispatch_runtime=0 device_context=0x0
qairt244_native_file_v1 DispatchDelegate::CreateDelegateKernelInterface FATAL no usable dispatch runtime
```

`Engine.initialize` did not return. The process still crashed with `SIGABRT`.

## Tombstone / Mapping

Tombstone top app frame:

```text
DispatchDelegate::CreateDelegateKernelInterface()+544
BuildId: 38b795aeb83183c12361f108dc2308bc
```

Mapped library summary:

| Library | Mapped | Present in nativeLibraryDir/APK |
| --- | --- | --- |
| `liblitertlm_jni.so` | true | true |
| `libLiteRt.so` | false | true |
| `libLiteRtDispatch_Qualcomm.so` | false | true |
| `libQnnSystem.so` | false | true |
| `libQnnHtp.so` | false | true |
| `libQnnHtpPrepare.so` | false | true |
| `libQnnHtpV79Stub.so` | false | true |
| `libQnnHtpV79Skel.so` | false | true |
| `libllm_inference_engine_jni.so` | true | true |

## Classification

The immediate failure is now:

```text
LiteRtDispatchInitialize failure status=kLiteRtStatusErrorDynamicLoading(502)
```

This is before `LiteRtDispatchCheckRuntimeCompatibility` and before any visible
QNN/HTP/skel initialization in this log. The leading branch is therefore a
dispatch runtime dynamic-loading/path discovery failure, not a model schema
mismatch and not yet a confirmed QNN/HTP/FastRPC failure.

## Next Step

Add the same file-backed logger inside the lower-level
`LiteRtDispatchInitialize` implementation and dynamic loader path selection to
capture the exact dispatch library candidate path and `dlerror`.

## dlopen Trace Follow-Up

Result date: 2026-05-22

The lower-level dynamic loading file logger was added with marker:

```text
qairt244_dlopen_trace_v1
```

Build artifact:

```text
artifacts/qairt244_dlopen_trace_build/20260522_083658/
```

The build succeeded and the custom probe script now also enables the
customnpu-only linker debug property during an explicit dry-run:

```text
debug.ld.app.io.github.ninbyo02.lami.customnpu=dlerror,dlopen,dlsym
```

The connected-device dry-run was not executed because no adb device was
connected. The one allowed dry-run for the dlopen trace build remains unused.
