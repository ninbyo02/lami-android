# Grouped CP Android diagnostic pilot (2026-10-08)

The debug diagnostic accepts the hash-pinned grouped-cache16 CP model exported in #2743. The default FP32 bundle remains unchanged. Both variants use the same APK, main INT8 copy cache128, input reuse, sampling, heads, and PCM decoder.

Device: NX733J / SM8750. Order: baseline, grouped, grouped, baseline; two short sentences per process. APK assembled successfully with the explicit non-NPU smoke flag. This provides CPU evidence only.

| Measurement | Baseline | Grouped |
|---|---|---|
| Frame p50, ms (four requests) | [109, 127, 122, 143] | [117, 118, 114, 126] |
| CP prepare + forward weighted mean, ms/call | 3.287 | 2.838 |
| Main prepare + forward weighted mean, ms/call | 42.212 | 43.278 |

All eight requests completed. Generated code and PCM hashes match within each sentence and match the previously reviewed device clips. No duplicate Voice Lab clips are needed. The original APK, manifest, last WAV and probes were restored and hash verified; temporary candidate models were removed.

Full per-request metrics and restoration checks are in the adjacent JSON. Temperature and frequency were not controlled; this small trial does not establish a general speedup or meet the 80 ms/frame realtime budget (which must also accommodate PCM processing). The candidate remains diagnostic-only.

Artifacts on the test host: `/home/sato/project/lami-android-voice-dataset/device/cp-grouped-20261008/`.
