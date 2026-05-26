# Recommended next experiment

Recommended single axis: **B. prompt間 Activity restart only**.

Rationale:

- Full force-stop already passes, so repeating prompt間 force-stop adds little
  new information.
- State-file/receiver-id separation is useful, but current evidence does not
  strongly indicate a state-file collision because the isolated success uses
  the same state path and wait condition.
- Activity restart only sits between current sequential warm-process reuse and
  full force-stop. It can test whether Activity/UI/process state is enough to
  trigger the timeout while avoiding the heavier process-kill reset.
- If Activity restart only passes, the next target is Activity/lifecycle state.
- If Activity restart only still times out, the next target is process/native
  runtime inheritance and force-stop remains the required 512 mode.

Proposed guard for the next run, if separately approved:

- one run only of the approved three prompts
- `max_output_tokens=512`
- `timeout_seconds<=60`
- same prompt order
- no force-stop between successful prompts
- finish Activity or restart Activity between prompts
- keep state-file cleanup as-is
- no ChatScreen promotion, DB, TTS, Markdown, streaming, selectedPath=NPU
  persistence, native change, or QAIRT rebuild

Do not proceed to 1024/2048/4096 before this boundary is resolved.
