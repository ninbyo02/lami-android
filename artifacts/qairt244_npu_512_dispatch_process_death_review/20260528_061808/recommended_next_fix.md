# Recommended Next Fix

Next single fix candidate:

Add hidden-only receiver/native-worker terminal instrumentation before any new
NPU run.

Minimum scope:

- Add run-id scoped markers around
  `DevOnlyNpuChatScreenBlockedBranch.runForChatScreen(...)`:
  - `receiver_worker_before_runForChatScreen`
  - `receiver_worker_after_runForChatScreen`
  - `receiver_worker_throwable`
  - `receiver_worker_finally_finish`
- Persist those markers to a separate debug-only file under app files.
- Capture `Throwable` and `Error` text where possible without routing it to UI,
  DB, TTS, Markdown, streaming, assistant list, or selectedPath.
- Keep max output at 512 and do not change native guard or QAIRT.

Reason:

The current process-boundary instrumentation proves disappearance by
post-broadcast snapshot, but not whether the process exits before, during, or
after `runForChatScreen`. Receiver-worker terminal markers are the smallest
safe next boundary.
