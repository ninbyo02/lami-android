# LiteRT-LM Main NPU CLI Proof Plan

Date: 2026-05-21

Scope: planning only. This pass did not execute `litert_lm_main`, did not run
NPU inference, did not create a `Conversation` or `Session`, and did not call
generation.

## Target Status

LiteRT-LM contains a CLI target:

```text
//runtime/engine:litert_lm_main
```

The target is declared in:

```text
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/engine/BUILD
```

The target is not safe to run for the present task. Its `MainHelper` requires a
model path, creates `EngineSettings`, creates an engine, creates a
`Conversation`, sends a prompt, waits up to 10 minutes, and prints benchmark
info. That violates this investigation's current guardrails.

Relevant source behavior:

- `litert_lm_main.cc` declares `--backend`, `--model_path`, `--input_prompt`,
  and `--input_prompt_file`.
- It defaults to a prompt when none is supplied.
- It calls `EngineFactory::CreateAny`.
- It builds `ConversationConfig`.
- It calls `Conversation::Create`.
- It calls `conversation->SendMessageAsync`.
- It calls `engine->WaitUntilDone(absl::Minutes(10))`.

## Query Results

Agent D confirmed:

- `bazel query //runtime/engine:litert_lm_main` succeeded.
- Android arm64 `cquery` using the repo's `--config=android_arm64` failed at
  analysis due to missing CC toolchain resolution.
- At the time of that cquery, `ANDROID_NDK_HOME` and `ANDROID_HOME` were empty,
  while `ANDROID_SDK_ROOT=/usr/lib/android-sdk`.

The cquery failure is build-environment setup, not evidence that the target is
absent.

## QAIRT 2.44 Runtime Inputs

The exact QAIRT 2.44 SDK is available at:

```text
/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225
```

For a future CLI proof, the runtime payload candidates are:

```text
artifacts/litert_custom_build/20260517_230448_qairt244/
/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225/lib/aarch64-android/
/home/sato/compose/qairt/workspace/sdk/qairt/2.44.0.260225/lib/hexagon-v79/unsigned/
```

Required app-equivalent native pieces to stage into a future isolated
`/data/local/tmp` directory would include:

- `litert_lm_main` Android arm64 binary, only if a non-generating dry-run CLI
  variant is created.
- `liblitertlm_jni.so` is not relevant to a pure C++ CLI, but the same LiteRT
  and dispatch stack generation should be used.
- `libLiteRt.so`
- `libLiteRtDispatch_Qualcomm.so`
- `libLiteRtCompilerPlugin_Qualcomm.so`
- `libGemmaModelConstraintProvider.so`, if required by the chosen binary link.
- `libQnnSystem.so`
- `libQnnHtp.so`
- `libQnnHtpPrepare.so`
- `libQnnHtpV79Stub.so`
- DSP-side `libQnnHtpV79Skel.so`, placed according to the QNN/HTP loader's
  expected skel search path.

## Environment Variables For A Future CLI Proof

The future CLI environment should be explicit and logged:

```sh
export LD_LIBRARY_PATH=/data/local/tmp/litertlm-qairt244/lib
export ADSP_LIBRARY_PATH=/data/local/tmp/litertlm-qairt244/dsp
```

The exact `ADSP_LIBRARY_PATH` form should be validated against Qualcomm QNN HTP
loader expectations before execution. This is one reason a CLI proof can help:
it can isolate app linker namespace behavior from explicit shell paths.

## Safe Future Execution Design

Do not run upstream `litert_lm_main` as-is for this investigation. It is a
generation CLI.

A safe CLI proof would require a new or patched local-only CLI target that stops
after the same boundary as the Android dry-run:

1. Parse `--model_path` and `--backend=npu`.
2. Create `ModelAssets`.
3. Create NPU `EngineSettings`.
4. Create the engine or explicitly call the same initialize boundary being
   studied.
5. Exit before `Conversation::Create`, `Session` creation, prompt submission,
   `SendMessageAsync`, `GenerateContent`, or `WaitUntilDone` for inference.

Safety conditions before any CLI execution:

- Build artifact is Android arm64 and clearly separated from app flavors.
- Staging path is a new `/data/local/tmp/litertlm-qairt244-*` directory.
- `LD_LIBRARY_PATH` and `ADSP_LIBRARY_PATH` are printed before execution.
- The command line has no prompt flags and no code path that creates
  `Conversation` or `Session`.
- The run is approved as an initialize-only dry-run, not an inference smoke
  test.

## App vs CLI Difference To Test Later

A future safe CLI proof can separate these variables:

| Area | Android app dry-run | Future CLI dry-run |
| --- | --- | --- |
| Linker namespace | App package namespace and `nativeLibraryDir` | Shell process with explicit `LD_LIBRARY_PATH` |
| DSP skel path | QNN manager mutates `ADSP_LIBRARY_PATH` from dispatch dir | Explicit shell `ADSP_LIBRARY_PATH` possible |
| Java/JNI | `Engine.initialize()` via `liblitertlm_jni.so` | Pure C++ target if built |
| Current failure | SIGABRT before dispatch/QNN libs are mapped in qairt244 tombstone | Not executed |
| Inference risk | Guarded by custom dry-run UI path | Upstream CLI is unsafe unless modified |

## Recommendation

The next CLI-related step is not execution. It is to add or locate an
initialize-only C++ target that cannot create `Conversation`, cannot create
`Session`, and cannot submit a prompt. Only after that should Android arm64
build/cquery and `/data/local/tmp` staging be revisited.
