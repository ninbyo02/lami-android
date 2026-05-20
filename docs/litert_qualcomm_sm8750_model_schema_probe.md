# LiteRT-LM Qualcomm SM8750 Model Schema Probe

Date: 2026-05-21

Scope: read-only metadata/container inspection of
`gemma-4-E2B-it_qualcomm_sm8750.litertlm`.

No inference was run. The model file was not modified.

## Inputs Checked

Device paths:

- `/data/local/tmp/gemma-4-E2B-it_qualcomm_sm8750.litertlm`: not probed; `adb devices` showed no connected device.
- app-private `run-as` copies: not probed; no connected device, so a safe read-only `run-as` check was unavailable.

Local copy:

- `/home/sato/Downloads/gemma-4-E2B-it_qualcomm_sm8750.litertlm`
- size: `3016294400` bytes
- sha256: `41dd675fbe735b6029012b5576a5716bac614fd8156de0128db4c9dff3cebd4e`
- mtime: `2026-05-10 19:16:59.883337791 +0900`
- mode: `-rw-rw-r--`
- `file`: `data`

## Container Format

Observed first bytes:

```text
00000000: 4c49 5445 5254 4c4d 0100 0000 0500 0000  LITERTLM........
```

The file starts with `LITERTLM`, not a ZIP, TAR, ELF, or plain TFLite magic.

Negative archive checks:

- `zipinfo -l`: end-of-central-directory signature not found.
- `tar -tf`: not a tar archive.

The header and early strings show a LiteRT-LM container with multiple named
sections, including:

- `tf_lite_end_of_audio`
- `tf_lite_audio_adapter`
- `tf_lite_audio_encoder_hw`
- `tf_lite_end_of_vision`
- `tf_lite_vision_adapter`
- `tf_lite_vision_encoder`
- `tf_lite_prefill_decode`
- `tf_lite_per_layer_embedder`
- `tf_lite_aux`
- `tf_lite_embedder`

## Bounded Metadata Strings

Observed target strings include:

```text
LITERTLM
CONVERSION_METADATA
LiteRtStamp
Qualcomm
SM8750
qnn_partition_0
qnn_partition_1
DISPATCH_OP
min_arch=79
soc_type=SM8750
training_mode=inference
v2.44.0.260225143659
Vrv2.44.0.260225143659.64312b4030
```

Keyword occurrence summary from bounded string extraction:

```text
10 CONVERSION_METADATA
2 DISPATCH_OP
1 LITERTLM
2 LiteRtStamp
3 Qualcomm
2 SM8750
8 min_arch=79
10 model_type
6 qnn_partition_0
6 qnn_partition_1
8 soc_type=SM8750
8 training_mode=inference
```

The probe also saw late strings for `schema`, `Schema`, `backend`, `Backend`,
`compiled`, `schemas`, and `schematic`; these were not decoded into structured
schema fields in this pass.

## Compiled Model Reading

Observed facts:

- The model contains `DISPATCH_OP`.
- The model contains `qnn_partition_0` and `qnn_partition_1`.
- The model contains `Qualcomm`, `SM8750`, `soc_type=SM8750`, and `min_arch=79`.
- The model contains explicit QAIRT/QNN generation-looking strings:
  `v2.44.0.260225143659` and
  `Vrv2.44.0.260225143659.64312b4030`.

Reading from those observed markers: this `.litertlm` includes Qualcomm/SM8750
dispatch/QNN partition metadata and appears to be a precompiled SM8750/V79
payload, not a generic model that depends on app-side runtime compilation from
scratch. It still requires a compatible LiteRT dispatch runtime and QNN/HTP
runtime to load and execute the dispatch partitions.

No model string in this bounded pass identified Gallery as a required runtime
payload. The explicit version marker aligns with QAIRT `2.44.0.260225`, which is
the generation already called out in prior Lami QAIRT notes.

## Gallery Handling Context

Existing docs record that Gallery SM8750 is a special native payload with
different Build IDs from both public Maven and the local/custom stacks. Prior
Gallery handling did not resolve the NPU failure after Java/JNI descriptor
alignment; the remaining failure stayed in the `No usable Dispatch runtime found`
class.

This model probe adds direct evidence that the model itself carries SM8750/V79
and QAIRT 2.44 generation markers. It does not prove Gallery-specific runtime
coupling.

## Limits

- No device-side copy was available because `adb` reported no device.
- No app-private copy was read.
- No full LiteRT-LM schema decode was performed.
- No inference, `Engine.initialize`, `Conversation`, `Session`, or generation
  path was run.
