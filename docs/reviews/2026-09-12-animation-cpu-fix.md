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

Validation and on-device results will be recorded after execution. This change does not establish the cause of the earlier background EXCESSIVE CPU USAGE termination.
