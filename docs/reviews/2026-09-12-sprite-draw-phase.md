# Draw-phase sprite updates

Based on #2591 and the rendering profile at commit 1acd9fb. Keep error-selection Flow identity stable. Move the changing frame state read into the Canvas draw callback; configuration and optional diagnostics stay in composition. Preserve the existing integer-frame API with an optional lazy frame provider. The debug overlay intentionally reads the frame in composition only when enabled.

Memoize source/destination geometry by frame under stable configuration, with a 64-entry FIFO cap. Replace the cache when sheet, crop, offset, density-derived geometry or map inputs change. No bitmap copies are cached per frame. Remove the unconditional per-frame SpriteRuntime logging from the former geometry path; the existing diagnostic overlay and debug animation traces remain available.

Regression coverage preserves sheet/frame-map precedence, crop adjustments, non-square scaling, invalid base geometry, cache reuse, bounded eviction and configuration replacement. Full local validation passed: 1,753 standardDebug and 1,225 standardRelease unit tests, zero failures/errors/skips, both lint tasks and both APK assemblies. This uses UI smoke builds with the missing-QAIRT-JNI allowance; it does not validate NPU readiness.

## Device comparison

NX733J, no inference, same foreground scene and preferences. The temporary ready/idle/thinking sequences were [0,1,2,3] at 200 ms; existing insertion configuration was retained. Release APKs used the same signing key. Baseline code: `1acd9fb9fb07dced99b140e1d8ea6871d903bdac`; candidate code: `feddd89f6c50fa787ac4fbe6e11fa15a347c8411`. Later report commits do not change APK code.

Unprofiled CPU samples use process user+system ticks over about 40 seconds after 20 seconds settling. Percent means whole-app CPU relative to one fully occupied core, not whole-device CPU or GPU utilization. PID and foreground were checked throughout. Each condition was sampled once, so these are observed results, not confidence intervals.

| Condition | CPU |
|---|---:|
| Baseline Release, animated | 8.8957% |
| Candidate Release, animated | 4.8869% |
| Candidate Release, background, 60.7 seconds | 0.3787% |
| Candidate Debug, original user configuration | 2.9178% |

Matched Release animation CPU fell by 45.1%. The original-configuration Debug sample has no fresh matched baseline in this run. Background CPU is app-wide and is not evidence of absolute zero animation-related activity.

## Rendering attribution

Separate 25-second Perfetto captures followed the CPU windows. Slice durations are elapsed durations, whereas scheduler totals are actual thread CPU. Nested slices must not be added together.

| Metric, 25-second trace | Baseline | Candidate |
|---|---:|---:|
| Main thread CPU | 1147.793 ms | 557.819 ms |
| RenderThread CPU | 594.359 ms | 619.985 ms |
| Whole-app scheduled CPU | 2426.783 ms | 1556.416 ms |
| Recomposer:recompose count | 134 | 10 |
| Recomposer:recompose elapsed total | 468.045 ms | 101.297 ms |
| Compose:applyChanges elapsed total | 99.150 ms | 2.929 ms |
| Record View#draw count | 130 | 129 |
| Record View#draw elapsed total | 87.024 ms | 101.627 ms |

This supports removing frame-driven composition work while retaining drawing frequency. Remaining prominent CPU consumers are RenderThread and the main thread. RenderThread CPU and draw recording did not decrease; their work is not eliminated by this patch. The existing pipeline already reports Skia/Vulkan. These traces do not provide GPU busy time or render-stage attribution, so they do not justify migrating to a separate GPU renderer.

Actual FrameTimeline late presentations were **15/128 (11.7%) baseline versus 20/128 (15.6%) candidate**. Baseline: 4 App Deadline Missed only, 11 Display HAL only. Candidate: 5 App Deadline Missed only, 10 Display HAL only, 5 combined. Thus this run demonstrates lower CPU, not improved presentation smoothness; the increase in late presentations is an unresolved follow-up, requiring repeated matched traces to distinguish variance from regression. Candidate Recomposer maximum elapsed duration was also higher (24.702 versus 19.944 ms), despite much lower total work. Do not infer smoothness from the large improvement in gfxinfo's janky-frame percentage: it measures a different population and is not the actual late-presentation percentage.

Both traces reported no trace errors. The scoped aggregate JSON excludes raw whole-system traces. No claim is made about long-run thermals, battery consumption, minimum possible resource usage, GPU saturation, or NPU execution.

## Visual and restoration checks

24 sprite crops per condition yielded the same five distinct SHA-256 hashes in baseline and candidate, providing exact equality for the captured images. After 60 seconds in the background, the candidate resumed and produced four distinct images, all present in the baseline set. This checks observed frame appearance and resumed playback; it does not prove every possible insertion or timing sequence.

The new Debug APK was reinstalled at the end. Both original preference files were restored and compared byte-for-byte, the original screen timeout was restored, and the foreground was returned to `com.openai.chatgpt/.MainActivity`. APK hashes and raw app-scoped sample aggregates are in the accompanying device JSON.

## Review status

PR #2592 is stacked on #2591 (`perf/sprite-event-clock`). Local unit tests, lint and APK builds passed. CI must be dispatched explicitly because the stacked PR base and perf branch do not match the workflow's automatic trigger filters. No merge is included in this measurement task. The CPU change is supported by the matched measurement; presentation latency remains the main unresolved performance check before calling the overall animation fully optimized.

## Repeated latency follow-up

The initial single-run latency concern was subsequently remeasured over six 60-second captures. See [the repeated latency review](2026-09-12-sprite-latency-repeat.md): late presentations were 10.73% old versus 11.04% new, with nearly unchanged display latency. CPU savings persisted; app completion median increased while p95 decreased. This updates the initial uncertainty without claiming improved smoothness.
