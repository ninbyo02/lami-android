# QAIRT 2.44 Single-Token Smoke Implementation Prep

Date: 2026-05-23

Scope: implementation preparation, safety gating, and the first explicitly
approved lower-level one-token smoke run.

## Current Boundary

`customBuildExperimentDebug` has already proven the initialize-only path:

- `Engine.initialize`: `2/2` success
- `Engine.close`: `2/2` success
- `LiteRtDispatchCheckRuntimeCompatibility`: `kLiteRtStatusOk(0)`
- `QnnDevice_create`: `status 0x0`
- V79 stub / FastRPC path active
- no crash/tombstone

This does not prove generation.

## API Finding

The current Kotlin/JNI API exposed to the app cannot guarantee a hard
`maxOutputTokens=1` smoke:

- Kotlin `Session.runDecode()` calls `LiteRtLmJni.nativeRunDecode(handle)`.
- JNI `nativeRunDecode` calls native `session->RunDecode()` without a
  `DecodeConfig`.
- Kotlin `Conversation` / `sendMessage*` / `generateContent*` can initiate
  generation, but the inspected public API does not expose a verified hard
  one-token output cap.
- C++ lower-level runtime does support the required cap:
  `DecodeConfig::CreateDefault()` followed by
  `decode_config.SetMaxOutputTokens(1)`.

Therefore the smoke should not run through the current Kotlin high-level
surface.

## Prepared Safety Script

```text
scripts/run_qairt244_single_token_smoke.sh
```

The script is intentionally a blocking preflight in this commit. It writes an
artifact and exits without running the app when the hard one-token cap is not
available.

Latest preflight artifact:

```text
artifacts/qairt244_single_token_smoke/20260523_044804/
artifacts/qairt244_single_token_smoke/20260523_044941/
```

It records:

- intended app id: `io.github.ninbyo02.lami.customnpu`
- intended prompt: `Hi`
- intended max output tokens: `1`
- classification: `maxOutputTokens=1-not-guaranteed`
- executed: `false`
- safety checks in `safety_checks.tsv`

## Lower-Level Execution

```text
scripts/run_qairt244_lower_level_single_token_smoke.sh
```

This stricter runner checks the lower-level requirement directly and only runs
when `--run` is explicitly supplied. Execution artifact:

```text
artifacts/qairt244_lower_level_single_token_smoke/20260523_053258/
```

Result:

```text
classification=executed
executed=true
result=success
prompt=Hi
max_output_tokens=1
elapsed_ms=1115
output=!
```

LiteRT-LM C++ exposes the needed primitive and the isolated native entrypoint
statically calls `SetMaxOutputTokens(1)`. The native diagnostic file confirms:

```text
before RunDecode SetMaxOutputTokens(1)
success output_candidates=1 output_bytes=1 elapsed_ms=1115 Engine.close=unique_ptr_cleanup
```

Build artifact:

```text
artifacts/qairt244_single_token_entrypoint_build/20260523_052106/
```

Entrypoint:

- native:
  `Java_io_github_ninbyo02_lami_ui_screens_home_Qairt244LowerLevelSingleTokenSmoke_nativeRun`
- wrapper:
  `Qairt244LowerLevelSingleTokenSmoke.run(...)`
- Activity extra: `runLowerLevelSingleTokenSmoke=true`
- prompt: `Hi`
- hard cap: `DecodeConfig.SetMaxOutputTokens(1)`
- normal UI routing: none

## Implemented Lower-Level Contract

The isolated `customBuildExperimentDebug`-only native/JNI entrypoint:

1. constructs the same NPU-backed engine used by the initialize probe
2. initializes it
3. creates the minimum native session needed for prefill/decode
4. runs prefill with prompt `Hi`
5. runs decode with `DecodeConfig.SetMaxOutputTokens(1)`
6. records output text, elapsed time, and backend diagnostics to app-private
   files
7. releases session and engine through native ownership cleanup

The source must be statically checkable for:

- `SetMaxOutputTokens(1)`
- customBuildExperimentDebug-only source set or binary
- no normal UI routing
- no `LocalStreamingRunner`
- no unconstrained `Conversation.sendMessage*`

## Runner Contract

Before the script is allowed to run generation, it requires all of the
following:

- explicit command acknowledgement
- one connected device
- `customBuildExperimentDebug` package only
- initialize stability artifact with `2/2` clean initialize/close
- prompt length under a small fixed limit
- timeout between 10 and 60 seconds
- static source marker `SetMaxOutputTokens(1)`
- no forbidden source markers such as `LocalStreamingRunner`,
  `Conversation.sendMessage*`, or normal chat UI routing

The app-side result file should be machine-readable, for example:

```text
run_id=...
flavor=customBuildExperiment
backend=NPU
max_output_tokens=1
prefill_invoked=true
decode_invoked=true
decoded_token_count=1
completed=true
close_session=true
close_engine=true
result=success
```

If this contract is absent, the runner must classify the smoke as invalid even
if text is produced.

## Execution Status

Executed once:

- lower-level native session creation required for LiteRT-LM decode
- `Session::RunPrefill("Hi")`
- `Session::RunDecode(decode_config)` with `maxOutputTokens=1`

Not executed:

- `Conversation` creation
- Kotlin/public `Session` object creation
- high-level `generateResponse`
- normal UI NPU path

## Classification

Current classification:

```text
1 token生成成功
```
