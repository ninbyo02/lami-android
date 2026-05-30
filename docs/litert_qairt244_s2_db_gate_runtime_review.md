# QAIRT244 Phase S2 DB Gate Runtime Review

Date: 2026-05-30

Scope: static review only. This document does not implement code, run runtime
probes, install APKs, change native code, or connect TTS/Markdown/streaming.

## Gate Location

`ChatScreen.kt` defines the S2 DB gate as a top-level constant near the UI
layout constants:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S2_DB = false
```

The S2 branch is inside the existing S1 gate under `InferenceTarget.LOCAL`.
The order is:

1. reject active local inference;
2. reject image input;
3. capture `requestPrompt`;
4. reject blank prompt;
5. enter S1 gate with `shouldEnterNpuStandardRouteS1(...)`;
6. run `NpuStandardRouteS1Bridge().run()`;
7. set `npuStandardRouteS1DisplayText`;
8. if `ENABLE_NPU_STANDARD_ROUTE_S2_DB`, prepare and persist the S2 DB
   candidate;
9. return before the dev hidden QAIRT route and normal local route.

## Gate False Behavior

When `ENABLE_NPU_STANDARD_ROUTE_S2_DB=false`, the S2 block is skipped.

The current S1 behavior is preserved:

- S1 bridge runs;
- transient `NPU STANDARD ROUTE S1` display is updated;
- input state is cleared;
- handler returns before existing dev hidden QAIRT and normal local route;
- no chat row is created;
- no user message row is created;
- no assistant message row is created.

This is the rollback state.

## Gate True Save Conditions

When `ENABLE_NPU_STANDARD_ROUTE_S2_DB=true`, S2 still saves only if:

- `NpuStandardRouteS2DbBridge().prepareSaveCandidate(...)` returns a mapping
  with `hasSaveCandidate=true`;
- the candidate was produced from a nonblank user prompt;
- the S1 result has `successCriteriaMet=true`;
- `saveCandidate.readyToPersist=true`.

`NpuStandardRouteS2DbMapper` returns no candidate for:

- blank user prompt;
- any S1 result where `successCriteriaMet=false`.

`successCriteriaMet=false` includes fallback, timeout, fresh crash, missing NPU
evidence, empty sanitized output, non-natural Japanese quality, or any S1
side-effect mismatch.

## Save Order

For a valid S2 candidate, `ChatScreen.kt` performs DB work in this order:

1. create a chat if `effectiveChatId == null`;
2. update `effectiveChatId` and `pendingNavigateChatId` for a new chat;
3. insert the user `Message`;
4. insert the assistant `Message` via `createAssistantMessage(...)`.

The user row uses:

```kotlin
Message(
    chatId = resolvedChatId,
    message = saveCandidate.userMessage.text,
    isSendbyMe = saveCandidate.userMessage.isSendByMe,
)
```

The assistant row uses:

```kotlin
createAssistantMessage(
    chatId = resolvedChatId,
    response = saveCandidate.assistantMessage.text,
    localSourceSummary = saveCandidate.assistantMessage.sourceDisplayText,
)
```

The assistant body is the sanitized response text. The S1 display/source text is
kept in `localSourceSummary`, not in `Message.message`.

## Failure Behavior

When S2 does not have a save candidate:

- no chat row is created by S2;
- no user message row is inserted by S2;
- no assistant message row is inserted by S2;
- the handler falls through to the existing S1 display-only cleanup and returns;
- the dev hidden QAIRT route and normal local route are still skipped because
  the S1 gate already took ownership.

This preserves the S2 policy that failed NPU attempts do not create DB rows.

## Disconnected Surfaces

The S2 branch does not call:

- `LocalStreamingRunner`;
- `runWithHeldEngine`;
- streaming placeholder helpers;
- Markdown processors;
- TTS speak/queue helpers;
- `Backend.NPU` persistence.

The only new side effect behind the S2 gate is DB persistence of one user row
and one assistant row for a successful candidate.

## Rollback

Rollback is one-line:

```kotlin
private const val ENABLE_NPU_STANDARD_ROUTE_S2_DB = false
```

With the S2 gate off, the route returns to S1 display-only behavior. With the
S1 gate off, the app returns to the existing local/Ollama route.

No migration is required. Failure runs should not leave rows to clean up.

## Runtime Check Commands

Do not run these as part of this review. They are the proposed manual commands
for the later S2 gate ON verification.

Build and install the custom build:

```bash
./gradlew :app:assembleCustomBuildExperimentDebug
adb -s 192.168.52.52:34437 install -r app/build/outputs/apk/customBuildExperiment/debug/app-customBuildExperiment-debug.apk
```

Launch the app:

```bash
adb -s 192.168.52.52:34437 shell am start -n io.github.ninbyo02.lami.customnpu/.MainActivity
```

After sending a local prompt from ChatScreen, verify:

- `NPU STANDARD ROUTE S1` is visible;
- the assistant text is `こんにちは。`;
- the active conversation contains exactly one user row for the prompt;
- the active conversation contains exactly one assistant row with `こんにちは。`;
- no diagnostic block appears as the assistant message body;
- no TTS starts;
- no Markdown-specific rendering behavior is required;
- no streaming placeholder or partial response appears.

Suggested DB inspection should be read-only. Prefer app UI inspection first.
If shell DB inspection is needed later, document the exact package path and DB
filename before running commands.

## Failure Runtime Check

For a later failure-path check, use a controlled failure such as gate-on with an
S1 result that does not satisfy `successCriteriaMet`, if such a test hook exists.
Expected result:

- transient S1/S2 failure display only;
- no new chat row;
- no user row;
- no assistant row;
- existing local/Ollama route remains skipped because S1 gate owns the send.

## Test Coverage To Keep

Current static/unit coverage should continue to include:

- S2 gate off returns false even when a save candidate exists;
- S2 gate on returns true only when `mapping.hasSaveCandidate=true`;
- S2 bridge returns no candidate for failed S1;
- S2 bridge returns no candidate for blank prompt;
- candidate side effects are `dbConnected=true` and
  `tts=false/markdown=false/streaming=false/backendNpuPersisted=false`.
