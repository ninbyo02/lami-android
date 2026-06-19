# NPU S1 Dispatch / CompilerPlugin Overlay Review

## 1. Comparison Targets

This review compares the binary artifacts around the current abort path:

```text
nativeRunEditableEngineCreateOnlyMinimal
-> EngineFactory::CreateDefault
-> LiteRtCompiledModelT::Create
-> DispatchDelegate::CreateDelegateKernelInterface
-> abort(SIGABRT)
```

This is an investigation-only review. It does not change Kotlin code, native
patches, staged libraries, chat routing, provider selection, fallback, TTS, DB,
streaming, prompt, or sanitizer behavior. The normal-chat native route block
(`npu_s1_native_route_blocked_for_normal_chat`) remains out of scope and must
be preserved.

Compared artifacts:

```text
known_good=artifacts/litert_custom_build/20260529_080336_qairt244_native_length_bypass
crash=artifacts/litert_custom_build/20260609_212211_editable_engine_create_minimal_direct
```

The crash artifact matches the tombstone BuildId reported for
`tombstone_16`:

```text
liblitertlm_jni.so BuildId: 89aac06377e25627695d408eb12ae8cd
```

Files investigated:

```text
libLiteRt.so
libLiteRtDispatch_Qualcomm.so
libLiteRtCompilerPlugin_Qualcomm.so
liblitertlm_jni.so
libQnnSystem.so
libQnnHtp.so
libQnnHtpPrepare.so
libQnnHtpV79Stub.so
libQnnHtpV79Skel.so
environment.txt
qairt_env.txt
qairt_root_check.txt
built_lib_candidates.txt
metadata/*.txt
symbols/*.txt
strings/*.txt
build_logs/*.log
```

QNN runtime libraries were not present in either artifact `built_libs`
directory. The known-good artifact contains `reference_libs/gallery_stack`
metadata for QNN libraries; the crash artifact has
`reference_libs/gallery_stack/MISSING_APK.txt`. Those reference files are
useful context, but they are not the built outputs being compared here.

## 2. BuildId

| library | known-good BuildId | crash BuildId | result |
|---|---|---|---|
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` | `a03032ad1eeefda446478aea308c2ed0` | same |
| `libLiteRtDispatch_Qualcomm.so` | `283f860170c8b970f14db885eab73a95` | `31c67156bd941133c38700fdff1b2aee` | different |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `443391d4c4348191230b67a3ab8a6037` | `9053b81d7cbccdc3b5460c5e7395e293` | different |
| `liblitertlm_jni.so` | `9ab061c7eaec0611ae63d10f9ab6603b` | `89aac06377e25627695d408eb12ae8cd` | different |
| `libQnnSystem.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtp.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtpPrepare.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtpV79Stub.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtpV79Skel.so` | not in `built_libs` | not in `built_libs` | unavailable |

Important point: `libLiteRt.so` is identical, while both Qualcomm companion
libraries changed.

## 3. SHA256

| library | known-good SHA256 | crash SHA256 | result |
|---|---|---|---|
| `libLiteRt.so` | `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` | `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` | same |
| `libLiteRtDispatch_Qualcomm.so` | `7d3d37cb13cf88fc679ea8d07d271865db36e2f6f6eab80e3a1d02783000c34f` | `eac00da73e7c8fda6991738616113133e6bda48439444dbdc7fea83a392aa9dd` | different |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c` | `9e1547a45fa31a63ef9fd77e79880f576487035c78d99eb1ecbfa85823d306cb` | different |
| `liblitertlm_jni.so` | `26eeaba7b05cb12d2112e5b6d55afca9d9190170fdaee8d00da1387946d2aba2` | `b6d5666bfe4abd10593eb46e74cdc2f695468d58980b6d5118c3713c89aa083c` | different |
| `libQnnSystem.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtp.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtpPrepare.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtpV79Stub.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtpV79Skel.so` | not in `built_libs` | not in `built_libs` | unavailable |

## 4. Size

| library | known-good size | crash size | result |
|---|---:|---:|---|
| `libLiteRt.so` | 5405080 | 5405080 | same |
| `libLiteRtDispatch_Qualcomm.so` | 691184 | 691184 | same size, different content |
| `libLiteRtCompilerPlugin_Qualcomm.so` | 1002320 | 1002320 | same size, different content |
| `liblitertlm_jni.so` | 55196560 | 55249224 | different |
| `libQnnSystem.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtp.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtpPrepare.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtpV79Stub.so` | not in `built_libs` | not in `built_libs` | unavailable |
| `libQnnHtpV79Skel.so` | not in `built_libs` | not in `built_libs` | unavailable |

`libLiteRtDispatch_Qualcomm.so` and
`libLiteRtCompilerPlugin_Qualcomm.so` have the same file sizes across the two
artifacts, but their SHA256 and BuildId differ. That points to a binary content
or build provenance difference, not a missing file or obvious packaging size
change.

## 5. NEEDED Diff

Observed NEEDED entries are the same between known-good and crash for the
available built libraries.

| library | NEEDED |
|---|---|
| `libLiteRt.so` | `libdl.so, libGLESv3.so, libEGL.so, libm.so, liblog.so, libc.so` |
| `libLiteRtDispatch_Qualcomm.so` | `libLiteRt.so, libandroid.so, liblog.so, libdl.so, libc.so, libm.so` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `libandroid.so, liblog.so, libdl.so, libc.so, libm.so` |
| `liblitertlm_jni.so` | `libGemmaModelConstraintProvider.so, libdl.so, liblog.so, libandroid.so, libGLESv3.so, libEGL.so, libm.so, libc.so` |

No NEEDED difference explains the abort directly. The important difference is
inside the Qualcomm Dispatch and CompilerPlugin binaries themselves.

## 6. Dispatch Diff

Compared file:

```text
known_good/built_libs/libLiteRtDispatch_Qualcomm.so
crash/built_libs/libLiteRtDispatch_Qualcomm.so
```

Binary identity:

```text
known_good sha256=7d3d37cb13cf88fc679ea8d07d271865db36e2f6f6eab80e3a1d02783000c34f
known_good build_id=283f860170c8b970f14db885eab73a95
crash sha256=eac00da73e7c8fda6991738616113133e6bda48439444dbdc7fea83a392aa9dd
crash build_id=31c67156bd941133c38700fdff1b2aee
size=691184 in both
NEEDED=same
```

Exported symbol and string comparison:

```text
known_good dynamic_symbol_count=124
crash dynamic_symbol_count=124
good_only_dynamic_symbols=0
crash_only_dynamic_symbols=0
good_only_sorted_strings=0
crash_only_sorted_strings=0
```

Relevant Dispatch/QNN strings are present in both, including:

```text
libLiteRtDispatch_Qualcomm.so
libQnnDsp.so
LiteRtGetTensorBufferFastRpcBuffer
Failed to set up QNN manager
Failed to create QNN context
external/litert/litert/vendors/qualcomm/qnn_manager.cc
Qualcomm
```

Interpretation: the Dispatch library changed at the byte/build level but did
not change its exported dynamic symbol names or visible strings. Since the
tombstone enters `DispatchDelegate::CreateDelegateKernelInterface`, this
library remains the highest-priority binary difference.

## 7. CompilerPlugin Diff

Compared file:

```text
known_good/built_libs/libLiteRtCompilerPlugin_Qualcomm.so
crash/built_libs/libLiteRtCompilerPlugin_Qualcomm.so
```

Binary identity:

```text
known_good sha256=c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c
known_good build_id=443391d4c4348191230b67a3ab8a6037
crash sha256=9e1547a45fa31a63ef9fd77e79880f576487035c78d99eb1ecbfa85823d306cb
crash build_id=9053b81d7cbccdc3b5460c5e7395e293
size=1002320 in both
NEEDED=same
```

Exported symbol and string comparison:

```text
known_good dynamic_symbol_count=248
crash dynamic_symbol_count=248
good_only_dynamic_symbols=0
crash_only_dynamic_symbols=0
good_only_sorted_strings=0
crash_only_sorted_strings=0
```

Relevant strings are present in both, including:

```text
LiteRtCompilerPlugin_Qualcomm.so
qnn_compose_graph.cc
INFO: [Qnn] [G2G] MHA optimization (Decode)
SDK version is in [2.35.0, 2.38.0); Quantize OP validation is bypassed.
```

Interpretation: CompilerPlugin changed together with Dispatch, again without
visible API/string surface changes. It is less directly visible in the
tombstone than Dispatch, but it participates in compiled-model preparation and
should be treated as part of the same QAIRT overlay mismatch.

## 8. QAIRT Overlay Diff

Known-good environment:

```text
head=1d535d5038c6a951b7f9f7adbed69efca1f62566
describe=v0.11.0-4-g1d535d50
LITERT_QAIRT_SDK=/home/sato/project/lami-android/artifacts/litert_custom_build/20260529_080336_qairt244_native_length_bypass/qairt_overlay/
QAIRT_ROOT=/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
BAZEL_OUTPUT_BASE=/home/sato/project/litert-custom-build/bazel_output_base/build_20260529_080336
```

Known-good `qairt_root_check.txt` recorded:

```text
requested_qairt_root=/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
expected_version=2.44.0.260225
basename_match=true
status=present
required_files=present
```

Crash environment:

```text
head=1d535d5038c6a951b7f9f7adbed69efca1f62566
describe=v0.11.0-4-g1d535d50-dirty
LITERT_QAIRT_SDK=/home/sato/project/litert-custom-build/qairt_overlay/
QAIRT_ROOT=<default-overlay>
BAZEL_OUTPUT_BASE=/home/sato/project/litert-custom-build/bazel_output_base/build_20260609_212211
```

Crash `qairt_env.txt` only recorded:

```text
LITERT_QAIRT_SDK=/home/sato/project/litert-custom-build/qairt_overlay/
QAIRT_ROOT=<default-overlay>
Expected LiteRT strip path: /home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225
```

No `qairt_root_check.txt` exists in the crash artifact. Therefore the crash
artifact does not prove that it used the same checked QAIRT 2.44 SDK root as
the known-good build.

This environment difference lines up with the binary facts:

- `libLiteRt.so` stayed identical.
- `libLiteRtDispatch_Qualcomm.so` changed.
- `libLiteRtCompilerPlugin_Qualcomm.so` changed.
- The failing stack enters the Dispatch delegate creation path.

A later minimal candidate built with an explicit QAIRT 2.44 root restored the
Dispatch and CompilerPlugin SHA/BuildId to the known-good values. That strongly
suggests the overlay/source path controls these two binaries.

## 9. Most Likely Cause Ranking

1. Default QAIRT overlay mismatch in the crash build.
   The known-good build used an explicit QAIRT 2.44 SDK root and recorded
   required file checks. The crash build used `<default-overlay>`, lacks the
   same root check, and produced different Qualcomm Dispatch/CompilerPlugin
   binaries.

2. `libLiteRtDispatch_Qualcomm.so` binary content mismatch.
   The crash stack reaches `DispatchDelegate::CreateDelegateKernelInterface`.
   This library has identical NEEDED/symbol/string surface but different
   SHA/BuildId, so the change can be behavioral without being visible in
   exported names.

3. `libLiteRtCompilerPlugin_Qualcomm.so` binary content mismatch.
   It changed together with Dispatch and affects LiteRT NPU compiled-model
   preparation. It is a likely coupled difference rather than an independent
   primary cause.

4. Dirty LiteRT-LM checkout or build-output-base state.
   Both artifacts report the same head commit, but the crash artifact is
   `v0.11.0-4-g1d535d50-dirty`. This may include local changes or stale overlay
   state that affected companion-library generation.

5. New JNI probe code.
   The minimal entrypoint adds symbols in `liblitertlm_jni.so`, but
   `entrypoint_only` and `before_engine_create` succeed, and the stronger
   mismatch is in the companion Dispatch/CompilerPlugin binaries. Persistent
   holder, mutex, session, prefill, decode, and full loop are lower priority
   because `editable_engine_create_only_minimal` does not use them.

## 10. Next Minimal Experiment

Do not change normal chat routing. Keep
`npu_s1_native_route_blocked_for_normal_chat`.

Next experiment should use a minimal engine-create build whose companion
libraries match the known-good binaries:

```text
libLiteRtDispatch_Qualcomm.so
  sha256=7d3d37cb13cf88fc679ea8d07d271865db36e2f6f6eab80e3a1d02783000c34f
  build_id=283f860170c8b970f14db885eab73a95

libLiteRtCompilerPlugin_Qualcomm.so
  sha256=c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c
  build_id=443391d4c4348191230b67a3ab8a6037
```

Build/stage policy for the next experiment:

- Use explicit `QAIRT_ROOT=/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225`.
- Preserve `qairt_root_check.txt` equivalent evidence in the artifact.
- Confirm Dispatch/CompilerPlugin SHA and BuildId before installing.
- Press only `editable_engine_create_only_minimal` on device.

Decision table:

| Result | Interpretation |
|---|---|
| `editable_engine_create_only_minimal` succeeds | The `20260609_212211` default-overlay Dispatch/CompilerPlugin mismatch is the leading cause. |
| It still SIGABRTs with known-good Dispatch/CompilerPlugin SHA/BuildId | Move focus to JNI call-site shape, process/device state, or non-companion runtime state. |
| It SIGABRTs with new Dispatch/CompilerPlugin SHA/BuildId | Treat the experiment as invalid; first restore known-good companion libraries. |

