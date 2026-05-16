# LiteRT / LiteRT-LM Bazel Query Results

Date: 2026-05-16

This document records the query/cquery-only preparation for a future LiteRT / LiteRT-LM Qualcomm dispatch build. No `bazel build` command was run, and no native artifact was generated or staged into the app.

## Artifact

```text
artifacts/litert_custom_build_query/20260516_225450/
```

## Tooling

| Item | Result |
| --- | --- |
| Bazelisk | installed at `/home/sato/.local/bin/bazelisk` |
| `bazel` symlink | `/home/sato/.local/bin/bazel -> bazelisk` |
| Bazelisk version | `v1.29.0` |
| Bazel version selected by `.bazelversion` | `7.6.1` |
| Android cmdline tools | installed under `/home/sato/Android/Sdk/cmdline-tools/latest` |
| `sdkmanager` version | `20.0` |
| Android NDK | `/home/sato/Android/Sdk/ndk/28.2.13676358` |
| NDK release | `r28c` |
| NDK clang | Android clang `19.0.1` |

## Source Checkout

| Repo | Path | Commit |
| --- | --- | --- |
| LiteRT-LM | `/home/sato/project/litert-custom-build/LiteRT-LM` | `c87189528a758db32ead241f4fc9c64836398ee7` (`v0.11.0`) |
| LiteRT | `/home/sato/project/litert-custom-build/LiteRT` | `47615eb6eaec25e8dfcd1aba922c560a57cba0a2` |

LiteRT-LM `WORKSPACE` confirms:

```text
LITERT_REF = "47615eb6eaec25e8dfcd1aba922c560a57cba0a2"
```

## QAIRT / QNN Setup

Local QAIRT SDK:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424
```

LiteRT commit `47615eb6...` expects `LITERT_QAIRT_SDK + qairt/2.44.0.260225` from `third_party/qairt/workspace.bzl`. For query/cquery only, an isolated overlay was created:

```text
/home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225
  -> /home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424
```

This is only a query/cquery convenience. It does not prove a future build should use QAIRT 2.46 as a drop-in replacement for the source-pinned 2.44 package.

## Query Results

All targeted `bazel query` commands succeeded:

| Command | Result |
| --- | --- |
| `bazel query //kotlin/java/com/google/ai/edge/litertlm/jni:*` | success |
| `bazel query @litert//litert/vendors/qualcomm/dispatch:*` | success |
| `bazel query @litert//litert/c:*` | success |
| `bazel query @litert//litert/vendors/qualcomm/compiler:*` | success |

Key visible targets:

```text
@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so
@litert//litert/vendors/qualcomm/dispatch:libLiteRtDispatch_Qualcomm.so
@litert//litert/vendors/qualcomm/compiler:qnn_compiler_plugin_so
@litert//litert/vendors/qualcomm/compiler:libLiteRtCompilerPlugin_Qualcomm.so
@litert//litert/c:litert_runtime_c_api_so
//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni
```

## Cquery Results

All Android arm64 `bazel cquery` commands succeeded:

| Command | Result | Notes |
| --- | --- | --- |
| `bazel cquery @litert//litert/vendors/qualcomm/dispatch:dispatch_api_so --config=android_arm64` | success | target resolves under Android arm64 config |
| `bazel cquery @litert//litert/c:litert_runtime_c_api_so --config=android_arm64` | success | target resolves under Android arm64 config |
| `bazel cquery //kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni --config=android_arm64` | success | target resolves under Android arm64 config |

The cquery logs include Bazel's standard line:

```text
Build completed successfully, 0 total actions
```

That is from cquery analysis. No `bazel build` command was issued and no action produced native output.

## Target Visibility

| Target | Visibility / feasibility |
| --- | --- |
| Qualcomm dispatch `dispatch_api_so` | query and Android arm64 cquery visible |
| LiteRT runtime `litert_runtime_c_api_so` | query and Android arm64 cquery visible |
| Qualcomm compiler plugin `qnn_compiler_plugin_so` | query visible |
| LiteRT-LM JNI `litertlm_jni` | query and Android arm64 cquery visible |

## Build Blockers Before Next Phase

- Build has not been attempted.
- QAIRT source pin expects `2.44.0.260225`, while local SDK is `2.46.0.260424`.
- QNN/QAIRT license and redistribution status remain unresolved.
- Need decide whether to build dispatch-only or a matched `libLiteRt.so` / `liblitertlm_jni.so` / dispatch set.
- Need maintainers' guidance if possible, because Gallery SM8750 native payload still appears special.

## Next Recommended Phase

Proceed to build only after explicit approval.

Suggested order:

1. Build `@litert//litert/c:litert_runtime_c_api_so` into artifacts only.
2. Build `@litert//litert/vendors/qualcomm/dispatch:dispatch_api_so` into artifacts only.
3. Build `//kotlin/java/com/google/ai/edge/litertlm/jni:litertlm_jni` into artifacts only.
4. Static-compare Build ID, NEEDED, symbols, and dispatch strings against Gallery and Maven.
5. Only after static comparison, consider isolated `galleryStackExperimentDebug` replacement.

