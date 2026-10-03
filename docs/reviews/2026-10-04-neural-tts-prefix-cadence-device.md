# SM8750 streaming prefix cadence diagnostic (2026-10-04)

Debug-only PR #2722 (`ef48a86f`), NX733J / SM8750, two sentences in order: `こんにちは。` and `今日は良い天気ですね。`. Each cadence used a fresh app process; the second sentence reused loaded voice modules in that run. Single run per cadence, so timings are directional.

| Prefix cadence | First sentence playback start | First underruns | Second sentence playback start | Second underruns | PCM parity |
| --- | ---: | ---: | ---: | ---: | --- |
| 8 frames | 10,761 ms | 0 | 3,565 ms | 3 | Exact for both sentences |
| 4 frames | 10,919 ms | 3 | 3,324 ms | 6 | Exact for both sentences |
| 2 frames, strict | 10,194 ms | incomplete | not reached | not reached | Exact comparison stopped at 8-frame prefix |
| 2 frames, diagnostic tolerance 1e-6 | 9,773 ms | 6 | 2,548 ms | 12 | Full assembled PCM exact; intermediate drift at most 7.01e-7 |

The 4-frame cadence gained only 241 ms on the warmed second sentence and doubled reported underruns. A strict 2-frame run stopped when previously emitted PCM changed after 6 to 8 frames; follow-up instrumentation found a maximum change of 3.73e-7 at sample 7,680. A diagnostic-only 1e-6 bound allowed completion; intermittent prefix drift peaked at 7.01e-7, while assembled PCM matched full decode exactly. It also produced 6 / 12 underruns, so 2-frame cadence is not recommended. First sentence includes cold validation, model loading, and decoder startup; each condition ran once and does not establish statistical significance. None of these measured cadences reaches real-time audio production.

The installed APK was backed up before the test, then restored after the failure. SHA-256 of both the original backup and APK pulled back from the phone: `7cc2f5d6334d7efb5c1efaa4fac0096c380defb6016e2208dc9e9b335f939651`. Raw logs are kept on the test host at `/home/sato/tmp/lami-tts-cadence-test-20261004/cadence{8,4,2}.txt` and `cadence2-{stability,tolerance}.txt`.
