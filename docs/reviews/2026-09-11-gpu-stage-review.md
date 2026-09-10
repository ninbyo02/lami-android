# GPU revalidation — 2026-09-11 JST

## Changes and build identity

Main `c43710f1` was merged into the PR #2554 branch without conflicts. The device APK was built from `b0d45f4c` (LiteRT-LM 0.11.0), and installed into the separate `io.github.ninbyo02.lami.gpustandardminimal` package on NX733J. The model size is 2,588,147,712 bytes. The main application remains on provider-fix build `fb49dccc`.

The stage runner now gives each batch a unique directory, rejects a prior run’s terminal state, and labels a still-running case at the host deadline as failure. It does not import a stale result CSV. Two mock-adb regression tests passed; they are also wired into Android CI.

Local validation: 1,704 StandardDebug unit tests passed with zero failures/errors/skips; StandardDebug lint and the GPU candidate APK build passed.

## Fresh isolated saturation results

Each run force-stopped the benchmark package before launch. Timeout was 120,000 ms, with the existing 15-second host observation grace. The fixed prompt requests repeated `alpha`. The runner stops at the first failure. CSV timestamps were checked against the new batch IDs.

| Requested output budget | Completions | Total duration | Callback evidence |
| ---: | ---: | --- | --- |
| 512 | 3/3 | 33.395–34.427 s | onDone=1 for every run; emits=481 |
| 832 | 10/10 | 40.905–51.172 s | onDone=1 for every run; emits=801 |

All 13 runs report zero callback errors, no CPU fallback, no timeout, and no fresh crash. Requested output budgets and callback emits are not measured generated-token counts; the runtime reports output tokens as unavailable. Compact per-run evidence is in [the TSV](2026-09-11-gpu-stage-results.tsv).

## Decision and remaining gates

Keep the product cap at **512** and PR #2554 **Draft**. The 832 candidate passed its first fresh 10/10 saturation batch, but this is not completion of the promotion gate. A second 10/10 batch after restart, long Japanese output quality, normal Chat UI saturation, real-timeout recovery, and bounded close/route recovery still require evidence. Higher-budget terminal callback stalls previously observed at 864/896/1024 are not fixed by the test-runner change.

No larger output budget was tested in this session. See [the existing validation report](../gpu_max_output_token_validation_2026-09.md) for historical results and the complete promotion criteria.
