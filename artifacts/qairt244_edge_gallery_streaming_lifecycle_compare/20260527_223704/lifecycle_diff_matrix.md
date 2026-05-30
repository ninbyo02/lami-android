# Lifecycle Diff Matrix

| Area | Edge Gallery | LiteRT-LM | Lami current hidden route | Lami implication |
| --- | --- | --- | --- | --- |
| Engine lifecycle | Engine can be reused across conversation resets. | Engine owns sessions/executors and may wait during close. | Custom native smoke path scopes engine/session per native run, but process may remain warm between sequential prompts. | Do not assume warm process is clean just because JNI objects are scoped. |
| Conversation/session lifecycle | Conversation is closed/recreated on reset while engine remains. | Session state moves through fresh/prefill/decode; close waits for work. | No explicit Android-level session wrapper; state inferred from files. | Add a hidden lifecycle wrapper before any sequential 512 retest. |
| Callback delivery | Direct streaming callback to ViewModel state. | Callback APIs expose message/done/error; Flow wrapper does not cancel native work on close. | No app-level callback contract; runner waits for state files. | Separate callback/result ids per run and require terminal evidence. |
| Stop/cancel | Calls cooperative `cancelProcess()`. | Cancellation flag is cooperative and decode must observe it. | Timeout currently handled by runner and force-stop in rollback cases. | Keep bounded timeout and mark session suspect if terminal cleanup is missing. |
| Cleanup | Model cleanup closes conversation then engine and invokes cleanup listener. | Session destructor waits for work before executor reset. | Completed native runs record cleanup and `Engine.close`; timed-out sequential code does not. | Cleanup evidence must remain a gate, not an assumption. |
| UI surface | Streaming chunks update assistant text messages. | Streaming is callback-oriented. | Hidden diagnostics must not insert assistant messages or DB rows. | Do not adopt streaming renderer in this phase. |
| Isolation | No process force-stop model in normal Gallery chat. | Native close/cancel is cooperative. | Force-stop between prompts is the only passing 512 three-prompt mode. | Preserve per-run isolated 512 gate until a hidden wrapper passes separately. |

Decision:

The closest safe adaptation is not Edge Gallery UI streaming. It is a hidden
NPU lifecycle contract that borrows the concepts of per-turn session boundary,
terminal callback, cleanup listener, and explicit reset, while keeping Lami's
DB/TTS/Markdown/streaming/ChatScreen paths disconnected.
