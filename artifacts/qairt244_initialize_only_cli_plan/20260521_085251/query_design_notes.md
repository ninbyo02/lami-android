# QAIRT 2.44 Initialize-Only CLI Plan Notes

Generated: 2026-05-21T08:52:51+09:00

This artifact is planning-only. No CLI binary was executed, no model inference
was run, no prompt was sent, no `Conversation` was created, no `Session` was
created, and no generation API was called.

## Inspected Sources

```text
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/engine/litert_lm_main.cc
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/engine/litert_lm_advanced_main.cc
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/engine/engine.h
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/engine/engine_factory.h
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/engine/engine_settings.h
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/engine/engine_settings.cc
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/core/engine_impl.cc
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/executor_settings_base.h
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_executor_settings.h
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/executor/llm_executor_settings.cc
/home/sato/project/litert-custom-build/LiteRT-LM/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc
/home/sato/project/litert-custom-build/LiteRT-LM/runtime/engine/BUILD
```

## Findings

- Existing `//runtime/engine:litert_lm_main` is a generation CLI. It creates a
  `Conversation`, sends a prompt, and waits for generation.
- Existing `litert_lm_advanced_main` is also an inference CLI even though it
  has a dispatch library directory flag.
- Engine creation is the desired stop point:
  `EngineFactory::CreateAny(std::move(engine_settings))`.
- `Engine::CreateSession` is the next forbidden boundary.
- For NPU, the native library directory should be set on main executor settings
  with `SetLitertDispatchLibDir(...)`.
- JNI already maps Android NPU native library directory strings to
  `SetLitertDispatchLibDir(...)` before `EngineFactory::CreateDefault(...)`.

## Decision

An Android arm64 initialize-only `cc_binary` is feasible as a design. The
implementation should remain a plan for this pass. A future source target
should be added upstream as:

```text
runtime/engine/litert_lm_initialize_only_main.cc
//runtime/engine:litert_lm_initialize_only_main
```

The future binary should accept:

```text
--model_path=<path>
--native_library_dir=<path>
--backend=npu
--no_generate=true
```

It should stop after successful engine creation, destroy the engine, and exit.

## Forbidden Markers For Future Source

```text
Conversation::Create
ConversationConfig
SessionConfig
CreateSession
SendMessageAsync
GenerateContent
GenerateContentStream
RunPrefill
RunDecode
input_prompt
input_prompt_file
default prompt
```
