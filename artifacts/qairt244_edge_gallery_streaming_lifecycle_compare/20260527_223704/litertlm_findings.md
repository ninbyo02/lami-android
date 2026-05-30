# LiteRT-LM Findings

Source checkout:
`/home/sato/project/litert-custom-build/LiteRT-LM`

Relevant files:

- `kotlin/java/com/google/ai/edge/litertlm/Conversation.kt`
- `kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc`
- `runtime/core/session_basic.cc`
- `runtime/core/session_basic.h`
- `runtime/engine/engine.h`
- `runtime/framework/resource_management/threaded_execution_manager.cc`

Findings:

1. Kotlin `Conversation.sendMessageAsync` is callback based.
   It creates a JNI callback wrapper and calls `nativeSendMessageAsync(...)`.
   The callback exposes `onMessage`, `onDone`, and `onError`.

2. The Flow wrapper does not cancel native work by itself.
   `Conversation.sendMessageAsync(...): Flow<Message>` uses `callbackFlow`,
   but `awaitClose {}` is empty. Collector cancellation alone does not call
   `cancelProcess()`.

3. `cancelProcess()` is cooperative.
   Kotlin forwards to `nativeConversationCancelProcess`, native forwards to
   `conversation->CancelProcess()`, and C++ forwards to the session's
   cancellation path. This depends on the active decode path observing the
   cancellation flag or producing a terminal callback.

4. Basic streaming is prefill async followed by decode async.
   `GenerateContentStream(...)` runs `RunPrefillAsync(...)`; when prefill
   returns done, it starts `RunDecodeAsync(...)`. Decode streaming receives the
   shared cancellation atomic and max-output-token configuration.

5. Session destruction waits for completion before reset.
   `SessionBasic::~SessionBasic()` calls `WaitUntilDone()` before resetting
   executors. The default engine timeout is long, so a stuck decode/callback
   path can make close behavior unsuitable as an immediate hidden-run boundary.

6. The QAIRT244 editable prompt path is currently synchronous in Lami's custom
   JNI entrypoint.
   It creates engine/session scoped objects, calls `RunDecode` with
   `SetMaxOutputTokens(512)`, and records `Engine.close=unique_ptr_cleanup`
   only after a completed native path returns.

Relevance to Lami:

- A missing terminal callback or non-returning decode can leave Lami with
  pre-RunDecode evidence but no completed result, no cleanup evidence, and no
  backend evidence for the timed-out prompt.
- A purely cooperative cancel/close design is not enough to replace the passing
  force-stop boundary for 512.
- A safe Lami design should classify session/engine state as suspect if a
  bounded cleanup wait does not produce terminal callback and close evidence.
