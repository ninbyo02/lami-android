# GPU context revalidation — 2026-09-11 JST

## Build and scope

Device: NX733J. Isolated package: `io.github.ninbyo02.lami.gpustandardminimal`. APK commit: `b0d45f4c`, LiteRT-LM 0.11.0, model size 2,588,147,712 bytes. Main c43710f1 had been merged into that candidate branch. The main application was separately on provider-fix build fb49dccc.

Each saturation run force-stopped the benchmark process before launch, requested repeated `alpha`, and used a 120,000 ms timeout plus the existing 15-second host observation grace. New batch IDs were verified against CSV timestamps.

## Corrected parameter meaning

The legacy parameter `max_output_tokens` went to `EngineConfig.maxNumTokens` in the selected GPU variant. Thus 512 and 832 were **total Engine context settings**, not output-generation caps. The earlier report’s output-budget and production-cap statements were incorrect. This corrected report changes interpretation, not the observed completion results.

| Requested Engine context | Completions | Total duration | Callback evidence |
| ---: | ---: | --- | --- |
| 512 | 3/3 | 33.395–34.427 s | onDone=1; 481 emits per run |
| 832 | 10/10 | 40.905–51.172 s | onDone=1; 801 emits per run |

All 13 CSV rows report zero callback errors, no fallback, no timeout and no fresh crash. Output-token measurement is unavailable. Callback emits must not be called generated tokens. [Per-run TSV](2026-09-11-gpu-stage-results.tsv) retains the original values with a corrected context header; original reports remain in archived commit e940c193.

Local validation of that candidate: 1,704 StandardDebug tests passed, lint passed, candidate APK built successfully. This is historical candidate validation, not a test of the replacement branches or the latest main APK.

## Limits and decision

No product capacity change is justified by this batch alone. Main retains its existing 1024 context default. The candidate differs in native packaging and backend modality configuration; normal Chat UI, Japanese quality, actual timeout recovery, late callbacks and bounded close need separate evidence. Higher-context stalls remain unresolved. No new device tests were performed while splitting the PR.
