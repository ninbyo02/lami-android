# QAIRT 2.44 NPU Diagnostic Chat Design

Date: 2026-05-23

Scope: `customBuildExperimentDebug` only. This design prepares an isolated NPU
diagnostic screen after the QAIRT 2.44 lower-level one-token verifier succeeded
on Nubia Z70S Ultra / SM8750. It does not connect NPU to the normal chat UI.

## Current Evidence

The isolated lower-level verifier has already shown:

- `Engine.initialize`: success
- `Engine.close` / native cleanup: success
- QNN HTP / V79 / FastRPC path: success
- `RunDecode`: success
- hard cap: `maxOutputTokens=1`
- output: `!`
- token timing verifier artifact:
  `artifacts/qairt244_token_timing_verifier/20260523_062321/`

The normal `ChatScreen` path remains disconnected from NPU.

## Skeleton Implementation

Activity:

```text
io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity
```

Source:

```text
app/src/customBuildExperimentDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuDiagnosticChatActivity.kt
```

Manifest:

```text
app/src/customBuildExperimentDebug/AndroidManifest.xml
```

The Activity is declared only in the `customBuildExperimentDebug` manifest and
has no normal navigation entry. It can be launched explicitly by ADB or a future
debug-only launcher.

The screen displays:

- title: `NPU Diagnostic Chat`
- current flavor and application id
- native library directory
- fixed model path
- fixed prompt: `Hi`
- fixed `maxOutputTokens=3` for the short multi-token diagnostic path
- disabled prompt field
- `DEV confirm isolated 3-token NPU smoke` checkbox
- `Run 3-token smoke` button, disabled until the DEV checkbox is checked
- disabled `Normal ChatScreen NPU route disabled` button
- last result summary from `files/qairt244_short_multitoken_smoke_result.txt`
- timing fields from the same result file
- native diagnostic summary from `files/qairt244_native_diag.txt`
- warm/cold-start cleanup baseline status
- safety status showing normal UI route disconnected

## Data Sources

The screen reads app-private files produced by the already isolated runner:

```text
files/qairt244_short_multitoken_smoke_result.txt
files/qairt244_native_diag.txt
```

The guarded button is implemented only in the `customBuildExperimentDebug`
source set and calls the isolated lower-level short multi-token wrapper:

```text
Qairt244ShortMultitokenSmoke.run(...)
```

The implementation does not call:

- high-level `generateResponse`
- any normal `ChatScreen` inference path
- `selectedPath=npu` in the normal route

`Engine.initialize` and `RunDecode` can be reached only if the DEV checkbox is
explicitly checked and the guarded button is clicked. The default launch path
remains read-only.

## Launch

Manual launch candidate:

```bash
adb shell am start \
  -n io.github.ninbyo02.lami.customnpu/io.github.ninbyo02.lami.ui.screens.home.NpuDiagnosticChatActivity
```

This should remain a debug-only launch path until another explicit approval
adds a custom diagnostic launcher.

## Read-Only Launch Verification

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_readonly/20260523_065214/
```

Result:

- `customBuildExperimentDebug` APK assembled and installed on Nubia `NX733J`.
- `NpuDiagnosticChatActivity` launched by explicit ADB component.
- `window.xml` confirmed package `io.github.ninbyo02.lami.customnpu`.
- title `NPU Diagnostic Chat` was visible.
- prompt display stayed fixed to `Hi`.
- `maxOutputTokens=1` was visible.
- `RUN 1-TOKEN SMOKE DISABLED` was visible with `enabled=false`.
- last isolated verifier result was visible:
  `result=success`, `output=!`, `elapsed_ms=1053`, `npu_backend=NPU`.
- timing fields were visible:
  `engine_create=905 ms`, `session_create=0 ms`, `prefill=13 ms`,
  `decode=22 ms`, `cleanup=111 ms`.
- native diagnostic summary was visible:
  `QNN=true`, `HTP=true`, `V79Stub=true`, `FastRPC=true`.
- screenshot captured: `screenshot.png`.

No launch extra was provided, the disabled button was not clicked, and this
verification did not run `Engine.initialize`, `RunDecode`, generation, or the
normal chat UI path.

## Future Run Button Design

The first guarded button implementation is now present, but it defaults to a
non-runnable state until the DEV confirmation checkbox is checked. It must
continue to:

- stay `customBuildExperimentDebug` only
- require explicit confirmation or developer-only extra
- keep prompt fixed to `Hi`
- keep `maxOutputTokens` hard-capped to `3`
- enforce a 30 second app-side timeout marker
- use a running lock to prevent double execution
- write result/native diag/stage/logcat/tombstone artifacts
- preserve stale tombstone classification
- never write to normal chat DB, TTS, Markdown, or message UI state

The UI button is not a replacement for the host runner. Host-side execution with
forced cleanup and tombstone collection should still use:

```bash
bash scripts/run_qairt244_short_multitoken_smoke.sh \
  --artifact artifacts/qairt244_short_multitoken_entrypoint_build/20260523_073526 \
  --run
```

## Short Multi-Token Follow-up

The next smoke was run as a separate short multi-token path with
`maxOutputTokens=3`. Run artifacts:

```text
artifacts/qairt244_short_multitoken_smoke/20260523_075743/
artifacts/qairt244_short_multitoken_smoke/20260523_085004/
```

Current classification:

```text
result=success 2/2
output=! How Hi
fresh_crash=false
```

## Guarded Run Button Verification

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_guarded_run/20260523_094457/
```

Result:

- `customBuildExperimentDebug` APK assembled and installed on Nubia `NX733J`.
- `NpuDiagnosticChatActivity` launched by explicit ADB component.
- `DEV confirm isolated 3-token NPU smoke` was visible and unchecked.
- `RUN 3-TOKEN SMOKE` was visible with `enabled=false`.
- `NORMAL CHATSCREEN NPU ROUTE DISABLED` remained disabled.
- `prompt=Hi`, `maxOutputTokens=3`, last `output=! How Hi`, and timing fields
  were visible.
- screenshot captured: `screenshot.png`.

No button was clicked. This verification did not run `Engine.initialize`,
`RunDecode`, generation, or the normal chat UI path.

## Guarded UI Smoke Run

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_guarded_ui_run/20260523_100701/
```

Result:

- The Diagnostic Chat UI was launched from `customBuildExperimentDebug`.
- The guarded UI path produced a successful short multi-token result:
  `result=success`, `output=! How Hi`, `max_output_tokens=3`.
- Timing:
  `engine_create=883 ms`, `prefill=13 ms`, `decode=64 ms`,
  `cleanup=129 ms`, total `1090 ms`.
- `npu_backend=NPU`.
- Native diagnostics include the expected
  `QNN_HTP_V79_FastRPC_native_diag` evidence and
  `before RunDecode SetMaxOutputTokens(3)`.
- Tombstone classification: `stale-tombstone-ignored`; no current-run id was
  present in the selected old tombstone/dropbox body.
- The result view screenshot shows `result=success`, `output=! How Hi`, and
  `npu_backend=NPU`.

UI operation caveat: an earlier troubleshooting tap sequence caused an
in-memory completed guarded run state to appear in `screenshot_armed.png`.
The final captured run in this artifact is the documented result above. No
normal UI path was connected during either diagnostic-only interaction.

## Automated Guarded UI Smoke Runner

Script:

```text
scripts/run_qairt244_npu_diagnostic_chat_ui_smoke.sh
```

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_smoke/20260523_102810/
```

Result:

- The runner selected the non-emulator Nubia device
  `192.168.52.52:37859`.
- It assembled and installed `customBuildExperimentDebug`.
- It launched only `NpuDiagnosticChatActivity`.
- It checked `DEV confirm isolated 3-token NPU smoke` once.
- It tapped `RUN 3-TOKEN SMOKE` once.
- The guarded UI path returned:
  `result=success`, `output=! How Hi`, `max_output_tokens=3`.
- Timing:
  `engine_create=986 ms`, `prefill=27 ms`, `decode=97 ms`,
  `cleanup=155 ms`, total `1268 ms`.
- `npu_backend=NPU` with
  `QNN_HTP_V79_FastRPC_native_diag` evidence.
- Tombstone classification: `stale-tombstone-ignored`.
- `screenshot_before.png` and `screenshot_after.png` were captured.
- `window_before.xml` came from `uiautomator`; `window_after.xml` used the
  runner's text fallback because `uiautomator` reported
  `could not get idle state` after the completed run.

The runner records `ui_dev_checkbox_taps=1` and `ui_run_button_taps=1` in
`summary.md`. It does not connect the normal `ChatScreen`, does not set the
normal `selectedPath=npu` route, does not call high-level `generateResponse`,
and does not use streaming generation.

## Multi-Run UI Stability Runner

Script:

```text
scripts/run_qairt244_npu_diagnostic_chat_ui_multirun.sh
```

Attempt artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_110017/
```

Observed result records:

- run1: `result=success`, `output=! How Hi`, `max_output_tokens=3`,
  `decode_elapsed_ms=64`
- run2: `result=success`, `output=! How Hi`, `max_output_tokens=3`,
  `decode_elapsed_ms=65`
- NPU evidence remained `QNN_HTP_V79_FastRPC_native_diag`
- tombstone classification for both captured runs:
  `stale-tombstone-ignored`

This attempt exposed a runner wait bug: the script accepted an earlier
`state=success` line while a later guarded UI marker still contained
`state=started`. The runner is now fixed to wait for the last
`qairt244_diagnostic_chat_guarded_run_v1` marker to reach `success`,
`failure`, or `timeout`, and to parse uiautomator one-line XML before extracting
tap bounds. No additional rerun was performed in this turn to avoid exceeding
the requested two-run scope.

## Fixed Multi-Run UI Runner Verification

Artifact:

```text
artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/
```

Result:

- run1: `result=success`, `output=! How Hi`, `max_output_tokens=3`,
  `decode_elapsed_ms=96`
- run2: `result=success`, `output=! How Hi`, `max_output_tokens=3`,
  `decode_elapsed_ms=70`
- run1 final guarded marker: `state=success`
- run2 final guarded marker: `state=success`
- `state=started` was not left as the final state for either run
- `npu_backend=NPU` with `QNN_HTP_V79_FastRPC_native_diag` evidence
- both tombstone classifications: `stale-tombstone-ignored`

The fixed runner verified the two-run Diagnostic Chat UI path without touching
the normal `ChatScreen`, the normal `selectedPath=npu` route, high-level
`generateResponse`, or streaming generation.

## Result Viewer Refresh

The `customBuildExperimentDebug` `NpuDiagnosticChatActivity` now includes a
read-only result viewer for the latest fixed multi-run runner evidence.

Displayed fields:

- latest runner artifact:
  `artifacts/qairt244_npu_diagnostic_chat_ui_multirun/20260523_114243/`
- run1 result, output, elapsed, and decode elapsed
- run2 result, output, elapsed, and decode elapsed
- final guarded marker state for each run
- whether `state=started` remained as the final state
- after-10s TOTAL PSS and Native Heap PSS
- stale/fresh tombstone classification
- fresh crash status
- normal `ChatScreen` route disabled status
- normal `selectedPath=npu` route disabled status

The `Refresh result view` button only rereads app-private diagnostic files and
the committed latest verification values shown above. It updates the on-screen
`Last result`, `Timing`, `Native diag`, `Latest runner`, and `Route guards`
sections. It does not call the lower-level smoke entrypoint, does not initialize
the engine, and does not run NPU generation.

App-private runner summary file:

```text
files/qairt244_diagnostic_runner_summary.txt
```

If present, this file is parsed as `key=value` lines and replaces the committed
latest verification values. This keeps the viewer useful for future local
runner refreshes without connecting the normal chat route.

Host sync script:

```text
scripts/sync_qairt244_npu_diagnostic_summary_to_app.sh
```

The sync script auto-detects the latest Diagnostic Chat UI runner artifact, or
accepts `--artifact <path>`, then writes:

```text
/data/user/0/io.github.ninbyo02.lami.customnpu/files/qairt244_diagnostic_runner_summary.txt
```

The script records `npu_generation=not_run`, `engine_initialize=not_run`,
`run_decode=not_run`, and `activity_launch=not_run` in the synced file. It only
uses ADB file copy plus `run-as`; it does not launch the Activity and does not
execute generation.

Verified sync artifact:

```text
artifacts/qairt244_npu_diagnostic_summary_sync/20260523_121424/
```

The synced app-private file matched `synced_key_value.txt` and is ready for the
screen's next read-only Refresh action.

Read-only display verification:

```text
artifacts/qairt244_npu_diagnostic_summary_readonly_verify/20260523_122946/
```

The Diagnostic Chat Activity was cold-launched after syncing
`qairt244_diagnostic_runner_summary.txt`. The UI showed
`source=app_private_file`, the latest multi-run artifact, run1/run2 result and
timing fields, final guard state, after-10s memory fields, stale tombstone
classification, and disabled normal route status. The DEV checkbox and guarded
run button were not pressed.

## Summary Freshness Indicator

Freshness threshold: `86400` seconds, or 24 hours.

The sync script now adds these metadata keys to
`qairt244_diagnostic_runner_summary.txt`:

- `synced_at_epoch_ms`
- `synced_at_local`
- `source_artifact`
- `source_artifact_timestamp`
- `source_artifact_age_seconds`
- `source_artifact_age_human`
- `freshness_status`
- `freshness_warning`
- `freshness_threshold_seconds`
- `summary_source`

Freshness states:

- `fresh`: source artifact timestamp is parseable and no more than 24 hours old
- `stale`: source artifact timestamp is parseable and older than 24 hours
- `unknown`: source artifact timestamp cannot be parsed

Verification artifact:

```text
artifacts/qairt244_npu_diagnostic_summary_freshness/20260523_124234/
```

Observed result:

- `synced_at_local=2026-05-23 12:42:34 +0900`
- `source_artifact_timestamp=20260523_114243`
- `source_artifact_age_human=59m 51s`
- `freshness_status=fresh`
- `freshness_warning=none`

The Activity displayed the freshness fields from the app-private file. The
verification did not press the DEV checkbox or guarded run button, and did not
run Engine.initialize, RunDecode, or token generation.

## Short Prompt DEV Guard Spec

STEP 2 is not implemented yet. The pre-enable specification is fixed in:

```text
docs/litert_qairt244_diagnostic_short_prompt_guard_spec.md
```

Summary:

- `customBuildExperimentDebug` Diagnostic Chat only
- initial prompt remains `Hi`
- future editable prompt maximum: 32 characters
- allowed characters: ASCII letters, digits, space, `. , ? ! ' - _`
- empty strings, newlines, tabs, control characters, emoji, and non-ASCII
  symbols are rejected for the first editable phase
- `maxOutputTokens=3` remains hard fixed
- timeout remains 30 seconds
- DEV checkbox, explicit confirmation, running lock, and artifact collection
  are required
- normal `ChatScreen`, normal `selectedPath=npu`, high-level
  `generateResponse`, and streaming remain forbidden

## Non-Goals

This work intentionally does not:

- add `selectedPath=npu` to the normal route
- modify `ChatScreen` inference execution
- enable streaming generation
- change `standard`, `galleryStackExperiment`, `npuExperiment`, or `release`
- modify `app/src/main/jniLibs`
