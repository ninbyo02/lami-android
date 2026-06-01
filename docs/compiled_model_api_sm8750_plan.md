# CompiledModel API SM8750 NPU route investigation plan

Date: 2026-06-02

## Scope and guardrails

This document is investigation and design only. It does not change production
ChatScreen, S1-S5, fallback settings, QAIRT/QNN settings, native libraries,
model files, or Gradle packaging.

Allowed future work described here must be isolated from the current
`Backend.NPU` route until a minimal NPU smoke test proves that the new route can
initialize, run, and fail closed without touching normal chat behavior.

## Executive summary

`Backend.NPU` and LiteRT `CompiledModel` are different integration surfaces:

- Current Lami `Backend.NPU`: LiteRT-LM `EngineConfig.backend = Backend.NPU(...)`
  over `.litertlm` LLM packages. The latest local docs show constructor and
  config dry-runs can succeed, but `Engine.initialize` still crashes in
  `liblitertlm_jni.so` with a likely mixed LiteRT-LM / QNN / V79 runtime stack.
- Candidate route: LiteRT `CompiledModel` API over `.tflite` models, using
  Qualcomm AI Engine Direct / QNN through LiteRT's NPU compiler and dispatch
  layer. Google documents Qualcomm support for both AOT and on-device JIT
  compilation, and explicitly lists Snapdragon 8 Elite / SM8750 as supported.

Practical conclusion: CompiledModel is worth investigating as a separate
SM8750 NPU proof path, but it is not a drop-in replacement for Lami's current
`.litertlm` chat runtime. The first milestone should be a small `.tflite`
classical ML smoke test, not Gemma chat generation.

## Sources

Primary sources reviewed:

- LiteRT for Android:
  https://ai.google.dev/edge/litert/android
- LiteRT NPU acceleration:
  https://ai.google.dev/edge/litert/next/npu
- LiteRT Qualcomm NPU / AI Engine Direct:
  https://ai.google.dev/edge/litert/next/qualcomm
- LiteRT `CompiledModel` C++ API reference:
  https://ai.google.dev/edge/api/litert/c/classlitert/1-1-compiled-model
- LiteRT samples:
  https://github.com/google-ai-edge/litert-samples
- LiteRT-LM:
  https://github.com/google-ai-edge/LiteRT-LM
- LiteRT-LM Kotlin getting started:
  https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
- Qualcomm AI Engine Direct SDK:
  https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk
- Google AI Edge SLM / Gemma 3n announcement:
  https://developers.googleblog.com/en/google-ai-edge-small-language-models-multimodality-rag-function-calling/
- Gemma 3n E2B LiteRT-LM model:
  https://huggingface.co/google/gemma-3n-E2B-it-litert-lm
- Gemma 3n model card:
  https://ai.google.dev/gemma/docs/gemma-3n/model_card

Local Lami references used for comparison:

- `docs/backend_npu_runtime_stack_mismatch_investigation.md`
- `docs/npu_standard_route_s1_to_s5_completion_report.md`
- `docs/litert_qairt244_chat_screen_npu_integration_plan.md`

## What the public APIs imply

### CompiledModel API

LiteRT presents `CompiledModel` as the modern high-performance API for Android
CPU/GPU/NPU inference. The C++ API reference describes the flow as:

1. create `CompiledModel` from a model and compilation options;
2. query input/output requirements;
3. create `TensorBuffer` inputs/outputs;
4. write input data;
5. invoke the model;
6. read and evaluate outputs.

For Android Kotlin, the NPU guide shows `CompiledModel.create(...)` with
`CompiledModel.Options(Accelerator.NPU, Accelerator.GPU)` so NPU can be tried
first and GPU can be used as fallback. For a strict smoke test, Lami should not
enable fallback initially, because fallback would hide whether the SM8750 NPU
actually ran.

### Qualcomm QNN / AI Engine Direct

Qualcomm describes AI Engine Direct as a lower-level unified API for targeting
Kryo CPU, Adreno GPU, and Hexagon NPU, with model parsing and partitioning
usually handled by higher-level frameworks. LiteRT is one such higher-level
route: Google documents Qualcomm QNN support through `CompiledModel` for both
AOT and on-device compilation.

Important SM8750 facts from the LiteRT Qualcomm page:

- supported SoC includes Snapdragon 8 Elite Mobile Platform, `SM8750`;
- backend target is QNN Hexagon Tensor Processor, HTP;
- Android development requirements include Android API level 34 and NDK API
  level 28 support;
- supported modes are AOT and on-device JIT.

### Model Garden / Gemma3n / LiteRT-LM

Gemma 3n E2B/E4B LiteRT-LM models are distributed as `.litertlm` packages and
are intended for LiteRT-LM, not raw `CompiledModel` directly. Hugging Face
model cards describe Gemma 3n as multimodal and LiteRT-LM optimized; the
LiteRT-LM repo states `.litertlm` is its model format.

This means there are two different model-format tracks:

| Track | Runtime API | Model format | Best first Lami use |
| --- | --- | --- | --- |
| Classical LiteRT | `CompiledModel` | `.tflite`, optionally AOT-compiled / AI Pack | minimal NPU smoke test |
| LiteRT-LM GenAI | `Engine` / `Conversation` | `.litertlm` | future chat route, after stack alignment |
| Current Lami diagnostic route | custom QAIRT/QNN probe path | existing local SM8750 artifacts | already validates S1-S5 diagnostic behavior |

## Required model formats

### For the first CompiledModel smoke test

Use a small `.tflite` model with NPU-compatible ops. Prefer an official LiteRT
sample model from `google-ai-edge/litert-samples/compiled_model_api`, such as
the segmentation sample, because it already exercises the CompiledModel NPU
path.

Two valid deployment forms:

- JIT/on-device compilation: ship the original `.tflite` in app assets, provide
  Qualcomm compiler/dispatch/runtime libraries, and let the device compile on
  first run. This is the fastest investigation route but has higher first-run
  latency.
- AOT compilation: compile the `.tflite` for SM8750 ahead of time and package
  through a Play AI Pack or equivalent test-only asset structure. This is closer
  to production for large models but costs more setup.

For Lami's first proof, JIT is the recommended starting point because it avoids
introducing an AOT build/distribution step before basic SM8750 dispatch is
proven.

### For Gemma3n / LLM work

Use `.litertlm` only with LiteRT-LM APIs. Do not assume `CompiledModel` can load
or chat with `google/gemma-3n-E2B-it-litert-lm` directly. If a future LiteRT-LM
NPU route is pursued, it should be evaluated after the classical `.tflite`
CompiledModel proof and after confirming the exact `litertlm-android`,
`liblitertlm_jni.so`, `libLiteRt.so`, dispatch, QNN HTP, V79 stub/skel, and
model-generation compatibility.

## Minimal SM8750 NPU smoke test design

Goal: prove that Lami can run any model through LiteRT `CompiledModel` on
SM8750 NPU without touching the existing chat routes.

### Isolation requirements

- New debug-only activity, instrumentation target, or broadcast receiver.
- No production ChatScreen wiring.
- No S1-S5 branch changes.
- No fallback policy changes in existing routes.
- No QAIRT/QNN setting changes for current probes.
- No replacement of current native libraries.
- Output only to a standalone artifact/report.

### Test matrix

| Phase | Accelerator options | Expected proof | Why |
| --- | --- | --- | --- |
| CM-0 CPU control | `CPU` only | model loads and output checksum is stable | validates model/input/output plumbing |
| CM-1 NPU strict | `NPU` only | success or explicit NPU init/compile failure | proves whether NPU path is viable without hidden fallback |
| CM-2 NPU cache | `NPU` only with compiler cache dir | second run init is faster or cache is reused | validates JIT artifact caching behavior |
| CM-3 NPU+GPU fallback | `NPU, GPU` | records whether NPU was used or fallback happened | only after strict NPU has clear diagnostics |
| CM-4 AOT candidate | SM8750 AOT model | lower init cost than JIT | later production-like packaging check |

### Required observations

Each run should record:

- device SoC and ABI, including `SM8750` and `arm64-v8a`;
- Android version/API;
- LiteRT Maven/runtime version;
- model filename, size, SHA-256, and whether it is `.tflite` or AOT package;
- accelerator option order;
- runtime library directory used by `Environment`;
- presence and build IDs or hashes for `libLiteRt.so`,
  `libLiteRtDispatch_Qualcomm.so`, `libLiteRtCompilerPlugin_Qualcomm.so`,
  `libQnnSystem.so`, `libQnnHtp.so`, `libQnnHtpPrepare.so`,
  `libQnnHtpV79Stub.so`, and `libQnnHtpV79Skel.so`;
- init/compile elapsed time;
- run elapsed time;
- output tensor checksum or small decoded summary;
- logcat markers for LiteRT/QNN/HTP dispatch;
- tombstone path if the process aborts.

### Pass/fail definitions

Pass for CM-1 requires all of:

- `CompiledModel.create` returns successfully with NPU-only options;
- input/output buffers are created;
- at least one `run` succeeds;
- output checksum differs from zero/default and matches CPU control within
  expected tolerance;
- diagnostic logs indicate Qualcomm QNN/HTP or LiteRT Qualcomm dispatch, not
  CPU/GPU fallback.

Fail but useful:

- unsupported op error: model must be changed or AOT support checked;
- compiler plugin missing: packaging/runtime issue;
- QNN/HTP/V79 load failure: library delivery or version issue;
- process abort: same class of native-stack problem as `Backend.NPU`, but now
  isolated from LiteRT-LM.

Invalid proof:

- any run where fallback was enabled but the report cannot distinguish NPU from
  GPU/CPU;
- any run that modifies existing ChatScreen, S1-S5, fallback, QAIRT/QNN
  settings, or native library layout.

## Backend.NPU comparison

| Axis | Current `Backend.NPU` route | Candidate `CompiledModel` route |
| --- | --- | --- |
| API level | LiteRT-LM `Engine` / `Conversation` | LiteRT `CompiledModel` / `TensorBuffer` |
| Model format | `.litertlm` | `.tflite` or AOT-compiled LiteRT model |
| Current Lami evidence | S1-S5 diagnostic route completes with `QNN_HTP_V79_FastRPC_native_diag`; explicit `Backend.NPU` init remains unsafe | not yet tested in Lami |
| Known failure | `Engine.initialize` SIGABRT candidate from mixed `liblitertlm_jni.so`, `libLiteRt.so`, QNN HTP, and V79 stack | unknown; first test should isolate compiler/dispatch/QNN loading |
| Fit for chat | Directly suitable when stable | not directly suitable unless used underneath a higher-level LLM runtime |
| Fit for minimal NPU proof | Heavy and crash-prone because `.litertlm` and LLM stack are involved | strong fit with small `.tflite` model |
| Fallback behavior | Must remain unchanged in existing app | smoke test should start NPU-only, then separately test fallback |
| Production risk | High until runtime stack compatibility is solved | lower for a debug-only proof; production use still requires model delivery plan |

Key distinction: `CompiledModel` can answer "can LiteRT reach Qualcomm NPU on
this SM8750 device?" without the complexity of tokenization, conversation
state, `.litertlm` metadata, and LiteRT-LM JNI.

## Lami adoption plan

### Stage 0: Documentation-only decision

Status: this document.

Decision gate:

- Confirm that the first implementation task is a debug-only CompiledModel
  smoke test with a `.tflite` model.
- Do not reuse or mutate the current production ChatScreen or S1-S5 route.

### Stage 1: Offline model and dependency inventory

Produce a bill of materials before coding:

- selected official `.tflite` sample model;
- exact LiteRT version;
- required `litert_npu_runtime_libraries_jit.zip` or equivalent release
  source;
- Qualcomm runtime subdir for V79 / SM8750;
- expected Gradle/packaging implications, especially `arm64-v8a`, min SDK 31+
  for NPU distribution paths, and legacy JNI packaging if required by the
  runtime bundle.

No app code should change until the inventory is reviewed.

### Stage 2: Debug-only CompiledModel probe

Implement a separate probe that runs CM-0 through CM-2. It should write a
single report artifact and should not expose a normal user-facing setting.

The code should use strict NPU-only mode first. Only after that should a
fallback-enabled variant be added, and the report must show whether fallback
occurred.

### Stage 3: AOT feasibility

If JIT succeeds, compile the same `.tflite` for SM8750 via the LiteRT AOT
notebook/tooling and compare:

- init latency;
- memory;
- output parity;
- package size;
- delivery complexity.

AOT should remain an experiment until there is a clear packaging story that
does not disturb current Lami native libraries.

### Stage 4: Bridge decision

After a successful classical `.tflite` proof, choose one of two paths:

- keep CompiledModel as a hardware smoke/diagnostic facility only;
- use the knowledge to re-approach LiteRT-LM NPU, with exact stack alignment
  requirements documented before changing any chat route.

Do not promote CompiledModel to chat unless a real LLM API surface is available
and tested. Raw `CompiledModel` tensor I/O is not a complete chat runtime.

### Stage 5: Production-readiness gate

Production use requires:

- deterministic accelerator reporting;
- crash-free repeated initialization and teardown;
- no fallback ambiguity;
- clear model/runtime licensing and redistribution rights;
- Play delivery or equivalent packaging plan;
- performance and battery comparison against the current standard route;
- rollback behavior that cannot corrupt chat state or persisted backend
  selection.

## Open questions

- Which exact LiteRT release should Lami target for a debug-only CompiledModel
  probe: current stable `2.1.5` from Google docs, or the version already pulled
  transitively by the current LiteRT-LM stack?
- Does the public LiteRT NPU runtime bundle include all Qualcomm V79 pieces
  needed for the local SM8750 device, or does it require a separate Qualcomm
  SDK/license flow?
- Can the official LiteRT segmentation sample model compile on SM8750 JIT
  without unsupported op partitions?
- What runtime log signal is reliable enough to prove QNN HTP execution rather
  than CPU/GPU fallback?
- If JIT works but AOT fails, is JIT acceptable for a diagnostic-only Lami
  route?

## Recommendation

Proceed only with a debug-only, `.tflite`, NPU-only CompiledModel smoke test
design. Treat it as a hardware/runtime proof path, not as a chat integration.

For Lami, the value is high because it separates "SM8750 Qualcomm NPU can be
used through LiteRT" from "LiteRT-LM `.litertlm` chat stack is ABI/model
compatible." That separation should make the next failure more actionable and
avoid destabilizing the existing S1-S5 and `Backend.NPU` investigation lines.
