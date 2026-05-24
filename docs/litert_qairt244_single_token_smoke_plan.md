# QAIRT 2.44 Single-Token Smoke Plan

Date: 2026-05-23

This is a design-only document. The smoke test was not implemented or run in
the initialize-stability phase. The follow-up implementation-prep phase added a
blocking preflight script, but still did not run generation because a hard
one-token cap is not yet available from the app-accessible Kotlin/JNI surface.

## Preconditions

- Keep the experiment `customBuildExperimentDebug`-only.
- Require explicit ADB opt-in, for example:
  `run_qairt244_single_token_smoke=true`.
- Require the initialize stability probe to show `2/2` initialize success and
  `2/2` close success.
- Keep normal UI inference routing unchanged; do not set production
  `selectedPath=npu`.
- Keep `libcdsprpc.so` as a manifest optional native library, not a staged or
  committed vendor binary.

## Proposed Isolated Flow

1. Start a dedicated probe Activity or a guarded branch in
   `NpuExperimentProbeActivity`.
2. Create `Backend.NPU(nativeLibraryDir)`.
3. Create `EngineConfig` with the SM8750 LiteRT-LM model and the NPU backend.
4. Construct `Engine`.
5. Call `Engine.initialize()`.
6. Enter a dedicated lower-level smoke path, not normal UI inference.
7. Run prefill for prompt `Hi`.
8. Decode with an explicit hard cap such as
   `DecodeConfig.SetMaxOutputTokens(1)`.
9. Close all native/session/engine resources in `finally`.
10. Write result, timing, exception chain, and backend diagnostics to app
    private files.

## Safety Controls

- Hard timeout around the whole probe.
- No streaming UI.
- No retry loop.
- No fallback to CPU/GPU inside the probe; fallback would hide NPU behavior.
- No normal chat history write.
- No user-facing route.
- Always collect app-private diagnostics and tombstone/logcat tail.

## API Finding

The Kotlin `Conversation.sendMessage*` surface is not the safest first smoke
path because a hard one-token cap is not obvious there. The safer design is a
customBuildExperimentDebug-only native/JNI or initialize-only CLI path that uses
the lower-level session decode API with an explicit one-token cap before any
generation is attempted.

## Stop Conditions

Do not run if any of these are true:

- initialize stability is not clean
- max-token control is unknown or cannot be statically verified
- probe would use normal chat UI code
- probe would run more than one token
- probe would require release, standard, npuExperiment, or galleryStackExperiment
  changes

## Plan Artifact

```text
artifacts/qairt244_single_token_smoke_plan/20260523_043907/
```

## Implementation Prep Update

```text
docs/litert_qairt244_single_token_smoke_impl.md
scripts/run_qairt244_single_token_smoke.sh
```

The script is currently a safety preflight. It records
`classification=maxOutputTokens=1-not-guaranteed` and does not build, install,
launch the app, create a `Conversation`, create a `Session`, call
`generateResponse`, or generate tokens.
