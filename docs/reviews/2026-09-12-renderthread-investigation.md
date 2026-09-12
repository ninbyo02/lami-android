# RenderThread and buffer-submission investigation

## Scope

Read-only analysis of the six existing NX733J Release traces from the repeated latency test: old/new/new/old/old/new, 60 seconds each. No new device interaction, settings changes, APK changes or production changes were needed. Code identities remain old `1acd9fb9fb07dced99b140e1d8ea6871d903bdac` and new `feddd89f6c50fa787ac4fbe6e11fa15a347c8411`.

## Findings

The remaining RenderThread work is mostly CPU execution inside drawing and submission, not a long blocked wait for a free buffer. The larger elapsed RenderThread duration in the new build includes more runnable/preempted time. This identifies the observed cost, but not why the scheduler allocated CPU differently.

| Scoped interval, total over three 60-second traces | Old CPU running | New CPU running | Old runnable/preempted | New runnable/preempted |
|---|---:|---:|---:|---:|
| DrawFrames | 3777.746 ms | 3902.625 ms | 410.624 ms | 806.591 ms |
| Drawing | 3216.287 ms | 3336.824 ms | 346.499 ms | 724.140 ms |
| flush commands | 1397.497 ms | 1445.167 ms | 131.454 ms | 369.597 ms |
| queueBuffer, outermost | 786.209 ms | 822.377 ms | 110.104 ms | 215.781 ms |
| QueueSubmit | 190.431 ms | 198.621 ms | 56.801 ms | 244.599 ms |
| dequeueBuffer | 136.639 ms | 137.987 ms | 12.509 ms | 15.770 ms |

These intervals nest. In particular, drawing contains flush/submission work, so table rows must **not** be added. Nested queueBuffer slices were deduplicated by selecting only the outermost interval with the same name. Earlier unspecialized queries counted two queueBuffer slices per frame; this investigation corrects that double counting.

Within DrawFrames, CPU running increases only 3.3% (3777.746 → 3902.625 ms), while runnable/preempted time increases 96.4% (410.624 → 806.591 ms). Blocking/sleep time is small: old D+S 33.984 ms versus new 38.326 ms over 180 seconds. The new build's outer queueBuffer intervals contain 822.377 ms CPU and 215.781 ms runnable/preempted time, with no recorded blocking/sleep time. Dequeue median is about 0.14 ms in both versions. These traces do not support a long buffer-availability stall as the dominant cost.

Drawing contains 3336.824 ms of the new DrawFrames CPU, about 85.5%. This is the next CPU optimization target. The reported full-window Drawing bounds do not establish that every pixel was rerasterized, nor prove the sprite is the only contributor; the scope includes the window's rendering work. Queue submission is part of this cost, not an independently additive bottleneck.

## Timing decomposition

App frame IDs were joined to RenderThread DrawFrames tokens. 1,827 of the 1,828 completed app frames had a complete matching DrawFrames interval (one frame in run 3 lacks a complete match at the trace boundary). The comparison below uses matched frames only; each join is unique. Stage aggregates include a few complete RenderThread intervals whose matching app interval crosses a capture boundary, so stage counts differ slightly from matched-frame counts.

| Run | Matched frames | App start → RenderThread start, median | RenderThread elapsed, median | RenderThread end → app completion, median |
|---|---:|---:|---:|---:|
| 1_old | 305 | 4.611 ms | 4.882 ms | 0.166 ms |
| 2_new | 305 | 1.184 ms | 5.042 ms | 7.403 ms |
| 3_new | 304 | 1.138 ms | 5.143 ms | 7.048 ms |
| 4_old | 302 | 4.464 ms | 4.868 ms | 0.248 ms |
| 5_old | 306 | 4.521 ms | 4.687 ms | 0.164 ms |
| 6_new | 305 | 1.126 ms | 5.073 ms | 7.260 ms |

The optimization gets RenderThread started earlier (about 4.5 → 1.1 ms), consistent with eliminating frame-driven composition. RenderThread duration stays near 5 ms. The remaining completion tail increases from a median near 0.2 ms to roughly 7.0–7.4 ms. Medians of separate components do not add to the median of the total.

Across new traces, positive completion tails sum to 4963.715 ms, but RenderThread actually runs for only 35.594 ms inside them. Therefore the tail is not seven milliseconds of additional RenderThread CPU work. Observed GPU-completion wait slices total only 9.633 ms over all three new traces (maximum individual wait 0.527 ms). Those waits may begin after earlier GPU work and cannot measure total GPU utilization or explain the full tail. Fence timestamps, hardware/driver completion and display pacing remain candidates; assigning the tail to pure GPU execution would be unsupported.

The prior presentation result still applies: old/new display-latency medians 31.354/31.387 ms and late-present rates 10.73%/11.04%. This investigation explains where CPU and elapsed time differ; it does not show a new display regression or improvement.

## CPU frequency cross-check

Scheduling slices were intersected with cpufreq counter intervals by CPU and timestamp. Values below are running-time-weighted reported frequencies, not effective instruction throughput or a power measurement.

| Run | RenderThread CPU with frequency coverage | Weighted frequency |
|---|---:|---:|
| 1_old | 1391.770 ms | 437.2 MHz |
| 2_new | 1388.505 ms | 442.3 MHz |
| 3_new | 1388.050 ms | 451.4 MHz |
| 4_old | 1309.013 ms | 475.4 MHz |
| 5_old | 1323.702 ms | 470.0 MHz |
| 6_new | 1382.017 ms | 450.6 MHz |

The ranges overlap; these data do not support a simple claim that the new build always ran at a lower CPU frequency. Core assignment and scheduler competition may vary. No performance-mode, governor, priority or refresh-rate change was made.

## Next bounded experiment

Retain #2591/#2592's demonstrated CPU savings. Next compare the current sprite with an isolated sprite drawing layer, initially without forcing offscreen compositing. The question is whether it reduces repeated window drawing/submission work without increasing memory, clipping frame offsets/crops, or worsening late presentations. This is an experimental hypothesis, not an approved performance claim or a reason to replace Compose with a separate GPU renderer.

A useful acceptance check is the same order-balanced Release protocol, paired with RenderThread CPU, runnable time, FrameTimeline late presentations, drawing counts, memory, exact captured-image equality and background/resume. Reject the experiment if it merely moves cost into GPU/memory or increases presentation latency. Additional device measurements should be announced before starting; no additional device test was needed for this report.

## Reproducibility and limits

Select the active RenderThread by the largest count of DrawFrames slices, rather than selecting the last process/thread row: traces can contain stale same-name process records. Intersect each selected interval with thread_state, clipping durations at both ends. R/R+ are treated as runnable/preempted, Running as actual CPU, D/S as blocked/sleep. Select outermost duplicate-name scopes. Frequency intervals use LEAD(timestamp) per CPU counter track, joined to sched intervals with overlap weighting. GPU wait slices are queried only in the active app process.

The source traces had no reported errors. This is app-scoped aggregation of previously acquired traces; raw system traces and private preferences are not committed. The companion JSON preserves per-run interval, completion-tail and frequency results. Production code was unchanged.
