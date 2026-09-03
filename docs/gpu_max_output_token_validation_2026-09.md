# GPU maximum-output token validation (NX733J, 2026-09-03)

## Scope

This report records the isolated Generic GPU saturation tests performed for PR #2554 on the NX733J connected at `192.168.52.52:33511`. The installed package was `io.github.ninbyo02.lami.gpustandardminimal`, using the 2,588,147,712-byte LiteRT-LM model at the app-private model path.

The fixed saturation prompt repeatedly requests the word `alpha`. Every case force-stops the package before launch and is accepted only when the timestamp-matched terminal state is `success/completed`, callback `onDone=1`, and fallback/timeout counts are both zero.

`max_output_tokens` is an output-generation budget. It must not be confused with `EngineConfig.maxNumTokens`, which sizes total context/KV cache. Edge Gallery's observed default `maxNumTokens=1024` is therefore not direct proof of 1,024 generated tokens.

## PR correctness change

Commit `4e229d55` changes a GPU timeout with non-empty partial text from `READY` to `ERROR`. The accumulated partial response remains visible and saved, but is not routed as a normal success and is not sent to TTS/history as a completed answer. Regression tests cover classification, normal success exclusion, and finish-reason selection.

The callback accumulation path was rechecked: `LocalStreamingRunner.runWithHeldEngine()` builds cumulative text before invoking `onPartial`, so `latestHeldPartialText` already represents the cumulative response rather than only the final delta.

## Saturation results

| Requested output budget | Accepted runs | Terminal evidence | Classification |
| ---: | ---: | --- | --- |
| 512 | 3/3 | fallback 0, timeout 0 | Current conservative product limit |
| 768 | 5/5 | 737 emits, `onDone=1`, 37.1-45.7 s total | Repeatable PASS |
| 800 | 5/5 | 769 emits, `onDone=1`, 41.8-48.3 s total | Repeatable PASS |
| 832 | 6/6 | 801 emits, `onDone=1`, 45.6-57.7 s total | Highest repeatable isolated PASS in this run |
| 864 | 0/1 | 806 emits by 43.3 s, then no terminal state before 120 s plus observer grace | FAIL; generation stalled before `onDone` |
| 896 | 0/1 | 799 emits by 43.3 s, then no terminal state before 120 s plus observer grace | FAIL; generation stalled before `onDone` |
| 1,024 | 3/4 observed | One 51.1 s success with 993 emits; repeated batch was 2/3 with one non-terminal run at 939 emits | Technically reachable but not repeatable |

A 1,024-token case with a 60-second outer timeout failed after reaching 896 emits because the per-case budget was about 45 seconds after reserves. Raising the timeout to 120 seconds enabled a complete run, but did not eliminate the intermittent missing terminal callback.

The 864 and 896 failures were still producing output before stalling. They are not evidence of an allocation or engine-creation ceiling. They identify an intermittent decode/callback-lifecycle boundary above the repeatable 832 setting.

## Decision

- Keep the production GPU cap at **512** in PR #2554.
- Treat **832** as the highest isolated setting demonstrated repeatedly in this session, not yet as a product promise.
- Do not advertise GPU parity with a CPU setting of 1,024 or more until the terminal-callback failure is fixed and the promotion gate passes.
- Do not interpret short-prompt acceptance of large `maxNumTokens` values as equivalent generated-output capability.

The gap to Edge Gallery is now narrower but still material. LAMI can generate a 1,024-budget response on GPU, while Edge Gallery's configuration accepts a 1,024 total-context setting. LAMI's unresolved issue is repeatable completion/cleanup near the upper output range, not basic GPU execution or CPU fallback.

## Safe promotion gate

Before changing the user-facing cap, the proposed candidate must pass all of the following on the target device:

1. 10/10 isolated saturation completions with `onDone=1`.
2. Zero CPU fallback, timeout, native crash, or stale state.
3. A second 10/10 batch after app/device restart.
4. Long Japanese quality checks without raw callback corruption.
5. Normal prompt and UI-route verification, including timeout-partial failure presentation and no TTS completion.
6. Bounded conversation/engine close and stable subsequent CPU/GPU route selection.

The diagnostic stage runner now accepts `640`, `768`, `800`, `832`, `864`, and `896`, and stops after the first failed run. This limits repeated exposure to a known non-terminal native state. These values remain debug-only and do not change production configuration.

## Next engineering target

Instrument the LiteRT-LM callback boundary around emits 790-810 and record thread state, callback executor progress, native completion, and close behavior. If the runtime has completed but `onDone` is missing, add a narrowly scoped completion watchdog that can prove end-of-generation without converting a partial timeout into success. Otherwise, keep the failure classification introduced by `4e229d55` and pursue the runtime/delegate fix.

A cap increase should proceed in two steps: qualify 800 or 832 behind a device/runtime allowlist, then separately pursue reliable 1,024 completion. The current evidence does not justify changing the default from 512 in this PR.
