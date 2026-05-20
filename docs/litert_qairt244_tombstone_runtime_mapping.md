# LiteRT QAIRT 2.44 Tombstone Runtime Mapping

Source artifacts:

- `artifacts/npu_diagnostics/20260521_074641_customnpu/tombstone_app_extract.txt`
- `artifacts/npu_diagnostics/20260521_074641_customnpu/dropbox_full.txt`
- `artifacts/npu_diagnostics/20260521_074641_customnpu/loaded_libs_matrix.tsv`
- `artifacts/npu_diagnostics/20260521_074641_customnpu/native_lib_build_ids.txt`
- `artifacts/npu_diagnostics/20260521_074641_customnpu/abort_text_candidates.txt`
- `artifacts/npu_diagnostics/20260521_074641_customnpu/stage_file.txt`

## Run boundary

- applicationId: `io.github.ninbyo02.lami.customnpu`
- tombstone timestamp: `2026-05-21 07:46:36.145461100+0900`
- final stage: `1779317195995 runId=1779317161924 Engine.initialize invoking method=Engine.initialize(): void`
- signal: `signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------`
- direct abort message: not found in tombstone
- reconstructed register text: `] Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`

The stage file stops in `Engine.initialize`. It does not show `generateResponse`, `Conversation`, `Session`, or inference execution.

## Classification

The abort occurs in `liblitertlm_jni.so` at `(anonymous namespace)::DispatchDelegate::CreateDelegateKernelInterface()+312`, before any mapped evidence of `libLiteRtDispatch_Qualcomm.so` or QAIRT/QNN runtime libraries.

Overall classification: `QNN libs not loaded` and `dispatch loaded before abort: false`.

`liblitertlm_jni.so` and `libGemmaModelConstraintProvider.so` are mapped from the app native library directory. `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, `libQnnSystem.so`, `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, and `libQnnHtpV79Skel.so` are present in the app native library directory/APK metadata but not mapped in the tombstone. `libQnnDsp.so` and `libQnnGpu.so` were not found in the inspected tombstone, dropbox, package dump, or native library metadata.

## Library mapping

| Library | Mapped before abort | Path class | Classification | Evidence |
| --- | --- | --- | --- | --- |
| `liblitertlm_jni.so` | true | app nativeLibraryDir | loaded before abort | Backtrace frames #01-#20 and map entries in tombstone/dropbox. |
| `libLiteRt.so` | false | app nativeLibraryDir/APK metadata | QNN libs not loaded | Present in `loaded_libs_matrix.tsv` and `native_lib_build_ids.txt`; no tombstone map entry. |
| `libLiteRtDispatch_Qualcomm.so` | false | app nativeLibraryDir/APK metadata | dispatch loaded before abort: false | Present in native metadata; no tombstone map entry. |
| `libQnnSystem.so` | false | app nativeLibraryDir/APK metadata | QNN libs not loaded | Present in native metadata; no tombstone map entry. |
| `libQnnHtp.so` | false | app nativeLibraryDir/APK metadata | QNN libs not loaded | Present in native metadata; no tombstone map entry. |
| `libQnnHtpPrepare.so` | false | app nativeLibraryDir/APK metadata | HTP prepare/skel/stub not loaded | Present in native metadata; no tombstone map entry. |
| `libQnnHtpV79Stub.so` | false | app nativeLibraryDir/APK metadata | HTP prepare/skel/stub not loaded | Present in native metadata; no tombstone map entry. |
| `libQnnHtpV79Skel.so` | false | app nativeLibraryDir/APK metadata | HTP prepare/skel/stub not loaded | Present in native metadata; no tombstone map entry. |
| `libQnnDsp.so` | false | not found in inspected artifacts | unknown | No hit in tombstone/dropbox/package/native metadata. |
| `libQnnGpu.so` | false | not found in inspected artifacts | unknown | No hit in tombstone/dropbox/package/native metadata. |
| `libGemmaModelConstraintProvider.so` | true | app nativeLibraryDir | loaded before abort | Tombstone/dropbox map entries at `r-x`, `r--`, and `rw-` segments. |

## Interpretation

The failure is earlier than QNN backend initialization. The tombstone has a register-resident error string for dispatch delegate creation failure and no mapped Qualcomm dispatch or QNN/HTP runtime libraries. That supports a "no usable Dispatch runtime found" failure during LiteRT delegate setup, not a crash inside QAIRT/QNN execution.
