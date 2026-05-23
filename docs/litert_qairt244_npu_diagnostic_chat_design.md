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

## Non-Goals

This work intentionally does not:

- add `selectedPath=npu` to the normal route
- modify `ChatScreen` inference execution
- enable streaming generation
- change `standard`, `galleryStackExperiment`, `npuExperiment`, or `release`
- modify `app/src/main/jniLibs`
