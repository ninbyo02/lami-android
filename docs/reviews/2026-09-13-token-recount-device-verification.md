# Token recount: final device verification

Final StandardDebug APK installed on NX733J and SHA-256 verified:
`1c180d0742e4a1d01a7021d77d2fff5a4ffd9e81c6730578750ddcaff4b32e0e`.
The earlier resident-counter experiment has been replaced. The combined GPU/NPU native stack is unchanged and passed the build's pinned-input/final-APK checks.

## Device finding and correction

The first deferred implementation counted the display-sanitized response. For the same long answer this produced 454 tokens instead of the previous 455: the raw callback text contained 841 characters, while display text contained 835. Preserve the original effective prompt and generated response in a transient `DeferredTokenizerInput`, and consume those strings during deferred counting. Clear the transient payload from the recounted snapshot. Neither original strings nor native objects are kept in the count cache or persisted by the metadata patch.

Final tests produced matching input/output counts for greeting (11/5), long (16/455), short (11/1) and resend (10/2). Each final GPU response was committed before its separate tokenizer count started. The original decode interval and response completion time remain unchanged by the later metadata update.

## Observed completion times

| Case | Previous synchronous count | Final deferred count |
|---|---:|---:|
| Greeting, 11 displayed characters | 10.074 s | 8.790 s |
| Long, 835 displayed characters | 25.070 s | 23.352 s |
| Short, 1 displayed character | 3.058 s | 2.193 s |
| Resend after stop, 6 displayed characters | 3.705 s | 1.944 s |

These are stored response completion times from sequential same-device runs, not a controlled thermal benchmark or pure native decoding rate. New text still incurs asynchronous counting work (roughly 1.4 seconds in these final samples). Identical text uses the bounded result cache. No extra counting engine is retained after counting; native allocated heap returned to the earlier scale rather than the rejected resident experiment's additional ~0.8 GB. Physical RSS/PSS can remain higher because of allocator/page retention.

## Functional and concurrency checks

- Final GPU: long → short → stop → resend and repeated-short cache hit passed; engine reuse and no fallback verified.
- Final NPU strict repeat: greeting, 751-character long response, short, stop and resend passed. Example completion times: 0.386 s, 11.060 s, 0.079 s and 0.136 s for the successful turns.
- GPU and NPU: no active audio 2 and 8 seconds after playback stop and generation stop; no late speech request/accept/start in that observation window.
- Concurrent check: first recount started at elapsed 2404272962 ms; the next send finished at 2404273180 ms; the first recount finished at 2404274278 ms. Both answers completed with exact later counts; the second reused GPU without fallback and completed in 1.290 s.
- All 106 existing database rows at the final-test baseline remained unchanged; final database had 142 rows, with test additions only. SQLite checks passed.
- Preferences, input method and screen timeout restored; ChatGPT returned to foreground.

One initial NPU harness run could not find `speak_accepted` for resend in the retained trace. It did contain a matching current-generation `playback_done`; earlier trace content was absent. Preserve this as an observation gap, consistent with trace rotation, rather than silently discarding the failure. A full strict NPU repeat passed without TTS code changes. The standard stop checks were successful in both attempts.

## Local validation and limits

1,799 StandardDebug + 1,262 StandardRelease JVM tests passed (3,061 total); both lint variants passed. Added tests preserve original effective prompt/output text and retain the legacy fallback behavior. The final device APK was built from the tested working tree before this evidence commit; its exact hash above is authoritative. Latest GitHub CI status is tracked on PR #2603.

No additional CPU/power/thermal or Release-device performance claim is made. GPU/NPU full sequences restart the app between backends. The separate concurrent-send check covers submitting while recounting; the generation-stop sequence observes silence for 8 seconds before resend.
