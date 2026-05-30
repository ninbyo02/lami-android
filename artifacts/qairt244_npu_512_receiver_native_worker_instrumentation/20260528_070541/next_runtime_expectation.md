# Next Runtime Expectation

The next approved runtime should run the instrumented 512 sequential
soft-reset path once.

Expected diagnostic outcomes:
- If prompt2 trace stops after `before_native_adapter_run`, classify
  `NATIVE_NON_RETURN_OR_PROCESS_DEATH`.
- If `throwable_caught` appears, classify `WORKER_THROWABLE_CAUGHT`.
- If native returns but no terminal result is written, classify
  `TERMINAL_RESULT_WRITE_MISSING`.
- If terminal result is written but cleanup is missing, classify
  `CLEANUP_MISSING`.
- If clean markers complete and process still disappears later, investigate
  post-worker lifecycle/process state separately.

The run must remain hidden-only. It must not promote ChatScreen, insert
assistant messages, persist selectedPath=NPU, connect DB/TTS/Markdown/
streaming, or proceed to 1024+.
