# GPU context-capacity experiments — corrected interpretation

This filename is retained for existing links. Earlier revisions incorrectly described the test parameter as a generated-output budget. The receiver passes the legacy `maxOutputTokens` value to `EngineConfig.maxNumTokens`, which limits input plus output context / KV-cache capacity. It does not establish an independent generation cap.

## Historical observations (2026-09-03, NX733J)

| Requested Engine context | Observed completed runs | Evidence / limitation |
| ---: | ---: | --- |
| 512 | 3/3 | Zero reported fallback and timeout |
| 768 | 5/5 | 737 callback emits, onDone=1 |
| 800 | 5/5 | 769 callback emits, onDone=1 |
| 832 | 6/6 observed | 801 callback emits, onDone=1 |
| 864 | 0/1 | Stalled at 806 emits, no terminal callback before observer deadline |
| 896 | 0/1 | Stalled at 799 emits, no terminal callback before observer deadline |
| 1024 | 3/4 observed | Intermittent missing terminal callback; not a reliable product qualification |

Callback emits are not measured tokens. The observations show completion/non-completion for these specific runtime, prompt and context configurations. They do not isolate a decode limit, prove an allocation ceiling, establish an 832-output-token limit, or demonstrate Edge Gallery parity. Earlier conclusions attributing the boundary to output length were too strong.

## Fresh revalidation

See [2026-09-11 results](reviews/2026-09-11-gpu-stage-review.md): requested context 512 passed 3/3; 832 passed 10/10. These completed runs remain valid observations after correcting the parameter interpretation.

## Production decision

The main default `GPU_EDGE_GALLERY_LIKE_MAX_TOKENS` is 1024. The old PR proposed lowering that Engine context value to 512. That change and the tests asserting 512 are deliberately excluded from the replacement PRs. There is no new output-only cap in this work.

The minimal candidate has a different native-library packaging configuration from Standard Debug/Release. Its GPU benchmark uses GPU vision and CPU audio plus a cache path, while the Edge-Gallery-like normal configuration can use null modalities and cache. Results must not be transferred across those configurations without matched validation.

## Next validation design

1. Record runtime/library identity, model, backend/modalities, cache, requested context and resolved Engine context independently.
2. Hold context constant while comparing prompts and measured output. If the runtime cannot expose generated-token counts or a separate output cap, mark those unavailable; do not use callback counts as a substitute.
3. Exercise normal UI stop, real-timeout failure persistence, late callbacks and next-turn recovery on the intended APK.
4. Test longer context configurations separately, starting from the existing main setting. Do not lower or increase production capacity based solely on isolated saturation results.

The original experiment and code are preserved at `archive/gpu-pr2554-e940c193` (commit `e940c193`). That snapshot contains historical wording superseded by this correction.
