# Recommendations

Adopt for design only:

1. Hidden NPU session lifecycle wrapper.
   Model each hidden prompt as a single turn with explicit states:
   `created`, `prefill_reached`, `decode_started`, `terminal_callback`,
   `cleanup_started`, `cleanup_done`, `closed`, `failed_or_suspect`.

2. Callback/state file id separation.
   Every hidden run should have a unique run id used in state, result,
   native-diagnostic, timeout, cleanup, and memory artifacts. A runner must not
   accept stale terminal files from a previous run.

3. Per-turn close/cancel/cleanup wait.
   On success, require cleanup and `Engine.close` evidence before allowing the
   next sequential run. On timeout, call a bounded cancel/cleanup path if
   implemented later, but do not trust the same session or process unless a
   terminal cleanup signal arrives.

4. Suspect session classification.
   If a hidden run times out after pre-RunDecode evidence and lacks terminal
   callback or cleanup, classify the engine/session/process as suspect and keep
   512 in per-run isolated mode.

Do not adopt in this phase:

- Gallery normal chat streaming renderer.
- Assistant message-list streaming insertion.
- Chat session persistence or DB history.
- Markdown renderer connection.
- TTS or selectedPath=NPU persistence.
- Release/standard behavior changes.
- Native guard changes or QAIRT rebuild.
- Any 1024/2048/4096 expansion.

Recommended next step:

Design a hidden-only lifecycle wrapper for QAIRT244 diagnostics, with run-id
separated state files and mandatory cleanup/close evidence. The next runtime
experiment should not be 1024. If approved later, it should test only this one
axis: sequential 512 with the lifecycle wrapper and run-id isolation, still
hidden-only and still bounded.
