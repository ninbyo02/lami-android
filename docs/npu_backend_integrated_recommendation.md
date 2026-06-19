# NPU Backend integrated recommendation

Date: 2026-06-02

Scope: integration of the three investigation reports only. No production
ChatScreen, S1-S5, fallback setting, QAIRT/QNN setting, native library, Gradle,
or app code was changed.

## Input reports

- `docs/npu_backend_official_findings.md`
- `docs/npu_backend_lami_gallery_delta.md`
- `docs/compiled_model_api_sm8750_plan.md`

## Integrated findings

### 1. SM8750 NPU support exists, but not as a generic app switch

Google's LiteRT documentation lists Snapdragon 8 Elite / SM8750 as a supported
Qualcomm AI Engine Direct / QNN target through the `CompiledModel` API. The
LiteRT-LM NPU guide also documents Qualcomm NPU execution, but that path is
tightly coupled to a SoC-specific `.litertlm` model, QAIRT/QNN runtime
libraries, LiteRT dispatch, HTP V79 stub/skel payloads, and the runtime library
search path.

Conclusion: SM8750 NPU is a real supported target, but `Backend.NPU` should not
be treated as a small configuration toggle for arbitrary `.litertlm` chat
models.

### 2. The old Lami-vs-Gallery mismatch is mostly closed

For the `galleryAlignedNpuProbe` line, the prior Java/native descriptor problem
is no longer the leading explanation: the aligned variant uses LiteRT-LM
`0.11.0`, which matches the Gallery JNI API shape identified in the existing
docs.

The selected core native stack also matches the Gallery reference by SHA for
the important pieces:

- `liblitertlm_jni.so`
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libQnnSystem.so`
- `libQnnHtp.so`
- `libQnnHtpPrepare.so`
- `libQnnHtpV79Stub.so`
- `libQnnHtpV79Skel.so`
- `libllm_inference_engine_jni.so`

Conclusion: if `Engine.initialize` still SIGABRTs with that aligned stack, the
highest-value search area is no longer a simple same-name native library byte
mismatch.

### 3. Remaining Backend.NPU suspects are below EngineConfig

The strongest remaining candidates are:

- Android manifest or linker namespace differences that affect vendor
  FastRPC/CDSP visibility.
- `libcdsprpc.so` / FastRPC lookup or namespace failures from the HTP V79 path.
- Full APK native inventory differences beyond the matched core stack.
- QNN HTP initialization constraints, including platform config, VTCM, secure
  PD, memory registration, or runtime capability checks.
- Model/runtime schema coupling for the SM8750 QNN partitions embedded in the
  `.litertlm` file.

`cacheDir`, `/data/user/0` versus `/data/data`, `maxNumTokens`, and
`maxNumImages` remain safe probe axes, but they are lower-priority after
repeated aborts across Gallery-like variants.

### 4. CompiledModel is a separate proof route, not a chat replacement

LiteRT `CompiledModel` uses `.tflite` or AOT-compiled LiteRT models and exposes
tensor input/output. LiteRT-LM `Backend.NPU` uses `.litertlm` LLM packages and
the `Engine` / `Conversation` surface.

Conclusion: `CompiledModel` cannot directly replace the Lami chat route, but it
is the cleanest way to answer a narrower question: can this app reach Qualcomm
QNN/HTP on the SM8750 device at all, independent of LiteRT-LM, tokenization,
conversation state, and `.litertlm` model metadata?

## 今後の推奨ルート

### Recommended route A: keep Backend.NPU frozen as diagnostic-only

Do not promote `Backend.NPU` into production ChatScreen or S1-S5. Do not change
fallback policy, QAIRT/QNN settings, or staged libraries.

Use the existing explicit opt-in probe only for further `Backend.NPU`
investigation. The next useful checks are non-destructive:

1. Re-run the Gallery/Lami static delta with a valid Gallery APK so manifest,
   assets, `extractNativeLibs`, meta-data, process declarations, and full
   `lib/arm64-v8a` inventory are captured from the real APK.
2. Compare Gallery and Lami manifests specifically for native library
   visibility and process/linker namespace relevant to FastRPC/CDSP.
3. Run only existing opt-in probe variants and collect tombstone/logcat evidence
   around `QnnManager::Init`, HTP backend init, `libcdsprpc.so`,
   `LiteRtDispatchCheckRuntimeCompatibility`, and `No usable Dispatch runtime`.

Decision gate: resume Backend.NPU implementation work only if the failure is
reduced to a concrete, reproducible namespace/runtime/model mismatch with a
debug-only fix that does not affect production routing.

### Recommended route B: start an independent CompiledModel smoke test design

In parallel or next, design a new debug-only `CompiledModel` `.tflite` smoke
test. Start with NPU-only, no fallback, and a small official LiteRT sample
model. Record accelerator options, LiteRT version, model hash, runtime library
build IDs, init time, run time, output checksum, and QNN/HTP log markers.

This route should answer one bounded question:

```text
Can Lami initialize and run any LiteRT model through Qualcomm QNN/HTP on SM8750?
```

If yes, Lami gains a clean hardware/runtime proof independent of LiteRT-LM. If
no, the failure is likely a broader LiteRT/QNN/Android packaging problem rather
than a `.litertlm` or `Engine.initialize`-specific issue.

### Recommended route C: keep current working NPU-like route gated

Existing QAIRT/QNN diagnostic and hidden experimental work should remain
isolated under its current gates. It should not be used to justify production
Backend.NPU promotion unless it proves stable repeated initialization,
deterministic accelerator evidence, no fallback ambiguity, and safe teardown.

## Final decision

The recommended next route is:

```text
1. Do not change production ChatScreen, S1-S5, fallback, QAIRT/QNN settings, or libs.
2. Treat LiteRT-LM Backend.NPU as blocked pending namespace/runtime/model proof.
3. Perform one more static Gallery APK delta focused on manifest/linker/native inventory.
4. Separately design a debug-only LiteRT CompiledModel .tflite NPU smoke test.
5. Use the CompiledModel result to decide whether SM8750 QNN access is globally viable in Lami before returning to .litertlm chat NPU work.
```

This is the least risky path because it separates two problems that are
currently entangled: Qualcomm NPU reachability from Android, and LiteRT-LM
`.litertlm` chat runtime compatibility.
