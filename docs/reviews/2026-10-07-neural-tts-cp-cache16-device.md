# CP cache16 device comparison

NX733J / SM8750, CPU/XNNPACK dynamic INT8, main copy-delegated INT8 fixed. Two texts, two fresh-process trials per CP capacity, order32/16/16/32.

| CP cache | CP forward mean (ms/call) | Main forward mean (ms/call) | Codec-frame p50 range (ms) | Over80ms |
| --- | ---: | ---: | ---: | ---: |
| 32 | 3.355 | 59.192 | 103–167 | 196/196 |
| 16 | 3.231 | 66.735 | 134–158 | 196/196 |

Aggregate CP speed ratio: 1.038×. Timings are uncontrolled end-to-end diagnostic measurements; thermal and battery snapshots are retained. Small differences must not be interpreted as controlled fixed-input performance guarantees. Main means include prefill; codec-frame timings exclude PCM decode and are not streaming first-chunk latency.

The small CP-only aggregate reduction does not establish an overall frame-speed improvement. Keep cache16 as a memory-reduction pilot; do not promote it as a realtime fix based on these timings.

All eight sentences completed synthesis and playback. Individual hashes and restoration checks are retained in JSON.

Both texts produced identical code and PCM hashes between capacities and across repeats.

All outputs also match the earlier reviewed actual-device copy-delegated main / CP cache32 outputs. The existing Voice Lab clips remain the listening reference; no duplicate audio was added. The saved user statuses are candidates with “問題なし。”.

The installed original APK, manifest, last WAV, and both diagnostic reports were freshly backed up and restored. Their hashes matched, and all three pilot model files were removed. The diagnostic APK was built with `lami.allowMissingQairt244Jni=true` for voice-only testing; it is not a production NPU APK.

The realtime target remains50–60ms per80ms audio frame, including PCM handling. Measure remaining main cache/attention work and CP delegate/call overhead next; this cache change alone cannot establish realtime TTS.

Raw host artifacts: `/home/sato/project/lami-android-voice-dataset/device/cp-cache16-20261007`.
