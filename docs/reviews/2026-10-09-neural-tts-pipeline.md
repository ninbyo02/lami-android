# Serial CPU TTS pipeline device accounting

Recomputed from the eight restored-and-verified cp-delta-native-20261009 captures, not a new device run. The summarizer pins each source log by SHA256 and rejects missing/duplicate timeline events. All code/PCM outputs match accepted audio.

## Native delta warm request (second sentence, 42 frames / 3.36 seconds audio)

| Stage | Run 1 | Run 2 |
|---|---:|---:|
| Prefill | 1128 ms | 1133 ms |
| Codec generation | 5398 ms | 5500 ms |
| CP forward (inside codec) | 2285 ms | 2419 ms |
| CP heads (inside codec) | 712 ms | 628 ms |
| CP sampling (inside codec) | 208 ms | 238 ms |
| PCM forward | 3027 ms | 3035 ms |
| Decoder process total (includes PCM forward) | 3053 ms | 3062 ms |
| Synthesis total | 10247 ms | 10330 ms |
| Request to playback start | 10328 ms | 10408 ms |

Codec plus PCM computation alone takes 2.51–2.54 times audio duration, requiring about 60% less serial computation to fit audio duration, before startup/prefill/other overhead. This is an accounting requirement, not a predicted achievable speedup. Warm PCM batch amortized cost is 72.1–72.3 ms/frame; it is not measured incremental decoder throughput. Cold first-sentence playback delay is 22.61–23.11 seconds and includes loading/validation/preparation; cold and warm sentences differ, so their difference cannot isolate warmup savings.

Main-forward aggregate includes prefill and cannot be added to codec or interpreted directly as steady-state main cost. CP counters occur within codec. Decoder total includes PCM forward. The summarizer keeps these nesting relationships explicit.

## Decision

Prioritize fixed accepted-code PCM replay and warmed whole-runtime thread comparisons, with PCM bit equality, before concurrent pipeline work. ExecuTorch module loading may reset a process-global pool: do not claim a CP-only thread override is isolated. Use a separate decoder process and explicitly record settings for all modules in each process. Current streaming probe repeatedly decodes cumulative prefixes every eight frames; that duplicates work and is not stateful incremental decoding. Merely enabling it is not evidence of sustained realtime throughput. Even ideal PCM overlap cannot overcome current codec generation of roughly 129–131 ms/frame against 80 ms of audio.

No phone mutations or new audio clips were needed for this analysis. Next device experiment must use fresh backups and restore verification, announce its estimated duration, and compare identical fixed codes across settings. Default chat behavior is unchanged. Existing CPU smoke build is not NPU evidence.

## Reproduce

`python3 scripts/voice/summarize_tts_pipeline.py /home/sato/project/lami-android-voice-dataset/device/cp-delta-native-20261009 docs/reviews/2026-10-09-neural-tts-pipeline-device.json`
