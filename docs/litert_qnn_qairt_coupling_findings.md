# LiteRT QNN/QAIRT Coupling Findings

Date: 2026-05-17

## Summary

`customBuildExperimentDebug` still fails before inference:

- `Backend.NPU(String)`: success
- `EngineConfig`: success
- `Engine(EngineConfig)`: returned
- `Engine.initialize`: invoked, did not return
- signal: `SIGABRT`
- top frame: `liblitertlm_jni.so / DispatchDelegate::CreateDelegateKernelInterface()+312`
- register fragments: `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`

The static coupling pass did not run `Engine.initialize`, did not launch the app, did not install an APK, and did not modify native libraries.

Artifact:

```text
artifacts/qairt_qnn_coupling/20260517_012057/
```

Key files:

- `qnn_lib_matrix.tsv`
- `needed_matrix.tsv`
- `loaded_libs_matrix.tsv`
- `custom_apk_packaged_libs.tsv`
- `qairt_version_summary.md`
- `model_metadata_probe.txt`
- per-library filtered `strings`, exports, and undefined-symbol files

## Evidence Table

| Area | Evidence | Reading |
| --- | --- | --- |
| Custom built LiteRT stack | `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, `liblitertlm_jni.so`, compiler plugin, and Gemma constraint provider are present and packaged in `customBuildExperimentDebug`. | Same-source/tag LiteRT-LM stack is staged correctly for the isolated flavor. |
| QNN payload in custom APK | `libQnnSystem.so`, `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnHtpV79Stub.so`, `libQnnHtpV79Skel.so`, `libQnnDsp.so`, `libQnnGpu.so`, and `libQnnTFLiteDelegate.so` are packaged. | QNN files are present, but they are not from the same custom LiteRT build. |
| QNN Build IDs differ | custom APK QNN libs differ from Gallery SM8750 and local QAIRT 2.46 libs. | There are at least three QNN generations in play: Gallery, Lami/custom APK packaged QNN, and local QAIRT 2.46. |
| Tombstone loaded libs | Tombstone maps `liblitertlm_jni.so`, `libGemmaModelConstraintProvider.so`, and `libllm_inference_engine_jni.so`; it does not show `libLiteRtDispatch_Qualcomm.so`, `libLiteRt.so`, or QNN libs in the extracted map lines. | The failure likely occurs before QNN backend/skel/stub loading is observable, or the dispatch runtime is rejected/not selected before those libs are mapped. |
| Dispatch strings | built and Gallery dispatch both contain `SM8750`, `ADSP_LIBRARY_PATH`, `LD_LIBRARY_PATH`, QNN API/version mismatch messages, and `LiteRtDispatchGetApi`. | Dispatch has explicit version and path checks; these are plausible failure gates. |
| Runtime C API | No `libLiteRtRuntimeCApi.so` is packaged, no `NEEDED` edge points to it, and no current string/log evidence requires it. | `libLiteRtRuntimeCApi.so` remains a low-probability primary cause. |
| Logcat | Existing `logcat_litert_qnn_extract.txt` has no useful dispatch/QNN lines for this crash. | Current diagnosis relies mostly on tombstone and static library evidence. |
| Model metadata | Local model was not provided to the script. The prior dry-run recorded `exists=true`, `canRead=true`, and size `3016294400`. | Model schema remains unresolved; no direct metadata strings were collected in this pass. |

## Build ID Comparison

### Custom built LiteRT stack

| Library | Build ID |
| --- | --- |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` |
| `libLiteRtDispatch_Qualcomm.so` | `e999216e6d32c2f38702cd8538299e7d` |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `9053b81d7cbccdc3b5460c5e7395e293` |
| `libGemmaModelConstraintProvider.so` | `f9e5e73e668032550042319e43012011` |

### QNN libraries packaged in `customBuildExperimentDebug`

| Library | Build ID |
| --- | --- |
| `libQnnSystem.so` | `94d63184c6b1f968` |
| `libQnnHtp.so` | `e227353d86be672b` |
| `libQnnHtpPrepare.so` | `9ae62cf17f972404` |
| `libQnnHtpV79Stub.so` | `c079c75e0fd8ee92` |
| `libQnnHtpV79Skel.so` | none |
| `libQnnDsp.so` | `f3d6ba754632bec7` |
| `libQnnGpu.so` | `7b85f5b584c891ec` |
| `libQnnTFLiteDelegate.so` | `234bcfd44a262b4223beac759500b208a2cca949` |

### Local QAIRT 2.46 Android libraries

| Library | Build ID |
| --- | --- |
| `libQnnSystem.so` | `4c8186d9f7beaf1a` |
| `libQnnHtp.so` | `39fd84e5b14a4fd3` |
| `libQnnHtpPrepare.so` | `993583771e8e0c07` |
| `libQnnHtpV79Stub.so` | `8465889b27ea70a0` |
| `libQnnHtpV79Skel.so` | none |
| `libQnnDsp.so` | `b72771333d09bb46` |
| `libQnnGpu.so` | `fa7378b87c6c17b2` |
| `libQnnTFLiteDelegate.so` | `668de21d1c5bc9b8328bb45de807b72a6fc61bfd` |

### Gallery SM8750 reference

| Library | Build ID |
| --- | --- |
| `libQnnSystem.so` | `0d409cdd664b8b0a` |
| `libQnnHtp.so` | `f2c90c1775a109e1` |
| `libQnnHtpPrepare.so` | missing from Gallery APK extraction |
| `libQnnHtpV79Stub.so` | `10d7ad6f9195411a` |
| `libQnnHtpV79Skel.so` | none |

## Hypothesis Evaluation

### H1: QAIRT 2.44 expected vs 2.46 built/runtime mismatch

Status: likely.

Evidence:

- LiteRT source expects QAIRT `2.44.0.260225`.
- Local build used an overlay to QAIRT `2.46.0.260424`.
- QAIRT 2.46 headers report QNN core `2.35.0` and HTP `5.46.0`.
- custom APK packaged QNN libs are not the same Build IDs as local QAIRT 2.46 and not the same as Gallery SM8750.
- Dispatch strings explicitly contain QNN library/backend/system version mismatch checks.

This is currently the most actionable hypothesis.

### H2: SM8750/V79 dispatch capability mismatch

Status: likely.

Evidence:

- built and Gallery dispatch both contain `SM8750` and `LiteRtDispatchGetApi`.
- QNN HTP/skel strings include V79 and SM8750 support strings, but also unsupported Snapdragon and context/blob-version error strings.
- The abort occurs at dispatch delegate kernel creation, before generation.

Missing evidence:

- No concrete `insufficient capabilities` log line was captured.
- No decoded dispatch compatibility result is available.

### H3: QNN/HTP skel/stub / ADSP_LIBRARY_PATH issue

Status: possible, but not proven.

Evidence:

- Dispatch strings mention `ADSP_LIBRARY_PATH` and `LD_LIBRARY_PATH`.
- QNN HTP strings mention `skel file path`, `failed to initialize skel/stub file path`, and `libcdsprpc.so`.
- The custom APK packages V79 stub/skel files.

Counter-evidence:

- Tombstone does not show QNN HTP/stub/skel libraries mapped.
- Existing logcat extract has no linker, `dlopen`, ADSP, or QNN path error.

This remains a candidate if a later instrumented run shows QNN loader/path messages.

### H4: Qualcomm model/runtime schema mismatch

Status: possible, unresolved.

Evidence:

- QNN HTP/skel strings include context/blob-version and future-blob errors.
- The model is a compiled SM8750 Qualcomm `.litertlm` payload.

Missing evidence:

- The script did not have a local copy of the model to inspect strings/metadata.
- Current crash may occur before model context blob reaches QNN HTP loading.

### H5: app sandbox/nativeLibraryDir discovery limitation

Status: possible, secondary.

Evidence:

- Dispatch and QNN strings reference path/env behavior.
- `Backend.NPU(nativeLibraryDir)` can instantiate, but dispatch runtime is still considered unusable.
- Tombstone does not show the dispatch or QNN libraries mapped in extracted map lines.

Counter-evidence:

- The relevant libraries are packaged in the APK.
- No loader error is present in captured logcat.

## Most Likely Current Reading

The strongest current signal is not a single missing `.so`. It is generation/capability coupling:

1. same-source/tag LiteRT stack reaches dispatch delegate creation,
2. QNN runtime files are present in the APK,
3. but the QNN payload is not generation-matched to the built stack or Gallery,
4. and the abort is still `No usable Dispatch runtime found`.

Current ranking:

1. QAIRT/QNN version coupling mismatch.
2. SM8750/V79 dispatch capability mismatch.
3. QNN/HTP skel/stub path issue.
4. model/runtime schema mismatch.
5. app sandbox/nativeLibraryDir discovery limitation.

## Next Safe Experiment

Recommended next step: do not run generation. First choose one of these static/build-only paths:

1. Obtain exact QAIRT `2.44.0.260225` and rebuild the same limited LiteRT-LM/LiteRT targets into `artifacts/`, then static-compare before insertion.
2. Find a LiteRT source/ref that explicitly expects QAIRT `2.46.0.260424`, then query/build only into `artifacts/`.
3. Prepare an isolated QNN-libs alignment experiment for `customBuildExperimentDebug`, but only after licensing/reuse review and only for `Engine.initialize` dry-run.
4. Build or query a same-source `litert_lm_main` CLI path to separate Android app packaging from model/runtime compatibility.
5. Post the prepared upstream issue with this new evidence if source/QAIRT matching cannot be resolved locally.

Safety remains unchanged:

- no normal UI NPU path
- no `selectedPath=npu`
- no `Conversation`
- no `Session`
- no `generateResponse`
- isolated flavor only

## QAIRT 2.44 Exact-Match Rebuild Status

Result date: 2026-05-17

Local search artifact:

```text
artifacts/qairt_244_exact_match/20260517_013958/local_search.txt
```

Status:

```text
blocked-awaiting-qairt244
```

The exact QAIRT `2.44.0.260225` SDK was not found locally. The only matching path is:

```text
/home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225
```

That is a symlink to:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424
```

No QAIRT 2.44 exact-match rebuild was performed.

Acquisition notes:

```text
docs/qairt_244_acquisition_notes.md
```

Prepared compare doc:

```text
docs/litert_custom_build_qairt244_compare.md
```

The build helper now supports:

```bash
bash scripts/build_litert_custom_artifacts.sh \
  ~/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244
```

This creates a per-run overlay under the build artifact directory and does not overwrite the existing 2.46 overlay.
