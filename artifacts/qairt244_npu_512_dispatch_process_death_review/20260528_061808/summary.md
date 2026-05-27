# QAIRT244 NPU 512 Dispatch Process Disappearance Review

Source artifact:
`artifacts/qairt244_npu_max_output_512_sequential_soft_reset_runtime_instrumented/20260528_052237/`

Review scope: artifact/log/dumpsys/runner review only. No additional NPU run,
512 rerun, native change, QAIRT rebuild, ChatScreen promotion, assistant-list
insertion, DB, TTS, Markdown, streaming, selectedPath=NPU persistence,
release/standard behavior change, or 1024+ progression was performed.

Finding: prompt 2 was process-present before dispatch (`pid=17226`) and
process-absent at the first post-broadcast boundary. The boundary name is
`after_dispatch`, but that snapshot is taken after `adb shell am broadcast`
returns, not at the instant the broadcast is enqueued. The broadcast returned
`result=0`, the receiver wrote `started` state and native diagnostics through
`before RunDecode SetMaxOutputTokens(512)`, then no completed result,
cleanup, or `Engine.close` evidence was produced.

Classification: `broadcast_receiver_native_worker_process_exit`.

This is not supported by runner-induced process stop, explicit Activity
restart, app data clear, foreground/background broadcast rejection, stale
result mismatch, or visible crash/tombstone evidence in the saved artifacts.
The best next fix is to add receiver/native-worker terminal instrumentation:
write a run-id scoped marker immediately before and after
`DevOnlyNpuChatScreenBlockedBranch.runForChatScreen`, and capture
`Throwable`/`Error` around that worker path before any new NPU rerun.

Policy remains unchanged: 512 sequential is incomplete and non-baseline; 512
remains `hidden_per_run_isolated_512` candidate only; 256 remains the hidden
experimental baseline candidate; H1 remains pinned to 128; 1024/2048/4096
remain blocked.
