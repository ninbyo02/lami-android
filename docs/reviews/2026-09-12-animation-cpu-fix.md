# Sprite animation CPU optimization and rebuilt #680 coverage

Baseline measurements and the audit of #680 are in PR #2588. Animation on/off/on was 48.75% / 0.82% / 48.72% CPU (one-core basis) with no inference.

## Implementation

Keep Compose Canvas sprite rendering. Replace the display-refresh clock with a lifecycle-aware clock that wakes at the next configured base-frame boundary, using the existing shared uptime epoch. Pause below STARTED and compute the current time on resume. Include animation settings in derived-state keys so changing settings does not retain a stale specification.

Both synchronous and non-synchronous playback stop outside STARTED. Invalid insertion patterns fall through to base playback instead of retrying without suspension. Animation delays have a 16ms minimum, including zero/negative insertion intervals; stored configuration is not modified. Sub-16ms requested intervals are sampled at this minimum to prevent a busy loop, rather than promising a visible frame for every configured millisecond.

## Rebuilt regression coverage

- Lifecycle clock: no work before STARTED; pause/resume at the shared epoch; cancellation; sprite cadence rather than display cadence.
- Insertion logic: enable/probability boundaries, loop periods, cooldowns, empty/invalid patterns and weighted boundaries.
- Sprite frame maps: grid-relative X/Y offsets, configured fallback and invalid geometry/index handling.
- Server initialization: invalid URL pruning and promotion when no active entry exists, while preserving the modern empty-server behavior.
- Server settings: validate active entries only; publish the normalized active URL after client refresh.
- Model selection: persist a single model, clear unavailable selections, and restore per-server choices when switching single/multiple model servers.

The original header-padding change in #680 is unrelated and is deliberately omitted. The three previously restored test files remain in the normal test source set.

## GPU decision

Android hardware-accelerated Canvas already handles supported image drawing. Moving application-side state/timing work to a bespoke OpenGL/Vulkan renderer would add complexity without addressing redundant Compose updates. Measure reduced CPU work before considering renderer replacement.

Sources: https://developer.android.com/develop/ui/compose/performance/bestpractices and https://developer.android.com/develop/ui/views/graphics/hardware-accel .

This change does not establish the cause of the earlier background EXCESSIVE CPU USAGE termination.

Rebuilt URL coverage exposed a bare-scheme normalization bug: `http://` was trimmed and then prefixed as though `http:` were a hostname. Reject bare HTTP/HTTPS schemes before adding a default scheme. The mixed-valid/invalid initialization regression covers this fix.

Repeated frame indices are now derived state, so clock ticks that resolve to the same image do not invalidate composition. Debug diagnostics observe the clock through snapshotFlow rather than effect keys.

## Completed validation and device results

All 1,734 standardDebug unit tests passed with zero failures/errors/skips; lintStandardDebug and assembleStandardDebug passed after the final code change, commit `3d1ec6618c13e8449dbd208b224450c5ee9b6897`.

NX733J, animation enabled, unchanged preferences, no inference; process CPU is reported on a one-core basis using /proc/PID/stat and device CLK_TCK. Each foreground sample followed 20 seconds settling and lasted about 40 seconds.

| Version | Foreground CPU | Foreground after resume |
| --- | ---: | ---: |
| Previous installed baseline (see #2588) | 48.75% | 48.72% (on/off/on restoration) |
| Frame-paced clock | 13.75% | 11.25% |
| Frame-paced clock + unchanged-frame suppression | 8.42% | 7.02% |

The final initial sample is approximately 83% below baseline. Background CPU over 60 seconds after 5 seconds settling was 1.08%; the process survived and foreground playback scheduling resumed. This short test does not prove the historical five-minute excessive-CPU termination is fixed.

The device reports `Pipeline=Skia (Vulkan)`: GPU-assisted rendering is already active. The observed ready/idle configurations repeat identical base frame indices (`[0,0,0,0]` / `[8,8,8,8]`). The first optimized avatar screenshot samples were identical, consistent with that configuration; these measurements do not establish continuously changing visual playback quality or the same gain during inference. Results apply to this debug build and configuration, not all release workloads.

The UI verification APK used `-Plami.allowMissingQairt244Jni=true`; it is not evidence for NPU runtime readiness. No app data was cleared or animation preference changed, and ChatGPT was restored to the foreground afterward.

Raw records: [first optimization](2026-09-12-animation-optimized-device.json), [final optimization](2026-09-12-animation-deduplicated-device.json). They include code identity and APK SHA-256.
