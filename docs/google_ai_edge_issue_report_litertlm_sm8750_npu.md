# Google AI Edge / LiteRT-LM issue report: SM8750 NPU dispatch runtime

日本語要約: Nubia Z70S Ultra / SM8750 / Android 16 で、LiteRT-LM Android の `Backend.NPU(nativeLibraryDir)` と Gemma 4 E2B Qualcomm SM8750 モデルを使った `Engine.initialize` dry-run が `SIGABRT` で終了します。Gallery SM8750 由来 stack だけでなく、LiteRT-LM `v0.11.0` と pinned LiteRT ref から同一source/tagで built native stack を作って隔離flavorへ投入しても `DispatchDelegate::CreateDelegateKernelInterface()+312` で `No usable Dispatch runtime found` となるため、現時点の最有力は QAIRT/QNN generation/capability mismatch です。QAIRT 2.44 exact SDK は未入手で、QAIRT 2.46 対応 public source/ref も見つかっていません。

## Title Candidate

`[Android][SM8750][Backend.NPU] Engine.initialize SIGABRT: No usable Dispatch runtime found with same-source custom stack`

## Latest Update

Date: 2026-05-17

Latest repo commit at report refresh:

```text
b6ff70ac docs: prepare QAIRT 2.44 SDK acquisition workflow
```

New evidence since the initial Gallery-stack report:

- same-source/tag custom build completed from LiteRT-LM `v0.11.0`
  (`c87189528a758db32ead241f4fc9c64836398ee7`) and pinned LiteRT
  `47615eb6eaec25e8dfcd1aba922c560a57cba0a2`
- custom built stack contained:
  - `libLiteRt.so`
  - `libLiteRtDispatch_Qualcomm.so`
  - `liblitertlm_jni.so`
  - `libLiteRtCompilerPlugin_Qualcomm.so`
  - `libGemmaModelConstraintProvider.so`
- the stack was staged only into `customBuildExperimentDebug`
  (`io.github.ninbyo02.lami.customnpu`)
- `Backend.NPU(String)`, `EngineConfig`, and `Engine(EngineConfig)` succeeded
- `Engine.initialize()` still aborted
- latest top frame:

  ```text
  liblitertlm_jni.so / DispatchDelegate::CreateDelegateKernelInterface()+312
  ```

- register fragments remained consistent with:

  ```text
  Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
  ```

Therefore the strongest current reading is no longer Java/native descriptor
mismatch, missing dispatch `.so`, or missing `libLiteRt.so`. The current blocker
is most likely QAIRT/QNN generation/capability coupling, SM8750/V79 dispatch
capability, or model/runtime schema compatibility.

QAIRT status:

- public LiteRT metadata expects QAIRT `2.44.0.260225`
- exact QAIRT `2.44.0.260225` SDK is not installed locally
- the local `2.44.0.260225` path was only a symlink/overlay to QAIRT
  `2.46.0.260424`
- QPM / Qualcomm Software Center CLI was not detected locally
- bounded public search found no LiteRT/LiteRT-LM source/ref with QAIRT
  `2.46.0.260424` evidence
- LiteRT `origin/main` and LiteRT-LM `origin/main` still appear to reference
  QAIRT `2.44.0.260225` metadata

## Experiment Timeline

| Phase | Variant / stack | Result |
| --- | --- | --- |
| GPU baseline | `standardDebug`, `litertlm-android:0.11.0` | normal GPU inference works |
| dispatch-only probe | `npuExperimentDebug`, `litertlm-android:0.10.0`, Gallery dispatch only | `Engine.initialize` SIGABRT |
| Gallery stack with wrong Java API | `galleryStackExperimentDebug`, Gallery native stack + Maven `0.10.0` Java API | CheckJNI SIGSEGV |
| Gallery stack with matching Java API | `galleryStackExperimentDebug`, Gallery native stack + Maven `0.11.0` Java API | SIGSEGV fixed, `Engine.initialize` SIGABRT |
| same-source/tag custom stack | `customBuildExperimentDebug`, built LiteRT-LM/LiteRT stack + Maven `0.11.0` Java API | SIGABRT at `DispatchDelegate::CreateDelegateKernelInterface()+312` |
| QAIRT coupling search | static only | QNN Build IDs differ across custom APK, Gallery, and local QAIRT 2.46 |
| QAIRT 2.44 acquisition | docs/scripts only | exact SDK missing; QPM tooling not detected |

## Current Blocker

The next actionable path is either:

1. obtain official QAIRT `2.44.0.260225`, rebuild the limited source-matched
   stack, and static-compare before any insertion; or
2. get maintainer guidance on the public source/ref and QNN package expected for
   QAIRT `2.46.0.260424` / SM8750 / V79.

No generation, `Conversation`, `Session`, or `generateResponse` has been run.

## Summary

On a Nubia Z70S Ultra / Snapdragon 8 Elite / SM8750 device running Android 16, `Engine.initialize()` crashes the process with `SIGABRT` when using LiteRT-LM Android `Backend.NPU(nativeLibraryDir)` with the Gemma 4 E2B Qualcomm SM8750 `.litertlm` model.

The crash still happens after isolating a debug-only flavor with:

- `litertlm-android:0.11.0` Java/Kotlin API
- Google AI Edge Gallery SM8750 native stack
- matching Java/native `LiteRtLmJni.nativeCreateEngine` descriptor
- `libLiteRtDispatch_Qualcomm.so` present and mapped
- `libLiteRt.so`, `libQnnSystem.so`, and `libQnnHtp.so` present and mapped

The current classification is:

- primary: `no-usable-dispatch-runtime`
- likely underlying: `dispatch-runtime-compatibility-mismatch`
- confidence: medium

## Environment

| Item | Value |
| --- | --- |
| Device | Nubia Z70S Ultra |
| Model | `NX733J` |
| SoC | Qualcomm / QTI `SM8750` |
| Hardware | `qcom` |
| GPU | Adreno 830 |
| Android SDK | 36 |
| ABI | `arm64-v8a` |
| Model file | `gemma-4-E2B-it_qualcomm_sm8750.litertlm` |
| Model path in isolated app | `/data/user/0/io.github.ninbyo02.lami.gallerynpu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm` |
| Model size | `3016294400` bytes |
| Model readable | true |

External QAIRT / QNN validation:

- `qnn-net-run`: available
- `qnn-platform-validator`: available
- QNN SDK version: `v2.46.0.260424121129`
- External QNN GPU validation: passed
- External QNN DSP/HTP validation: passed
- DSP core reported by external validator: Hexagon Architecture V79

Lami app variants used for isolation:

| Variant | applicationId | LiteRT-LM dependency | Purpose |
| --- | --- | --- | --- |
| `standardDebug` | `io.github.ninbyo02.lami` | `litertlm-android:0.11.0` | Normal app path; GPU inference works |
| `npuExperimentDebug` | `io.github.ninbyo02.lami.npu` | `litertlm-android:0.10.0` | Dispatch-only / NPU probe experiment |
| `galleryStackExperimentDebug` | `io.github.ninbyo02.lami.gallerynpu` | `litertlm-android:0.11.0` | Gallery SM8750 native stack isolation |

## Native Stack Under Test

Source APK:

- APK: `ai-edge-gallery-sm8750.apk`
- package: `com.google.ai.edge.gallery`
- versionName: `1.0.12`
- versionCode: `29`
- source tag candidate: `google-ai-edge/gallery` `1.0.12` / commit `302f7e463b19f45f51825f4ec2fd30309366cb06`
- public Gradle dependency candidate: `litertlm-android:0.10.0`
- observation: the native payload does not appear identical to public Maven `litertlm-android:0.10.0`

Gallery native libraries staged only in `galleryStackExperimentDebug`:

| Library | Build ID | Notes |
| --- | --- | --- |
| `liblitertlm_jni.so` | `76e4dccd9c5f9cba468d9cae7becfec0` | Gallery JNI |
| `libLiteRt.so` | `869121bd7f4b0b77fa581218117a5c14` | Gallery LiteRT runtime |
| `libLiteRtDispatch_Qualcomm.so` | `643ad77b8ac2f54bd1b61e4133c77b3a` | Gallery Qualcomm dispatch runtime |
| `libQnnSystem.so` | `0d409cdd664b8b0a` | Gallery QNN |
| `libQnnHtp.so` | `f2c90c1775a109e1` | Gallery QNN HTP |
| `libQnnHtpPrepare.so` | `9ae62cf17f972404` | Present in isolated APK |
| `libQnnHtpV79Stub.so` | `10d7ad6f9195411a` | V79 stub |
| `libQnnHtpV79Skel.so` | none | V79 skel, no GNU Build ID |

## What I Tried

1. Baseline `standardDebug` GPU path:
   - `litertlm-android:0.11.0`
   - normal `gemma-4-E2B-it.litertlm`
   - GPU inference works
   - held engine reuse works
   - approximately 32 tokens/s backend baseline observed

2. `npuExperimentDebug` dispatch-only path:
   - `litertlm-android:0.10.0`
   - Gallery `libLiteRtDispatch_Qualcomm.so` only
   - `Backend.NPU(String nativeLibraryDir)` instantiate succeeded
   - `EngineConfig.backend = Backend.NPU` dry-build succeeded
   - `Engine` constructor returned
   - `Engine.initialize` crashed with `SIGABRT`

3. `galleryStackExperimentDebug` with Gallery SM8750 native stack:
   - separate applicationId: `io.github.ninbyo02.lami.gallerynpu`
   - Gallery native stack included only in this debug flavor
   - probe Activity only
   - no normal UI inference wiring
   - no `Conversation`, `Session`, or `generateResponse`

4. Java/native API surface comparison:
   - Gallery JNI `nativeCreateEngine` descriptor matches Maven `litertlm-android:0.11.0`
   - Gallery JNI descriptor does not match Maven `litertlm-android:0.10.0`
   - initial Gallery-native + Maven-0.10.0 experiment crashed with `SIGSEGV` in `CheckJNI::GetStringCharsInternal`
   - switching `galleryStackExperimentDebug` back to `litertlm-android:0.11.0` made the descriptor match true and the `SIGSEGV` disappeared

5. Current result:
   - `Engine` constructor returns
   - process dies during `Engine.initialize()`
   - signal: `SIGABRT`
   - classification: `no-usable-dispatch-runtime` / `dispatch-runtime-compatibility-mismatch`

## Reproduction Steps

The repro is intentionally isolated to a debug-only flavor and explicit dry-run command.

```bash
./update.sh update --flavor galleryStackExperiment

bash scripts/run_gallery_stack_probe.sh \
  /tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk \
  --engine-dry-run \
  --model-path /data/user/0/io.github.ninbyo02.lami.gallerynpu/files/local_models/gemma-4-E2B-it_qualcomm_sm8750.litertlm

bash scripts/collect_npu_tombstone_diagnostics.sh \
  --app-id io.github.ninbyo02.lami.gallerynpu \
  --label gallerynpu
```

The dry-run performs:

- `Backend.NPU(nativeLibraryDir)` construction
- `EngineConfig(modelPath, Backend.NPU, ...)` construction
- `Engine(EngineConfig)` construction
- `Engine.initialize()`

It does not perform:

- `Conversation` creation
- `Session` creation
- prompt evaluation
- token generation
- `generateResponse`
- normal app inference wiring

## Expected Behavior

`Engine.initialize()` should either:

- initialize successfully, or
- fail with a catchable Java/Kotlin exception containing the exact dispatch/QNN/runtime reason.

It should not abort the app process.

## Actual Behavior

The process aborts during `Engine.initialize()`.

Final stage file:

```text
Engine.initialize invoking method=Engine.initialize(): void
```

Process state after probe:

```text
pidof io.github.ninbyo02.lami.gallerynpu: <not-running>
```

Crash summary:

```text
signal 6 (SIGABRT), code -1 (SI_QUEUE)
explicit Abort message line: not found
likely abort/register/log text:
  register-fragments: Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

Top native frame:

```text
liblitertlm_jni.so
Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1668
BuildId: 76e4dccd9c5f9cba468d9cae7becfec0
```

## Crash Details

Top backtrace excerpt:

```text
#00 pc 000000000007128c  /apex/com.android.runtime/lib64/bionic/libc.so (abort+160)
#01 pc 0000000000d9c2dc  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#02 pc 0000000000da3308  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#03 pc 0000000000fda32c  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#04 pc 0000000000fd9d90  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#05 pc 0000000000fd99c8  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#06 pc 0000000000dc1d50  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#07 pc 0000000000da3254  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#08 pc 0000000000fde95c  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#09 pc 0000000000fdf840  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#10 pc 0000000000fd3cd0  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#11 pc 0000000000d814a0  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#12 pc 0000000000d7d91c  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#13 pc 00000000007b7f0c  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#14 pc 0000000000807c1c  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#15 pc 0000000000807b50  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#16 pc 00000000007c409c  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#17 pc 00000000007a70c0  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#18 pc 000000000057ea6c  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#19 pc 000000000057af90  .../lib/arm64/liblitertlm_jni.so (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#20 pc 000000000057aa24  .../lib/arm64/liblitertlm_jni.so (Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1668) (BuildId: 76e4dccd9c5f9cba468d9cae7becfec0)
#27 pc ... base.apk (com.google.ai.edge.litertlm.Engine.initialize+0)
#39 pc ... base.apk (io.github.ninbyo02.lami.ui.screens.home.AcceleratorProbe.invokeEngineInitializeOperation+0)
```

Register ASCII fragments include:

```text
] Failed
 to crea
legate k
ernel: N
ch runti
me found
```

This is consistent with:

```text
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

## Native Library Matrix

| Library | Source | Build ID | Mapped in tombstone | Present in nativeLibraryDir/APK | Notes |
| --- | --- | --- | --- | --- | --- |
| `liblitertlm_jni.so` | Gallery SM8750 | `76e4dccd9c5f9cba468d9cae7becfec0` | true | true | JNI entry reaches `nativeCreateEngine` |
| `libLiteRt.so` | Gallery SM8750 | `869121bd7f4b0b77fa581218117a5c14` | true | true | Dispatch compatibility symbols present |
| `libLiteRtDispatch_Qualcomm.so` | Gallery SM8750 | `643ad77b8ac2f54bd1b61e4133c77b3a` | true | true | Exports `LiteRtDispatchGetApi@@VERS_1.0` |
| `libQnnSystem.so` | Gallery SM8750 | `0d409cdd664b8b0a` | true | true | QNN system mapped |
| `libQnnHtp.so` | Gallery SM8750 | `f2c90c1775a109e1` | true | true | QNN HTP mapped |
| `libQnnHtpPrepare.so` | Gallery SM8750 | `9ae62cf17f972404` | false | true | Present but not clearly mapped in latest tombstone |
| `libQnnHtpV79Stub.so` | Gallery SM8750 | `10d7ad6f9195411a` | false | true | Present but not clearly mapped in latest tombstone |
| `libQnnHtpV79Skel.so` | Gallery SM8750 | none | false | true | Present but not clearly mapped in latest tombstone |
| `libLiteRtRuntimeCApi.so` | none | n/a | false | false | Not present; current evidence does not indicate it is required |
| `libllm_inference_engine_jni.so` | app dependency | `2f6f9104344966674bf6587935d27cc8` | true | true | Also mapped |

## Java/Native API Surface Finding

The initial Gallery native stack experiment used Maven `litertlm-android:0.10.0` Java classes and crashed with `SIGSEGV`:

```text
libart.so CheckJNI::GetStringCharsInternal
liblitertlm_jni.so Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+816
```

Static API comparison found that Gallery JNI expects the Maven `0.11.0` `nativeCreateEngine` descriptor:

```text
(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;ZLjava/lang/Boolean;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)J
```

Maven `0.10.0` uses a different descriptor:

```text
(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;ZZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;II)J
```

After changing `galleryStackExperimentDebug` to Maven `litertlm-android:0.11.0`:

- Java/native descriptor match: true
- `EngineConfig` constructor selected: `EngineConfig(String, Backend, Backend, Backend, Integer, Integer, String)`
- the `CheckJNI::GetStringCharsInternal` `SIGSEGV` disappeared
- the remaining failure is now a `SIGABRT` consistent with no usable dispatch runtime

## Analysis / Hypothesis

The current evidence points to dispatch runtime compatibility/capability negotiation rather than simple file absence.

Evidence against plain file absence:

- `libLiteRtDispatch_Qualcomm.so` is present in `nativeLibraryDir`.
- `libLiteRtDispatch_Qualcomm.so` is mapped in the tombstone.
- `libLiteRt.so` is mapped.
- `libQnnSystem.so` and `libQnnHtp.so` are mapped.

Evidence against `libLiteRtRuntimeCApi.so` as the primary cause:

- Gallery SM8750 APK does not contain `libLiteRtRuntimeCApi.so`.
- `galleryStackExperimentDebug` does not contain it.
- static scan found no `LiteRtRuntimeCApi` / `libLiteRtRuntimeCApi.so` string hits.
- `readelf -d` found no `NEEDED` edge to it.
- dispatch undefined `LiteRt*` symbols appear to resolve through `libLiteRt.so`.

QNN/ADSP path remains possible but currently weaker:

- dispatch strings contain `ADSP_LIBRARY_PATH`, `LD_LIBRARY_PATH`, QNN library loading, and QNN version mismatch messages.
- however, latest tombstone/logcat did not show direct missing QNN library, ADSP path, or version mismatch messages.

Most likely cause at the moment:

1. dispatch runtime compatibility / capability mismatch between the Gallery SM8750 native stack and the model/runtime context used by `Engine.initialize`, or
2. SM8750 Qualcomm dispatch runtime capability not recognized as usable through this third-party app path, or
3. a model/runtime/schema compatibility condition that currently surfaces only as `No usable Dispatch runtime found`.

## Questions for Maintainers

1. Is `libLiteRtDispatch_Qualcomm.so` from the Gallery SM8750 APK intended to be reusable by third-party LiteRT-LM Android apps?
2. Which Maven `litertlm-android` version or source tag exactly matches the Gallery SM8750 native stack listed above?
3. Are additional libraries, assets, or environment variables required for Qualcomm SM8750 NPU on Android, such as `ADSP_LIBRARY_PATH` / HTP skel/stub search path setup?
4. Is `gemma-4-E2B-it_qualcomm_sm8750.litertlm` expected to run through `Backend.NPU(nativeLibraryDir)` in third-party Android apps?
5. Should `Engine.initialize()` return a Java/Kotlin exception instead of aborting the process when no usable dispatch runtime is found?
6. Is there an official distribution channel for the Qualcomm dispatch runtime matching `litertlm-android:0.11.0`?
7. Is there a known compatibility issue with SM8750 / Android 16 / Hexagon V79 dispatch runtime capability detection?
8. Does `No usable Dispatch runtime found` in this stack usually mean:
   - dispatch API version mismatch,
   - insufficient capabilities,
   - QNN library/version/path issue,
   - model/runtime schema mismatch, or
   - unsupported SoC/model compiled graph?

## Attachments / Artifacts

Relevant local artifact directories:

- `artifacts/gallery_dispatch_requirements/20260516_210635/`
- `artifacts/npu_diagnostics/20260516_210643_gallerynpu/`
- `artifacts/litertlm_api_surface_compare/20260516_201159/`
- `artifacts/litertlm_flavor_dependencies/20260516_204821/`

These include:

- static `strings` / `readelf` / `nm` summaries
- tombstone/dropbox/logcat extracts
- stage files and crash marker
- loaded library matrix
- Java API surface descriptor comparison
- Gradle dependency flavor matrix

## Safety Note

No NPU generation was attempted.

The isolated dry-run only reaches `Engine.initialize()`. It does not create `Conversation` or `Session`, does not call `generateResponse`, does not evaluate a prompt, and does not wire `Backend.NPU` into normal app inference. Normal `standardDebug` GPU inference remains separate and working.
