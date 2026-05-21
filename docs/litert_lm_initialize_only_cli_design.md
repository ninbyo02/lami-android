# LiteRT-LM Initialize-Only CLI Design

Date: 2026-05-21

Scope: design and planning only. This pass did not execute any LiteRT-LM CLI,
did not run model inference, did not send a prompt, did not create a
`Conversation`, did not create a `Session`, and did not call generation.

## Objective

Design a non-generating Android arm64 CLI proof target for the same boundary as
the Android NPU engine initialize dry-run:

```text
model_path=<model>
native_library_dir=<directory containing LiteRT dispatch/QNN native libraries>
backend=npu
no_generate=true
```

The proof must stop after the Engine initialization boundary and close. It must
not create `Conversation`, create `Session`, read or synthesize a prompt, call
`SendMessageAsync`, call `GenerateContent`, run prefill, or run decode.

## Upstream Path Inspection

Relevant LiteRT-LM source tree:

```text
/home/sato/project/litert-custom-build/LiteRT-LM
```

Relevant files inspected:

```text
runtime/engine/litert_lm_main.cc
runtime/engine/litert_lm_advanced_main.cc
runtime/engine/engine.h
runtime/engine/engine_factory.h
runtime/engine/engine_settings.h
runtime/engine/engine_settings.cc
runtime/core/engine_impl.cc
runtime/executor/executor_settings_base.h
runtime/executor/llm_executor_settings.h
runtime/executor/llm_executor_settings.cc
kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc
runtime/engine/BUILD
```

### `litert_lm_main` Behavior

`runtime/engine/litert_lm_main.cc` is not safe for this proof target. Its
`MainHelper` does the following:

1. Parses `--backend`, `--model_path`, `--input_prompt`, and
   `--input_prompt_file`.
2. Uses a default prompt when no prompt is supplied.
3. Calls `ModelAssets::Create(model_path)`.
4. Converts the backend string with `GetBackendFromString`.
5. Calls `EngineSettings::CreateDefault(model_assets, backend)`.
6. Enables benchmark params.
7. Calls `EngineFactory::CreateAny(std::move(engine_settings))`.
8. Creates `SessionConfig` and `ConversationConfig`.
9. Calls `Conversation::Create(*engine, conversation_config)`.
10. Sends a user message with `conversation->SendMessageAsync(...)`.
11. Calls `engine->WaitUntilDone(absl::Minutes(10))`.

That crosses the forbidden boundary because it creates a conversation, supplies
a prompt, and starts generation.

### Advanced Main Behavior

`runtime/engine/litert_lm_advanced_main.cc` exposes more knobs, including
`--litert_dispatch_lib_dir`, but it still populates an input prompt, calls
`RunLiteRtLm(settings, ...)`, and is an inference CLI. It is also not a safe
entry point for this proof.

### Engine Boundary

The boundary required by this proof is:

```c++
ASSIGN_OR_RETURN(ModelAssets model_assets, ModelAssets::Create(model_path));
ASSIGN_OR_RETURN(Backend backend, GetBackendFromString("npu"));
ASSIGN_OR_RETURN(EngineSettings settings,
                 EngineSettings::CreateDefault(std::move(model_assets), backend));
settings.GetMutableMainExecutorSettings().SetLitertDispatchLibDir(native_library_dir);
ASSIGN_OR_RETURN(auto engine,
                 EngineFactory::CreateAny(std::move(settings)));
engine.reset();
```

`EngineFactory::CreateAny` delegates to `CreateDefault`, chooses a registered
engine for `Backend::NPU`, and calls the engine creator. In
`runtime/core/engine_impl.cc`, engine creation loads model resources, metadata,
tokenizer, creates the LiteRT environment, and creates the executor. For NPU,
`GetEnvironment` passes `LlmExecutorSettings::GetLitertDispatchLibDir()` to
LiteRT as `EnvironmentOptions::Tag::kDispatchLibraryDir` when it is non-empty.

`Engine::CreateSession` is the next boundary that must not be crossed. Session
creation calls `InitializeSessionBasic(...)`, and generation APIs live below the
session/conversation layers.

### Android JNI Equivalence

`kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc` shows the Android JNI
initialize path accepts NPU native library directory strings and maps them onto
executor settings with `SetLitertDispatchLibDir(...)` before calling
`EngineFactory::CreateDefault(*settings)`.

That confirms the CLI proof should use a C++ flag named `--native_library_dir`
or similar, then pass it to:

```c++
settings.GetMutableMainExecutorSettings().SetLitertDispatchLibDir(native_library_dir);
```

The existing shared flag name is `--litert_dispatch_lib_dir`, but the intended
proof argument is `native_library_dir`. A new CLI can accept
`--native_library_dir` and document it as equivalent to the JNI
`Backend.NPU(nativeLibraryDir)` argument.

## Target Design

Recommended future source:

```text
runtime/engine/litert_lm_initialize_only_main.cc
```

Recommended future Bazel target:

```python
cc_binary(
    name = "litert_lm_initialize_only_main",
    srcs = ["litert_lm_initialize_only_main.cc"],
    linkopts = select({
        "@platforms//os:android": ["-lEGL", "-lGLESv3"],
        "//conditions:default": [],
    }),
    deps = [
        ":engine_factory",
        ":engine_impl_selected",
        ":engine_interface",
        ":engine_settings",
        "@com_google_absl//absl/base:log_severity",
        "@com_google_absl//absl/flags:flag",
        "@com_google_absl//absl/flags:parse",
        "@com_google_absl//absl/log:absl_check",
        "@com_google_absl//absl/log:globals",
        "@com_google_absl//absl/status",
        "@com_google_absl//absl/status:statusor",
        "@com_google_absl//absl/strings",
        "//runtime/executor:executor_settings_base",
        "//runtime/util:litert_status_util",
    ],
)
```

The target should intentionally omit `//runtime/conversation` and any session
helper dependency. If a dependency later pulls conversation transitively, the
entry point still must not include or call conversation/session APIs.

Required flags:

```text
--model_path=<path>
--native_library_dir=<path>
--backend=npu
--no_generate=true
```

Validation rules:

- `--model_path` is required.
- `--backend` must parse successfully and should default to `npu` for this
  proof target.
- `--native_library_dir` is required for `backend=npu`.
- `--no_generate` must be present and true. If false, return
  `InvalidArgumentError`. This keeps the binary non-generating even if someone
  later tries to repurpose it.

Success path:

1. Parse flags.
2. Set log threshold as needed.
3. Create `ModelAssets`.
4. Create NPU `EngineSettings`.
5. Set `native_library_dir` on main executor settings.
6. Call `EngineFactory::CreateAny`.
7. Print a short initialize success marker.
8. Destroy the engine and exit.

Forbidden code in the target:

```text
Conversation::Create
ConversationConfig
SessionConfig
engine->CreateSession
SendMessageAsync
GenerateContent
GenerateContentStream
RunPrefill
RunDecode
input_prompt
input_prompt_file
default prompt
```

## Android Arm64 Feasibility

An Android arm64 `cc_binary` target is feasible as a design because upstream
already declares Android-compatible `cc_binary` targets in
`runtime/engine/BUILD`, including `litert_lm_main`, with Android link options
for EGL/GLES. The initialize-only target can reuse the same engine deps but
remove the conversation dependency and generation path.

The expected Android arm64 query/build target would be:

```text
//runtime/engine:litert_lm_initialize_only_main
```

Implementation should be kept as a plan in this pass. Reasons:

- The requested artifacts are design and planning outputs.
- The source tree to patch is outside the Android app repo working root.
- Running the proof is explicitly forbidden in this task.
- A buildable target should be added in a coordinated upstream LiteRT-LM change,
  then reviewed with a source scan proving no conversation/session/generation
  symbols are referenced by the entry point.

## Future Verification Plan

Read-only/build-only checks after implementation:

```sh
bazel query //runtime/engine:litert_lm_initialize_only_main
bazel cquery --config=android_arm64 //runtime/engine:litert_lm_initialize_only_main
bazel build --config=android_arm64 //runtime/engine:litert_lm_initialize_only_main
```

Static safety scan:

```sh
rg -n 'Conversation::Create|ConversationConfig|SessionConfig|CreateSession|SendMessageAsync|GenerateContent|GenerateContentStream|RunPrefill|RunDecode|input_prompt|default prompt' \
  runtime/engine/litert_lm_initialize_only_main.cc
```

The scan should return no unsafe markers except in comments that describe the
prohibition.

Runtime execution remains out of scope for this pass.
