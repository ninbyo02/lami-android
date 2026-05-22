# QAIRT 2.44 Lower-Level Single-Token Smoke Result

Date: 2026-05-23

Scope: `customBuildExperimentDebug` lower-level smoke implementation preflight.
No app launch or generation was executed.

## Result

Artifact:

```text
artifacts/qairt244_lower_level_single_token_smoke/20260523_052224/
```

Classification:

```text
entrypoint-implemented-not-executed
```

The preflight found the required LiteRT-LM C++ primitive and a runnable
`customBuildExperimentDebug` wrapper. It did not execute because `--run` was not
requested.

Build artifact:

```text
artifacts/qairt244_single_token_entrypoint_build/20260523_052106/
```

The build artifact is a QAIRT 2.44 execution candidate composed from the
previous initialize-success stack plus the rebuilt `liblitertlm_jni.so` that
contains the lower-level entrypoint. The dispatch library keeps
`DT_NEEDED [libLiteRt.so]`.

Key native IDs:

| Library | Build ID | SHA-256 |
| --- | --- | --- |
| `libLiteRt.so` | `731b74da505bef341a184b3778d0412d` | `3ba100245ed79d45abf3c34230aee77d6aabd0b6c302a1ce8dd060b95575e7ec` |
| `libLiteRtDispatch_Qualcomm.so` | `a1b66b12e643f15a94cb34093f9efcac` | `459ceb6e3912fa72b43363c763315b2fbf5d336e744e82e4850f33967c7bbeba` |
| `liblitertlm_jni.so` | `a5f78bc1fb6839abead290eebd139860` | `b5a74c656c99830fbc8f0888f71643078d71c2957712eef2dfff06d991ad286b` |
| `libLiteRtCompilerPlugin_Qualcomm.so` | `12a6ac7197aff7045fc5f5c263b35f9f` | `299769ef90f9ee4b74b357bd867545e1f48312bb8b1d97f9d16968a2be175655` |

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
- the staged `liblitertlm_jni.so` exposes C++/JNI decode symbols, but not a
  stable app-owned C API such as
  `litert_lm_session_config_set_max_output_tokens`

The new lower-level entrypoint avoids that public Kotlin surface:

- native symbol:
  `Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244LowerLevelSingleTokenSmoke_nativeRun`
- marker: `qairt244_lower_level_single_token_smoke_v1`
- prompt: `Hi`
- hard cap: `decode_config.SetMaxOutputTokens(1)`
- path: `ModelAssets::Create -> EngineSettings::CreateDefault(NPU) ->
  EngineFactory::CreateDefault -> Engine::CreateSession ->
  Session::RunPrefill -> Session::RunDecode(decode_config)`
- cleanup: native `unique_ptr` ownership releases session and engine on return

App wrapper:

- `app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/Qairt244LowerLevelSingleTokenSmoke.kt`
- Activity extra: `runLowerLevelSingleTokenSmoke=true`
- result file: `files/qairt244_single_token_smoke_result.txt`
- native diag file: `files/qairt244_native_diag.txt`

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

The isolated entrypoint now exists and the custom native artifacts were rebuilt.
The next step is the first execution, but only with an explicit one-run command:

```bash
bash scripts/run_qairt244_lower_level_single_token_smoke.sh \
  --artifact artifacts/qairt244_single_token_entrypoint_build/20260523_052106 \
  --run
```

That command is still limited to `customBuildExperimentDebug`, uses a 30 second
timeout, and collects result/native diag/stage/logcat/tombstone artifacts.
