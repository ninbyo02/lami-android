# Backend.NPU runtime stack mismatch investigation

Date: 2026-06-02

## Scope

This pass investigates the `Backend.NPU` `Engine.initialize` native crash by static comparison only.

It did not install an APK, launch the app, run `Engine.initialize`, replace native libraries, change QAIRT/QNN settings, change fallback policy, or connect anything to the production ChatScreen / S1-S5 route.

Generated artifact:

```text
artifacts/backend_npu_runtime_stack_mismatch/20260602_055448/
```

Key generated files:

- `lib_inventory.tsv`
- `gallery_comparison.tsv`
- `model/model_inventory.txt`
- `model/model_strings.filtered.txt`
- `strings/`
- `dynamic/`
- `symbols/`
- `diff/`

## Background

`Backend.NPU` attach probe variants all still crash inside `liblitertlm_jni.so` `nativeCreateEngine` during `Engine.initialize`:

- `default`: SIGABRT
- `max32`: SIGABRT
- `cache-files`: SIGABRT
- `backend-only`: SIGABRT

This makes an `EngineConfig` argument-level fix unlikely. The tombstone also showed `qnn_partition_0`, so the current leading candidate is a runtime/model stack mismatch rather than cache directory or token limit configuration.

## Compared inputs

`npuExperiment` source:

```text
app/build/outputs/apk/npuExperiment/debug/app-npuExperiment-debug.apk
```

Gallery SM8750 reference source:

```text
artifacts/gallery_dispatch_requirements/20260516_210635/gallery_stack/
```

Model source:

```text
/home/sato/Downloads/gemma-4-E2B-it_qualcomm_sm8750.litertlm
```

Model hash:

```text
41dd675fbe735b6029012b5576a5716bac614fd8156de0128db4c9dff3cebd4e
```

## Summary result

Static comparison covered 11 target libraries.

Result:

- `suspected_root_cause=runtime_stack_mismatch_candidate`
- build-id mismatch or missing count: `8`
- `npuExperiment` missing count: `3`
- Gallery reference missing count: `2`
- model contains `qnn_partition_0` and `qnn_partition_1`

## Library comparison

| Library | npuExperiment | Gallery SM8750 | Reading |
| --- | --- | --- | --- |
| `liblitertlm_jni.so` | Build ID `ecacedccf835d7674c95bd40186d0fde` | Build ID `76e4dccd9c5f9cba468d9cae7becfec0` | Different JNI/nativeCreateEngine implementation generation. Strong mismatch candidate. |
| `libLiteRt.so` | missing from APK inventory | Build ID `869121bd7f4b0b77fa581218117a5c14` | Dispatch `NEEDED` references `libLiteRt.so`, but the npu APK inventory did not expose a matching packaged file. Strong mismatch/load candidate. |
| `libLiteRtDispatch_Qualcomm.so` | Build ID `643ad77b8ac2f54bd1b61e4133c77b3a` | same Build ID | Dispatch binary itself matches Gallery. Dispatch alone is not the mismatch. |
| `libLiteRtCompilerPlugin_Qualcomm.so` | missing | missing | Not a direct Gallery delta in this comparison. |
| `libQnnHtp.so` | Build ID `e227353d86be672b` | Build ID `f2c90c1775a109e1` | QNN HTP runtime generation differs. Strong mismatch candidate. |
| `libQnnSystem.so` | Build ID `94d63184c6b1f968` | Build ID `0d409cdd664b8b0a` | QNN system runtime generation differs. Strong mismatch candidate. |
| `libQnnHtpPrepare.so` | Build ID `9ae62cf17f972404` | same Build ID | This one matches. |
| `libQnnHtpV79Skel.so` | present, different size/hash | present | DSP skel payload differs. Strong V79 payload mismatch candidate even though GNU Build ID is blank. |
| `libQnnHtpV79Stub.so` | Build ID `c079c75e0fd8ee92` | Build ID `10d7ad6f9195411a` | V79 host stub differs. Strong mismatch candidate. |
| `libGemmaModelConstraintProvider.so` | missing | missing | Not a Gallery delta here. |
| `libllm_inference_engine_jni.so` | Build ID `2f6f9104344966674bf6587935d27cc8` | same Build ID | This library matches and is less likely as the primary cause. |

## Model evidence

The local SM8750 model is a raw data file rather than a zip container:

```text
/home/sato/Downloads/gemma-4-E2B-it_qualcomm_sm8750.litertlm: data
```

Static binary scan found:

```text
qnn_partition_0,qnn_partition_1
```

This supports the tombstone observation that `Engine.initialize` reaches a Qualcomm/QNN partitioned payload path. It does not prove which native library rejects the model, but it raises the likelihood that the model expects a specific LiteRT/QNN/V79 runtime generation.

## Root cause candidates

### 1. Mixed LiteRT-LM JNI and Gallery dispatch/runtime generation

`npuExperiment` uses Gallery-matching `libLiteRtDispatch_Qualcomm.so`, but `liblitertlm_jni.so` differs from Gallery. Since the crash is inside `nativeCreateEngine`, this mismatch is a strong candidate.

### 2. `libLiteRt.so` packaging/load mismatch

The npu APK comparison did not find `libLiteRt.so`, while Gallery has Build ID `869121bd7f4b0b77fa581218117a5c14`. The dispatch library has `NEEDED` dependency on `libLiteRt.so`. A missing or differently sourced `libLiteRt.so` can explain a late native abort once dispatch/runtime paths are used.

### 3. QNN System/HTP/V79 generation mismatch

`libQnnSystem.so`, `libQnnHtp.so`, `libQnnHtpV79Stub.so`, and `libQnnHtpV79Skel.so` all differ from Gallery. This matches the current failure shape: `qnn_partition_0` appears, and all EngineConfig variants crash consistently.

### 4. Model/runtime schema mismatch

The model contains QNN partitions. If the model was compiled for the Gallery SM8750 runtime stack, using a different QNN System/HTP/V79 stack can cause initialization failure before any token generation.

## Current conclusion

`EngineConfig` variants did not change the crash outcome, and static comparison shows a mixed stack:

- Gallery dispatch: present and matching.
- Gallery LiteRT runtime: not present in npu APK inventory.
- Gallery LiteRT-LM JNI: not matching.
- Gallery QNN System/HTP/V79 runtime: mostly not matching.
- QNN-partitioned SM8750 model: present.

The best current root cause candidate is:

```text
runtime_stack_mismatch_candidate
```

More specifically: `npuExperiment` appears to combine Gallery dispatch with non-Gallery LiteRT-LM JNI and non-Gallery QNN/HTP/V79 runtime pieces.

## Next minimal change to try

Do not change production ChatScreen, S1-S5, fallback policy, QAIRT/QNN global settings, or always-on library loading.

The next safe experiment should be a new explicit debug-only flavor/probe variant that stages a coherent native stack as one unit:

1. Start with a Gallery-aligned stack in an isolated source set:
   - `liblitertlm_jni.so`
   - `libLiteRt.so`
   - `libLiteRtDispatch_Qualcomm.so`
   - `libQnnSystem.so`
   - `libQnnHtp.so`
   - `libQnnHtpPrepare.so`
   - `libQnnHtpV79Stub.so`
   - `libQnnHtpV79Skel.so`
   - `libllm_inference_engine_jni.so`
2. Run only the existing dev-only Backend.NPU attach probe.
3. Keep `Engine.initialize` explicit opt-in.
4. Keep logcat/tombstone fallback collection.
5. Stop at `Engine.initialize` first; do not connect to ChatScreen or generation.

If that still aborts, the next smallest axis is model/runtime schema compatibility rather than EngineConfig.
