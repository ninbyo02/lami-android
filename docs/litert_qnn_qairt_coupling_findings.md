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

### Current QAIRT 2.44 custom built LiteRT stack

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` | `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` |
| `libLiteRtDispatch_Qualcomm.so` | `a8006da3bd9b4fdf5b7131f8d864b6ee` | `00c26484621ab42bea6e3bee0d7e908451a428cf19cbd1ebfecf4ccee79e1739` |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` | `310e37ff7cf770c24d636bbb0f9647a0d59dd893ba0c2530acdfc06569704230` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `443391d4c4348191230b67a3ab8a6037` | `c56c7cd5ea3aaee69bae18085b270491507e5736ba8ec1af18aa798f7ac1a64c` |
| `libGemmaModelConstraintProvider.so` | `f9e5e73e668032550042319e43012011` | `45ca57e55d52976e5d2dadfc0e874499fc0671c169a28077772c25264f9d81f6` |

This stack came from exact QAIRT `2.44.0.260225` installed at
`/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225` and the limited
build artifact `artifacts/litert_custom_build/20260517_230448_qairt244/`.
Packaging/install into `customBuildExperimentDebug` succeeded. First dry-run
attempt `runId=1779296283194` was skipped by stale expected IDs
(`custom-stack-build-id-mismatch`), so `Engine.initialize` was still uninvoked
for this expected stack at that point.

After refreshing the expected IDs, initialize-only dry-run `runId=1779317161924`
invoked `Engine.initialize` and aborted with `SIGABRT`. Diagnostics are in
`artifacts/npu_diagnostics/20260521_074641_customnpu/`; the result class is
`no-usable-dispatch-runtime`.

### QAIRT 2.44 initialize-only dry-run

| Field | Value |
| --- | --- |
| Run ID | `1779317161924` |
| Stage artifact | `artifacts/litert_custom_build_stage/20260521_074601/` |
| Diagnostics artifact | `artifacts/npu_diagnostics/20260521_074641_customnpu/` |
| Device tombstone | `/data/tombstones/tombstone_11` |
| Final stage | `Engine.initialize invoking method=Engine.initialize(): void` |
| `Engine.initialize` invoked | yes |
| `Engine.initialize` returned | no |
| Signal | `SIGABRT` |
| Classification | `no-usable-dispatch-runtime` |
| Evidence text | `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found` |

No `Conversation`, `Session`, `generateResponse`, `selectedPath=npu`, or normal
UI NPU inference path was used.

### Previous 2.46-overlay custom built LiteRT stack

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

Status: resolved for LiteRT custom build artifacts; runtime still fails with no usable dispatch runtime.

Evidence:

- LiteRT source expects QAIRT `2.44.0.260225`.
- The older local build used an overlay to QAIRT `2.46.0.260424`.
- Exact QAIRT `2.44.0.260225` has now been obtained through QPM.
- Limited qairt244 rebuild, stage, APK packaging, and install succeeded.
- The first dry-run attempt was skipped before `Engine.initialize` because the app guard still expected the old 2.46-overlay dispatch/compiler Build IDs.
- After updating the expected qairt244 IDs, `Engine.initialize` was invoked and aborted with `No usable Dispatch runtime found`.
- QAIRT 2.46 headers report QNN core `2.35.0` and HTP `5.46.0`.
- custom APK packaged QNN libs are not the same Build IDs as local QAIRT 2.46 and not the same as Gallery SM8750.
- Dispatch strings explicitly contain QNN library/backend/system version mismatch checks.

The next evidence point is no longer the stale guard. The current failure is
inside dispatch delegate creation after `Engine.initialize` starts. This remains
isolated to `customBuildExperimentDebug` and did not run generation.

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

Result dates: 2026-05-17, updated 2026-05-21

Local search artifact:

```text
artifacts/qairt_244_exact_match/20260517_013958/local_search.txt
```

```text
qairt244-initialize-invoked-sigabrt-no-usable-dispatch-runtime
```

Initial 2026-05-17 state: the exact QAIRT `2.44.0.260225` SDK was not found
locally. The only matching path was:

```text
/home/sato/project/litert-custom-build/qairt_overlay/qairt/2.44.0.260225
```

That was a symlink to:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.46.0.260424
```

At that time, no QAIRT 2.44 exact-match rebuild had been performed. The exact
qairt244 limited rebuild later succeeded at
`artifacts/litert_custom_build/20260517_230448_qairt244/`.

Acquisition notes:

```text
docs/qairt_244_acquisition_notes.md
```

Prepared compare doc:

```text
docs/litert_custom_build_qairt244_compare.md
```

The build helper used for the exact SDK was:

```bash
bash scripts/build_litert_custom_artifacts.sh \
  ~/project/litert-custom-build/LiteRT-LM \
  --qairt-root /home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225 \
  --label qairt244
```

This created a per-run overlay under the build artifact directory and did not
overwrite the existing 2.46 overlay.

## QAIRT 2.46 Source/Ref Search Status

Result date: 2026-05-17

Artifact:

```text
artifacts/litert_qairt246_ref_search/20260517_062055/
```

Docs:

- `docs/litert_qairt246_source_ref_candidates.md`
- `docs/litert_qairt246_ref_search_results.md`

Result:

- local QAIRT `2.46.0.260424` is available
- public LiteRT `origin/main` still references QAIRT `2.44.0.260225`
- LiteRT-LM `origin/main` pins LiteRT `d865fd82cd7fe6752908b3a0836895461c305679`
- that pinned LiteRT ref also references QAIRT `2.44.0.260225`
- no exact `2.46.0.260424`, `260424`, or `260424121129` evidence was found in bounded public LiteRT QAIRT metadata refs
- no query/cquery was run for a QAIRT 2.46 candidate because no exact candidate source ref was identified

Updated reading:

The local QAIRT 2.46 path remains useful as an installed SDK, but there is no
public source/ref evidence that it is the intended SDK generation for the
available LiteRT/LiteRT-LM refs. The strongest next path is still exact QAIRT
`2.44.0.260225` acquisition or maintainer guidance for a QAIRT 2.46 source/ref.

## Current Recommended Path

1. Treat the stale expected Build ID guard as unblocked.
2. Investigate why qairt244 still reports no usable dispatch runtime after `Engine.initialize` starts.
3. Compare QNN runtime packaging/capability expectations against the qairt244 build and Gallery SM8750 stack before any further isolated experiment.
4. Do not run generation, create `Conversation`/`Session`, set
   `selectedPath=npu`, or wire `Backend.NPU` into normal UI inference.

## QAIRT 2.42 Comparison Status

Result date: 2026-05-17

Local search artifact:

```text
artifacts/qairt242_acquisition/20260517_083526/local_search.txt
```

Status:

```text
qairt242-local-missing
```

QAIRT `2.42.0.251225` was not found locally. The existing SDK tree contains
QAIRT `2.46.0.260424`, but that cannot be used as a 2.42 comparison root.

QAIRT 2.42 is now documented as a public Radxa/Linux comparison generation, not
as the primary SM8750/V79 candidate:

- Radxa public docs use QAIRT `2.42.0.251225`.
- Radxa examples focus on Linux board workflows and QCS6490/V68 or QCS9075/V73
  style paths.
- LiteRT public Qualcomm metadata references QAIRT `2.44.0.260225`.
- The local SDK is QAIRT `2.46.0.260424`.
- Lami's target remains Android app `Backend.NPU(nativeLibraryDir)` on
  SM8750/V79 with a Qualcomm SM8750 `.litertlm` model.

Prepared helpers:

```text
scripts/check_qairt242_sdk.sh
scripts/stage_qairt242_sdk_from_download.sh
docs/qairt_242_acquisition_notes.md
```

Recommended priority:

1. official issue / maintainer guidance,
2. exact QAIRT `2.44.0.260225` acquisition and rebuild compare,
3. QAIRT `2.42.0.251225` static comparison only, if obtained,
4. no QAIRT 2.42 build or app insertion unless explicitly approved later.

## QAIRT 2.44 Acquisition Probe

Probe date: 2026-05-17

Artifact:

```text
artifacts/qairt244_acquisition/20260517_074537/
```

Initial 2026-05-17 result:

- `qpm`, `qpm-cli`, `qualcomm-package-manager`, and `software-center` were not found
- `qpm search` could not be run
- no `/opt/qcom/aistack/qairt/` install was found
- exact QAIRT `2.44.0.260225` was missing at that time
- local QAIRT `2.46.0.260424` remains the only full SDK found

Prepared workflow:

- `scripts/check_qairt244_sdk.sh`
- `scripts/stage_qairt244_sdk_from_download.sh`
- `scripts/run_qairt244_rebuild_compare.sh`

Safety status remains unchanged: no normal UI NPU path, no `selectedPath=npu`,
no `Conversation`, no `Session`, and no `generateResponse`.

## Public Qualcomm Ecosystem Comparison

Result date: 2026-05-17

Detailed analysis:

```text
docs/radxa_qairt_ecosystem_analysis.md
docs/litert_generation_strategy_options.md
```

Radxa Dragon Q6A and AIRbox Q900 public docs currently present a Linux/SBC QAIRT
workflow centered on QAIRT `2.42.0.251225`. That public workflow assumes:

- board/system fastrpc setup,
- `/dev/fastrpc-*` devices,
- `/usr/lib/dsp` libraries,
- `source bin/envsetup.sh`,
- explicit `ADSP_LIBRARY_PATH`,
- Linux QNN ABI paths such as `aarch64-oe-linux-gcc11.2`,
- SoC-specific `dsp_arch` / `soc_id` context generation.

This differs from Lami's Android app path, which depends on app-packaged
`lib/arm64-v8a` libraries and `Backend.NPU(nativeLibraryDir)` without shell
environment setup.

Generation comparison after adding Radxa evidence:

| Ecosystem | Generation signal | Reading |
| --- | --- | --- |
| Radxa public Linux docs | QAIRT `2.42.0.251225` | stable public Linux/SBC generation, not SM8750-specific |
| LiteRT public refs | QAIRT `2.44.0.260225` | current public LiteRT Qualcomm metadata |
| local SDK | QAIRT `2.46.0.260424` | newer local SDK, no matching public LiteRT/LiteRT-LM ref found |
| Gallery SM8750 APK | special native payload | likely internal/special generation |

This makes `No usable Dispatch runtime found` consistent with a generation or
capability acceptance failure even when the dispatch `.so`, `libLiteRt.so`, and
QNN files are present. Exact QAIRT `2.44.0.260225` has since been acquired and
rebuilt; the initialize-only dry-run now reaches `Engine.initialize` and still
classifies as `no-usable-dispatch-runtime`.
