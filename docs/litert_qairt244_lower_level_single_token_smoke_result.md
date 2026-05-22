# QAIRT 2.44 Lower-Level Single-Token Smoke Result

Date: 2026-05-23

Scope: `customBuildExperimentDebug` lower-level smoke implementation preflight.
No app launch or generation was executed.

## Result

Artifact:

```text
artifacts/qairt244_lower_level_single_token_smoke/20260523_045952/
```

Classification:

```text
lower-level-entrypoint-missing
```

The preflight found the required LiteRT-LM C++ primitive, but no runnable
`customBuildExperimentDebug` JNI/CLI entrypoint currently calls it.

## Static Findings

LiteRT-LM C++ has the required hard cap path:

- `runtime/engine/io_types.h`: `DecodeConfig::CreateDefault()` and
  `DecodeConfig::SetMaxOutputTokens(int)`
- `runtime/core/session_basic.cc`: `SessionBasic::DecodeInternal` consumes
  `decode_config.GetMaxOutputTokens()`
- `runtime/core/tasks.cc`: decode stop logic stops when decoded steps reach the
  configured max output token count
- `runtime/engine/litert_lm_lib.cc`: session path creates a `DecodeConfig`,
  applies `decode_config.SetMaxOutputTokens(settings.max_output_tokens)`, then
  calls `session->RunPrefill(inputs)` and `session->RunDecode(decode_config)`

The app-accessible Kotlin/JNI path is still not sufficient:

- `Session.runDecode()` calls `LiteRtLmJni.nativeRunDecode(handle)`
- JNI `nativeRunDecode` calls `session->RunDecode()` without a `DecodeConfig`
- no current `customBuildExperimentDebug` app source contains the marker
  `qairt244_lower_level_single_token_smoke_v1`
- no current runnable app path statically contains `SetMaxOutputTokens(1)`
- the staged `liblitertlm_jni.so` exposes C++/JNI decode symbols, but not a
  stable app-owned C API such as
  `litert_lm_session_config_set_max_output_tokens`

## Safety Outcome

The runner intentionally did not:

- build or install the APK
- launch the app
- create `Conversation`
- create `Session`
- call `generateResponse`
- run token generation
- connect NPU to the normal UI path

## Next Required Implementation

Add an isolated `customBuildExperimentDebug` lower-level entrypoint in the
custom LiteRT-LM stack, then rebuild the custom native artifacts. The executable
path must be statically checkable for:

- `qairt244_lower_level_single_token_smoke_v1`
- `DecodeConfig.SetMaxOutputTokens(1)`
- prompt fixed to `Hi`
- no normal UI routing
- explicit `Engine.close` / cleanup path
- one-run-only script timeout and artifact collection

Only after those checks pass should
`scripts/run_qairt244_lower_level_single_token_smoke.sh` be changed from
blocking preflight to the one allowed execution path.
