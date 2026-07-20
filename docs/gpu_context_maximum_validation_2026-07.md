# Generic GPU context-limit validation (NX733J, 2026-07)

## Scope

This report closes the fixed, debug-only foreground investigation of LiteRT-LM Generic GPU context sizing on the NX733J. It records only evidence obtained through the allowlisted `DebugTokenBenchmarkActivity` route with the Generic model slot. The Qualcomm/NPU model slot was not used.

The benchmark surface is debug-only. It exposes fixed cases rather than arbitrary prompts, paths, token values, shell commands, or network inputs.

## Interpretation rules

`EngineConfig.maxNumTokens` is a total context configuration. A successful short prompt proves that the runtime accepted that configuration and performed short generation; it does **not** prove that the model processed an input of that many tokens.

The LiteRT-LM public SDK used here does not expose the tokenizer/input-token count needed to report an exact successful input length. Long-context results therefore record configured total context and UTF-8 payload size. No exact successful token count is inferred.

A result is a terminal PASS only when a fresh timestamp-matched report shows `status=success`, `reason=completed`, Generic GPU, non-empty output, stable process, and no fallback, timeout, or fresh crash.

## Results

### Fixed short-prompt ladder

| Configured total context | Result | Meaning |
|---:|---|---|
| 16 | Expected rejection: input required 19 tokens (`19 >= 16`) | Input-size boundary, not GPU initialization failure |
| 32 | PASS | Generic GPU short generation completed |
| 128 | PASS | Generic GPU short generation completed |
| 512 | PASS | Generic GPU short generation completed |
| 1,024 | PASS | Generic GPU short generation completed |
| 2,048 | PASS | Generic GPU short generation completed |
| 4,096 | PASS | Generic GPU short generation completed |
| 8,192 | PASS | Generic GPU short generation completed |
| 16,384 | PASS | Generic GPU short generation completed |
| 32,768 | Configuration/short-prompt evidence only | Not accepted as long-context proof |
| 65,536 | Configuration/short-prompt evidence only | Not accepted as long-context proof |
| 131,072 | PASS for a short prompt | Engine create 10,356 ms; output `5`; not long-context proof |
| 262,144 / 524,288 / 1,048,576 | Fixed diagnostic cases exist; no terminal artifact accepted in this investigation | No capacity claim |

Representative accepted artifacts from the serialized foreground route include:

- GPU 32: app timestamp `20260720_071937`, `status=success`, Generic GPU, output `5`.
- GPU 128: app timestamp `20260719_225641`, `status=success`, Generic GPU, output `5`.
- GPU 512: app timestamp `20260720_072823`, `status=success`, Generic GPU, non-empty output.
- GPU 131072: app timestamp `20260720_083855`, `status=success`, Generic GPU, output `5`.
- GPU long context 16384: app timestamp `20260720_214335`, `status=success`, `reason=completed`, Generic GPU, stable PID, no fallback/timeout/crash. Engine creation was 9,263 ms, conversation creation 12 ms, first token 14,207 ms, and total runtime 26,198 ms.

### Fixed long-context ladder

| Configured total context | Final classification |
|---:|---|
| 2,048 | Fixed long-payload case available |
| 8,192 | Fixed long-payload case available |
| 16,384 | Highest terminal long-context level accepted in the investigation |
| 24,576 | No terminal PASS accepted |
| 32,768 | Partial runtime evidence only; **not a terminal capacity PASS** |
| 32,769 boundary | Fixed negative-boundary case available; no terminal rejection artifact accepted |

For the 32,768 case, the fixed payload is approximately 55.7 KB and the marker records `actual_input_tokens=unavailable_public_sdk`. The runtime repeatedly reached GPU engine creation, conversation creation, and `prompt_started`. One pre-fix run completed the long prompt in approximately 49.4 seconds with non-empty raw output, but the old newline protocol split the payload into two independent runs, so the complete benchmark was invalid. The protocol was corrected with a fixed `EXTRA_SINGLE_PROMPT`, and readback then confirmed `requested_run_count=1` and one 55,741-byte payload. Subsequent single-prompt attempts could remain in native prompt processing beyond the bounded host wait and did not produce a terminal success report.

The final 32,768 Stop validation used app timestamp `20260720_223243` and artifact directory `20260720_223238-gpu-long-32768`. A foreground Stop produced `cancelled_by_debug_foreground_ui`, `report_written`, and timestamp-matched terminal state for the single 55,741-byte prompt. The forced-command Stop then reported `terminal_cancelled_and_process_stopped`; PID `30466` was absent afterward. The run command returned exit 65 as intended for a cancelled, non-passing benchmark. This proves bounded observability, cancellation transport, and backend process cleanup—not 32K completion.

Therefore this investigation does **not** claim a verified 32K processed input. The conservative conclusion is:

- highest accepted terminal long-context level: **16,384 configured total-context tokens**;
- 32,768: engine/conversation/prompt-entry and partial decode evidence, but lifecycle/reproducibility remains insufficient for a PASS;
- 131,072: highest recorded successful **short-prompt configuration**, not input capacity.

## Harness and lifecycle findings

The investigation added or corrected the following debug-only safeguards:

- fixed foreground UI cases and strict exact-label lookup;
- bounded scrolling with visible-label diagnostics;
- fresh process/activity state for each serialized measurement;
- `FLAG_KEEP_SCREEN_ON` while the debug benchmark is visible;
- timestamp-bound marker, state, CSV, Markdown, environment, PID, and crash readback;
- a single-value long-context channel so payload newlines are not treated as prompt delimiters;
- a visible Stop control that remains available after host-observer timeout;
- active Future/Conversation/Engine cancellation diagnostics;
- bounded native resource close and a package-specific force-stop fallback;
- receiver-process cancellation transport diagnostics.
- cancellation bypasses the blocking send fallback, cancels the case Future for terminal reporting, lets the worker attempt its normal bounded cleanup, and schedules package-specific process cleanup after a five-second readback window;
- host acceptance fails closed unless state is `success/completed` with zero fallback and timeout counts;
- fixed boundary values 24,576 and 32,769 are parsed explicitly rather than falling back to the default 32/64/128/256 matrix.

OEM AutoLaunch consistently blocked the service entry point. This is classified as a service harness limitation, not a GPU inference failure. The authoritative measurements use the exported debug-only foreground Activity.

## Product decision

Do not raise the normal Generic GPU user-facing context promise to 32K from this evidence. Keep 16K as the conservative verified long-context ceiling until a 32K single-prompt run produces a fresh terminal success artifact repeatedly with stable process, no fallback/timeout/crash, and bounded resource close.

High `maxNumTokens` short-prompt successes must remain DEV diagnostics and must not be presented as processed-input capacity.
