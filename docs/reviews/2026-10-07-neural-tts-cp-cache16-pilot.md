# CP cache16 pilot

## Change

The code predictor uses positions 0..15 in each codec frame: one hidden input, the first codec embedding, and 14 subsequent codec embeddings. Exporting a 16-slot rather than 32-slot cache halves KV input/output storage (1,310,720 to 655,360 bytes for both FP32 K/V together). It preserves all five layers and 35 dynamic INT8 linear constants.

The existing exporter already supports capacity16. The full-feedback validator now accepts `--cp-capacity 16` only with an isolated candidate; its default remains32. The debug-only Android selector pins `cp-int8-cache16.pte` to its exact SHA-256 and chooses a matching 16-slot workspace. Fixed “hai” and the production path remain unchanged.

## Host evidence

| Input | Cache32 median trial mean | Cache16 median trial mean | Speedup |
| --- | ---: | ---: | ---: |
| Frame16, position0 | 5.116 ms | 4.451 ms | 1.15× |
| Frame16, position15 | 5.058 ms | 4.476 ms | 1.13× |

These are seven alternating trials, each with three warmups and eight measured forwards, on the host Python ExecuTorch runtime (torch threads2). Inputs are previously captured FP32 host full-feedback states. All three outputs match bit for bit for both inputs after comparing the cache32 prefix to cache16.

Separate instrumented executor-runner traces are retained in JSON. They are noisy and mixed: position0 slightly regresses, while position15 improves. They do not establish Android performance, and trace timings must not be combined with Python microbenchmarks. Native expand/index updates remain visible.

With the reviewed copy-delegated main INT8 model, both held-out texts reach EOS at49/50 frames, produce finite bounded PCM, and yield identical codes and WAV SHA-256 to the corresponding cache32 PC outputs. These two clips therefore need no duplicate PC listening review. Android output equivalence and speed are pending.

## Next gate

Compare cache32/cache16 on NX733J with main copy-delegated INT8 fixed, backing up and restoring APK/config/audio/reports. Capture new device audio if output hashes differ. Even a comparable CP improvement cannot by itself close the155–190ms measured codec-frame budget to the50–60ms target.

Candidate SHA-256: `43d29ccbd80d8f9c6e0e2cf57d84c5939a772d31262301088b73f7613e22e22c`. Raw host artifacts: `/home/sato/project/lami-android-voice-dataset/qat/cp-cache16-pilot-20261007`.
