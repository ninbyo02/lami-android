# Sprite rendering load attribution

## Findings

The largest CPU consumer in the Release changing-frame capture is the main UI thread, not RenderThread. Across 25 seconds it used 1,205.951 ms (48.36% of app scheduled CPU); RenderThread used 537.765 ms (21.56%); JIT compilation used 230.238 ms (9.23%). GPU device execution is not included in these CPU shares.

Release `Recomposer:recompose` occurred 130 times, averaging 3.740 ms and peaking at 21.143 ms. UI draw recording averaged 0.752 ms; render command flush averaged 1.651 ms. These nested wall-time slices must not be added together as independent CPU totals. The corresponding Debug recomposition average was 8.827 ms (maximum 34.172 ms).

Separate 2ms ART stack sampling on Debug identified `LamiStatusSprite` as the largest application composable subtree (320.946 ms inclusive), including `LamiSprite3x3` (116.078 ms). The actual `drawFrameRegion` subtree was 19.963 ms. Inclusive samples overlap, and sampling changes execution behavior: use them to locate code, not predict Release speedups. The parser observed no stack mismatches.

## Concrete source paths

1. `LamiStatusSprite.kt`: each event writes `syncFrameIndex`, which is then read in composition as `resolvedFrameIndex`. A visible frame change therefore recomposes the status sprite and calls `LamiSprite3x3` again, doing Compose slot/snapshot/change bookkeeping and configuration work before drawing. The clock now skips unchanged images, but changing images still take this whole route.
2. The same composable directly calls `settingsPreferences.selectedKeyFlow(SpriteState.ERROR).collectAsState(...)`. `selectedKeyFlow` creates a fresh mapped Flow each call. Recomposition therefore changes the collector's Flow key and restarts that subscription. The sampled stack contains this collection path. Other configuration flows declared as stable `val`s and the remembered per-state animation flow are different. This is a confirmed avoidable restart, but its isolated percentage requires an A/B measurement.
3. `LamiSprite.kt` reconstructs per-frame source/destination geometry and the Canvas drawing closure through composition. Sprite sheet decoding is already cached and no decode loop was identified. Changing only the displayed frame should be possible in the draw phase with stable configuration/region tables, preserving scale, offsets, crop and theme behavior.

## Display latency interpretation

For the same Release capture, gfxinfo reports 83 janky frames out of 131 (63.36%), while SurfaceFlinger FrameTimeline records 7 late presentations out of 127 app-layer frames (5.51%). There are 3 App Deadline Missed-only, 2 Display HAL-only and 2 combined events; 120 were on time. These are distinct indicators and buffers, not interchangeable dropped-frame percentages. In particular, the previous 55.76% gfxinfo value should not be read as that percentage of visibly dropped sprite frames.

Release frame phase medians: UI animation/recomposition 3.631 ms; traversal/draw recording 0.857 ms; render dispatch wait 0.185 ms; render submission 3.288 ms; post-submission GPU-completion wait 8.816 ms; end-to-end completion 17.859 ms. GPU-completion wait includes queued work/fence synchronization and is not measured GPU busy time. This trace contains no GPU render-stage slices, so GPU saturation or exact shader cost cannot be established. It would be inaccurate to claim the GPU has zero cost or is definitively the bottleneck.

## Recommended next change

First remember the error-selection Flow so frame updates cannot resubscribe it. Then keep animation state reads inside the Canvas draw phase, separating stable settings/geometry from the changing frame index. Preserve the shared event clock, lifecycle pause/resume, insertion decisions, crop/offset maps and debug overlay behavior. Verify with the same Release scenario: recomposition should stop following every frame while visible frames and timing remain unchanged. Do not replace the renderer with custom OpenGL/Vulkan based on this evidence.

This turn diagnoses the load; it does not implement those changes or claim their speedup. Existing randomized playback and long-background insertion catch-up are separate issues.

## Capture and restoration

Source commit `1acd9fb`, NX733J, no inference, original Debug wait plus Debug/Release changing-frame scenarios. Each Perfetto capture lasts 25 seconds after 20 seconds settling. ART sampling was a separate 10-second Debug run. Parser error counters were empty. The initial configuration file location was rejected by Perfetto; the successful run used its documented `/data/misc/perfetto-configs` location. No device security controls were changed.

Both preference files were restored byte-for-byte, the prior screen timeout restored, the new standardDebug APK restored, and ChatGPT brought to the foreground. Smoke builds are not NPU readiness evidence. Raw system/method traces stay in the local analysis directory; the committed [aggregate evidence](2026-09-12-sprite-render-load.json) contains only the app-specific results.
