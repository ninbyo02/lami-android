# Event-driven sprite clock

Follow-up to #2589 on main 6c0fb63d. Synchronous playback now sleeps until the next distinct visible frame, or a loop boundary where insertion decisions must be reconsidered. Completely static configurations without eligible insertions suspend without periodic wakeups. Lifecycle restart and setting changes refresh immediately.

Retain seeded insertion selection, cooldown history, shared epoch, exclusive/non-exclusive mixing and interval rounding. Cache the finite loop timeline across ticks and across loops with identical decisions. Resolve long insertion holds by indexing rather than constructing an expanded list; storage is bounded by base frame count.

Regression coverage compares legacy visible frames and event deadlines across representative insertions and intervals, tests loop-boundary preservation, static suspension for a virtual day, lifecycle restart and cancellation, and a maximum-int insertion interval.

Scope: normal synchronized UI playback. Legacy randomized non-synchronized playback is unchanged. Historical long-background catch-up still replays insertion decisions to preserve cooldown semantics; this change does not claim bounded catch-up time or repair of the old excessive-CPU termination. Diagnostic tick logging now follows useful update events rather than every base tick.

Validation results will be appended after execution. UI-only smoke APKs do not establish NPU readiness.
