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

The screen currently displays:

- title: `NPU Diagnostic Chat`
- current flavor and application id
- native library directory
- fixed model path
- fixed prompt: `Hi`
- fixed `maxOutputTokens=1`
- disabled prompt field
- disabled `Run 1-token smoke` button
- last result summary from `files/qairt244_single_token_smoke_result.txt`
- timing fields from the same result file
- native diagnostic summary from `files/qairt244_native_diag.txt`
- safety status showing normal UI route disconnected

## Data Sources

The skeleton is read-only. It reads app-private files produced by the already
isolated runner:

```text
files/qairt244_single_token_smoke_result.txt
files/qairt244_native_diag.txt
```

It does not call:

- `Engine.initialize`
- `RunDecode`
- `Conversation`
- high-level `generateResponse`
- any normal `ChatScreen` inference path

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

The run button is intentionally disabled in the current skeleton. Before it is
enabled, the implementation must:

- stay `customBuildExperimentDebug` only
- require explicit confirmation or developer-only extra
- keep prompt short and bounded
- keep `maxOutputTokens` hard-capped
- enforce timeout in the runner
- write result/native diag/stage/logcat/tombstone artifacts
- preserve stale tombstone classification
- never write to normal chat DB, TTS, Markdown, or message UI state

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

The Diagnostic Chat remains read-only. It should not expose a runnable
multi-token button from isolated smoke success alone. The next step should be a
separate diagnostic-only UI design update or another explicitly bounded smoke,
still disconnected from the normal `ChatScreen` route.

## Non-Goals

This work intentionally does not:

- add `selectedPath=npu` to the normal route
- modify `ChatScreen` inference execution
- enable streaming generation
- change `standard`, `galleryStackExperiment`, `npuExperiment`, or `release`
- modify `app/src/main/jniLibs`
