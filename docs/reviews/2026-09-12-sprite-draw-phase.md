# Draw-phase sprite updates

Based on #2591 and the rendering profile at commit 1acd9fb. Keep error-selection Flow identity stable. Move the changing frame state read into the Canvas draw callback; configuration and optional diagnostics stay in composition. Preserve the existing integer-frame API with an optional lazy frame provider. The debug overlay intentionally reads the frame in composition only when enabled.

Memoize source/destination geometry by frame under stable configuration, with a 64-entry FIFO cap. Replace the cache when sheet, crop, offset, density-derived geometry or map inputs change. No bitmap copies are cached per frame. Remove the unconditional per-frame SpriteRuntime logging from the former geometry path; the existing diagnostic overlay and debug animation traces remain available.

Regression coverage preserves sheet/frame-map precedence, crop adjustments, non-square scaling, invalid base geometry, cache reuse, bounded eviction and configuration replacement. Validation and device comparison follow.
