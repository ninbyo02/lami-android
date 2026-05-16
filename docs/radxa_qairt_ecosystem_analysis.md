# Radxa QAIRT Public Ecosystem Analysis

Date: 2026-05-17

## Scope

This document compares Radxa public Qualcomm NPU documentation with the current
Lami LiteRT-LM / LiteRT / QAIRT / QNN / SM8750 investigation.

No build was run, no app was installed, no native library was changed, and no
`Engine.initialize` or NPU inference was executed for this analysis.

## Radxa Sources Reviewed

- Dragon Q6A NPU development index: <https://docs.radxa.com/en/dragon/q6a/app-dev/npu-dev>
- Dragon Q6A QAIRT install: <https://docs.radxa.com/en/dragon/q6a/app-dev/npu-dev/qairt-install>
- Dragon Q6A QAIRT usage: <https://docs.radxa.com/en/dragon/q6a/app-dev/npu-dev/qairt-usage>
- Dragon Q6A fastrpc setup: <https://docs.radxa.com/en/dragon/q6a/app-dev/npu-dev/fastrpc-setup>
- AIRbox Q900 QAIRT guide: <https://docs.radxa.com/en/fogwise/airbox-q900/ai-dev/qairt-sdk>
- AIRbox Q900 QNN Execution Provider: <https://docs.radxa.com/en/fogwise/airbox-q900/ai-dev/qnn_onnxrt_execution_provider>

## Public Radxa QAIRT/QNN Assumptions

### Recommended QAIRT Version

Radxa's Dragon Q6A QAIRT install page explicitly recommends:

```text
QAIRT 2.42.0.251225
```

The examples repeatedly use:

```text
qairt/2.42.0.251225
```

This is older than both:

- LiteRT public Qualcomm metadata in the current LiteRT refs: `2.44.0.260225`
- local installed QAIRT: `2.46.0.260424`

### Install / Environment Pattern

Radxa's public pattern is:

```bash
export QAIRT_VERSION=2.42.0.251225
wget https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/${QAIRT_VERSION}/v${QAIRT_VERSION}.zip
unzip v${QAIRT_VERSION}.zip
cd qairt/${QAIRT_VERSION}
source bin/envsetup.sh
```

This matches the local assumption that QAIRT SDKs are versioned, standalone SDK
roots and should not be mixed by symlink overlays when exact generation matching
is required.

### NPU Runtime / fastrpc Prerequisites

Radxa treats NPU availability as a board/system runtime property. For Dragon Q6A
system image R2 or newer, the docs state that the NPU runtime environment is
pre-installed.

The fastrpc checklist includes:

```text
fastrpc
/dev/fastrpc-adsp
/dev/fastrpc-cdsp
/dev/fastrpc-cdsp-secure
/usr/lib/dsp libraries
```

The package examples install:

```bash
sudo apt install fastrpc libcdsprpc1
sudo apt install fastrpc-test
```

and validate with architecture-specific fastrpc tests:

```bash
fastrpc_test -a v68
fastrpc_test -a v75
```

This is important because LiteRT-LM Android experiments run inside an Android
app sandbox and do not have the same Linux `/usr/lib/dsp` and shell-exported
environment assumptions.

### QNN / HTP Path Hints

For ONNX Runtime QNN EP verification, Radxa exports:

```bash
cd qairt/2.42.0.251225
source bin/envsetup.sh
export ADSP_LIBRARY_PATH=$QNN_SDK_ROOT/lib/hexagon-v${DSP_ARCH}/unsigned
```

The QNN EP sample points `backend_path` at:

```text
libQnnHtp.so
```

For Linux builds, Radxa instructs changing ONNX Runtime's QNN ABI from:

```text
aarch64-android
```

to:

```text
aarch64-oe-linux-gcc11.2
```

This highlights a major difference from Lami's Android app path: Linux SBC
examples use a Linux QNN ABI, shell environment, and system fastrpc setup. Lami
uses Android packaged libraries and app-private `nativeLibraryDir`.

### Model / Context-Binary Generation

Radxa's QAIRT usage page describes an NPU model flow:

1. convert model to DLC,
2. quantize,
3. generate a QNN context binary,
4. run it on NPU with QAIRT/QNN tools.

For SoC-specific context-binary generation, the docs explicitly set:

| SoC | dsp_arch | soc_id |
| --- | --- | --- |
| QCS6490 | `v68` | `35` |
| QCS9075 | `v73` | `77` |

The AIRbox Q900 QAIRT guide says QAIRT model formats have different portability
properties:

| Format | Backends | Cross-OS | Cross-Chip |
| --- | --- | --- | --- |
| Library | CPU/GPU/NPU | no | yes |
| DLC | CPU/GPU/NPU | yes | yes |
| Context Binary | NPU | yes | no |

The Qualcomm SM8750 Gemma model under Lami is a compiled device-specific NPU
artifact. Radxa's docs reinforce that compiled NPU context/model artifacts are
generation, SoC, and runtime sensitive.

## Comparison With Current Lami Findings

| Source / ecosystem | Observed QAIRT/QNN generation | Reading |
| --- | --- | --- |
| Radxa public docs | QAIRT `2.42.0.251225` | Stable public Linux/SBC workflow for QCS6490/QCS9075. |
| LiteRT public refs inspected by Lami | QAIRT `2.44.0.260225` metadata | Newer public LiteRT Qualcomm dispatch build expectation. |
| Local SDK | QAIRT `2.46.0.260424` | Newer than public LiteRT metadata; no matching public LiteRT/LiteRT-LM ref found. |
| Gallery SM8750 APK | special native payload; Build IDs do not match local 2.46 or custom APK QNN libs | Likely internal/special generation, not public Maven 0.10/0.11 payload. |
| customBuildExperimentDebug | LiteRT-LM v0.11.0 + pinned LiteRT built using 2.46 overlay over 2.44 expected path | Same-source stack, but QAIRT generation may still be mismatched. |

## Stable/Public vs Ahead/Internal

### Appears stable/public

- Radxa Linux docs: QAIRT `2.42.0.251225`
- LiteRT public refs: QAIRT `2.44.0.260225`

These are the only generations currently backed by public docs/source metadata.

### Appears ahead/local

- local QAIRT `2.46.0.260424`

It is usable for external QAIRT validation on the device, but Lami did not find a
public LiteRT/LiteRT-LM source/ref that explicitly targets this generation.

### Appears special/internal

- Gallery SM8750 APK native payload

It is not identical to public Maven `litertlm-android:0.10.0` or the custom
public-source build. Its exact source/ref/QAIRT generation remains unknown.

## Android vs Linux Differences Relevant to Lami

Radxa Linux workflow assumes:

- system fastrpc packages and devices,
- `/usr/lib/dsp` runtime libraries,
- shell-exported `ADSP_LIBRARY_PATH`,
- `source bin/envsetup.sh`,
- Linux QAIRT ABI paths such as `aarch64-oe-linux-gcc11.2`.

Lami Android app workflow uses:

- app-private `nativeLibraryDir`,
- APK-packaged `lib/arm64-v8a/*.so`,
- no shell `ADSP_LIBRARY_PATH`,
- Android linker namespace/app sandbox behavior,
- `Backend.NPU(nativeLibraryDir)` as the only API-level runtime path.

Therefore, a public Linux QAIRT success path does not prove that Android
`Backend.NPU(nativeLibraryDir)` can discover or accept the same QNN/HTP runtime
layout.

## Fit With `No usable Dispatch runtime found`

The Radxa docs make four failure gates plausible even when files are present:

1. QNN/QAIRT generation coupling:
   - Radxa uses exact QAIRT roots.
   - LiteRT public refs expect 2.44.
   - local build used 2.46 over a 2.44 path.
2. capability mismatch:
   - context generation is SoC/dsp_arch/soc_id specific.
   - SM8750/V79 is outside Radxa's documented QCS6490/V68 and QCS9075/V73 paths.
3. ADSP/CDSP path setup:
   - Radxa explicitly configures fastrpc and `ADSP_LIBRARY_PATH`.
   - Android app-private packaging cannot assume this shell environment.
4. model/runtime schema:
   - compiled NPU context artifacts are not cross-chip.
   - a Qualcomm SM8750 `.litertlm` model can still be sensitive to exact runtime generation.

## Current Generation Strategy Reading

Ranking after adding Radxa evidence:

1. **QAIRT 2.44 exact acquisition remains the cleanest next step.**
   It matches public LiteRT metadata and avoids the known 2.46-over-2.44 overlay.
2. **Official maintainer escalation is now equally important.**
   Public docs/source show 2.42/2.44, while local available SDK is 2.46 and
   Gallery is special. Maintainers need to identify the supported Android
   artifact generation.
3. **QAIRT 2.42 downgrade is informative but lower confidence for SM8750.**
   It is Radxa-public and stable for Linux QCS6490/QCS9075, but older than LiteRT
   public metadata and does not document SM8750/V79.
4. **Explicit QNN libs packaging/path experiments are possible only after
   licensing and safety review.**
   They may address Android app discovery, but will not fix a generation mismatch
   by themselves.

## QAIRT 2.42 Local Workflow

QAIRT `2.42.0.251225` was searched locally and was not found:

```text
artifacts/qairt242_acquisition/20260517_083526/local_search.txt
```

The repository now has static-only helpers for a future official 2.42 SDK:

```text
scripts/check_qairt242_sdk.sh
scripts/stage_qairt242_sdk_from_download.sh
docs/qairt_242_acquisition_notes.md
```

These helpers exist to compare Radxa's public generation against LiteRT 2.44
metadata, local QAIRT 2.46, and Gallery/custom payloads. They do not build,
install, modify `jniLibs`, or run `Engine.initialize`.

## Source Links

- Radxa Dragon Q6A NPU overview: <https://docs.radxa.com/en/dragon/q6a/app-dev/npu-dev>
- Radxa Dragon Q6A QAIRT install: <https://docs.radxa.com/en/dragon/q6a/app-dev/npu-dev/qairt-install>
- Radxa Dragon Q6A QAIRT usage: <https://docs.radxa.com/en/dragon/q6a/app-dev/npu-dev/qairt-usage>
- Radxa Dragon Q6A fastrpc setup: <https://docs.radxa.com/en/dragon/q6a/app-dev/npu-dev/fastrpc-setup>
- Radxa AIRbox Q900 QAIRT guide: <https://docs.radxa.com/en/fogwise/airbox-q900/ai-dev/qairt-sdk>
- Radxa QNN Execution Provider: <https://docs.radxa.com/en/fogwise/airbox-q900/ai-dev/qnn_onnxrt_execution_provider>
