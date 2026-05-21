# QAIRT 2.44 Android Logcat Native Patch

Date: 2026-05-21

## Purpose

The previous `QAIRT244_DIAG` build used LiteRT internal logging and embedded
diagnostic strings, but those lines did not appear in Android logcat during the
`customBuildExperimentDebug` `Engine.initialize` crash. This patch adds direct
Android logcat calls:

```cpp
__android_log_print(ANDROID_LOG_ERROR, "QAIRT244_DIAG", ...);
```

Every direct native line carries the marker:

```text
qairt244_android_log_v1
```

## External LiteRT Source Files

The Android-only log calls were added in the external LiteRT checkout:

- `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/dispatch_delegate.cc`
- `/home/sato/project/litert-custom-build/LiteRT/litert/runtime/dispatch/litert_dispatch.cc`
- `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/dispatch/dispatch_api.cc`
- `/home/sato/project/litert-custom-build/LiteRT/litert/vendors/qualcomm/qnn_manager.cc`

The touched Bazel targets already link `liblog.so` in the Android build. Agent
A also added Android-only `-llog` link options in the external LiteRT tree, but
the rebuilt `libLiteRt.so` and `libLiteRtDispatch_Qualcomm.so` already report
`liblog.so` in `NEEDED`.

## Covered Stages

Direct logcat calls now cover:

- `DispatchDelegate::CreateDelegateKernelInterface` entry
- `has_dispatch_runtime_` checks and transitions
- fatal-before-abort paths in delegate kernel creation
- `InitializeDispatchApi` entry and return paths
- `LiteRtDispatchInitialize` entry and return paths
- dispatch library discovery and candidate path reporting
- dispatch `dlopen` before/after
- `LiteRtDispatchGetApi` `dlsym` before/after
- `LiteRtDispatchGetApi` call before/after
- `LiteRtDispatchCheckRuntimeCompatibility` before/after
- Qualcomm dispatch `Initialize`
- Qualcomm compatibility check
- QNN `LoadSystemLib`, backend `LoadLib`, provider `dlsym`, provider calls
- `ADSP_LIBRARY_PATH`
- HTP backend load, resolve, and init stages

## Android-Log Build

Artifact:

```text
artifacts/qairt244_android_log_build/20260521_210911/
```

Build IDs:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `2ab5deef60fa7b8ce78a5e4f4aae5d82` | `1abbc4d2a61b8631af6d9ba8bb6ef9ac5e0fef75fa2e608e6fd13a0b9768944d` |
| `libLiteRtDispatch_Qualcomm.so` | `e249453cf79d19c37af2b2019fea71f1` | `ec12f96959b543782d906afc5cc2caa888dc3b29ea2403ff175088d88acdf093` |
| `liblitertlm_jni.so` | `27bb6eaa5358f3c23f080cdd33023eac` | `2dd403c7706080499473f4cc21217ccb62494372ba7e8b89a2c56b30aff9b77d` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `696d69bb8a9de9988bc5a24efec61a2e` | `22ce807533dc659c3f482f6943f2a8b7311869e0a2c61ab8629d15bcaf3d496d` |

`strings` verification confirms both `QAIRT244_DIAG` and
`qairt244_android_log_v1` are present in the rebuilt stack before staging.

## Probe Guard

`customBuildExperimentDebug` now accepts the Android-log build IDs in the custom
stack guard. This does not affect `standard`, `npuExperiment`,
`galleryStackExperiment`, or release variants.
