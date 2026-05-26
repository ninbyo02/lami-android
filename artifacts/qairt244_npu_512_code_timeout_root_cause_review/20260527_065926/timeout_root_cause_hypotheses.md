# Timeout Root Cause Hypotheses

## Primary: sequential_decode_timeout

The code prompt timed out only in the sequential three-prompt run. It was the
second prompt after a successful `こんにちは` run. The native stage reached
`before RunDecode SetMaxOutputTokens(512)`, then no completed native success,
cleanup, or receiver state was written before the 60 second runner deadline.

## Secondary: code_prompt_decode_too_long_under_three_prompt_runner

The same prompt is inherently heavier than the Japanese prompts. At 256 it
needed `decode_ms=7351`; at 512 isolated it needed `decode_ms=11600` and
produced a long code response. In the sequential 512 run, the code decode did
not return within the 60 second bound.

## Possible: cleanup_dependency_between_runs

The first 512 prompt completed with cleanup evidence, but the process remained
alive with higher resident/native memory before the second prompt. The code
prompt then timed out and the runner force-stopped the app. This does not prove
a cleanup bug, but it makes per-run process freshness or explicit force-stop
between prompts a useful next isolation axis.

## Possible: thermal_or_resource_slowdown_possible

There is no direct thermal log evidence in the artifact. However, the sequential
run starts the code prompt after a prior NPU decode in a warm app process, while
the isolated success started from no process. Thermal/resource slowdown remains
a plausible but unproven contributor.

## Not primary: runner_wait_condition_too_strict

The isolated retry and sequential runner both wait for
`files/qairt244_standard_hidden_prompt_state.txt` with a 60 second bound. The
isolated retry succeeded under the same state-file condition. In the sequential
timeout, the state file was missing because no receiver completion was written.
