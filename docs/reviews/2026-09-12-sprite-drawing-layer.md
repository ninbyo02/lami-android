# Isolated sprite drawing experiment

Baseline main: `00cb7fc299fb868d1e0249cab08ac20ed1b5c257` (production code in the baseline APK is `feddd89f6c50fa787ac4fbe6e11fa15a347c8411`). Candidate: one default graphicsLayer on the animated LamiSprite3x3 Canvas, with default compositing and no added clipping. No new image buffers, frame caches, clocks or rendering backend are explicitly introduced.

[Android graphics modifier documentation](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) describes graphicsLayer as isolating drawing instructions. That is the hypothesis for reducing parent/window drawing overhead; it is not by itself proof of device performance or zero memory cost.

The comparison retains the previous baseline Release APK and uses a candidate Release built with the same missing-QAIRT-JNI smoke-build allowance and debug signing key. This is a UI performance check, not NPU validation.

Protocol: NX733J, no inference, the same temporary [0,1,2,3] sequences at 200 ms and retained insertion settings, order old/new/new/old/old/new. Each condition settles for 20 seconds, then has a 30-second unprofiled whole-app CPU sample followed by a separate 45-second Perfetto trace. Track foreground/PID, thermal boundaries, memory, captured sprite hashes and candidate background/resume. Restore original preferences and screen timeout, then return to ChatGPT.

Acceptance requires repeatable CPU benefit with preserved captured images/resume, and no concerning presentation or memory regression. No CPU-frequency, priority or display-refresh setting changes are part of this experiment. Report all runs rather than selecting a favorable one.

## Decision and validation

Retain the change as a small reduction in UI draw-recording work. This is not a claim of a major whole-app CPU improvement, a faster GPU renderer, or minimum possible resource usage. The code change is one graphicsLayer modifier plus its import/comment; clocks, drawing frequency, frame maps and geometry remain unchanged.

Local validation passed in 4m10s: 1,753 Debug and 1,225 Release tests, zero failures/errors/skips, both lint tasks and both APK builds. The measured candidate code is `e48aba64d9b68b86d462484ba01f052edd92544c`. Subsequent report commits do not change APK code.

## Unprofiled CPU and memory

CPU percentages mean the whole app relative to one fully occupied core. Each sample is about 30 seconds after 20 seconds settling. PSS is one post-CPU-sample observation per run; it does not establish a leak rate or GPU memory utilization.

| Run | CPU | PSS |
|---|---:|---:|

| 1_old | 4.999% | 179.67 MiB |
| 2_new | 4.837% | 177.29 MiB |
| 3_new | 4.951% | 177.99 MiB |
| 4_old | 4.923% | 180.01 MiB |
| 5_old | 5.148% | 173.57 MiB |
| 6_new | 4.693% | 170.16 MiB |

Time-weighted CPU is 5.023% baseline versus 4.827% candidate, an observed 3.9% reduction. This small whole-app difference has overlapping per-run ranges; one reversed-order pair slightly favors baseline. Do not treat three samples as statistical proof of a general whole-app speedup. PSS also varies between runs; no increased PSS was observed for the candidate within matched pairs, but a memory-saving claim is not established.

## Draw-recording CPU attribution

Intersect each complete main-thread Record View#draw() interval with thread_state, clipping at both ends, to separate Running from runnable/preempted time. Select the active process/thread by recorded frames; traces may contain stale same-name records.

| Run | Recording count | CPU per recording |
|---|---:|---:|
| 1_old | 231 | 0.7044 ms |
| 2_new | 231 | 0.5646 ms |
| 3_new | 227 | 0.5564 ms |
| 4_old | 229 | 0.6439 ms |
| 5_old | 231 | 0.6947 ms |
| 6_new | 228 | 0.5326 ms |

Pooled draw-recording CPU per event fell 0.6811 → 0.5512 ms (19.1%). All candidate runs had lower recording CPU per event than all baseline runs. This is the clearest evidence supporting the change; it is a scoped improvement, not a 19% whole-app CPU reduction.

RenderThread scheduled CPU did not improve: 3172.825 → 3206.603 ms over three 45-second traces. The remaining backend drawing/submission cost is still present. Trace-window CPU must not be mixed with the unprofiled CPU samples above. Nested slice durations must not be added together.

## Presentation timing

| Metric | Baseline | Candidate |
|---|---:|---:|
| Late Present | 91/688 (13.23%) | 91/684 (13.30%) |
| App Deadline Missed, including combined | 34 | 35 |
| App completion, median | 13.986 ms | 13.871 ms |
| App completion, p95 | 16.360 ms | 16.067 ms |
| App start to display, median | 31.390 ms | 31.371 ms |
| App start to display, p95 | 47.941 ms | 47.985 ms |

Presentation latency is effectively unchanged in these observations. The late-present count is 91 in both versions, with slightly different frame totals; there is no demonstrated smoothness improvement or statistical equivalence claim. App-start-to-display joins use the corresponding SurfaceFlinger display token and complete slices; this is not touch-to-photon latency. No trace errors were reported. Rendering remained approximately five updates per second.

## Visual, lifecycle and restoration checks

24 captures each yielded exactly the same five distinct sprite-image hashes in the first baseline and candidate runs. After the candidate spent 60 seconds in the background, playback resumed with four distinct images, all within the baseline set. The captured image check is not exhaustive coverage of every crop/editor configuration or a many-preview screen.

Candidate background CPU was 0.427% over 60.8 seconds. This is whole-app activity, not evidence of zero animation-related work.

Measurement ran from 2026-09-12T05:57:53.463350+00:00 to 2026-09-12T06:09:46.435821+00:00 (about 11m53s). The original preference files were restored and verified byte-for-byte, and the original screen timeout of 600000 ms was restored. After assessing the results, the exact measured candidate Debug APK was installed; preferences were verified again and ChatGPT was confirmed in the foreground.

## Limits and next target

The existing parent clip already supplies a drawing boundary, so the additional Canvas layer does not eliminate all window/RenderThread work. Its measured value is the smaller draw-recording scope. The graphicsLayer also applies to the integer-frame API; this experiment focuses on the animated chat scene, not every static preview arrangement. Default compositing does not imply zero allocation or zero GPU memory cost under all future modifiers.

Keep further changes separate and measured. RenderThread submission/presentation and event-to-vsync timing remain possible next targets; this experiment provides no justification for a custom GPU renderer, forced offscreen compositing, altered refresh policy or thread-priority overrides.

The evidence JSON contains app-scoped samples, per-run timing/CPU attribution, APK identities and restoration status. Raw whole-system traces, screenshots and private preference files are not committed.
