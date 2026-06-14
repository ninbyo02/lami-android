# Runtime Stack Difference Matrix

Scope: static comparison only. This document records the runtime/native stack
evidence behind the current GPU callback corruption investigation. It does not
propose changing production routing, callback joining, CPU, NPU, or fallback
behavior.

## Current Behavioral Baseline

| App / flavor | Model | Backend | Result | Interpretation |
|---|---:|---|---|---|
| Edge Gallery official app | Edge Gallery E2B | GPU | Long Japanese output is visually normal | GPU route reaches a quality-preserving runtime/executor path. |
| Lami `standardDebug` | same Edge Gallery E2B | GPU | `cc:735` compiled model invoke failure | Standard runtime stack cannot invoke this GPU path. |
| Lami `standardGpuMinimalRuntimeCandidateDebug` | same Edge Gallery E2B | GPU | Short output succeeds, long output corrupts in raw callback | Minimal runtime pair fixes invoke failure but not quality. |
| Lami CPU route | generic / Edge Gallery E2B | CPU | Long output succeeds after holder identity separation | Model artifact is not the primary quality root cause. |

## Native Library Matrix

Concrete runtime identity is represented by SHA-256, size, and build-id because
semantic LiteRT/LiteRT-LM/QNN version strings were not reliably recovered from
the APK static artifacts.

| Library | Edge Gallery APK | Lami `standardDebug` | Lami successful minimal / alignment probe | Difference / risk |
|---|---|---|---|---|
| `libLiteRt.so` | present, size `4,981,376`, sha `1b27b3f8c107c9e9a4c9fcf8f9fe05d33b5bcc941fd5a6030d2d38cfba207aed`, build-id `d61cffe2701624c903ec98a7b0de243d` | present, size `5,405,080`, sha `84e2d8a90490ddd7948f3922caaca521554d3f32675476bf5dc78d0b699b1553` | present, size `5,046,960`, sha `31b3c86cefaa0838a234af1bdff8831be4cff438c501afb9b9d50460fe83ed24` | All three differ. Minimal pair success shows Lami standard `libLiteRt.so` is part of the invoke-failure delta, but Edge Gallery still differs from minimal pair. |
| `liblitertlm_jni.so` | present, size `20,019,664`, sha `49ca8596e404dab468cbaa493e571f9e26d210815dc95e6bab89c3ee6e9afbb6`, build-id `3c7bc4eaf78db75989233163d72977eb` | present, size `55,249,224`, sha `b6d5666bfe4abd10593eb46e74cdc2f695468d58980b6d5118c3713c89aa083c` | present, size `15,370,288`, sha `ac97fd1a7e3755eb77127599928011a7ecd75f3170749f034f568de1e0d27b6f` | Highest-risk delta. This library contains LiteRT-LM executor selection, backend constraints, callback/decode code, and artisan executor strings. |
| `libLiteRtDispatch_Qualcomm.so` | present in Qualcomm NPU split, size `429,704`, sha `fadf7f0a1b4add782745a3647ee3daf0da506e2eebd69d891b53b73d714f4ca9` | present, size `691,184`, sha `7d300c509ab102ea84e10babee0c31e084ee7a0e59de99ef6d8dcaa8fe6dc34f` | absent | Not required for the observed generic GPU success in minimal/alignment probe. Still relevant for NPU/Qualcomm dispatch, not the current GPU quality blocker. |
| `libLiteRtCompilerPlugin_Qualcomm.so` | not observed in Edge Gallery static inventory | present, size `1,002,320`, sha `c56cc705d0abfdd17bc5bf3bad555e1554aac38a711d3c35960bad4508b0464c` | absent | Not required for minimal GPU invoke success. Treat as outside the current generic GPU success core. |
| `libGemmaModelConstraintProvider.so` | not observed in Edge Gallery static inventory | present, size `20,092,072`, sha `45ca07f36882c57f6ec493a8861d082f6e66c8ae4e5b2546ef9458315d4081f6` | absent | Standard `liblitertlm_jni.so` depends on this provider; successful minimal/alignment JNI does not. This is a major runtime graph/constraint selection difference. |

## Dependency Shape

| Flavor | `liblitertlm_jni.so` notable needed libraries | Interpretation |
|---|---|---|
| `standardDebug` | includes `libGemmaModelConstraintProvider.so` | Standard path uses a larger constraint-provider-linked JNI runtime and fails GPU invoke at `llm_litert_compiled_model_executor.cc:735`. |
| `gpuRuntimeAlignmentProbeDebug` / minimal candidate | includes `libLiteRt.so`, does not include `libGemmaModelConstraintProvider.so` | Minimal pair is internally aligned enough to invoke GPU generation, but still emits corrupted long raw callbacks. |
| Edge Gallery APK | `libLiteRt.so` + `liblitertlm_jni.so`; Qualcomm dispatch in separate split | Edge Gallery runtime is neither standardDebug nor minimal-probe identical. It also contains native artisan executor evidence. |

## Version String Findings

| Item | Static result |
|---|---|
| LiteRT-LM semantic version | Not reliably recovered from Edge Gallery APK static artifacts. Lami build artifacts have referenced `litertlm-android-0.11.0`, but the APK SHA/build-id identity is more reliable for this investigation. |
| LiteRT semantic version | Not reliably recovered from APK strings. |
| QNN runtime semantic version | Not reliably recovered from Edge Gallery APK strings. QNN HTP/V79 libraries are identifiable by filename, SHA, size, and build-id. |

Existing repository docs contain older Gallery/SM8750 experiment build IDs. This
matrix intentionally uses the current `artifacts/edge_gallery_static` extraction
as the Edge Gallery APK baseline. Treat older Gallery build IDs as separate
runtime generations unless the source APK and split set are explicitly matched.

## JNI / Callback Entry Points

Both Edge Gallery's `liblitertlm_jni.so` and Lami's successful minimal pair
export the same public JNI entry-point family:

- `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateEngine`
- `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeCreateConversation`
- `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeGenerateContent`
- `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeGenerateContentStream`
- `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeRunPrefill`
- `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeRunDecode`
- `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeSendMessage`
- `Java_com_google_ai_edge_litertlm_LiteRtLmJni_nativeSendMessageAsync`

This means the visible JNI ABI alone does not explain the quality difference.
The remaining difference is which native executor/runtime configuration those
entry points select and how the callback stream is produced internally.

## Runtime-Level Conclusions

1. `standardDebug` failure is a runtime/native stack issue, not model identity:
   the same Edge Gallery E2B model fails with `cc:735` in standard but succeeds
   in the minimal/alignment runtime pair.
2. The minimal pair (`libLiteRt.so` + `liblitertlm_jni.so`) is sufficient to
   cross the compiled-model invoke barrier, but not sufficient to match Edge
   Gallery output quality.
3. Edge Gallery's working GPU route is not proven equivalent to Lami public
   `Backend.GPU`. Edge Gallery native strings include `GPU_ARTISAN`,
   `LlmGpuArtisanExecutor`, backend constraints, preferred engine types, and GPU
   KV-cache references.
4. The current quality blocker is after GPU invoke success: long generation
   corrupts at raw callback source in Lami, while Edge Gallery GPU and Lami CPU
   produce normal long Japanese output.
