# Reviewed CP INT8 device comparison (2026-10-06)

## Setup and result

Latest main `7b44a010` with a debug-only CP selector was built and tested on NX733J / SM8750. The selector accepts the existing FP32 model or the exact reviewed full INT8 PTQ hash; both must appear in the verified bundle. The same APK, FP32 main transformer, decoder, embeddings, heads and native matrix implementation are used for both variants. The original FP32 CP file remains intact; only a temporary verified manifest and separate INT8 pilot file select the candidate.

Four fresh-process serial runs use FP32, INT8, INT8, FP32. Each run synthesizes the two reviewed sentences; the second request reuses loaded modules. All eight syntheses reach EOS, decode and complete playback. Code and FP32 PCM hashes repeat exactly within each model/sentence across the two runs.

CP forward weighted mean improves from **8.112 to 4.748 ms/call (1.71x)**. Main forward remains about 98–109 ms/call. INT8 CP alone still costs about 76 ms for the 16 serial calls in one audio frame. This cannot satisfy the overall 50–60 ms target by itself.

| Run / CP | Sentence 1 p50 / p95 (ms) | Sentence 2 p50 / p95 (ms) |
|---|---|---|
| 0 / fp32 | 296 / 368 | 214 / 223 |
| 1 / int8 | 212 / 231 | 243 / 296 |
| 2 / int8 | 217 / 268 | 225 / 266 |
| 3 / fp32 | 262 / 291 | 298 / 307 |

All 426 measured frames exceed 80 ms. First-sentence INT8 medians improve in both trials, but second-sentence medians overlap the FP32 range. Temperature, scheduling and different generated frame counts limit whole-pipeline percentage claims. Battery temperatures at run start (tenths °C): {'0': 330, '1': 360, '2': 360, '3': 360}. Raw thermal snapshots are retained.

## Cross-platform quality and retained audio

Android FP32 first-sentence codes match the PC baseline. The second FP32 sentence and both INT8 sentences differ from the PC outputs, while each repeats exactly on the phone. PC listening acceptance therefore does not automatically accept the Android clips. Numerical backend differences can affect sampled-token feedback; this test does not isolate their cause.

Four additional single-sentence phone generations save WAVs in Voice Lab group `lami-cp-int8-device-review-20261006`, ordered FP32 then INT8 for each sentence. These additional captures are for listening, not extra speed trials. Existing PC samples/reviews are preserved. Device listening acceptance remains pending; no normal playback promotion is made.

## Validation and restoration

Standard debug APK build passed. Focused frontend, codec sampler and speech pipeline unit tests passed. Both the speed comparison and audio capture restore the original APK and exact manifest; restored SHA-256 identities are recorded in the JSON report. The temporary candidate file is removed. The original last-WAV file is restored after captures. Audio HTTP serving and hashes are verified for all four new samples.

Playback-start times in the report are serial full-sentence latency, not streaming first-chunk latency. This is a two-sentence, two-trial speed pilot, not sustained realtime or general voice quality acceptance. Raw logs, APK backups, thermal snapshots and WAVs are retained under the voice dataset `device/cp-int8-20261006` directory; binaries are not committed.

Next: review actual device clips, then prioritize reducing main-transformer latency and serial CP work. The INT8 CP improvement is useful, but further CP quantization alone cannot meet the total frame budget. Any trained architecture or main quantization candidate needs separate quality and runtime validation.
