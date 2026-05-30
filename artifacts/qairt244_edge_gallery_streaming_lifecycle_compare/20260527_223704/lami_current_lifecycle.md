# Lami Current Lifecycle

Current hidden route:

- Standard hidden prompt entry is
  `StandardHiddenQairt244PromptReceiver`.
- The receiver uses `goAsync()`, executes work on a background `Thread`, writes
  app-private state files, and finishes the broadcast pending result.
- The route is gated by debug-only developer access, hidden route toggle,
  exact SM8750 model selection, max-output-token limits, prompt validation, and
  a file-based run guard.
- Completion is inferred from state/result/native-diagnostic files, not an
  Android `ResultReceiver` callback.

Current diagnostic files:

- `qairt244_standard_hidden_prompt_state.txt`
- `qairt244_short_multitoken_smoke_result.txt`
- `qairt244_native_diag.txt`
- `qairt244_dev_npu_ui_cleanup_state.txt`
- `qairt244_chat_screen_model_path_resolution.txt`

512 lifecycle observations:

- Sequential 512: Python code prompt reaches native pre-RunDecode
  `SetMaxOutputTokens(512)` evidence, then times out with no completed result,
  cleanup, `Engine.close`, backend evidence, raw output, or sanitized output.
- Activity-restart-only 512: still times out on the Python code prompt.
  Activity relaunch does not provide the same isolation boundary as process
  force-stop.
- Per-run force-stop 512: all three prompts pass. Python code returns
  `useful_code`; indentation and code fence checks pass; cleanup and
  `Engine.close=unique_ptr_cleanup` are recorded; after-10s process is absent.

Implication:

Current evidence favors process/native resource inheritance or callback/cleanup
state inheritance as the 512 sequential failure class. It does not show that
SM8750 NPU or max512 native guard is unsupported.
