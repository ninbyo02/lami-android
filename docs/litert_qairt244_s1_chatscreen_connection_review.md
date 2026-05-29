# QAIRT244 Phase S1 ChatScreen Connection Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, or change native code.

## Baseline

Phase S1 has these main-source pure pieces:

- `NpuStandardRouteS1Contract`
- `NpuStandardRouteS1Mapper`
- `NpuStandardRouteS1Invoker`
- `NpuStandardRouteS1Bridge`

The bridge currently has no ChatScreen dependency. It returns
`NpuStandardRouteS1Result` through the `Invoker -> Mapper` path. It is still a
mock/dev-only-success-equivalent bridge and is not connected to runtime NPU
execution.

## Minimal Insertion Point

The safest insertion point is inside `ChatScreen.kt` under
`InferenceTarget.LOCAL`, after:

- image input has been rejected for local inference;
- `requestPrompt` has been captured;
- blank prompt has returned;

and before any of the following:

- current dev hidden QAIRT244 route branch;
- new chat creation;
- `viewModel.insert(...)` user-message write;
- `insertAssistantMessageAndReturnId(...)` assistant write;
- `runWithHeldEngine(...)`;
- `DefaultLocalStreamingRunner`;
- streaming placeholder creation;
- TTS start logic.

This position lets S1 consume the current prompt and show a transient result
without entering the existing DB-backed local route.

## Existing Route Impact

Existing Ollama/server route:

- no S1 changes should be placed under `InferenceTarget.SERVER`;
- remote request job, attachment handling, and server DB flow remain unchanged.

Existing local route:

- the S1 gate must default to off;
- when off, execution must fall through to the current local path exactly as
  before;
- when on, S1 must return before the current dev hidden QAIRT244 branch and
  before the normal local route.

Existing dev hidden QAIRT244 route:

- do not reuse its DB-backed `runDevQairt244Sm8750NpuChatScreenRouteViaReflection`
  branch for S1;
- it currently creates/uses chat rows and assistant messages, so it is not the
  right S1 insertion target.

## DB Avoidance

S1 must run before:

- `viewModel.insertChatAndReturnId(...)`;
- `viewModel.insert(...)` for the user prompt;
- `viewModel.insertAssistantMessageAndReturnId(...)`;
- streaming placeholder insert/finalize helpers.

S1 should not require `effectiveChatId`. If no chat exists, S1 should still be
able to display a transient result and leave `effectiveChatId` unchanged.

Required S1 result flags:

```text
db=false
conversation_history_saved=false
```

## TTS Avoidance

S1 must not call:

- `ttsController.speak(...)`;
- `ttsController.speakQueued(...)`;
- streaming sentence TTS helpers;
- `maybeReleaseHeldEngineForTtsPlayback()`.

Calling `stopTtsWithCleanup(...)` before an S1 run is optional and should be
treated as UI cleanup, not TTS connection. The first S1 implementation can
avoid stop/TTS ownership entirely unless an existing running inference must be
cancelled.

Required S1 result flag:

```text
tts=false
```

## Markdown Avoidance

S1 must not call:

- `processEdgeGalleryCompatibleMarkdown(...)`;
- final Markdown normalization helpers;
- streaming Markdown chunk append helpers;
- code fence repair paths.

S1 should display `result.displayText` as plain text. Markdown is a Phase S3
concern.

Required S1 result flag:

```text
markdown=false
```

## Streaming Avoidance

S1 must not call:

- `LocalStreamingRunner.run(...)`;
- `runWithHeldEngine(...)`;
- `onPartial`;
- `streamLocalAssistantPreviewTextToUi(...)`;
- streaming placeholder upsert/finalize helpers.

S1 should set no streaming placeholder and should not update
`localStreamingResponseText` as a partial stream. If a visible value is needed,
use a dedicated transient S1 display state.

Required S1 result flag:

```text
streaming=false
```

## S1 Display Label

Use a label that is clearly not a normal assistant message:

```text
NPU STANDARD ROUTE S1
```

Minimum display fields:

```text
status=<success|failure>
reason=<reason>
sanitized_output=<text>
run_decode_reached=<true|false>
npu_backend_evidence=<evidence>
fallback_used=<true|false>
timeout=<true|false>
fresh_crash=<true|false>
quality_classification=<classification>
db=false
tts=false
markdown=false
streaming=false
backend_npu_persisted=false
conversation_history_saved=false
```

The display should be transient and visually separate from saved conversation
history. It can be implemented as a small debug/developer panel or as a local
state block near the composer before any DB-backed chat insertion is introduced.

## Gate Conditions

S1 gate must be explicit and default off.

Required gate checks:

- developer/debug gate is enabled;
- selected inference target is `LOCAL`;
- no image inputs are selected;
- prompt is not blank;
- S1 bridge returns `successCriteriaMet=true`;
- `raw_dialog_tail_variant_b`;
- requested/effective `max_output_tokens=32`;
- `run_decode_reached=true`;
- `npu_backend_evidence` contains the normalized S1 evidence;
- `fallback_used=false`;
- `timeout=false`;
- `fresh_crash=false`;
- `quality_classification=natural_japanese`;
- side-effect flags remain false.

## Rollback

Rollback is gate-off only.

Rollback behavior:

- turning the S1 gate off returns to existing server/local/dev hidden route
  behavior;
- no DB rows need cleanup because S1 does not write DB;
- no TTS state needs cleanup because S1 does not start TTS;
- no Markdown or streaming state needs cleanup;
- no Backend.NPU setting is persisted;
- no held engine or Conversation state is modified by S1.

Rollback triggers:

- any S1 result where `successCriteriaMet=false`;
- any DB/TTS/Markdown/streaming flag becomes true;
- any fallback, timeout, or fresh crash;
- missing NPU backend evidence;
- blank `displayText`.

## Implementation Candidate Files

First implementation candidate:

- `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt`

Possible supporting tests:

- `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1BridgeTest.kt`
- a new small ChatScreen-state/presenter test if the transient display is
  extracted from the composable.

Already present and reusable:

- `NpuStandardRouteS1Contract.kt`
- `NpuStandardRouteS1Mapper.kt`
- `NpuStandardRouteS1Invoker.kt`
- `NpuStandardRouteS1Bridge.kt`

## Files Not To Touch In S1

- `LocalStreamingRunner.kt`
- `LocalInferenceEngineHolder.kt`
- `HeldEngineLifecycleBridge.kt`
- `OllamaViewModel.kt`
- `ChatRepository.kt`
- DB entity/DAO files
- `AndroidTtsController.kt`
- `SpeechTextBuilder.kt`
- `MarkdownStreamingMode.kt`
- `MarkdownCodeRepair.kt`
- Settings/backend preference files
- native/JNI files

## First Minimal Implementation Sketch

1. Add a disabled-by-default S1 boolean gate in `ChatScreen.kt` local UI state
   or developer-only settings state.
2. In `InferenceTarget.LOCAL`, after prompt/image validation and before DB
   writes, check the S1 gate.
3. If gate is off, fall through unchanged.
4. If gate is on, call `NpuStandardRouteS1Bridge().run()`.
5. Store the returned `NpuStandardRouteS1Result.displayText` in transient UI
   state.
6. Show the transient block with label `NPU STANDARD ROUTE S1`.
7. Clear `userPrompt` only if the result is intentionally consumed by S1.
8. Return from the click handler without DB/TTS/Markdown/streaming calls.
9. On failure, show the same transient block with failure diagnostics and do
   not fall back silently to the normal local route.

This keeps S1 isolated: one visible standard ChatScreen selection experiment,
no persistence, no downstream feature connection, and gate-off rollback.

## Gate ON Display Check Result

Date: 2026-05-30

S1 Gate ON display was checked on device with the following temporary local
change:

```text
ENABLE_NPU_STANDARD_ROUTE_S1=true
```

Check flow:

- installed `standardDebug`;
- launched `MainActivity`;
- selected the Local send path;
- sent a text prompt;
- confirmed that ChatScreen displayed the transient S1 block.

Observed UI:

```text
NPU STANDARD ROUTE S1
こんにちは。
```

Rollback was completed by restoring:

```text
ENABLE_NPU_STANDARD_ROUTE_S1=false
```

and reinstalling `standardDebug`. After rollback, `git status -sb` was clean.

The result is a display-only S1 success. Per S1 design, this path still does
not connect DB, TTS, Markdown, streaming, `Backend.NPU` persistence, or
conversation history save.
