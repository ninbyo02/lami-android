# Lookahead viability from the existing SM8750 voice trace

The prior warm two-sentence device runs decoded each whole sentence before playback. The first clip contains 28,800 samples (1.20 s at 24 kHz), and the second contains 49,920 samples (2.08 s). Given the observed playback-start timestamps as conservative PCM-ready proxies, the earliest first playback that could make these two clips contiguous is max(first_ready, second_ready - 1.20 s).

| Warm mode | Observed first start | Observed silence after first | Estimated first start for gapless clips |
| --- | ---: | ---: | ---: |
| Pipeline 1 | 4.611 s | 6.228 s | 10.839 s |
| Pipeline 2 | 4.617 s | 6.748 s | 11.365 s |
| Reverse pipeline | 4.566 s | 6.589 s | 11.155 s |

This is a scheduling calculation from two fixed sentences, not a new device playback test. It assumes the observed start time closely bounds PCM readiness. The decoder and synthesis were not rerun under LLM contention here. Buffering the completed first sentence alone would trade several seconds of silence for a roughly 11-second first-voice delay. It cannot make a producer that remains slower than playback support arbitrary continuous speech.

A separate host ExecuTorch check used the natural-EOS 19-frame CPU and CPU-main/NPU-CP free-generation code sequences. For each, decoding a 2, 4, 8 or 16-frame prefix produced PCM samples **exactly equal** to the corresponding prefix of the final 19-frame decode. At 2 frames, host prefix decode took about 36 ms for 160 ms of audio; at 4 frames, about 54 ms for 320 ms. These are single host runs and cannot establish phone latency. Repeated full-prefix decoding becomes progressively more expensive; a production chunk decoder should avoid recomputing the entire prefix.

The supported next prototype is a bounded, cancelable small-chunk decoder with verified prefix agreement and an explicit buffer/underrun trace on the phone. Generation throughput remains a separate gate: prior full CPU frames consumed 259–283 ms per 80 ms audio frame and retained-context NPU CP alone was about 160 ms per frame in a sustained pilot. No ordinary app path should switch to chunk playback on the basis of these host checks alone.
