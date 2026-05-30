# Broadcast / Receiver Review

Prompt 2 broadcast:

```text
Broadcasting: Intent { act=io.github.ninbyo02.lami.action.STANDARD_HIDDEN_QAIRT244_PROMPT flg=0x10400000 cmp=io.github.ninbyo02.lami/.npu.StandardHiddenQairt244PromptReceiver (has extras) }
Broadcast completed: result=0, extras: Bundle[{callingUid=2000}]
```

Receiver implementation:

- `StandardHiddenQairt244PromptReceiver.onReceive()` uses `goAsync()`.
- It starts a new `Thread`.
- The worker calls `handle(context.applicationContext, intent)`.
- `pendingResult.finish()` runs in `finally`.
- Inside `handle`, `runBlocking` calls
  `DevOnlyNpuChatScreenBlockedBranch.runForChatScreen(...)`.

Interpretation:

- The broadcast was accepted and returned `result=0`.
- This is not a foreground/background broadcast rejection.
- The `after_dispatch` process snapshot is taken after the `am broadcast`
  command returns. It proves the process was gone by the time broadcast
  returned, not necessarily at the instant the broadcast was enqueued.
- Because prompt 2 state and native diagnostics were written, the receiver
  worker did start and reached the native path.

Conclusion: the failure sits inside the receiver worker/native execution
window after broadcast acceptance and before terminal result/cleanup.
