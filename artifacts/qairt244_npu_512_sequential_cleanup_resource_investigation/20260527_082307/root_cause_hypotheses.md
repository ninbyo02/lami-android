# Root cause hypotheses

1. `sequential_resource_inheritance`

   Highest confidence. The Python prompt fails only when it follows a completed
   512 prompt in the same warm app process. It succeeds with force-stop
   bracketing. Prompt 1 cleanup is present, so the inherited state is likely
   outside the narrow `Engine.close` line or in process/runtime/UI/native/QNN
   resources that survive cleanup.

2. `native_callback_missing_after_decode_or_decode_never_returns`

   The sequential Python run reaches pre-RunDecode and leaves receiver state at
   `started`. There is no native success, cleanup, backend evidence, raw output,
   or sanitized output. Current artifacts cannot distinguish "decode never
   returned" from "decode returned but callback/state write failed".

3. `cleanup_wait_insufficient`

   Plausible. Prompt 1 reports cleanup, but sequential prompt 2 begins without
   any explicit post-cleanup wait/no-process boundary. A soft wait or Activity
   restart may reveal whether cleanup needs a settling window without requiring
   full force-stop.

4. `code_decode_slow_after_warm_run`

   Plausible. The code prompt is heavy: 256 code decode took 7351 ms, isolated
   512 took 11600-12448 ms, and sequential 512 did not return within the 60
   second bound. Warm-process resource pressure may slow this prompt
   disproportionately.

5. `state_file_or_receiver_collision`

   Possible but lower confidence. Both runners use the same state file path and
   wait condition. The sequential runner deletes state files before each prompt,
   and the isolated run succeeds with the same state path. A collision cannot
   be ruled out, but current evidence points more strongly to process/resource
   inheritance.

6. `thermal_or_resource_slowdown_possible`

   Possible but unproven. There is no direct thermal evidence in the artifacts.
   The force-stop run happened later and in a colder per-run process, so thermal
   cannot be eliminated without a dedicated run, but it is not the leading
   hypothesis.

7. `runner_wait_condition_issue`

   Not primary. The isolated code success and sequential timeout use the same
   state-file wait pattern and 60 second bound. The problem is that the state
   file never reaches completion in the sequential code prompt.
