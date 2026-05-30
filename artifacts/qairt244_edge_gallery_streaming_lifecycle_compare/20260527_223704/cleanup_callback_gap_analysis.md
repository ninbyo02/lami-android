# Cleanup Callback Gap Analysis

Observed Lami gap:

- Sequential 512 code prompt times out after pre-RunDecode
  `SetMaxOutputTokens(512)` evidence.
- Completed result, raw output, sanitized output, backend evidence,
  `cleanup_elapsed_ms`, and `Engine.close=unique_ptr_cleanup` are missing.
- Per-run force-stop provides the missing boundary and the same code prompt
  succeeds.

Edge Gallery/LiteRT-LM gap relevant to Lami:

- Gallery callback delivery is direct and assumes callbacks progress to
  `onDone` or `onError`.
- Gallery stop calls cooperative cancel and updates UI state immediately.
- LiteRT-LM Flow cancellation does not call `cancelProcess()`.
- LiteRT-LM session close waits for in-flight work before executor reset.

Root-cause hypotheses still consistent with static evidence:

1. `sequential_resource_inheritance`
   Warm process or native executor/session residue affects the second prompt.
2. `native_callback_missing_after_decode_or_decode_never_returns`
   Native path reaches pre-decode but does not produce terminal callback/result.
3. `cleanup_wait_insufficient`
   The runner does not prove prompt-to-prompt cleanup before starting the next
   hidden run.
4. `state_file_or_receiver_collision`
   Less likely than resource inheritance because isolated and sequential use
   similar file waits, but still worth guarding with per-turn IDs.
5. `code_decode_slow_after_warm_run`
   Possible because the code prompt is longer and slower, but Activity restart
   did not solve it.

Design gap to close before any sequential 512 retest:

- A per-turn hidden lifecycle wrapper must create a run id before native entry,
  bind state/result/native diag files to that id, require exactly one terminal
  result for that id, require cleanup/close evidence, and classify the engine
  or process as suspect if cleanup does not arrive within a bounded wait.
