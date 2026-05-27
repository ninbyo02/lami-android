# Edge Gallery Findings

Source checkout:
`/home/sato/project/google-ai-edge-gallery`

Relevant files:

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/ChatViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/ModelManagerViewModel.kt`

Findings:

1. Engine lifecycle and conversation lifecycle are separated.
   `LlmChatModelHelper.kt` defines `LlmModelInstance(val engine: Engine, var
   conversation: Conversation)`. Initialization creates one `Engine`, calls
   `engine.initialize()`, then creates a `Conversation`. Reset closes only the
   old conversation and creates a new conversation from the existing engine.

2. Streaming delivery is direct callback delivery.
   `runInference(...)` calls `conversation.sendMessageAsync(...)` with a
   `MessageCallback`. `onMessage` pushes partial chunks to `resultListener`;
   `onDone` sends a terminal `done=true` signal; non-cancellation errors go to
   `onError`.

3. ViewModel state is updated incrementally from callbacks.
   `LlmChatViewModel.generateResponse(...)` launches on
   `viewModelScope.launch(Dispatchers.Default)`, sets `inProgress` and
   `preparing`, adds a loading message, and appends streaming chunks into the
   last assistant text message.

4. Stop is cooperative cancel, not a hard lifecycle boundary.
   `stopResponse(...)` calls `conversation.cancelProcess()`. The UI state is
   set to not-in-progress before native cancellation is proven complete.

5. Reset is conversation reset, not process isolation.
   `resetSession(...)` calls `stopResponse(...)`, then retries
   `runtimeHelper.resetConversation(...)` with a 200 ms delay until it succeeds.
   This closes/recreates the conversation but keeps the app process and engine
   alive.

6. Error handling can cleanup and reinitialize the model.
   `handleError(...)` removes the loading message, adds an error message, calls
   `cleanupModel(...)`, then reinitializes the model and records a warning.

Relevance to Lami:

- Useful: explicit per-turn callback boundaries, conversation reset, cleanup
  listener, and model cleanup/reinitialize as a recovery concept.
- Risky for current Lami 512: Gallery assumes cooperative callbacks return.
  It does not provide a hard process boundary equivalent to force-stop, and it
  does not add an app-level timeout wrapper around generation in the inspected
  chat path.
- Not adopted now: Gallery's streaming UI message insertion, persisted chat
  session model, normal chat renderer, and user-facing stop/retry behavior.
