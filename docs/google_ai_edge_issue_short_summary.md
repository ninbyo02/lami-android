# Short Summary: LiteRT-LM SM8750 NPU Engine.initialize SIGABRT

Recommended main repo: `google-ai-edge/LiteRT-LM`.

Title candidate:

`[Android][SM8750][Backend.NPU] Engine.initialize SIGABRT: No usable Dispatch runtime found with Gallery native stack`

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
- frame: `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine+1668`
- no direct tombstone `Abort message`
- register fragments match `Failed to create a dispatch delegate kernel: No usable Dispatch runtime found`

Current classification:

- primary: `no-usable-dispatch-runtime`
- likely underlying: `dispatch-runtime-compatibility-mismatch`
- confidence: medium

What seems unlikely:

- `libLiteRtRuntimeCApi.so` missing: weak evidence
- QNN/ADSP path problem: possible, but no direct missing-library/path/version log

Maintainer ask:

- Which `litertlm-android` Maven artifact or source tag matches Gallery SM8750 native stack?
- Is Gallery `libLiteRtDispatch_Qualcomm.so` intended for external app reuse?
- What is the supported Qualcomm dispatch runtime distribution/build path for Android SM8750?
- Should `Engine.initialize()` return a Java exception instead of aborting for this class?
