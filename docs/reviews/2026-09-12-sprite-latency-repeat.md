# PR #2592 review and repeated presentation-latency measurement

## Code review

Reviewed the production diff against `22ba585b78807c07a56aa4a2e209563070b83d70`, frame-region rendering and repository behavior, state/clock selection, diagnostic overlay, and the six geometry tests. No blocking functional defect was identified in the reviewed paths. The frame provider captures the selected state object and reads it in Canvas drawing; the overlay retains its explicit composition read. Geometry cache keys cover sheet/configuration, maps, crop, destination size and density-derived offsets. Existing source-map precedence and bounds/placeholder handling remain in place, with a 64-entry cache limit and no per-frame bitmap copies.

Scope limits: unit tests exercise geometry and caching, but are not an automated Compose assertion that each configuration change replaces the cache. The previous device run checked captured-image equivalence and background/resume. This repeated latency run retains the exact previous APKs and does not expand coverage to all editor settings or inference workloads. The comment calling the cache "Draw-thread local" should be understood as use in the Canvas draw callback on the UI thread, not Android RenderThread execution.

CI run [34675154097](https://github.com/ninbyo02/lami-android/actions/runs/34675154097) completed successfully for both standard Debug and Release verification at report head `24c0b412e79c670f89ab0a339df57dcb75acc378`. The measured code is `feddd89f6c50fa787ac4fbe6e11fa15a347c8411`; subsequent changes are reports only. #2592 still depends on unmerged #2591.

## Protocol and interpretation

Same NX733J, Release builds, no inference; temporary [0,1,2,3] sequences at 200 ms for ready/idle/thinking, existing insertion settings retained. Six 60-second Perfetto captures, with a fresh install/restart and 20-second settling period for every capture. Order: old, new, new, old, old, new. Each version therefore has three runs, including reversed order in the middle pair. Process identity and foreground were checked every 10 seconds. No screenshot capture occurred inside the trace windows. System thermal status and battery temperature were sampled before/after each capture; frequency/refresh policy was left as configured by the user. This is an order-balanced descriptive comparison, not a randomized long-term study.

The primary endpoint is FrameTimeline Late Present per completed app surface frame. App Deadline Missed and Display HAL are also counted; combined classifications belong to both categories and must not be added as exclusive counts. Only complete slices are used. Prediction validity and trace errors are recorded in the JSON.

[Perfetto's FrameTimeline documentation](https://perfetto.dev/docs/data-sources/frametimeline) distinguishes app completion from display presentation. Here, app duration includes completion/posting (including GPU completion), and is not CPU busy time. For display timing, join an app surface frame to SurfaceFlinger by display_frame_token, and its expected timeline by process/token/layer. Report app actual start to the associated SurfaceFlinger actual end; this is **not touch-to-photon latency or animation-clock-to-photon latency**. Display overrun is SurfaceFlinger actual end minus expected end. Each join is checked for duplicate app frame IDs. Quantiles use nearest-rank p95 and the ordinary median.

CPU attribution comes from scheduler running time in the same instrumented traces. It should not replace the earlier unprofiled CPU comparison. Raw whole-system traces and private preferences remain outside git; only app-scoped aggregates and the protocol/restoration record are included.

## Results

| Run | Version | Frames | Late presentations | App deadline missed | App completion median / p95 | Start-to-display median / p95 |
|---|---|---:|---:|---:|---:|---:|
| 1_old | old | 305 | 37 (12.13%) | 19 | 10.729 / 18.241 ms | 31.334 / 48.024 ms |
| 2_new | new | 305 | 41 (13.44%) | 18 | 13.886 / 16.243 ms | 31.388 / 47.874 ms |
| 3_new | new | 305 | 29 (9.51%) | 13 | 13.820 / 16.394 ms | 31.345 / 47.741 ms |
| 4_old | old | 302 | 31 (10.26%) | 14 | 10.936 / 18.093 ms | 31.372 / 47.963 ms |
| 5_old | old | 306 | 30 (9.80%) | 12 | 10.432 / 17.989 ms | 31.365 / 47.708 ms |
| 6_new | new | 305 | 31 (10.16%) | 12 | 13.956 / 16.314 ms | 31.416 / 47.853 ms |

| Aggregate metric | Old | New |
|---|---:|---:|
| Late presentations | 98/913 (10.73%) | 101/915 (11.04%) |
| App Deadline Missed, including combined | 45/913 (4.93%) | 43/915 (4.70%) |
| App completion, median | 10.683 ms | 13.863 ms |
| App completion, p95 | 18.119 ms | 16.352 ms |
| App start to display, median | 31.354 ms | 31.387 ms |
| App start to display, p95 | 47.963 ms | 47.857 ms |
| Display presentation overrun, median | 0.003 ms | 0.006 ms |
| Display presentation overrun, p95 | 16.645 ms | 16.650 ms |

Whole-app scheduled CPU across three 60-second traces was 17316.216 ms old and 10417.436 ms new (39.8% less under tracing). This supports the earlier unprofiled CPU reduction; it is not a fresh unprofiled CPU percentage. Rendering frequency remained approximately five frames per second.

All 1,828 complete app frames had valid predictions, all app-to-display joins were one-to-one and complete, and all six traces reported no errors. System thermal status was 0 at every recorded boundary, with battery temperature 31–32 °C. These boundary samples do not rule out every instantaneous frequency or scheduling variation.

## Review judgment

- The previous single-run late-presentation increase from 11.7% to 15.6% did not recur at that magnitude in the repeated aggregate: 10.73% old versus 11.04% new, a 0.31 percentage-point difference. Run ranges overlap (old 9.80–12.13%, new 9.51–13.44%); the middle order-reversed pair favors the new version, the other two have small increases. This does not establish statistically proven equivalence or a consistent presentation regression.
- Display latency is essentially unchanged in these observations: median +0.033 ms and p95 -0.106 ms. Do not advertise the CPU patch as a smoothness improvement.
- App completion is mixed: median rises 10.683 → 13.863 ms consistently across runs, while p95 falls 18.119 → 16.352 ms. The median increase is a repeatable observation and should remain visible in the review. It is elapsed completion/posting time, not CPU work, and does not by itself identify GPU processing or the exact wait responsible. App deadline classifications are slightly lower overall.
- The CPU reduction remains a useful result. Keep the draw-phase/Flow optimization as a CPU improvement, while tracking rendering/presentation separately. No functional blocker was found in the reviewed diff, and this experiment does not establish a display-latency regression large enough to justify reverting it. Merging remains outside this request.
- The next focused investigation should attribute RenderThread drawing, buffer submission/queueing and presentation scheduling before making another change. Inspecting the first old/new pair showed similar RenderThread scheduled CPU (1391.770 versus 1388.436 ms), with queueBuffer/flush activity still present. Full-window drawing slice names do not prove that every pixel was rerasterized. A separate GPU renderer or forced offscreen layer is not justified by the available measurements; either requires a new measured comparison and a memory cost assessment.

## Restoration and evidence

Measurement started 2026-09-12 05:23:33 UTC and finished 05:31:59 UTC (about 8 minutes 27 seconds). The new Debug APK was reinstalled, both original preference files were restored and verified byte-for-byte, the screen timeout was restored to 600000 ms, and ChatGPT was confirmed in the foreground. The device is available for use.

Companion `2026-09-12-sprite-latency-repeat.json` contains protocol, APK identity, restoration and per-run/aggregate measurements. Production code and APKs were not changed during this review.
