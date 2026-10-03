# SM8750 streaming prefix cadence diagnostic (2026-10-04)

Debug-only PR #2722 (`ef48a86f`), NX733J / SM8750, two sentences in order: `こんにちは。` and `今日は良い天気ですね。`. Each cadence used a fresh app process; the second sentence reused loaded voice modules in that run. Single run per cadence, so timings are directional.

| Prefix cadence | First sentence playback start | First underruns | Second sentence playback start | Second underruns | PCM parity |
| --- | ---: | ---: | ---: | ---: | --- |
| 8 frames | 10,761 ms | 0 | 3,565 ms | 3 | Exact for both sentences |
| 4 frames | 10,919 ms | 3 | 3,324 ms | 6 | Exact for both sentences |
| 2 frames | 10,194 ms | incomplete | not reached | not reached | Failed at 8-frame prefix: `Provisional PCM changed after more codes` |

The 4-frame cadence gained only 241 ms on the warmed second sentence and doubled reported underruns. The 2-frame cadence is unsafe to release as a streaming mode: a later decoder pass changed previously emitted PCM. This diagnostic stops on mismatch. First sentence includes cold validation, model loading, and decoder startup; the runs do not establish statistical significance. Neither measured cadence reaches real-time audio production.

The installed APK was backed up before the test, then restored after the failure. SHA-256 of both the original backup and APK pulled back from the phone: `7cc2f5d6334d7efb5c1efaa4fac0096c380defb6016e2208dc9e9b335f939651`. Raw logs are kept on the test host at `/home/sato/tmp/lami-tts-cadence-test-20261004/cadence{8,4,2}.txt`.
