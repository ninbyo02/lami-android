# Gallery SM8750 native stack experiment plan

Date: 2026-05-16

This plan prepares the next isolated experiment after `npuExperimentDebug` was switched to Maven `litertlm-android:0.10.0` and still aborted during `Engine.initialize`. It keeps Gallery native libraries isolated in `galleryStackExperimentDebug`, does not replace standard or `npuExperiment` native libraries, does not build `dispatch_api_so`, does not run NPU inference, does not create `Conversation` / `Session`, and does not call `generateResponse`.

## Why this experiment is needed

`npuExperimentDebug` now uses Maven `litertlm-android:0.10.0`, but the native payload is still not the same as Google AI Edge Gallery SM8750:

| Component | Gallery SM8750 | npuExperimentDebug after Maven 0.10.0 split |
| --- | --- | --- |
| `liblitertlm_jni.so` | Build ID `76e4dccd9c5f9cba468d9cae7becfec0` | Build ID `ecacedccf835d7674c95bd40186d0fde` |
| `libLiteRt.so` | Build ID `869121bd7f4b0b77fa581218117a5c14` | not packaged by Maven `0.10.0` |
| `libLiteRtDispatch_Qualcomm.so` | Build ID `643ad77b8ac2f54bd1b61e4133c77b3a` | same Gallery dispatch staged in `npuExperimentDebug` |

The dry-run still reaches `Engine.initialize` and then ends in `SIGABRT`. This suggests that Maven `0.10.0` API compatibility alone is not enough; the Gallery SM8750 APK likely ships a matched native payload.

## Classification artifact

Classification was generated with:

```bash
bash scripts/plan_gallery_native_stack_experiment.sh /tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk
```

Artifact:

- `artifacts/gallery_native_stack_plan/20260516_183915/`
- source APK SHA-256: `cb0eb290c546de29a48864fd3972d8b8a487f5a87e277447f52377ffa60ee5ba`

Category counts:

| Category | Count |
| --- | ---: |
| required candidate | 3 |
| QNN runtime candidate | 2 |
| HTP skel/stub candidate | 2 |
| unrelated or unknown | 3 |

## Required candidates

These are the minimum native stack candidates to keep generation-aligned with Gallery SM8750 in a future isolated flavor:

| Library | Build ID | Size | NEEDED | Risk |
| --- | --- | ---: | --- | --- |
| `liblitertlm_jni.so` | `76e4dccd9c5f9cba468d9cae7becfec0` | `19063832` | `libdl.so`, `libm.so`, `libEGL.so`, `libGLESv2.so`, `libGLESv3.so`, `libandroid.so`, `liblog.so`, `libc.so` | high: replaces LiteRT-LM JNI/native ABI generation |
| `libLiteRt.so` | `869121bd7f4b0b77fa581218117a5c14` | `4964616` | `libdl.so`, `libGLESv3.so`, `libEGL.so`, `libm.so`, `liblog.so`, `libc.so` | high: replaces LiteRT runtime generation |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` | `446088` | `libLiteRt.so`, `libandroid.so`, `liblog.so`, `libdl.so`, `libc.so`, `libm.so` | medium/high: must match `libLiteRt.so` and stay isolated |

`libLiteRtRuntimeCApi.so` is not present in Gallery SM8750 `lib/arm64-v8a`.

## QNN and HTP candidates

Gallery SM8750 includes:

| Library | Category | Build ID | Size | NEEDED |
| --- | --- | --- | ---: | --- |
| `libQnnSystem.so` | QNN runtime | `0d409cdd664b8b0a` | `2983560` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` |
| `libQnnHtp.so` | QNN runtime | `f2c90c1775a109e1` | `2778176` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so` |
| `libQnnHtpV79Stub.so` | HTP V79 stub | `10d7ad6f9195411a` | `679168` | `libc.so`, `libm.so`, `libdl.so`, `liblog.so`, `libcdsprpc.so` |
| `libQnnHtpV79Skel.so` | HTP V79 skel | no GNU Build ID | `10975268` | `libc++.so.1`, `libc++abi.so.1` |

Not present in the Gallery SM8750 APK arm64 payload:

- `libQnnHtpPrepare.so`
- `libQnnGpu.so`
- `libQnnDsp.so`
- V75/V73/V69/V68 HTP skel/stub
- `libQnnDspV66Skel.so`
- `libQnnDspV66Stub.so`

No separate compiler/plugin candidate was found by filename. The Qualcomm dispatch runtime and `libLiteRt.so` carry the relevant dispatch symbols.

## Unrelated or unknown

Do not stage these unless a later dependency analysis proves they are needed:

- `libandroidx.graphics.path.so`
- `libimage_processing_util_jni.so`
- `libsurface_util_jni.so`

## Collision candidates

If Gallery libraries are placed into an app variant, same-name collisions are expected:

| Library | Collides with `standardDebug` | Collides with `npuExperimentDebug` |
| --- | --- | --- |
| `libLiteRt.so` | yes | no |
| `liblitertlm_jni.so` | yes | yes |
| `libLiteRtDispatch_Qualcomm.so` | no | yes |
| `libQnnSystem.so` | yes | yes |
| `libQnnHtp.so` | yes | yes |
| `libQnnHtpV79Stub.so` | yes | yes |
| `libQnnHtpV79Skel.so` | yes | yes |
| `libandroidx.graphics.path.so` | yes | yes |

These collisions are why the next experiment must use a new isolated flavor or app id. Do not use `packagingOptions.pickFirst` to hide conflicts globally.

## Flavor isolation design

Proposed flavor:

- name: `galleryStackExperiment`
- build variant: `galleryStackExperimentDebug`
- release: disabled
- applicationId suffix: `.gallerynpu`
- version name suffix: `-galleryStackExperiment`
- native source set: `app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a/`
- dispatch/LiteRT/LiteRT-LM/QNN libraries: only under this flavor-specific source set
- probe: dedicated Activity or existing NPU probe gated by `BuildConfig.CURRENT_FLAVOR == "galleryStackExperiment"`
- normal UI inference path: not wired to Gallery NPU backend

Suggested BuildConfig policy:

| Field | Value |
| --- | --- |
| `CURRENT_FLAVOR` | `galleryStackExperiment` |
| `QUALCOMM_DISPATCH_EXPERIMENT` | `true` |
| `NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED` | `true` |
| `GALLERY_STACK_EXPERIMENT` | `true` |
| `DISPATCH_RUNTIME_SOURCE` | `gallery-sm8750 full-stack candidate, detection-only` |

This flavor has now been implemented as `galleryStackExperimentDebug`. Release is disabled, and the source set is isolated from `standardDebug` and `npuExperimentDebug`.

## Experiment phases

1. Phase 1: docs and classification only.
   - Completed by this plan.
   - No Gallery libraries copied into the app.

2. Phase 2: create `galleryStackExperiment` flavor.
   - Disable release.
   - Add empty flavor-specific `jniLibs/arm64-v8a`.
   - Add dependency report checks for `galleryStackExperimentDebugRuntimeClasspath`.

3. Phase 3: stage Gallery native stack only in `galleryStackExperimentDebug`.
   - Start with the matched required candidates: `liblitertlm_jni.so`, `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`.
   - Add QNN/HTP candidates only if the required trio still reaches QNN discovery errors.
   - Do not stage unrelated Gallery UI/image libraries.

4. Phase 4: run detection-only diagnostics.
   - Check nativeLibraryDir presence, SHA-256, Build IDs, NEEDED, and collision-free packaging.
   - No `System.loadLibrary`, `Runtime.load`, or explicit `dlopen`.

5. Phase 5: `Backend.NPU(String)` instantiate-only.
   - Object must not be passed to app inference.

6. Phase 6: `EngineConfig` dry-build only.
   - Confirm backend property is `Backend.NPU`.

7. Phase 7: `Engine.initialize` dry-run only with explicit opt-in.
   - No `Conversation`, no `Session`, no `generateResponse`.
   - Write stage files before native calls.

8. Phase 8: only if initialize succeeds, design a separate single-token smoke test.
   - This is not part of the current plan.

## Implementation result

Implementation date: 2026-05-16

Flavor:

- name: `galleryStackExperiment`
- installable variant: `galleryStackExperimentDebug`
- release variant: disabled
- applicationId: `io.github.ninbyo02.lami.gallerynpu`
- LiteRT-LM dependency: `com.google.ai.edge.litertlm:litertlm-android:0.10.0`
- native source set: `app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a/`
- probe source: existing `NpuExperimentProbeActivity` shared from `src/npuExperimentDebug/java`
- manifest source: existing debug probe manifest shared from `src/npuExperimentDebug/AndroidManifest.xml`

BuildConfig:

| Field | Value |
| --- | --- |
| `CURRENT_FLAVOR` | `galleryStackExperiment` |
| `QUALCOMM_DISPATCH_EXPERIMENT` | `true` |
| `NPU_BACKEND_INSTANTIATE_PROBE_ALLOWED` | `true` |
| `GALLERY_STACK_EXPERIMENT` | `true` |
| `DISPATCH_RUNTIME_SOURCE` | `gallery-sm8750 full native stack staged in app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a` |

Staged with:

```bash
bash scripts/stage_gallery_native_stack_for_experiment.sh /tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk
```

Staging artifact:

- `artifacts/gallery_native_stack_stage/20260516_191925/`

Staged libraries:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `liblitertlm_jni.so` | `76e4dccd9c5f9cba468d9cae7becfec0` | `607c4af2d405ff53a2a01415b47e202594b4e0dcce7f08f270bdfa7dd900c6d7` |
| `libLiteRt.so` | `869121bd7f4b0b77fa581218117a5c14` | `146f699ef6822a1e1f9489101a9dc5733e3788643396cab4fc768063cfde346c` |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` | `92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777` |
| `libQnnSystem.so` | `0d409cdd664b8b0a` | `7e69258e1278cc9b2bb62dbc6e2a52c227a100d6505a13fd6324a87993d0bba8` |
| `libQnnHtp.so` | `f2c90c1775a109e1` | `090e993822564851eab1405aff171643b21e644e3f696c95c96f2732aaed813a` |
| `libQnnHtpV79Stub.so` | `10d7ad6f9195411a` | `005bd3de462851ce3dde55260d7d8560d6d07dbc309f554780b1f6412e6d9df1` |
| `libQnnHtpV79Skel.so` | no GNU Build ID | `41f83395ed4b1bcfc43417a1b82f3f137c825747711c9ab4c9d50034ed198f98` |

Optional Gallery libs were not present in the APK and were not sourced elsewhere:

- `libQnnHtpPrepare.so`
- `libQnnGpu.so`
- `libQnnDsp.so`
- V75/V73/V69/V68 HTP skel/stub
- `libQnnDspV66Skel.so`
- `libQnnDspV66Stub.so`

Packaging result:

- `assembleGalleryStackExperimentDebug` succeeded.
- AGP reported same-path native library warnings for `liblitertlm_jni.so`, `libQnnSystem.so`, `libQnnHtp.so`, `libQnnHtpV79Stub.so`, and `libQnnHtpV79Skel.so`.
- The current AGP version selected the app/source-set files for those duplicates. This is acceptable for the isolated flavor, but should not be hidden with global `packagingOptions`.
- Final APK extraction confirmed the Gallery Build IDs for the staged required libraries.

Leakage result:

- `standardDebug` still has its existing QNN runtime libraries from shared dependencies, but no Gallery Qualcomm dispatch runtime and no Gallery `liblitertlm_jni.so`.
- `npuExperimentDebug` still has Maven `0.10.0` `liblitertlm_jni.so` and the previously staged dispatch-only experiment. It does not receive Gallery `libLiteRt.so` or Gallery QNN/V79 replacements.

Probe policy:

- `Gallery Stack Runtime Compatibility` is written by the probe Activity.
- `Backend.NPU(String)` instantiate-only and `EngineConfig` dry-build are allowed in `galleryStackExperimentDebug`.
- `Engine.initialize` remains explicit opt-in only.
- Normal UI inference remains GPU/fallback oriented and is not wired to `Backend.NPU`.

## Detection-only device probe result

Command:

```bash
bash scripts/run_gallery_stack_probe.sh /tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk
```

Device install:

- `./update.sh update --flavor galleryStackExperiment`
- installed variant: `galleryStackExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.gallerynpu`

Probe result:

```text
Dispatch Runtime Compatibility:
  current flavor=galleryStackExperiment
  nativeLibraryDir exists=true
  dispatch runtime present in nativeLibraryDir=true
  dispatch runtime sha256=92d923e70d301d088c2c7c50e42ea97694ed1d3b740f614cd1ce85efd2090777
  expected sha256 match=true
  dispatch runtime build id=643ad77b8ac2f54bd1b61e4133c77b3a
  ABI compatibility=likely-compatible

Gallery Stack Runtime Compatibility:
  Gallery stack present=true
  liblitertlm_jni.so build id=76e4dccd9c5f9cba468d9cae7becfec0
  libLiteRt.so build id=869121bd7f4b0b77fa581218117a5c14
  libLiteRtDispatch_Qualcomm.so build id=643ad77b8ac2f54bd1b61e4133c77b3a
  libQnnSystem.so build id=0d409cdd664b8b0a
  libQnnHtp.so build id=f2c90c1775a109e1
  libQnnHtpV79Stub.so build id=10d7ad6f9195411a
  expected Gallery build id match=true

Backend.NPU Instantiate Probe:
  result=success

EngineConfig NPU Dry-Build Probe:
  result=success
  selected constructor=EngineConfig(String, Backend, Backend, Backend, Integer, String)

Engine Initialize Dry-Run Probe:
  enabled=false
  skipped reason=explicit-opt-in-required
  initialize invoked=no
  initialize result=skipped

NPU safety status:
  selectedPath=gpu
  QNN/NPU attempted=no
```

No `--engine-dry-run` flag was passed. `Engine.initialize`, `Conversation`, `Session`, and `generateResponse` were not executed.

## Gallery Stack Engine.Initialize Crash Collection

Collection date: 2026-05-16

Collector command:

```bash
bash scripts/collect_npu_tombstone_diagnostics.sh \
  --app-id io.github.ninbyo02.lami.gallerynpu \
  --label gallerynpu
```

Artifact:

- `artifacts/npu_diagnostics/20260516_195739_gallerynpu/`

The collector now filters by exact application id. The selected tombstone contains:

```text
Cmdline: io.github.ninbyo02.lami.gallerynpu
tombstone selection: latest-tombstone-matches-app
```

This prevents mixing the older `io.github.ninbyo02.lami.npu` crash with the newer `io.github.ninbyo02.lami.gallerynpu` crash.

Final stage before process death:

```text
Engine.initialize invoking method=Engine.initialize(): void
```

Observed crash:

```text
signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0000000000ffffe0
abort message: not-found
likely abort/register/log text: not-found
process alive after probe: not-running
```

Top frames:

```text
#00 libc.so (__strlen_aarch64+240)
#01 libart.so art::JavaVMExt::JniAbort
#05 libart.so CheckJNI::GetStringCharsInternal
#06 liblitertlm_jni.so Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+816
#13 base.apk com.google.ai.edge.litertlm.Engine.initialize
#25 base.apk AcceleratorProbe.invokeEngineInitializeOperation
```

Native library metadata from the gallery app nativeLibraryDir:

| Library | Present | Build ID |
| --- | --- | --- |
| `liblitertlm_jni.so` | true | `76e4dccd9c5f9cba468d9cae7becfec0` |
| `libLiteRt.so` | true | `869121bd7f4b0b77fa581218117a5c14` |
| `libLiteRtDispatch_Qualcomm.so` | true | `643ad77b8ac2f54bd1b61e4133c77b3a` |
| `libQnnSystem.so` | true | `0d409cdd664b8b0a` |
| `libQnnHtp.so` | true | `f2c90c1775a109e1` |
| `libQnnHtpPrepare.so` | true | `9ae62cf17f972404` |
| `libQnnHtpV79Stub.so` | true | `10d7ad6f9195411a` |
| `libQnnHtpV79Skel.so` | true | no GNU Build ID |
| `libLiteRtRuntimeCApi.so` | false | missing |

The tombstone mapping excerpt shows `liblitertlm_jni.so` and `libllm_inference_engine_jni.so`. It does not show mapped `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, or QNN libraries in the extracted top crash block, even though those files are present in nativeLibraryDir.

Classification:

- `unknown-native-abort`
- confidence: `medium`

This latest gallery stack crash is not a confirmed `No usable Dispatch runtime found` case. It is a CheckJNI/string-handling crash inside `LiteRtLmJni_nativeCreateEngine`, which points more strongly at Java/Kotlin API surface and native JNI generation mismatch than at a plain missing dispatch runtime. There is also no direct log evidence in this artifact for `insufficient capabilities`, `LiteRtRuntimeCApi`, or ADSP path failure.

Next actions:

1. Treat this as a Gallery JNI/classes compatibility problem until proven otherwise.
2. Identify the exact Gallery Java/Kotlin artifact or source generation that matches `liblitertlm_jni.so` Build ID `76e4dccd9c5f9cba468d9cae7becfec0`.
3. Do not proceed to `Conversation`, `Session`, `generateResponse`, or normal app inference wiring.
4. If the next experiment changes Java/Kotlin classes or LiteRT-LM AAR generation, keep it inside `galleryStackExperimentDebug` or another separate app id.

## Native crash risks

- `liblitertlm_jni.so` and Kotlin/Java classes can drift. A Gallery JNI payload may not match public Maven `classes.jar`.
- QNN libraries can require a specific ADSP search path or matching skel/stub payload.
- `libLiteRtDispatch_Qualcomm.so` depends on `libLiteRt.so`; mixing generations has already failed.
- Native SIGABRT cannot be caught in Kotlin. The dry-run must stay in an isolated Activity and write stage files before native calls.

## Rollback

- Delete the future `app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a/` files.
- Disable or remove the `galleryStackExperiment` flavor.
- Keep `standardDebug` and `npuExperimentDebug` dependency checks as the guardrail.
- Verify `app-standard-debug.apk` does not contain `libLiteRtDispatch_Qualcomm.so` or Gallery `liblitertlm_jni.so`.

## galleryStackExperimentDebug 0.11.0 Java API alignment result

Date: 2026-05-16

`galleryStackExperimentDebug` now uses Maven `litertlm-android:0.11.0` for Java/Kotlin classes, while `standardDebug` remains on `0.11.0` and `npuExperimentDebug` remains on `0.10.0`.

The Gallery native stack remains isolated to:

```text
app/src/galleryStackExperimentDebug/jniLibs/arm64-v8a/
```

No Gallery native library was moved into `main`, `standard`, or `npuExperiment`.

Probe-only compatibility snapshot:

```text
current flavor=galleryStackExperiment
resolved expected Java API version=0.11.0
descriptor match=true
EngineConfig constructor selected=EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)
EngineConfig constructor match=true
Gallery stack present=true
expected Gallery build id match=true
selectedPath=gpu
QNN/NPU attempted=no
```

Explicit opt-in Engine.initialize dry-run:

```bash
bash scripts/run_gallery_stack_probe.sh \
  /tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk \
  --engine-dry-run \
  --model-path /data/user/0/io.github.ninbyo02.lami.gallerynpu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm
```

Result:

```text
final stage: Engine.initialize invoking method=Engine.initialize(): void
process alive after probe: not-running
signal: SIGABRT
top native frame: liblitertlm_jni.so Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1668
artifact: artifacts/npu_diagnostics/20260516_203939_gallerynpu/
```

The earlier CheckJNI `SIGSEGV` from Maven `0.10.0` Java/native API mismatch is no longer the active result. With Maven `0.11.0` classes, the dry-run progresses to a dispatch-runtime abort. The tombstone did not print a clean abort message line, but register fragments are consistent with:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

Classification:

- `no-usable-dispatch-runtime`
- confidence: `medium`

Why not high confidence:

- The collector did not extract a direct abort message line.
- The classification depends on register ASCII fragments and the known previous LiteRT-LM failure text.

Implications:

1. Java/native descriptor mismatch has been addressed for this flavor.
2. Gallery native stack is present and loaded far enough for `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, `libQnnSystem.so`, and `libQnnHtp.so` to appear in the tombstone mappings.
3. `libLiteRtRuntimeCApi.so` is still absent from the APK/nativeLibraryDir.
4. The next question is dispatch runtime usability/capability, Runtime C API expectations, or QNN/HTP runtime search behavior.

Still forbidden:

- `Conversation`
- `Session`
- `generateResponse`
- normal app inference wiring
- `selectedPath=npu`
