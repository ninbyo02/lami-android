# Short Summary: LiteRT-LM SM8750 NPU Engine.initialize SIGABRT

Recommended main repo: `google-ai-edge/LiteRT-LM`.

Title candidate:

`[Android][SM8750][Backend.NPU] Engine.initialize SIGABRT: No usable Dispatch runtime found with same-source custom stack`

Environment:

- Device: Nubia Z70S Ultra / `NX733J`
- SoC: QTI `SM8750`, hardware `qcom`, GPU Adreno 830
- Android SDK: 36, ABI: `arm64-v8a`
- External QAIRT/QNN validation: GPU passed, DSP/HTP passed, DSP core Hexagon V79
- Model: `gemma-4-E2B-it_qualcomm_sm8750.litertlm`
- Model size: `3016294400` bytes, exists and readable

Isolated app variant:

- `galleryStackExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.gallerynpu`
- Java API: `litertlm-android:0.11.0`
- Native stack: Gallery SM8750 APK `1.0.12`

Second isolated variant:

- `customBuildExperimentDebug`
- applicationId: `io.github.ninbyo02.lami.customnpu`
- Java API: `litertlm-android:0.11.0`
- Native stack: same-source/tag custom build from LiteRT-LM `v0.11.0` and pinned LiteRT `47615eb6eaec25e8dfcd1aba922c560a57cba0a2`

Key native Build IDs:

- `liblitertlm_jni.so`: `76e4dccd9c5f9cba468d9cae7becfec0`
- `libLiteRt.so`: `869121bd7f4b0b77fa581218117a5c14`
- `libLiteRtDispatch_Qualcomm.so`: `643ad77b8ac2f54bd1b61e4133c77b3a`
- `libQnnSystem.so`: `0d409cdd664b8b0a`
- `libQnnHtp.so`: `f2c90c1775a109e1`
- `libQnnHtpPrepare.so`: `9ae62cf17f972404`
- `libQnnHtpV79Stub.so`: `10d7ad6f9195411a`

What succeeds before crash:

- `Backend.NPU(String nativeLibraryDir)`: success
- `EngineConfig.backend = Backend.NPU`: dry-build success
- `Engine(EngineConfig)`: returned
- Java/native `nativeCreateEngine` descriptor: matches Gallery/Maven `0.11.0`
- prior CheckJNI `SIGSEGV`: resolved

Current failure:

- `Engine.initialize()` explicit dry-run only
- signal: `SIGABRT`
- Gallery frame: `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1668`
- custom stack frame: `DispatchDelegate::CreateDelegateKernelInterface()+312`
- no direct tombstone `Abort message`
- register fragments match `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`

Current classification:

- primary: `no-usable-dispatch-runtime`
- likely underlying: `dispatch-runtime-compatibility-mismatch`
- confidence: medium

Additional source/version findings:

- same-source/tag LiteRT-LM `v0.11.0` + pinned LiteRT custom stack also failed at `Engine.initialize`
- failure frame: `DispatchDelegate::CreateDelegateKernelInterface()+312`
- custom built stack includes `libLiteRt.so`, `libLiteRtDispatch_Qualcomm.so`, `liblitertlm_jni.so`, `libLiteRtCompilerPlugin_Qualcomm.so`, and `libGemmaModelConstraintProvider.so`
- exact QAIRT `2.44.0.260225` was obtained through QPM and used for the latest limited rebuild
- latest qairt244 artifact: `artifacts/litert_custom_build/20260517_230448_qairt244`
- latest diagnostics: `artifacts/npu_diagnostics/20260521_074641_customnpu/`
- qairt244 tombstone maps `liblitertlm_jni.so` and `libGemmaModelConstraintProvider.so`, but not `libLiteRtDispatch_Qualcomm.so` or QNN/HTP libraries before abort
- model metadata contains `DISPATCH_OP`, `qnn_partition_*`, `soc_type=SM8750`, `min_arch=79`, and `v2.44.0.260225143659`
- Java/native ABI mismatch is no longer likely
- missing dispatch `.so` is no longer likely
- missing `libLiteRt.so` is no longer likely
- QNN Build IDs differ across custom APK packaged QNN libs, Gallery QNN libs, and local QAIRT 2.46 libs
- public LiteRT metadata still points to QAIRT `2.44.0.260225`
- public LiteRT-LM `origin/main` pins a LiteRT ref that also points to QAIRT `2.44.0.260225`
- bounded search found no public QAIRT `2.46.0.260424` source/ref evidence

What seems unlikely:

- `libLiteRtRuntimeCApi.so` missing: weak evidence
- QNN/ADSP path problem: possible, but no direct missing-library/path/version log

Maintainer ask:

- Is QAIRT `2.44.0.260225` the expected SDK for public Qualcomm dispatch builds?
- Is there an official way to obtain QAIRT `2.44.0.260225`?
- Is there a public LiteRT/LiteRT-LM source/ref for QAIRT `2.46.0.260424`?
- Is `No usable Dispatch runtime found` expected when QNN/QAIRT generation does not match?
- Which QNN libs should be packaged for SM8750/V79 `Backend.NPU` Android apps?
- Which `litertlm-android` Maven artifact or source tag matches Gallery SM8750 native stack?
- Is Gallery `libLiteRtDispatch_Qualcomm.so` intended for external app reuse?
- What is the supported Qualcomm dispatch runtime distribution/build path for Android SM8750?
- Should `Engine.initialize()` return a Java exception instead of aborting for this class?
