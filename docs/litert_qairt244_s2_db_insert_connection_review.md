# QAIRT244 Phase S2 DB Insert Connection Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, change native code, or connect DB inserts.

## Baseline

The current S1 standard UI route is display-only:

```text
ChatScreen -> S1 Gate -> NpuStandardRouteS1Bridge -> RealProvider -> transient display
```

The S1 branch in `ChatScreen.kt` runs after image rejection, prompt capture, and
blank prompt rejection. It currently returns before:

- dev hidden QAIRT route;
- new chat creation;
- user message insert;
- assistant message insert;
- `runWithHeldEngine`;
- streaming placeholder creation;
- Markdown processing;
- TTS.

S2 DB candidate pieces now exist but are not connected:

- `NpuStandardRouteS2DbContract`
- `NpuStandardRouteS2DbMapper`
- `NpuStandardRouteS2DbBridge`

These pieces only create a save candidate. They do not insert rows.

## Existing User Message Insert

Existing local and dev hidden local routes create a `Message` with:

```kotlin
Message(
    chatId = resolvedChatId,
    message = requestPrompt,
    isSendbyMe = true,
)
```

The normal local route performs the insert through:

```kotlin
withContext(Dispatchers.IO) {
    viewModel.insert(
        Message(
            chatId = resolvedChatId,
            message = requestPrompt,
            isSendbyMe = true,
        )
    )
}
```

`OllamaViewModel.insert(message)` dispatches user messages to
`chatRepository.insert(message)`, which calls `messageDao.insertMessage`.

S2 should reuse the same user message shape, but it should not use the async
`viewModel.insert(...)` wrapper if the implementation needs strict ordering
between user and assistant rows. A direct `withContext(Dispatchers.IO)` sequence
using existing suspend APIs is preferable for the first S2 connection.

## Existing Assistant Message Insert

Assistant rows are created with `createAssistantMessage(...)`:

```kotlin
createAssistantMessage(
    chatId = resolvedChatId,
    response = assistantText,
    latestInferenceStats = stats,
    localSourceSummary = sourceSummary,
    generationTimeMs = elapsedMs,
)
```

The insert path is:

```kotlin
viewModel.insertAssistantMessageAndReturnId(createAssistantMessage(...))
```

This eventually calls `insertAssistantMessageAndAutoTitleAndReturnId`, which can
auto-title a temporary chat from the first user/non-empty message.

S2 should use `createAssistantMessage(...)` for the assistant row, but the
assistant body must be the clean S2 candidate text:

```text
saveCandidate.assistantMessage.text
```

Do not persist the S1 diagnostic display block as the assistant message body.
If diagnostics are stored in S2, put them in `localSourceSummary` or another
metadata field, not in `Message.message`.

## Existing Chat Creation Timing

Existing local routes create a chat when `effectiveChatId == null`:

```kotlin
val newChatId = withContext(Dispatchers.IO) {
    viewModel.insertChatAndReturnId(
        Chat(title = "New chat", titleSource = TitleSource.TEMP)
    )
}
effectiveChatId = newChatId
pendingNavigateChatId = newChatId
currentChatId = newChatId
```

S2 should reuse the same chat creation behavior only after the S2 save
candidate is ready. This preserves the current S2 policy: failure produces no
DB rows and no empty temp chat.

## Minimal S2 Success Insert Position

Recommended first connection point:

1. Stay inside the existing S1 gate in `InferenceTarget.LOCAL`.
2. Run `NpuStandardRouteS1Bridge().run()`.
3. Set the transient S1 display text as today.
4. Call `NpuStandardRouteS2DbBridge().prepareSaveCandidate(...)`.
5. If `saveCandidate == null`, return before DB work.
6. If `saveCandidate.readyToPersist == true`, create or resolve chat.
7. Insert the user row.
8. Insert the assistant row.
9. Clear input state.
10. Return before dev hidden QAIRT and normal local route.

This keeps the first S2 implementation success-only:

```text
S1 success -> S2 candidate -> chat row if needed -> user row -> assistant row
```

It also keeps failure side-effect-free:

```text
S1 failure -> no S2 candidate -> no chat row -> no user row -> no assistant row
```

## Failure Branch

When `NpuStandardRouteS2DbBridge` returns no candidate:

- keep `dbConnected=false` for the actual run;
- do not create a chat;
- do not insert a user message;
- do not insert an assistant failure message;
- keep the transient S1/S2 diagnostic display;
- return before the existing local route;
- keep TTS/Markdown/streaming disconnected.

Failure reasons currently expected from the candidate layer:

- `blank_user_message`
- `s1_success_criteria_not_met`

Provider-level failures such as `dev_only_entry_unavailable`, fallback, timeout,
fresh crash, empty output, or non-natural Japanese quality should surface as
`s1_success_criteria_not_met` at the S2 candidate boundary.

## Rollback

Rollback should be gate-only:

- S2 DB gate off returns to S1 display-only behavior.
- S1 gate off returns to the existing local/Ollama path.
- No migration is required.
- No cleanup is required for S2 failure because failure should write no rows.
- Successful S2 rows are ordinary conversation rows and do not require rollback
  deletion.

Rollback triggers:

- any DB row is written when `saveCandidate == null`;
- duplicate user/assistant rows are created;
- assistant body contains diagnostics instead of clean sanitized text;
- normal local/Ollama path behavior changes when S2 gate is off;
- TTS, Markdown, streaming, or `Backend.NPU` persistence becomes connected.

## Existing Route Isolation

S2 must not affect the existing local/Ollama path when its gate is off:

- no changes under `InferenceTarget.SERVER`;
- no changes to normal local route chat creation or inserts;
- no changes to dev hidden QAIRT route behavior;
- no changes to `RealNpuStandardRouteS1Provider`;
- no changes to held engine lifecycle;
- no changes to `LocalStreamingRunner`;
- no changes to TTS or Markdown code.

The S2 insertion must remain before the dev hidden QAIRT branch and before the
normal local route. Once S2 takes the gate, it must `return@IconButton`.

## Test Items

Unit-level tests:

- S2 bridge returns candidate for successful S1 result and nonblank prompt.
- S2 bridge returns no candidate for failed S1 result.
- S2 bridge returns no candidate for blank prompt.
- S2 candidate has `dbConnected=true`.
- S2 candidate has `tts=false`, `markdown=false`, `streaming=false`,
  `backendNpuPersisted=false`.

ChatScreen-level tests for the first DB connection:

- S2 gate off follows the existing route.
- S2 gate on with failed S1 writes no chat/user/assistant rows.
- S2 gate on with successful S1 creates a chat only when needed.
- S2 gate on with successful S1 inserts exactly one user row.
- S2 gate on with successful S1 inserts exactly one assistant row.
- Assistant row message is the sanitized response, not diagnostic display text.
- Existing-chat case inserts into the existing `effectiveChatId`.
- New-chat case sets `effectiveChatId` and `pendingNavigateChatId`.
- TTS/Markdown/streaming paths are not called.

Manual verification after implementation, not part of this review:

- `standardDebug` remains S1/S2 gated off.
- `customBuildExperimentDebug` can show the S1 block and persist one user/one
  assistant row only on natural Japanese NPU success.

## Open Decisions

- Whether to clear the input prompt on S2 failure. The safest first behavior is
  to keep it until the failure UX is explicitly chosen.
- Whether to store S2 diagnostics in `localSourceSummary` immediately or defer
  diagnostics persistence to a later metadata pass.
- Whether S2 should expose a distinct `conversation_history_saved=true` display
  flag after successful inserts, or keep that out of UI until the DB connection
  is runtime-verified.
