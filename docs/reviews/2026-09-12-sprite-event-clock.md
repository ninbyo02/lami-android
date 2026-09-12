# Event-driven sprite clock

Follow-up to #2589 on main 6c0fb63d. Synchronous playback now sleeps until the next distinct visible frame, or a loop boundary where insertion decisions must be reconsidered. Completely static configurations without eligible insertions suspend without periodic wakeups. Lifecycle restart and setting changes refresh immediately.

Retain seeded insertion selection, cooldown history, shared epoch, exclusive/non-exclusive mixing and interval rounding. Cache the finite loop timeline across ticks and across loops with identical decisions. Resolve long insertion holds by indexing rather than constructing an expanded list; storage is bounded by base frame count.

Regression coverage compares legacy visible frames and event deadlines across representative insertions and intervals, tests loop-boundary preservation, static suspension for a virtual day, lifecycle restart and cancellation, and a maximum-int insertion interval.

Scope: normal synchronized UI playback. Legacy randomized non-synchronized playback is unchanged. Historical long-background catch-up still replays insertion decisions to preserve cooldown semantics; this change does not claim bounded catch-up time or repair of the old excessive-CPU termination. Diagnostic tick logging now follows useful update events rather than every base tick.

Debug unit suite: 1,747 tests passed. Release unit suite: 1,219 tests passed (the existing release test selection differs). Both lint tasks and both APK assemblies passed. UI-only smoke APKs do not establish NPU readiness.

## Device verification limitation

Both attempts failed the foreground precondition after settling: the phone was showing ChatGPT rather than LAMI. No CPU sample or screenshot animation result was accepted, and Release was not installed. Do not infer a further percentage reduction from this change yet. The earlier 7–8% figure belongs to #2589 and is whole-process debug CPU, not a new result or sprite-only measurement.

Both preference files were restored byte-for-byte after each attempt. The new standardDebug APK remains installed; the phone was returned to ChatGPT. The [attempt record](2026-09-12-sprite-event-device.json) records APK hashes, the source commit and restoration status without preference contents.

For the next measurement, keep LAMI foreground throughout the settling and sampling windows. Compare old/new debug with identical original settings, then debug/release with visibly changing frames at 200ms intervals. Record process CPU, PSS, gfxinfo and distinct avatar crops separately so screenshot work does not contaminate CPU samples. Restore preferences afterward. Release frame pacing and memory use remain unverified on-device.
