# Packed CP head diagnostic (2026-10-08)

Debug-only opt-in `cp_head_packed4` packs four independent FP32 rows by column and computes four logits using NEON multiply then add. Each row retains sequential accumulation, no fused multiply-add. Main and CP are fixed grouped INT8 cache128/cache16. Default head path unchanged.

Same-APK NX733J / SM8750 comparison: row-major, packed, packed, row-major; two texts per process. All eight requests completed with code/PCM hashes matching accepted clips. Original APK, manifest, WAV and probes restored and hash verified; candidate files removed.

| Metric | Row-major | Packed4 |
|---|---|---|
| Frame p50 ms | [141, 107, 124, 139] | [122, 123, 132, 128] |
| CP head mean ms/call | 1.434 | 1.135 |
| Packing ms per request | [0, 0, 0, 0] | [98, 87, 97, 85] |

Additional packed weights: 125,829,120 bytes per request. Packing currently occurs per request and is excluded from frame budget; this is a diagnostic candidate rather than a default promotion. Existing row-major maps retained for ownership. CPU-only smoke APK, temperature/frequency uncontrolled, two short texts. No realtime or NPU acceptance. Arithmetic startup checks passed on device; disassembly contains separate fmul/fadd and no fmla/fmadd. Full metrics in adjacent JSON.

Host artifacts: `/home/sato/project/lami-android-voice-dataset/device/cp-head-packed4-20261008/`.

Decision: retain as opt-in diagnostic only. CP head mean fell about 21% (~4.5 ms per 15-head frame), but full-frame p50 ranges overlap and current packing costs additional memory and startup time. Default promotion withheld. Next comparison should isolate fixed-input head performance from whole-model frequency/thermal variation before attempting further rollout.
