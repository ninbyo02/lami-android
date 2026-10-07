# Main INT8 copy delegation: NX733J device comparison

## Result

Copy delegation improves the full INT8 main Transformer on NX733J, but does not achieve realtime TTS. The original FP32 deployment remains restored. CPU/XNNPACK INT8 PTQ is a pilot, not NPU or QAT.

| Main variant | Weighted main forward (ms/call) | Weighted CP forward (ms/call) | Codec-frame p50 range (ms) | Frames over 80 ms |
| --- | ---: | ---: | ---: | ---: |
| FP32 | 86.067 | 3.559 | 169–188 | 222/222 |
| INT8 control | 173.553 | 4.385 | 236–301 | 196/196 |
| INT8 with delegated permute copies | 72.715 | 4.032 | 155–190 | 196/196 |

Copy delegation improves weighted main-forward time by 2.39× versus the INT8 control. Main-forward aggregates include prefill and generation calls. Frame timings measure codec generation and exclude subsequent PCM decoding; they are not first-chunk streaming latency. The end-to-end 50–60 ms target per 80 ms audio frame remains unmet.

## Method and quality

- Two texts, two fresh-process trials per main variant; order FP32 / INT8 / delegated-copy INT8 / delegated-copy INT8 / INT8 / FP32.
- All variants use the same full INT8 CP program and FP32 heads/embeddings/decoder.
- Twelve sentence generations completed synthesis and playback. FP32 and INT8 generated lengths differ, so these are diagnostic comparisons rather than identical-input speed measurements.
- For both texts, INT8 control and copy-delegated INT8 produce identical code and PCM hashes across both trials. FP32 and INT8 audio differ.
- Battery and thermal snapshots are retained, but frequency, temperature, and scheduling were not controlled.

## Listening and restoration

Four new actual-device clips are available in [lami-main-copy-device-review-20261007](http://192.168.52.99:8088/#main-copy-device-review), in FP32 / copy-delegated INT8 order for each text. All four actual-device clips were marked candidate with “問題なし。” by the user; this acceptance covers these two texts only. See the accompanying listening JSON.

Before each phase, the installed APK, manifest, last WAV, and both diagnostic reports were backed up. Both phases restored and verified their original hashes and confirmed pilot model removal. APK and manifest hashes, individual timings, output hashes, and restoration checks are in the accompanying JSON.

## Next optimization

Keep the copy-delegated main pilot for further measurements. Profile the remaining main cache/attention work on ARM and the 16 serial CP forwards per frame. CP plus heads/sampling is still a substantial frame cost, so main INT8 alone cannot reach the realtime budget. Use captured real hidden/KV inputs for the next microbenchmarks and preserve full-feedback EOS and listening checks before promotion.

Raw host artifacts: `/home/sato/project/lami-android-voice-dataset/device/main-copy-20261007-0633`.
