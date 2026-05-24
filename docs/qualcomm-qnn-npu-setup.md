# Qualcomm QNN/NPU setup

This app can request LiteRT-LM's Qualcomm NPU backend, but it must fall back to GPU until the local build contains the runtime libraries and a compatible NPU model.

## Current blockers

Recent device diagnostics showed:

- Device/SOC: `nubia NX733J`, `QTI SM8750`
- LiteRT-LM backend API: `Backend.NPU` is present
- Missing QAIRT runtime libraries: `libQnnSystem.so`, `libQnnHtp.so`, `libQnnHtpPrepare.so`, `libQnnHtpV*.so`
- Missing LiteRT Qualcomm dispatch API `.so`
- Model requirement is still unresolved: use a SOC-specific LiteRT-LM model before enabling NPU as a default path

## Local library drop

Place licensed local test libraries under:

```text
app/src/main/jniLibs/arm64-v8a/
```

Do not commit vendor binaries. The repository ignores `*.so` files in this directory.

Minimum expected candidates:

```text
libQnnSystem.so
libQnnHtp.so
libQnnHtpPrepare.so
libQnnHtpV*.so
<litert/qnn/qualcomm dispatch api>.so
```

The dispatch API library is produced by the LiteRT-LM Qualcomm dispatch build. The app-side probe treats `libLiteRtDispatch_Qualcomm.so` as the exact match and reports other `.so` names containing `dispatch`, `LiteRtDispatch`, `qnn`, or `qualcomm` as diagnostic candidates.

You can also copy local QAIRT libraries with Gradle:

```bash
QAIRT_ROOT=/path/to/qairt/version \
LITERT_QUALCOMM_DISPATCH_SO=/path/to/libLiteRtQualcommDispatch.so \
./gradlew :app:copyQnnNpuNativeLibsFromQairt
```

If you prefer a Gradle property for the dispatch library:

```bash
QAIRT_ROOT=/path/to/qairt/version \
./gradlew :app:copyQnnNpuNativeLibsFromQairt \
  -PlitertQualcommDispatchSo=/path/to/libLiteRtQualcommDispatch.so
```

## Verification

Check local packaging readiness:

```bash
./gradlew :app:printQnnNpuNativeLibStatus
```

Check library and model readiness together:

```bash
./gradlew :app:printQnnNpuReadiness \
  -PqnnNpuModelPath=/path/to/gemma3-1b-sm8750-qnn-npu.litertlm
```

`QNN_NPU_MODEL_PATH` can be used instead of the Gradle property.

Install the app:

```bash
./update.sh update
```

Then run a local inference with inference stats display set to developer mode. The target signs are:

```text
LiteRT-LM NPU runtime lib status: candidate-detected-qairt
LiteRT-LM NPU dispatch lib status: candidate-detected
QNN/NPU試行: yes
Applied preferredBackend: NPU
```

Do not treat NPU as ready from app diagnostics alone. First prove the same model can run with LiteRT-LM's command-line NPU path on the device.

The app also blocks Qualcomm NPU selection unless the selected `.litertlm` filename looks like an NPU/SOC-specific candidate. Current accepted markers are:

```text
qualcomm
qnn
npu
sm8750
snapdragon
htp
```

This is intentionally conservative. A generic model such as `gemma-4-E4B-it.litertlm` should continue to use GPU until an NPU-specific model is selected and CLI proof exists.

## External prerequisites

Use the official LiteRT-LM NPU instructions as the source of truth:

- Download/extract the Qualcomm AI Runtime SDK and set `QAIRT_ROOT`
- Build LiteRT-LM runtime for Android arm64
- Build the Qualcomm dispatch API `.so`
- Push the model, QAIRT libraries, `litert_lm_main`, and dispatch API to the device
- Run `litert_lm_main --device=qualcomm_npu`

Only after CLI NPU proof should the app path be considered for default enablement.
