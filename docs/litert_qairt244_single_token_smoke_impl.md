# QAIRT 2.44 Single-Token Smoke Implementation Prep

Date: 2026-05-23

Scope: implementation preparation and safety gating only. The single-token
smoke was not executed.

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

## Lower-Level Preflight

```text
scripts/run_qairt244_lower_level_single_token_smoke.sh
```

This stricter runner checks the lower-level requirement directly. Latest
artifact:

```text
artifacts/qairt244_lower_level_single_token_smoke/20260523_045952/
```

Result:

```text
classification=lower-level-entrypoint-missing
executed=false
```

LiteRT-LM C++ exposes the needed primitive, but `customBuildExperimentDebug`
does not yet have a runnable JNI/CLI entrypoint that statically calls
`SetMaxOutputTokens(1)`. The runner therefore stopped before build/install/app
launch and did not create `Conversation` or `Session`.

## Required Future Implementation

The next implementation should add an isolated `customBuildExperimentDebug`-only
native/JNI or initialize-only CLI entrypoint that:

1. constructs the same NPU-backed engine used by the initialize probe
2. initializes it
3. creates the minimum native session needed for prefill/decode
4. runs prefill with prompt `Hi`
5. runs decode with `DecodeConfig.SetMaxOutputTokens(1)`
6. records output text, elapsed time, and backend diagnostics to app-private
   files
7. closes session and engine in `finally`

The source must be statically checkable for:

- `SetMaxOutputTokens(1)`
- customBuildExperimentDebug-only source set or binary
- no normal UI routing
- no `LocalStreamingRunner`
- no unconstrained `Conversation.sendMessage*`

## Future Runner Contract

Before the script is allowed to run generation, it should require all of the
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

Not executed:

- `Conversation` creation
- `Session` creation
- `generateResponse`
- token generation
- normal UI NPU path

## Classification

Current classification:

```text
maxOutputTokens=1を保証できず、未実行
```
