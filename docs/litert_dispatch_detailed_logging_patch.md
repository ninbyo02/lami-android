# LiteRT Dispatch Detailed Logging Patch

Date: 2026-05-21

Scope: diagnostic logging only. No failure handling, dispatch selection,
compatibility result, QNN initialization result, fatal behavior, generation
path, or app release behavior was changed.

## Source Patch Locations

The diagnostic patch was applied to LiteRT source files under the LiteRT-LM
Bazel external `@litert` source used for the build, and mirrored in the local
LiteRT checkout for review:

```text
/home/sato/project/litert-custom-build/bazel_output_base/build_20260521_091804/external/litert/
/home/sato/project/litert-custom-build/LiteRT/
```

Files changed:

- `litert/runtime/dispatch/dispatch_delegate.cc`
- `litert/runtime/dispatch/litert_dispatch.cc`
- `litert/vendors/qualcomm/dispatch/dispatch_api.cc`
- `litert/vendors/qualcomm/qnn_manager.cc`

Important build note: LiteRT-LM `WORKSPACE` downloads `@litert` from the pinned
GitHub archive. Editing `/home/sato/project/litert-custom-build/LiteRT` alone
does not affect the LiteRT-LM Bazel build. The patch therefore had to be copied
into the Bazel external `@litert` tree before the second rebuild.

## Log Prefix

All high-value added logs use:

```text
QAIRT244_DIAG
```

The logs use `LITERT_ERROR` intentionally so they should be visible in Android
logcat even when lower severity logs are filtered.

## Added Visibility

`dispatch_delegate.cc` now logs:

- `DispatchDelegate::Initialize` entry/exit.
- `has_dispatch_runtime` transition to true or false.
- exact `LiteRtStatus` code/name and message when `InitializeDispatchApi()`
  fails.
- `CreateDelegateKernelInterface` state before the existing fatal.
- `LiteRtDispatchInitialize` success/failure.
- `LiteRtDispatchCheckRuntimeCompatibility` status.
- capabilities and `kLiteRtDispatchCapabilitiesBasic` presence.
- `LiteRtDispatchDeviceContextCreate` success/failure.

`litert_dispatch.cc` now logs:

- dispatch library directory option.
- discovered dispatch library candidate paths.
- static dispatch API attempt status.
- dispatch `.so` `dlopen` path and success/failure.
- `LiteRtDispatchGetApi` `dlsym` success/failure.
- `LiteRtDispatchGetApi` returned API version and interface pointers.
- dispatch runtime version acceptance or mismatch.
- vendor initialize status.
- compatibility function presence and returned status.

`dispatch_api.cc` now logs:

- Qualcomm dispatch initialize entry.
- QNN shared library directory derived from dispatch library dir.
- `QnnManager::Create` shared library dir, success/failure, status, and message.
- QNN API version and backend build ID.
- Qualcomm compatibility result.
- Qualcomm `LiteRtDispatchGetApi` entry.

`qnn_manager.cc` now logs:

- QNN provider `dlsym` success/failure for system and backend libraries.
- `libQnnSystem.so` load path and result.
- backend QNN library load path and result, including `libQnnHtp.so`.
- `ADSP_LIBRARY_PATH` after mutation from the shared library dir.
- HTP backend selection, API resolve, HTP init begin/end.
- QNN backend build ID and parsed SDK version.
- `QnnManager::Create` begin/success/failure.

## Build Artifact

Build artifact:

```text
artifacts/qairt244_dispatch_logging_build/20260521_085251/
```

Build command shape:

```bash
OUT_DIR=/home/sato/project/lami-android/artifacts/qairt244_dispatch_logging_build/20260521_085251 \
BAZEL_OUTPUT_BASE=/home/sato/project/litert-custom-build/bazel_output_base/build_20260521_091804 \
bash scripts/build_litert_custom_artifacts.sh \
  /home/sato/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244_dispatch_logging
```

Limited targets all succeeded:

```text
@litert//litert/c:litert_runtime_c_api_so                         0
@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so          0
//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni          0
@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so    0
```

Built logging stack:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `04b7b85497a519e131777b55e6c9b456` | `794fd19a7067ba73abee4a8e6b4afbc805b1915106319799298ccd021df3307c` |
| `libLiteRtDispatch_Qualcomm.so` | `50f4dbc09b133acb5973747555f06bc1` | `30c3401b5df9d6e1b87517a6b89882f952e8d3790acade21ebf8931993f95f24` |
| `liblitertlm_jni.so` | `30ee8163ec17e1624a25f6936a163f9e` | `4d4302eac72ad3421eb22a96bf810b91cc0f4a9b8cc45e39a9eaf531b33e9c10` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `da4a7a69d0a36ad68a6dd10e6c183d62` | `2d3cc83e50e6522ff4d6423c02caa9c9a349b0fcd0933edfe60d326e83d701ea` |

`strings` verification confirmed `QAIRT244_DIAG` markers in:

- `built_libs/libLiteRt.so`
- `built_libs/libLiteRtDispatch_Qualcomm.so`

## Dry-Run Status

The detailed logging build was not staged or executed in an app dry-run during
this pass because `adb devices` reported no connected device.

The one allowed detailed-logging `Engine.initialize` dry-run remains unused.

## Probe Guard Update

Because diagnostic logging changes the Build IDs, the custom build probe guard
was extended to accept both:

- exact qairt244 stack IDs, and
- qairt244 diagnostic logging stack IDs.

This affects only `customBuildExperimentDebug` diagnostics. It does not connect
NPU to the normal UI inference path and does not change `standard`,
`npuExperiment`, `galleryStackExperiment`, or release behavior.
