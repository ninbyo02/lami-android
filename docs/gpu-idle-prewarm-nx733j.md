# NX733J GPU idle-prewarm diagnostic

## Decision

Do not enable automatic GPU idle prewarm in the product.

The isolated diagnostic reduced user-visible latency from **9,498 ms to 924 ms**
(8,574 ms, 90.27%), but it moved initialization work earlier rather than removing
it. While held, GPU prewarm added about **854,277 KiB PSS**. Immediately after
closing the Conversation and Engine, PSS was still about **385,447 KiB above the
pre-run baseline**; force-stopping the isolated diagnostic process released it.
The device did not expose a usable battery energy counter, so the power cost
remains unresolved.

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
- Conversation API with model-owned prompt template
- sampler profile lami_stable_v1 (top-k 40, top-p 0.9, temperature 0.3,
  seed 42)
- enable_thinking=false
- identical model SHA-256, prompt, and expected output

## Result

| Metric | On demand | Idle prewarm |
|---|---:|---:|
| Engine initialization | 8,504 ms | 5,161 ms |
| Conversation creation | 5 ms | 16 ms |
| Send / completion | 372 ms | 276 ms |
| User-visible latency | 9,498 ms | 924 ms |
| Total diagnostic time | 11,345 ms | 14,729 ms |
| Main-thread max probe latency | 1 ms | 2 ms |
| PSS before | 116,796 KiB | 116,059 KiB |
| PSS after engine | 1,041,218 KiB | 970,336 KiB |
| PSS immediately after close | 534,759 KiB | 501,506 KiB |

Both runs returned 赤 for 赤だけ答えてください。. Battery temperature was
31.0 °C before and after, and Android thermal status stayed at 0. The device
returned no energy counter; its charge counter did not move at 1 µAh resolution,
which is inconclusive rather than proof of zero power use.

LiteRT-LM completed the Conversation call, but TTFT and per-phase prefill/decode
values were not exposed by the benchmark getters available to this build. The
report therefore records them as unavailable instead of estimating them.

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
| User stop after engine initialization | cancelled, Conversation/Engine closed |

Cancellation remains cooperative during idle delay, model hashing, engine hold,
and before conversation/send boundaries. The diagnostic also registers Android
low-memory callbacks and treats TRIM_MEMORY_UI_HIDDEN as a confirmed background
transition.

After cancelling an already initialized engine, a fresh on-demand run succeeded
with output 赤 (engine 6,126 ms, conversation 16 ms, send 175 ms), and both
native resources reported closed. The diagnostic process was absent after
force-stop.

## Regression and restoration

The targeted GPU cancellation/reuse, TTS ownership/order, response speech,
stop/resend, and diagnostic source-contract tests passed. Device log review found
no crash, JNI detection error, or UnsatisfiedLinkError.

The isolated diagnostic package was removed after evidence capture. The normal
package remained installed with APK SHA-256
a36c30d139a4da5365beda095a83fdea825dbc456cce6b488fe68cdc40acafc4;
its first-install and last-update times remained 2026-09-12 23:59:15 and
2026-09-13 16:25:25. The normal model SHA-256 remained
ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42.
The diagnostic never opened the normal package's conversation databases or
settings.

Machine-readable evidence is in
docs/evidence/gpu-idle-prewarm-nx733j.json.
