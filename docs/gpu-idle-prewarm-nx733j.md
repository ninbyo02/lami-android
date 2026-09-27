# NX733J GPU idle-prewarm diagnostic

## Decision

Do not enable automatic GPU idle prewarm in the product.

Across three current-code runs per mode, isolated idle prewarm reduced median
user-visible latency from **6,282 ms to 243 ms** (**6,039 ms, 96.13%**).
The gain comes from moving GPU engine initialization ahead of the request:
median TTFT after the send began was effectively unchanged (**161 ms vs 162 ms**).
While held, prewarm added **823,485 KiB PSS** (about 804 MiB) over its baseline.
Immediately after closing Conversation then Engine, PSS remained **387,849 KiB**
above baseline until the isolated process was stopped. The device exposed no
usable energy counter, so the power cost remains unresolved.

## Safety boundary

The implementation is compiled only when all of these conditions are true:

- standardDebug
- explicit Gradle property lami.gpuIdlePrewarmDiagnostic=true
- isolated application ID io.github.ninbyo02.lami.gpuidleprewarm

The default Debug build and every Release build keep the feature disabled.
No product startup, automatic-selection, normal chat, database, settings, or
model-selection path was changed.
The diagnostic preserves the production constraints used for this comparison:

- text and vision: GPU
- audio: CPU
- GPU context limit: 512
- LiteRT-LM Conversation.sendMessageAsync
- model-owned prompt template
- sampler profile lami_stable_v1 (top-k 40, top-p 0.9, temperature 0.3,
  seed 42)
- enable_thinking=false
- identical model SHA-256, prompt, and output condition

## Result

The table shows medians of three alternating cold-process runs per mode.

| Metric | On demand | Idle prewarm |
|---|---:|---:|
| Engine initialization | 5,945 ms | 5,377 ms |
| Conversation creation | 17 ms | 14 ms |
| Send / completion | 210 ms | 218 ms |
| TTFT from send start | 161 ms | 162 ms |
| User-visible latency | 6,282 ms | 243 ms |
| Total diagnostic time | 10,262 ms | 16,267 ms |
| Prewarm lead | 0 ms | 14,842 ms |
| Main-thread max probe latency | 1 ms | 2 ms |
| PSS before | 116,959 KiB | 116,695 KiB |
| PSS after engine | 939,669 KiB | 940,180 KiB |
| PSS immediately after close | 500,740 KiB | 504,544 KiB |

All six comparison runs returned 赤 for 赤だけ答えてください。.
Battery temperature ranged from 27.0 to 29.0 °C and Android thermal status stayed
at 0. BATTERY_PROPERTY_ENERGY_COUNTER was unavailable. The charge counter was
not precise enough to attribute energy to individual short runs.

Streaming produced one text chunk for this deliberately one-token answer, so
TTFT is the first non-empty chunk time. LiteRT-LM rejected getBenchmarkInfo()
because benchmark parameters are not enabled in the production-equivalent
engine settings. Prefill/decode token counts and rates are therefore recorded as
unavailable, together with the exact API error, rather than enabling a
non-equivalent benchmark configuration.

## Cancellation and recovery

| Trigger | Result |
|---|---|
| Navigation | cancelled (navigation) |
| Model change | cancelled (model_changed) |
| Backend change | cancelled (backend_changed) |
| Low memory | cancelled (low_memory) |
| Confirmed background transition | cancelled (background_confirmed) |
| User stop | cancelled (user_stop) |
| Timeout | cancelled (timeout) |
| User stop after engine initialization | cancelled, resources closed |
Cancellation is cooperative during idle delay, model hashing, engine hold, and
before conversation/send boundaries. Android low-memory callbacks are
registered, and TRIM_MEMORY_UI_HIDDEN is treated as a confirmed background
transition. Cleanup always closes Conversation before Engine.

After cancelling an initialized engine, a fresh on-demand run succeeded with
output 赤 (engine 6,320 ms, conversation 40 ms, send 368 ms, TTFT 326 ms).
Both native resources reported closed.

## Regression and restoration

The full testStandardDebugUnitTest suite, the diagnostic source-contract test,
and the explicitly enabled diagnostic APK build passed. The APK also passed the
existing 14-library GPU runtime hash verification. Device log review found no
crash, JNI detection error, or UnsatisfiedLinkError; existing TTS, stop, and
resend coverage remained green.

The isolated diagnostic package and its copied model were removed after evidence
capture. The normal package remained installed with APK SHA-256
a36c30d139a4da5365beda095a83fdea825dbc456cce6b488fe68cdc40acafc4;
its first-install and last-update times remained 2026-09-12 23:59:15 and
2026-09-13 16:25:25. All recorded normal DB, WAL/SHM, history, settings, and
model hashes matched the pre-run baseline. The normal model SHA-256 remained
ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42.

Machine-readable evidence, including raw samples, cancellation results, build
identity, and restoration hashes, is in
docs/evidence/gpu-idle-prewarm-nx733j.json.
