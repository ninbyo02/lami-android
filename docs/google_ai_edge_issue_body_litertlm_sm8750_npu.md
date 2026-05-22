## Summary

On a Nubia Z70S Ultra / Snapdragon 8 Elite / SM8750 device running Android 16, `Engine.initialize()` crashes the process with `SIGABRT` when using LiteRT-LM Android `Backend.NPU(nativeLibraryDir)` with the Gemma 4 E2B Qualcomm SM8750 `.litertlm` model.

This still happens after two isolated debug-only paths:

1. Google AI Edge Gallery SM8750 native stack + `litertlm-android:0.11.0`
   Java/Kotlin API, with matching Java/native `LiteRtLmJni.nativeCreateEngine`
   descriptor.
2. Same-source/tag custom build from LiteRT-LM `v0.11.0` and its pinned LiteRT
   ref, staged as one stack in a separate `customBuildExperimentDebug` flavor.

The latest same-source/tag custom stack reaches dispatch delegate creation and
then aborts:

```text
DispatchDelegate::CreateDelegateKernelInterface()+312
Failed to create a dispatch delegate kernel: No usable Dispatch runtime found
```

Classification:

- primary: `no-usable-dispatch-runtime`
- likely underlying: `dispatch-runtime-compatibility-mismatch` or
  QAIRT/QNN generation/capability mismatch
- confidence: medium

Update after exact QAIRT 2.44 rebuild:

- Exact QAIRT `2.44.0.260225` was acquired through QPM and used for the limited
  custom rebuild.
- qairt244 artifact: `artifacts/litert_custom_build/20260517_230448_qairt244`
- qairt244 diagnostics: `artifacts/npu_diagnostics/20260521_074641_customnpu/`
- the isolated `customBuildExperimentDebug` `Engine.initialize` dry-run still
  aborts with `No usable Dispatch runtime found`
- tombstone mapping shows `liblitertlm_jni.so` and
  `libGemmaModelConstraintProvider.so` mapped before abort, but not
  `libLiteRtDispatch_Qualcomm.so` or QNN/HTP libraries
- local model metadata contains `DISPATCH_OP`, `qnn_partition_*`,
  `soc_type=SM8750`, `min_arch=79`, and `v2.44.0.260225143659`

Update after Android-native logcat diagnostics:

- added Android-only `__android_log_print(ANDROID_LOG_ERROR, "QAIRT244_DIAG", ...)`
  diagnostics with marker `qairt244_android_log_v1` around dispatch delegate
  creation, dispatch `dlopen`/`dlsym`, compatibility checks, Qualcomm dispatch
  init, and QNN manager load/init paths
- android-log build artifact:
  `artifacts/qairt244_android_log_build/20260521_210911`
- `customBuildExperimentDebug` initialize-only dry-run artifact:
  `artifacts/npu_diagnostics/20260521_211841_customnpu/`
- the process still aborts at `Engine.initialize`
- tombstone top frame is the rebuilt JNI library:
  `DispatchDelegate::CreateDelegateKernelInterface()+464`,
  BuildId `27bb6eaa5358f3c23f080cdd33023eac`
- no `QAIRT244_DIAG` / `qairt244_android_log_v1` lines were captured in
  logcat/dropbox/tombstone artifacts
- tombstone still maps `liblitertlm_jni.so` but not `libLiteRt.so`,
  `libLiteRtDispatch_Qualcomm.so`, or QNN/HTP libraries

Update after JNI-entry sentinel diagnostics:

- added an earlier Android-native sentinel at
  `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine`
- sentinel tag: `QAIRT244_SENTINEL`
- sentinel marker: `qairt244_jni_entry_v1`
- sentinel build artifact:
  `artifacts/qairt244_jni_sentinel_build/20260521_214511`
- initialize-only dry-run artifact:
  `artifacts/npu_diagnostics/20260521_215004_customnpu/`
- tombstone top frame is still:
  `DispatchDelegate::CreateDelegateKernelInterface()+464`
- tombstone BuildId is the sentinel JNI build:
  `8faff14dc850b7fb1986a300ac465fa4`
- the same tombstone includes
  `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1992`
- no `QAIRT244_SENTINEL`, `qairt244_jni_entry_v1`, `QAIRT244_DIAG`, or
  `qairt244_android_log_v1` lines were captured in the collected artifacts
- this suggests the current blocker includes native logcat visibility/capture;
  it does not look like the JNI entry is missing

Update after native file logger diagnostics:

- app-owned JNI smoke first proved native code can execute and write an
  app-private file while native logcat tags are not captured on this device
- added file-backed native diagnostics with marker `qairt244_native_file_v1`
  to `nativeCreateEngine` and the dispatch delegate initialization boundary
- native file logger build artifact:
  `artifacts/qairt244_native_file_logger_build/20260522_074639`
- initialize-only dry-run artifact:
  `artifacts/npu_diagnostics/20260522_074944_customnpu/`
- diagnostic file was written at:
  `/data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_native_diag.txt`
- native file confirms `nativeCreateEngine`, `ModelAssets::Create`,
  `EngineSettings::CreateDefault`, `SetLitertDispatchLibDir`,
  `EngineFactory::CreateDefault`, `DispatchDelegate::Initialize`, and
  `InitializeDispatchApi` were reached
- first concrete native failure:
  `LiteRtDispatchInitialize failure status=kLiteRtStatusErrorDynamicLoading(502)`
- `LiteRtDispatchCheckRuntimeCompatibility` is not reached in this log
- visible QNN/HTP/skel initialization is not reached in this log
- the process then aborts at
  `DispatchDelegate::CreateDelegateKernelInterface FATAL no usable dispatch runtime`

Update after preparing lower-level dlopen diagnostics:

- added file-backed dynamic loader trace marker `qairt244_dlopen_trace_v1`
  around dispatch candidate selection, raw `dlopen`, raw `dlerror`,
  `dlsym("LiteRtDispatchGetApi")`, and API version reporting
- dlopen trace build artifact:
  `artifacts/qairt244_dlopen_trace_build/20260522_083658`
- the connected-device dry-run has not been executed yet because no adb device
  was connected during the build attempt
- the next run should determine whether this is an Android linker namespace,
  wrong candidate path, missing transitive dependency, or dlsym/export issue

What I need from maintainers:

- the supported way to obtain or build a Qualcomm dispatch runtime matching LiteRT-LM Android,
- confirmation whether Gallery SM8750 native libraries are expected to work outside Gallery,
- guidance on whether this failure indicates dispatch API/capability mismatch, QNN/HTP path setup, or model/runtime support mismatch,
- guidance on the exact QAIRT/QNN SDK generation expected by public Qualcomm dispatch builds,
- and whether `Engine.initialize()` can return a Java/Kotlin exception instead of aborting the process for this failure class.

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

App variants used for isolation:

| Variant | applicationId | LiteRT-LM dependency | Purpose |
| --- | --- | --- | --- |
| `standardDebug` | `io.github.ninbyo02.lami` | `litertlm-android:0.11.0` | Normal app path; GPU inference works |
| `npuExperimentDebug` | `io.github.ninbyo02.lami.npu` | `litertlm-android:0.10.0` | Dispatch-only / NPU probe experiment |
| `galleryStackExperimentDebug` | `io.github.ninbyo02.lami.gallerynpu` | `litertlm-android:0.11.0` | Gallery SM8750 native stack isolation |
| `customBuildExperimentDebug` | `io.github.ninbyo02.lami.customnpu` | `litertlm-android:0.11.0` | same-source/tag custom stack isolation |

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

Same-source/tag custom native stack staged only in `customBuildExperimentDebug`:

| Library | Build ID | Notes |
| --- | --- | --- |
| `liblitertlm_jni.so` | `b78167f717866bbc1d9a981f01fb0334` | built from LiteRT-LM `v0.11.0` |
| `libLiteRt.so` | `a03032ad1eeefda446478aea308c2ed0` | built from LiteRT pinned ref `47615eb6eaec25e8dfcd1aba922c560a57cba0a2` with exact QAIRT 2.44 |
| `libLiteRtDispatch_Qualcomm.so` | `a8006da3bd9b4fdf5b7131f8d864b6ee` | same source/ref Qualcomm dispatch with exact QAIRT 2.44 |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `443391d4c4348191230b67a3ab8a6037` | same source/ref Qualcomm compiler plugin with exact QAIRT 2.44 |
| `libGemmaModelConstraintProvider.so` | `f9e5e73e668032550042319e43012011` | required by built JNI |

The current custom build uses exact QAIRT `2.44.0.260225`, matching the public
LiteRT metadata and the model's embedded `v2.44.0.260225143659` marker.

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

6. Same-source/tag custom build experiment:
   - built LiteRT-LM `v0.11.0` and its pinned LiteRT ref
     `47615eb6eaec25e8dfcd1aba922c560a57cba0a2`
   - generated a matched stack containing `liblitertlm_jni.so`, `libLiteRt.so`,
     `libLiteRtDispatch_Qualcomm.so`, `libLiteRtCompilerPlugin_Qualcomm.so`,
     and `libGemmaModelConstraintProvider.so`
   - the latest rebuild used exact QAIRT `2.44.0.260225`
   - isolated `customBuildExperimentDebug` still failed in `Engine.initialize`
     with `SIGABRT`
   - top frame: `DispatchDelegate::CreateDelegateKernelInterface()+312`
   - register fragments again matched
     `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`

7. QAIRT source/ref investigation:
   - LiteRT-LM `v0.11.0` pins LiteRT
     `47615eb6eaec25e8dfcd1aba922c560a57cba0a2`
   - that LiteRT ref expects QAIRT `2.44.0.260225`
   - exact QAIRT `2.44.0.260225` was acquired through QPM and is installed at
     `/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225`
   - searched public LiteRT / LiteRT-LM refs for QAIRT `2.46.0.260424`,
     `260424`, and `260424121129`
   - no public ref with QAIRT 2.46 evidence was found in the bounded search
   - public LiteRT `origin/main` and LiteRT-LM `origin/main` still appear to
     reference QAIRT `2.44.0.260225` metadata

8. QAIRT 2.44 exact rebuild:
   - exact QAIRT `2.44.0.260225` SDK was obtained through QPM
   - SDK path:
     `/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225`
   - limited rebuild artifact:
     `artifacts/litert_custom_build/20260517_230448_qairt244`
   - the isolated `customBuildExperimentDebug` dry-run reached
     `Engine.initialize`
   - `Engine.initialize` did not return and aborted with
     `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`
   - no `Conversation`, `Session`, `generateResponse`, normal UI NPU wiring, or
     single-token smoke test was run

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

`Engine.initialize()` should either initialize successfully or fail with a catchable Java/Kotlin exception containing the exact dispatch/QNN/runtime reason.

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

Latest same-source/tag custom stack top frame:

```text
liblitertlm_jni.so
(anonymous namespace)::DispatchDelegate::CreateDelegateKernelInterface()+312
BuildId: b78167f717866bbc1d9a981f01fb0334
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
- the same-source/tag custom stack also includes built dispatch, built
  `libLiteRt.so`, and built `liblitertlm_jni.so`, yet still fails at dispatch
  delegate kernel creation.

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

1. QAIRT/QNN generation or capability mismatch between the dispatch/runtime stack,
   packaged QNN libraries, model, and device runtime.
2. SM8750/V79 Qualcomm dispatch capability not recognized as usable through this
   third-party app path.
3. a model/runtime/schema compatibility condition that currently surfaces only as
   `No usable Dispatch runtime found`.
4. QNN/HTP skel/stub or ADSP path setup issue, although direct evidence for this
   remains weaker than the generation/capability mismatch evidence.

Additional source/version finding:

- public LiteRT metadata currently points to QAIRT `2.44.0.260225`
- no public LiteRT/LiteRT-LM ref with QAIRT `2.46.0.260424` evidence was found
- exact QAIRT `2.44.0.260225` rebuild is blocked locally until that SDK is acquired
- the previous same-source/tag build used a QAIRT 2.46 overlay and may still be
  affected by QNN/QAIRT generation coupling
- QNN Build IDs differ across custom APK packaged QNN libs, Gallery SM8750 QNN
  libs, and local QAIRT `2.46.0.260424` libs.

## Questions for Maintainers

1. Is QAIRT `2.44.0.260225` the expected SDK version for current public LiteRT Qualcomm dispatch builds?
2. Is there an official way to obtain QAIRT `2.44.0.260225` for this build?
3. Is there a public LiteRT / LiteRT-LM source ref compatible with QAIRT `2.46.0.260424`?
4. Is `No usable Dispatch runtime found` expected when QNN/QAIRT generation does not match?
5. Which QNN libraries should be packaged for SM8750/V79 `Backend.NPU` Android apps?
6. Is `gemma-4-E2B-it_qualcomm_sm8750.litertlm` expected to run through `Backend.NPU(nativeLibraryDir)` in third-party Android apps?
7. Should `Engine.initialize()` return a Java/Kotlin exception instead of aborting the process when no usable dispatch runtime is found?
8. Is `libLiteRtDispatch_Qualcomm.so` from the Gallery SM8750 APK intended to be reusable by third-party LiteRT-LM Android apps?
9. Which Maven `litertlm-android` version or source tag exactly matches the Gallery SM8750 native stack listed above?
10. Are additional libraries, assets, or environment variables required for Qualcomm SM8750 NPU on Android, such as `ADSP_LIBRARY_PATH` / HTP skel/stub search path setup?
11. Is there an official distribution channel for the Qualcomm dispatch runtime matching `litertlm-android:0.11.0`?
12. Is there a known compatibility issue with SM8750 / Android 16 / Hexagon V79 dispatch runtime capability detection?
13. Does `No usable Dispatch runtime found` in this stack usually mean:
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
- `artifacts/litert_qairt246_ref_search/20260517_062055/`
- `artifacts/qairt_qnn_coupling/20260517_012057/`
- `artifacts/qairt244_acquisition/20260517_074537/`
- `artifacts/npu_diagnostics/20260517_005032_customnpu/`
- `docs/litert_qairt246_ref_search_results.md`
- `docs/litert_custom_build_qairt244_compare.md`
- `docs/qairt_244_acquisition_notes.md`

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
