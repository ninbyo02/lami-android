# Neural TTS frame budget on SM8750

One diagnostic build after PR #2713 reports per-frame generation time (main token/head, 16 CP forward steps, 15 CP heads, sampling and next main step), plus cumulative prefix decoder time. Each codec frame represents 80 ms of audio at 24 kHz. Frames reaching EOS are excluded. Channel backpressure and decoder work are outside the frame timer; simultaneous decoder work can still affect measured generation time. The first decoder call includes a cold load. This is a paired diagnostic of two sentences, not a controlled performance benchmark under multiple thermal/load states.

| Utterance | Codec frames | Generation p50/p95/max | Frames over 80 ms | Main forward | CP forward | CP heads | Prefix decodes | Underruns |
| --- | ---: | --- | ---: | ---: | ---: | ---: | --- | ---: |
| こんにちは。 | 15 | 259/328/328 ms | 15/15 | 2341 ms (28 calls) | 1690 ms (240 calls) | 648 ms (225 calls) | 8: 3192 ms cold; 15: 702 ms | 1 |
| 好きな色は赤です。 | 23 | 283/331/342 ms | 23/23 | 3421 ms (40 calls) | 2688 ms (368 calls) | 979 ms (345 calls) | 8: 456 ms; 16: 818 ms; 23: 1029 ms | 2 |

All complete-stream and full-decode PCM comparisons were exact. The installed APK was restored and its SHA-256 matched the pretest image (`b45e29b33507d6950e23d05f13c38d324fb8e0ae9cab5636f5d540b4dfee3e51`). No normal-chat speech path was changed. Unit tests and Standard Debug compilation/assembly passed.

The current CPU code generation consumes approximately 3.2–4.3 times the audio duration per frame in the observed median. The CP plus main forwards already exceed the budget; reducing prefix decode repetition alone cannot make steady speech continuous. Next evaluate a production-checkpoint NPU partition or other compatible faster main/CP execution with independent full-utterance listening, while measuring resident runtime, decoder and LLM concurrency. Different sampled codec IDs by themselves are not an audio-quality verdict. Do not enable the experimental NPU output in normal chat until the audible quality and underruns are acceptable.
