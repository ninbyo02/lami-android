# NPU S1 Dispatch Delegate Binary Diff Review

## Purpose

Identify binary-level differences related to the `EngineFactory::CreateDefault`
abort path:

```text
nativeRunEditableEngineCreateOnlyMinimal
-> EngineFactory::CreateDefault
-> LiteRtCompiledModelT::Create
-> DispatchDelegate::CreateDelegateKernelInterface
-> abort()
```

This review is intentionally binary-only. It does not change chat routing,
provider selection, fallback, TTS, DB, streaming, prompt, sanitizer, native
patches, or staged libraries.

## Important BuildId Mapping

The tombstone referenced in the investigation reports:

```text
BuildId: 89aac06377e25627695d408eb12ae8cd
```

That BuildId matches:

```text
artifacts/litert_custom_build/20260609_212211_editable_engine_create_minimal_direct/built_libs/liblitertlm_jni.so
```

It does not match:

```text
artifacts/litert_custom_build/20260609_215707_qairt244_20260529_minimal_engine_create_only_lfsfixed/built_libs/liblitertlm_jni.so
```

The `20260609_215707` library has BuildId
`90630bb8b23ef67d71338550d51ec630`, so it should be treated as a later,
separate candidate unless a newer tombstone confirms that BuildId.

## Compared Binaries

### Known Good

```text
artifact=artifacts/litert_custom_build/20260529_080336_qairt244_native_length_bypass
lib=artifacts/litert_custom_build/20260529_080336_qairt244_native_length_bypass/built_libs/liblitertlm_jni.so
sha256=26eeaba7b05cb12d2112e5b6d55afca9d9190170fdaee8d00da1387946d2aba2
build_id=9ab061c7eaec0611ae63d10f9ab6603b
head=1d535d5038c6a951b7f9f7adbed69efca1f62566
QAIRT_ROOT=/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

### Tombstone-Matching Crash Build

```text
artifact=artifacts/litert_custom_build/20260609_212211_editable_engine_create_minimal_direct
lib=artifacts/litert_custom_build/20260609_212211_editable_engine_create_minimal_direct/built_libs/liblitertlm_jni.so
sha256=b6d5666bfe4abd10593eb46e74cdc2f695468d58980b6d5118c3713c89aa083c
build_id=89aac06377e25627695d408eb12ae8cd
head=1d535d5038c6a951b7f9f7adbed69efca1f62566-dirty
QAIRT_ROOT=<default-overlay>
```

### Later Minimal Candidate

```text
artifact=artifacts/litert_custom_build/20260609_215707_qairt244_20260529_minimal_engine_create_only_lfsfixed
lib=artifacts/litert_custom_build/20260609_215707_qairt244_20260529_minimal_engine_create_only_lfsfixed/built_libs/liblitertlm_jni.so
sha256=b9afcd033abd5218949cccfa555cb7046c1b78f145b0f245aee405e895664c2c
build_id=90630bb8b23ef67d71338550d51ec630
QAIRT_ROOT=/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

## NEEDED / RPATH / RUNPATH

`liblitertlm_jni.so` NEEDED entries are identical between known-good and
tombstone-matching crash build:

```text
libEGL.so
libGLESv3.so
libGemmaModelConstraintProvider.so
libandroid.so
libc.so
libdl.so
liblog.so
libm.so
```

No RPATH/RUNPATH entry was found in either `liblitertlm_jni.so`.

Conclusion: the crash is not explained by a direct `liblitertlm_jni.so`
`DT_NEEDED`, RPATH, or RUNPATH difference.

## Dynamic Symbol Diff

Dynamic symbol name counts:

```text
known_good symbols=29580
crash89 symbols=29582
good_only=0
crash_only=2
```

The only extra exported dynamic symbols in the tombstone-matching crash build
are:

```text
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditableEngineCreateOnlyMinimal
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunPersistentProbe
```

No dynamic symbols were removed from the known-good build.

`objdump -T` shows the same conclusion for exported symbols: the added JNI
entrypoints are the observable exported symbol delta.

## Strings Diff

String diff counts:

```text
good_only_strings=3987
crash_only_strings=4063
```

The crash-only strings with direct investigation relevance are mostly the new
DEV probe diagnostics:

```text
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunEditableEngineCreateOnlyMinimal
Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244ShortMultitokenSmoke_nativeRunPersistentProbe
qairt244_persistent_custom_jni_probe_v1
selected_native_probe_mode=editable_engine_create_only_minimal
editable_engine_create_only_minimal_success
before EngineFactory::CreateDefault mode=
EngineFactory::CreateDefault success
holder_key=
holder_generation=
holder_invalidated=
persistent_holder_used=false
```

Dispatch/QNN-related crash-only strings in `liblitertlm_jni.so` are limited to
new diagnostic text:

```text
backend_evidence=QNN_HTP_V79_FastRPC_native_diag_minimal_engine_create
backend_evidence=QNN_HTP_V79_FastRPC_native_diag_persistent_holder
```

No good-only Dispatch/QNN/LiteRtCompiledModel failure string stood out.

## DispatchDelegate / CompiledModel Symbol Review

Both known-good and tombstone-matching crash `liblitertlm_jni.so` expose the
same important LiteRT and DispatchDelegate symbols, including:

```text
LiteRtCreateCompiledModel
LiteRtCompiledModel*
LiteRtRunCompiledModel*
LiteRtCreateDispatchDelegate
LiteRtDestroyDispatchDelegate
litert::internal::DispatchDelegateKernel::Create
litert::internal::DispatchDelegateKernel::Init
litert::internal::DispatchDelegateKernel::InitHelper
litert::internal::DispatchDelegateKernel::CreateNodeInvocationContext
litert::lm::LlmLiteRtNpuCompiledModelExecutor*
litert::qualcomm::QualcommOptions*
```

The addresses differ because extra JNI code shifts layout, but the symbol
surface is not missing from the crash build.

Conclusion: based on exported names, `liblitertlm_jni.so` itself did not lose a
DispatchDelegate or LiteRtCompiledModel API. The crash is more likely a runtime
behavior or companion-library mismatch than a missing symbol.

## Companion Library Diff

The most important binary difference is outside `liblitertlm_jni.so`.

| library | known-good SHA / BuildId | tombstone crash SHA / BuildId | later minimal SHA / BuildId |
|---|---|---|---|
| `libLiteRt.so` | `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` / `a03032ad1eeefda446478aea308c2ed0` | same | same |
| `libLiteRtDispatch_Qualcomm.so` | `7d3d37cb13cf88fc679ea8d07d271865db36e2f6f6eab80e3a1d02783000c34f` / `283f860170c8b970f14db885eab73a95` | `eac00da73e7c8fda6991738616113133e6bda48439444dbdc7fea83a392aa9dd` / `31c67156bd941133c38700fdff1b2aee` | same as known-good |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c` / `443391d4c4348191230b67a3ab8a6037` | `9e1547a45fa31a63ef9fd77e79880f576487035c78d99eb1ecbfa85823d306cb` / `9053b81d7cbccdc3b5460c5e7395e293` | same as known-good |
| `libQnnSystem.so` | not present in artifact `built_libs` | not present | not present |
| `libQnnHtp.so` | not present in artifact `built_libs` | not present | not present |
| `libQnnHtpPrepare.so` | not present in artifact `built_libs` | not present | not present |
| `libQnnHtpV79Stub.so` | not present in artifact `built_libs` | not present | not present |
| `libQnnHtpV79Skel.so` | not present in artifact `built_libs` | not present | not present |

`libLiteRtDispatch_Qualcomm.so` and `libLiteRtCompilerPlugin_Qualcomm.so`
also have the same NEEDED lists between good and crash, so the significant
difference is their actual content/BuildId, not their direct dynamic
dependencies.

## Build Environment Difference

Known-good:

```text
LITERT_QAIRT_SDK=.../20260529_080336_qairt244_native_length_bypass/qairt_overlay/
QAIRT_ROOT=/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

Tombstone-matching crash:

```text
LITERT_QAIRT_SDK=/home/sato/project/litert-custom-build/qairt_overlay/
QAIRT_ROOT=<default-overlay>
```

Later minimal candidate:

```text
LITERT_QAIRT_SDK=.../20260609_215707.../qairt_overlay/
QAIRT_ROOT=/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

The later minimal candidate restores the Dispatch and CompilerPlugin binaries
to the known-good SHA/BuildId.

## Most Suspicious Candidates

1. `libLiteRtDispatch_Qualcomm.so` content mismatch in the tombstone build.
   This library owns Dispatch runtime/delegate behavior and sits directly on the
   failing stack through `DispatchDelegate::CreateDelegateKernelInterface`.

2. `libLiteRtCompilerPlugin_Qualcomm.so` content mismatch in the tombstone
   build. It affects compiled model/QNN preparation and changed together with
   Dispatch in the crash artifact.

3. Build used default QAIRT overlay instead of explicit QAIRT 2.44 overlay.
   The tombstone-matching build is `1d535d50-dirty` and did not record an
   explicit `QAIRT_ROOT`; the good and later minimal builds used
   `/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225`.

4. New JNI entrypoint code in `liblitertlm_jni.so`.
   It is visible as two extra exports and extra diagnostics. However, the
   DispatchDelegate/LiteRtCompiledModel symbol surface is unchanged and the
   more direct companion-library mismatch is stronger.

5. Persistent holder / mutex / session / prefill / decode.
   These remain low-probability for `editable_engine_create_only_minimal`
   because that mode does not use session, prefill, decode, or full loop. For
   the tombstone-matching build, the persistent symbol exists but is not needed
   to reach the minimal entrypoint crash.

## Next Minimal Fix / Test

Do not change normal chat routing. Keep
`npu_s1_native_route_blocked_for_normal_chat`.

Next test should install/run the later minimal candidate with:

```text
liblitertlm_jni.so build_id=90630bb8b23ef67d71338550d51ec630
libLiteRtDispatch_Qualcomm.so build_id=283f860170c8b970f14db885eab73a95
libLiteRtCompilerPlugin_Qualcomm.so build_id=443391d4c4348191230b67a3ab8a6037
```

Then press only:

```text
editable_engine_create_only_minimal
```

Expected interpretation:

- If it succeeds, the most likely root cause is the `20260609_212211`
  Dispatch/CompilerPlugin/default-overlay mismatch, not the minimal entrypoint.
- If it still SIGABRTs with BuildId `90630bb8b23ef67d71338550d51ec630`, then
  the crash is reproducible even with Dispatch/CompilerPlugin restored to the
  known-good binaries, and the next target is JNI call-site shape or runtime
  process/device state.

