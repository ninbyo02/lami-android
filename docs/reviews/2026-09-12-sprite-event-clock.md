# Event-driven sprite clock

Follow-up to #2589 on main 6c0fb63d. Synchronous playback now sleeps until the next distinct visible frame, or a loop boundary where insertion decisions must be reconsidered. Completely static configurations without eligible insertions suspend without periodic wakeups. Lifecycle restart and setting changes refresh immediately.

Retain seeded insertion selection, cooldown history, shared epoch, exclusive/non-exclusive mixing and interval rounding. Cache the finite loop timeline across ticks and across loops with identical decisions. Resolve long insertion holds by indexing rather than constructing an expanded list; storage is bounded by base frame count.

Regression coverage compares legacy visible frames and event deadlines across representative insertions and intervals, tests loop-boundary preservation, static suspension for a virtual day, lifecycle restart and cancellation, and a maximum-int insertion interval.

Scope: normal synchronized UI playback. Legacy randomized non-synchronized playback is unchanged. Historical long-background catch-up still replays insertion decisions to preserve cooldown semantics; this change does not claim bounded catch-up time or repair of the old excessive-CPU termination. Diagnostic tick logging now follows useful update events rather than every base tick.

Debug unit suite: 1,747 tests passed. Release unit suite: 1,219 tests passed (the existing release test selection differs). Both lint tasks and both APK assemblies passed. UI-only smoke APKs do not establish NPU readiness.

## Device verification limitation

Both attempts failed the foreground precondition after settling: the phone was showing ChatGPT rather than LAMI. No CPU sample or screenshot animation result was accepted, and Release was not installed. Do not infer a further percentage reduction from this change yet. The earlier 7–8% figure belongs to #2589 and is whole-process debug CPU, not a new result or sprite-only measurement.

Both preference files were restored byte-for-byte after each attempt. The new standardDebug APK remains installed; the phone was returned to ChatGPT. The [attempt record](2026-09-12-sprite-event-device.json) records APK hashes, the source commit and restoration status without preference contents.

For the next measurement, keep LAMI foreground throughout the settling and sampling windows. Compare old/new debug with identical original settings, then debug/release with visibly changing frames at 200ms intervals. Record process CPU, PSS, gfxinfo and distinct avatar crops separately so screenshot work does not contaminate CPU samples. Restore preferences afterward. Those checks were outstanding at that point; the completed reevaluation below supersedes that limitation.

## Completed on-device reevaluation

The user kept the phone available for a complete run. Both APKs identify source commit `1acd9fb`. No inference was requested. Foreground was checked before, every 10 seconds during, and after each CPU window; PID remained unchanged within each sample. Each foreground CPU sample lasted about 40 seconds after 20 seconds settling. Background sampling lasted about 60 seconds after 5 seconds settling. CPU is whole-process, one-core basis; it is not sprite-only CPU.

| Condition | CPU |
| --- | ---: |
| previous_debug_original_settings | 8.48% |
| event_debug_original_settings | 3.27% |
| event_debug_changing_frames | 14.80% |
| event_release_changing_frames | 8.48% |
| event_release_background | 0.39% |

The same-original-settings debug comparison shows a 61.4% reduction. This is a single-run comparison, not a statistical benchmark. Original wait settings repeat identical base frames while retaining eligible insertion decisions.

Changing-frame tests temporarily configured ready/idle/thinking base frames `[0,1,2,3]` at 200ms intervals. Avatar-region screenshot samples contained 5 distinct crops on Debug and 4 on Release, supporting visible changes rather than merely a running clock. Screenshot capture occurred outside CPU windows. Both preference files were restored byte-for-byte, the new Debug APK restored, and ChatGPT brought forward. The temporary screen timeout was also restored.

The renderer still reports Skia (Vulkan). Release changing-frame gfxinfo reported 321 rendered frames, 179 janky frames (55.76%), 95th percentile 22ms and 99th percentile 53ms. This is app-window telemetry, including the settling interval, not an isolated sprite trace or the percentage of sprite frames skipped. Visible motion works, but smoothness is not established and remaining rendering cost warrants tracing before claiming minimum resource use. No old Release changing-frame control was measured, so this is not evidence of a Release regression.

Whole-process PSS was 223,674 KiB for previous Debug wait, 220,318 KiB for new Debug wait, 224,902 KiB for new Debug changing frames, and 188,471 KiB for new Release changing frames. These point samples do not establish reduced peak allocation or absence of leaks. The short background test does not establish resolution of the historical excessive-CPU termination.

Raw samples, graphics/memory summaries, foreground checks and APK hashes: [reevaluation record](2026-09-12-sprite-event-reevaluation.json).
