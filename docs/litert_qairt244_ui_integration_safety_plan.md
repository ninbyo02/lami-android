# QAIRT 2.44 UI Integration Safety Plan

Date: 2026-05-23

Scope: safety gates for moving from isolated QAIRT 2.44 NPU probes toward an
NPU Diagnostic Chat screen. This is not approval to connect NPU to the normal
chat UI.

## Hard Boundaries

- `customBuildExperimentDebug` only.
- No normal `ChatScreen` inference path changes.
- No `selectedPath=npu` in the normal route.
- No high-level `generateResponse`.
- No streaming generation.
- No long prompt or long output.
- No `standard`, `galleryStackExperiment`, `npuExperiment`, or `release`
  behavior changes.
- No `app/src/main/jniLibs` changes.
- No packaged or tracked vendor `libcdsprpc.so`.

## Diagnostic Screen Gates

The diagnostic screen may display existing artifacts immediately, but any
future run action must pass all gates:

1. The current flavor is exactly `customBuildExperiment`.
2. The entrypoint is not reachable from normal navigation.
3. Prompt length is bounded before native code is called.
4. `maxOutputTokens` is hard-capped in native code.
5. The runner has a host-side timeout.
6. Timeout kills only the `io.github.ninbyo02.lami.customnpu` process.
7. `Engine.close` or native cleanup is recorded.
8. Result and diagnostic files are pulled after success and failure.
9. Tombstones are classified as fresh or stale by run id.
10. The result is never written to normal chat messages, DB, TTS, or Markdown
    rendering.

## Timeout And Recovery

Recommended first diagnostic-chat run policy:

- host timeout: 15 to 30 seconds
- app-side timeout: shorter than host timeout if implemented
- on host timeout: kill `io.github.ninbyo02.lami.customnpu`
- always collect:
  - result file
  - native diag file
  - stage file
  - logcat tail
  - tombstone diagnostics
  - package dump
- stale tombstones must not fail a successful current run unless the current
  run id is present in the tombstone.

## Diagnostic Chat Guarded Button

2026-05-23 update: `NpuDiagnosticChatActivity` now exposes a
`customBuildExperimentDebug`-only guarded `Run 3-token smoke` control. The
default launch remains read-only because the button is disabled until the
developer checks `DEV confirm isolated 3-token NPU smoke`.

The button is constrained to:

- prompt `Hi`
- `maxOutputTokens=3`
- isolated lower-level `Qairt244ShortMultitokenSmoke.run(...)`
- app-side running lock
- app-side 30 second timeout marker
- app-private result file only

It still does not use `ChatScreen`, normal message DB writes, TTS, Markdown,
`selectedPath=npu`, or high-level `generateResponse`. Host-side timeout,
process kill, logcat, and tombstone artifact collection remain the authoritative
path for evidence-producing runs.

Guarded UI smoke verification:

- artifact:
  `artifacts/qairt244_npu_diagnostic_chat_guarded_ui_run/20260523_100701/`
- result: `success`
- output: `! How Hi`
- hard cap: `max_output_tokens=3`
- native evidence: `QNN_HTP_V79_FastRPC_native_diag`
- tombstone classification: `stale-tombstone-ignored`

The UI path remains diagnostic-only. It must not be promoted to the normal chat
route without a separate integration review and host-side recovery plan.

Automated guarded UI runner:

- script:
  `scripts/run_qairt244_npu_diagnostic_chat_ui_smoke.sh`
- artifact:
  `artifacts/qairt244_npu_diagnostic_chat_ui_smoke/20260523_102810/`
- result: `success`
- output: `! How Hi`
- hard cap: `max_output_tokens=3`
- UI operations: `ui_dev_checkbox_taps=1`, `ui_run_button_taps=1`
- timeout: `false`
- native evidence: `QNN_HTP_V79_FastRPC_native_diag`
- tombstone classification: `stale-tombstone-ignored`

The runner is the preferred reproducible path for Diagnostic Chat UI evidence.
It selects a non-emulator device, builds/installs `customBuildExperimentDebug`,
launches only `NpuDiagnosticChatActivity`, drives the DEV checkbox and guarded
run button once, captures screenshots/window dumps, pulls app-private result
files, and runs the tombstone collector. Timeout handling force-stops only the
`customnpu` package and records the timeout in the artifact.

Multi-run runner update:

- script:
  `scripts/run_qairt244_npu_diagnostic_chat_ui_multirun.sh`
- fixed run count: `2`
- prompt: `Hi`
- hard cap: `maxOutputTokens=3`
- latest attempt artifact:
  `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_110017/`

The first multi-run attempt captured successful NPU outputs for both requested
runs, but it also exposed a host runner wait bug: the script stopped on any
earlier `state=success` marker rather than the last guarded UI marker. The
script now waits on the last marker state and treats `state=started` as still
running. This is a host artifact issue, not a normal UI integration change.
The normal `ChatScreen`, message DB, TTS, Markdown path, and normal
`selectedPath=npu` route remain untouched.

Fixed runner verification:

- artifact:
  `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/`
- run count: `2`
- run1: `result=success`, final guarded marker `state=success`
- run2: `result=success`, final guarded marker `state=success`
- final `state=started`: `false` for both runs
- timeout: `false`
- fresh crash: `false`
- native evidence: `QNN_HTP_V79_FastRPC_native_diag`

This confirms the corrected host runner waits for the actual final guarded
marker state before classifying a Diagnostic Chat UI run as complete.

Result viewer refresh:

- screen: `customBuildExperimentDebug` `NpuDiagnosticChatActivity`
- latest evidence shown:
  `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/`
- read-only fields include run1/run2 result, output, elapsed,
  decode elapsed, final guard marker state, `state=started` final-state
  status, after-10s TOTAL PSS/Native Heap PSS, tombstone classification, and
  fresh crash status
- route guard fields explicitly show normal `ChatScreen` route disabled and
  normal `selectedPath=npu` disabled
- `Refresh result view` only rereads app-private files or committed latest
  verification values
- Refresh does not call the lower-level smoke entrypoint, does not initialize
  the engine, and does not generate tokens

The existing DEV checkbox and guarded run button remain the only UI path that
can trigger the isolated 3-token smoke. The normal chat route remains
disconnected.

## Fallback Policy

Fallback is diagnostic-only:

- If NPU fails, show failure in the diagnostic screen.
- Do not fall back into the normal GPU/CPU chat path.
- Do not silently retry with a different backend.
- Do not automatically increase output tokens.
- Do not run a second generation in the same action.

## Artifact Policy

Each diagnostic run should create a timestamped directory under:

```text
artifacts/qairt244_npu_diagnostic_chat/<timestamp>/
```

Minimum files:

- `summary.md`
- `result.txt`
- `native_diag.txt`
- `stage_file.txt`
- `logcat_tail.txt`
- `stale_tombstone_note.md`
- diagnostics collector output

Large copied APK native libraries should remain untracked unless explicitly
needed for a static comparison artifact.

## 2-3 Token Smoke Gate

Before moving beyond one token:

- one-token verifier remains reproducible after the diagnostic screen is added
- diagnostic screen remains read-only or guarded
- token cap is set to `2` or `3` in a new isolated native marker
- prompt remains fixed and short
- timeout remains unchanged or tighter
- memory and cleanup are recorded
- no normal UI state is touched
- docs identify the exact new risk being tested

## Normal UI Integration Gate

Normal UI integration is a later phase and requires a separate decision. Before
that phase:

- isolated diagnostic screen must complete multiple bounded runs without fresh
  crash evidence
- cleanup must be reliable
- fallback behavior must be explicit and user-visible
- memory pressure must be measured
- normal chat state mutation must be reviewed independently
