# Idle timeout recomposition investigation — 2026-09-12

## Finding and change

`ChatScreen` collected the whole `LamiUiState` and launched a six-second timeout keyed by state and last interaction time. `moveToIdleIfStale` rewrote an already-idle state with a fresh timestamp, restarting the effect indefinitely. The prior drawing-layer candidate trace had expensive recompositions roughly 6.035 seconds apart. This cadence was a hypothesis until tested against the fix.

The fix only launches the timeout while speaking. An idle or thinking state is a no-op, and an atomic StateFlow update rechecks the latest interaction timestamp before expiring speaking state. A real transition retains the existing timestamp-refresh behavior. The animation clock, frame interval, drawing layer and GPU backend are unchanged.

## Scope and validation

Stacked on PR #2594 (`f4b60ed927c32d9485bee5ee8a19db5f3ad0e269`). Code under test: `58547342a19633db26e071cf2516b913d6710d23`.

Debug: 1,758 tests. Release: 1,230 tests. No failures, errors or skips. Both lint tasks and both APK builds passed in 4m 11s. Build uses `-Plami.allowMissingQairt244Jni=true` for UI-only evaluation; this does not validate NPU inference readiness.

Five regression tests cover repeated idle no-op identity, pending inference, the timeout boundary, older timeouts after newer interaction, and backward wall-clock movement. Atomic update prevents a stale snapshot from unconditionally replacing a newer state.

## Physical-device protocol

NX733J, no inference. Same four-frame 200 ms base animation and original insertion configuration. Old/new/new/old/old/new, with 20-second settling and a separate 30-second unprofiled whole-process CPU sample followed by a 45-second Perfetto trace per run. CPU percent is relative to one CPU core. Foreground and PID are checked throughout. Visual crops compare animation frames; the final candidate also undergoes background and resume checks. Preferences are backed up byte-for-byte and restored, along with the original screen timeout and ChatGPT foreground.

Actual app FrameTimeline frames are joined to their matching SurfaceFlinger display frame. Late Present and App Deadline Missed are reported separately from Display HAL. App-start-to-presentation is not scheduled-animation-update latency or input-to-photon latency. Trace overlaps provide correlation; removing the periodic state emission and observing the cadence disappear tests the proposed cause.

## Vsync assessment

Do not add a permanent display-rate frame loop to this low-rate animation. Waiting for a frame after each existing timer can also postpone an update by another frame, so lower app-frame-start latency alone cannot justify that change. Any subsequent event-only synchronization experiment must compare scheduled update → state commit → actual presentation with identical frame content and background behavior. This change isolates the established idle-state defect first.

## Results

| Metric | Before (#2594) | After |
|---|---:|---:|
| Whole-process CPU (one-core basis) | 4.720% | 4.272% |
| Recomposer events / 135 s | 42 | 0 |
| Recomposer wall time | 379.613 ms | 0.000 ms |
| App Deadline Missed | 32/684 (4.68%) | 13/667 (1.95%) |
| Late Present | 93/684 (13.60%) | 70/667 (10.49%) |
| RenderThread scheduled CPU / 135 s | 3218.087 ms | 3207.528 ms |
| app_ms median / p95 | 13.903 / 15.801 ms | 13.811 / 15.567 ms |
| app_start_to_present_ms median / p95 | 31.329 / 47.896 ms | 31.352 / 47.853 ms |

CPU observed reduction: 9.5%. Three repeated samples per version; this is a short controlled device comparison, not a population estimate. Frame counts can differ because redundant idle-state redraws disappear.

Per-run results:

| Run | Frames | Late | App deadline | Recompositions |
|---|---:|---:|---:|---:|
| 1_old | 229 | 31 | 9 | 14 |
| 2_new | 223 | 22 | 4 | 0 |
| 3_new | 222 | 30 | 6 | 0 |
| 4_old | 228 | 34 | 13 | 14 |
| 5_old | 227 | 28 | 10 | 14 |
| 6_new | 222 | 18 | 3 | 0 |

Evidence: [JSON](2026-09-12-idle-timeout-recomposition-evidence.json). Raw traces, private preference backups and screen crops remain outside git.


## Interpretation and disposition

All three old traces contain 14 recomposition slices (seven expensive events and seven small follow-ups); all three new traces contain none. App-only deadline misses fall from 19 to zero. Combined Display HAL + App Deadline Missed remains 13 in both versions; Display HAL-only changes from 61 to 56, with one additional SurfaceFlinger scheduling miss in the candidate. This supports the idle-state self-update as the cause of the periodic application work, while showing that the remaining presentation limitation is not removed by this fix. RenderThread CPU and presentation median/p95 are effectively unchanged. The short-run overall late-rate decrease is an observation, not a guarantee across devices or workloads.

The old per-run CPU range is 4.489–4.886%; new is 4.133–4.440%. The candidate background sample had zero measured CPU tick increment over 60.799 seconds, which means below this measurement's resolution, not literally zero resource use. Initial old/new crops have identical five-frame hash sets; resumed animation has four hashes, all in that set. This verifies sampled content and resumption, not every application flow.

The first sixth-run attempt lost foreground to ChatGPT before its CPU sample completed. It was discarded, settings were restored, and only that run plus background/resume checks were retried. All six retained traces have no Perfetto error stats. Measurement, including retry and restoration, ran 06:34:53–06:48:33 UTC. The accepted candidate Debug APK was then installed; preferences were reverified byte-for-byte, screen timeout is 600000 ms, and foreground is ChatGPT.

Adopt the idle-state fix. Keep event-driven animation scheduling as-is in this PR. Event-only vsync synchronization remains unimplemented and unmeasured; no benefit is claimed. A next experiment needs scheduled-update markers and presentation matching before it can justify changing the clock. Adding a wait based only on app-start-to-present measurements would not establish lower end-to-end delay.

GitHub CI passed both Debug and Release for code commit `58547342a19633db26e071cf2516b913d6710d23`: [run 34678338036](https://github.com/ninbyo02/lami-android/actions/runs/34678338036). The subsequent commit only adds this report and evidence; an exact-head CI rerun is dispatched separately. PR #2594 is the prerequisite and remains unmerged.
