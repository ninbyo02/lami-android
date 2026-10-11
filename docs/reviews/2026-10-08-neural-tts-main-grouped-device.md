# Main grouped Android diagnostic (2026-10-08)

Hash-pin the main grouped INT8 cache128 candidate in debug diagnostics. Default FP32 bundle unchanged. NX733J / SM8750; same APK, grouped CP cache16, heads, sampling and PCM decoder for both variants. Compare baseline/grouped/grouped/baseline, two sentences per process.

| Metric | Baseline | Grouped |
|---|---|---|
| Frame p50, ms (four requests) | [75, 117, 101, 118] | [89, 100, 88, 94] |
| Main prepare + forward weighted mean, ms/call | 36.629 | 26.841 |
| CP prepare + forward weighted mean, ms/call | 2.493 | 2.579 |

All eight requests completed with identical code and PCM hashes within each sentence. Original APK, manifest, WAV and probes restored and hash verified; candidate files removed. Full metrics and restoration checks are in the adjacent JSON. APK assembled with the explicit non-NPU smoke flag; no NPU evidence. Temperature/frequency uncontrolled, two short texts, frame budget excludes PCM decoding. This is a diagnostic pilot, not realtime or broad voice quality acceptance.

Host artifacts: `/home/sato/project/lami-android-voice-dataset/device/main-grouped-20261008/`.

All request hashes also match the accepted device audio from the prior CP grouped comparison. No duplicate Voice Lab clips were created.
