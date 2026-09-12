# CPU investigation and backlog cleanup — 2026-09-12

Reviewed main: `074db1037799811e0e3d70316d43ef9d27ea2f61`. Device: NX733J; installed revision `a1b71691` (Standard Debug), last updated 2026-09-12 05:45:44 JST. The investigation did not replace the APK or change inference/model settings.

## CPU findings

At 2026-09-12 09:04:25 JST, Android terminated PID 6761 with `EXCESSIVE RESOURCE USAGE / EXCESSIVE CPU USAGE`, importance 400. The recorded reason was `excessive cpu 20590 during 300067 dur=4554826 limit=2`. This corresponds to about 6.86% of one CPU over the recorded five-minute window. This is an OS resource-policy termination, not evidence of a Java/native crash. No stack trace was attached (`trace=null`).

Current PID 13718 was sampled through process utime+stime in `/proc/<pid>/stat`, using the device CLK_TCK and monotonic elapsed time. Percentages are relative to one CPU, not normalized across all cores. No inference prompt was sent.

| Condition | Elapsed seconds | CPU seconds | CPU % |
|---|---:|---:|---:|
| existing_background | 20.096 | 0.13 | 0.65 |
| foreground_idle | 20.143 | 12.28 | 60.96 |
| background_after_foreground | 30.139 | 0.23 | 0.76 |

The foreground measurement starts immediately after bringing LAMI to the foreground, so it includes resume work and ongoing UI updates. It is not a long-run steady-state benchmark. The background samples are only 20/30 seconds; they do not reproduce or rule out the earlier five-minute failure. The device was returned to ChatGPT after the foreground sample.

### Code evidence and limits

- `LamiStatusSprite.kt` updates `syncTimeMs` on every frame in sync mode. The downstream frame/timeline construction and diagnostics are candidates for the reproduced foreground cost; attribution requires profiling or an animation-on/off comparison.
- The non-sync animation loop uses delay-based playback without an explicit STARTED/STOPPED lifecycle boundary. Review its behavior when the composition remains present but the Activity is stopped.
- The non-sync insertion path contains `selectWeightedInsertionPattern(...) ?: continue` and allows a zero insertion delay. These deserve a cancellation/suspension audit for malformed or degenerate settings. They were not demonstrated to have caused this device termination.
- `HeldEngineLifecycleBridge` already notifies the inference holders on Activity start/stop. Do not assume all background work is an unhandled inference lifecycle.
- Native simpleperf profiling was unavailable under the device perf-hardening policy. No system security property was changed.
- Conclusion: foreground/resume UI cost is reproducible; the exact cause of the historical background CPU termination remains unconfirmed. No production fix is claimed by this report.

### Next investigation

1. Compare the same screen with character animation enabled/disabled, restoring the original preference after testing; collect per-thread CPU and separate resume cost from steady-state rendering.
2. Run a full five-minute background observation after model/control/settings transitions, correlating Activity state, process CPU, animation activity and request state.
3. If lifecycle leakage is reproduced, fix only the responsible loop and verify cancellation, foreground resume and inference/TTS behavior. Keep this distinct from Risk 1 parameter bundling.

## Pull-request cleanup

18 old PRs were closed as already implemented or superseded. Seven remain Draft because adoption or completion could not be established. No old PR was merged, no branch was deleted, and all original PR descriptions were retained with a dated audit appended. Comparison included package-path migration and current call sites; added-line matching was only a locator, not proof of semantic equivalence.

| PR | Disposition | Evidence / next action |
|---|---|---|
| [#680](https://github.com/ninbyo02/lami-android/pull/680) | Retained Draft | Tracking request for quarantined tests is not proven complete. The diff also includes obsolete header layout changes; retain as Draft and split/reassess before implementation. |
| [#1050](https://github.com/ninbyo02/lami-android/pull/1050) | Closed | These six PRs have identical diffs. PixelArtStable defaults and CenterSample are already present; the saved-operation field moved from index 3 to 4 in current SpriteEditorScreen. |
| [#1051](https://github.com/ninbyo02/lami-android/pull/1051) | Closed | These six PRs have identical diffs. PixelArtStable defaults and CenterSample are already present; the saved-operation field moved from index 3 to 4 in current SpriteEditorScreen. |
| [#1052](https://github.com/ninbyo02/lami-android/pull/1052) | Closed | These six PRs have identical diffs. PixelArtStable defaults and CenterSample are already present; the saved-operation field moved from index 3 to 4 in current SpriteEditorScreen. |
| [#1053](https://github.com/ninbyo02/lami-android/pull/1053) | Closed | These six PRs have identical diffs. PixelArtStable defaults and CenterSample are already present; the saved-operation field moved from index 3 to 4 in current SpriteEditorScreen. |
| [#1054](https://github.com/ninbyo02/lami-android/pull/1054) | Closed | These six PRs have identical diffs. PixelArtStable defaults and CenterSample are already present; the saved-operation field moved from index 3 to 4 in current SpriteEditorScreen. |
| [#1055](https://github.com/ninbyo02/lami-android/pull/1055) | Closed | These six PRs have identical diffs. PixelArtStable defaults and CenterSample are already present; the saved-operation field moved from index 3 to 4 in current SpriteEditorScreen. |
| [#1495](https://github.com/ninbyo02/lami-android/pull/1495) | Closed | Fullscreen image navigation now implements press/release repeat through movePageBy and a unified overlay modifier in ChatBubble.kt; this older gesture implementation is superseded. |
| [#1510](https://github.com/ninbyo02/lami-android/pull/1510) | Retained Draft | Current attachment remove button is a 28dp clipped Box with 1dp padding; the old 36dp IconButton offset is not equivalent. Retain for visual requirement review, not direct merge. |
| [#1628](https://github.com/ninbyo02/lami-android/pull/1628) | Retained Draft | Current server rows reserve ServerRowTrailingSlotWidth and use zero end padding on the button. The proposed 8dp adjustment is not confirmed equivalent; retain for visual review. |
| [#1679](https://github.com/ninbyo02/lami-android/pull/1679) | Retained Draft | Current DrawerSearchPill uses end=0dp, while this PR requests 8dp. Preserve the unadopted design choice for review. |
| [#1778](https://github.com/ninbyo02/lami-android/pull/1778) | Retained Draft | Current code block renderer has evolved. Exact accent and tint changes are not all present; preserve for visual design review rather than applying the old renderer diff. |
| [#1865](https://github.com/ninbyo02/lami-android/pull/1865) | Closed | LamiAvatar already uses selectModelAndKeepSheetOpen, haptic feedback, parent selection handling and a non-clicking radio button. Model selection was device-verified after #2586. |
| [#1929](https://github.com/ninbyo02/lami-android/pull/1929) | Closed | The generic character-animation description is already present in current Settings.kt. |
| [#1930](https://github.com/ninbyo02/lami-android/pull/1930) | Closed | Exact duplicate of #1929; the generic description is already present in current Settings.kt. |
| [#1939](https://github.com/ninbyo02/lami-android/pull/1939) | Closed | The latency indicator now uses a -5dp offset, superseding this earlier -4dp intermediate adjustment. |
| [#1947](https://github.com/ninbyo02/lami-android/pull/1947) | Closed | ConnectionSummaryRowStartPadding=20dp, label width=72dp and label/value spacing=12dp already align the status and information rows. |
| [#1992](https://github.com/ninbyo02/lami-android/pull/1992) | Closed | Current LocalBaseModelScreen already includes the .litertlm display-name validation and early rejection. |
| [#2076](https://github.com/ninbyo02/lami-android/pull/2076) | Closed | Message-local source summary is already persisted, restored and displayed; MIGRATION_10_11 exists and the current schema is version 14. Do not apply the obsolete version-11 schema declaration. |
| [#2079](https://github.com/ninbyo02/lami-android/pull/2079) | Closed | Probe state labels now live in LocalInferenceStatsSectionBuilder, with additional blocking-response provenance handling. The earlier ChatScreen implementation is superseded. |
| [#2260](https://github.com/ninbyo02/lami-android/pull/2260) | Closed | Current code already removes the redundant token-null checks and unnecessary nullable receivers, including evalTimeProbe.durationNsOrNull(). |
| [#2267](https://github.com/ninbyo02/lami-android/pull/2267) | Retained Draft | The proposed UI-applied assistant update counts and visible timestamps are absent from current main. This is distinct from backend token speed; retain and rebase to current streaming ownership before adoption. |
| [#2283](https://github.com/ninbyo02/lami-android/pull/2283) | Closed | Current LocalStreamingRunner already defines and consumes buildCreateSessionMethodSignatures, including parameter/return type details. |
| [#2446](https://github.com/ninbyo02/lami-android/pull/2446) | Retained Draft | Current repair logic changed to a representative event block with fixed 8/12-space indentation. The parent-relative improvement and new regression cases are not equivalent; retain for a fresh focused fix and tests. |
| [#2519](https://github.com/ninbyo02/lami-android/pull/2519) | Closed | Host validation, --host update support, limited controller template, documentation and smoke checks are already present and extended on main. This closure does not claim deployment of the PC controller. |

## Uncommitted-work preservation

Original directory: `/home/sato/project/lami-android`. Original branch: `codex/edge-gallery-streaming-stability`; original HEAD: `effb90f99905d0fca84fa83123f3abfc32d63010`.

All ten dirty tracked files were byte-for-byte identical to the already-pushed archive `archive/streaming-wip-20260911` at `4c32c9c7dc08833a4d0998aa3ced3403b183a6fd`. The complete working tree also had no diff against that archived tree. The remote branch SHA was checked before restoring anything. There were no staged changes or untracked files.

Only after these checks, the ten archived modifications were restored to the original HEAD and the directory was switched to `work/main-20260912-clean`, based on `origin/main` at `074db103`. Its tracked working tree is clean. No stash or source branch was deleted.

Archive: https://github.com/ninbyo02/lami-android/tree/archive/streaming-wip-20260911

| File | Relationship to reviewed main | Archive SHA-256 |
|---|---|---|
| `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt` | Archived mixed WIP; review and split before reusing | `09b87554b1c013d317f83fba3001d88570adbb2c3439662eb88b96c5191e5b98` |
| `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailability.kt` | Archived mixed WIP; review and split before reusing | `7106ad3ffb09b1393b7880d74971e38b1ba611533d33bafc94ca197e12e2b3fc` |
| `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt` | Archived mixed WIP; review and split before reusing | `e8bc685b9b2dac4b105678c7e434e76da1b1acde03d56d3bf3fefe9ee48795e2` |
| `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuKotlinConversationProductRoute.kt` | Archived mixed WIP; review and split before reusing | `9c83b2e6fb2872bc97765401c385f98159b6e6c1baa9e301d25962d0627e6787` |
| `app/src/main/java/io/github/ninbyo02/lami/ui/text/PythonCodeSyntaxInspector.kt` | Byte-identical; already integrated | `ce43b514bce68dba70b70d25ad500da2bf13b8454ebcae2d301e64c4b5e10d4b` |
| `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/ChatSendAvailabilityTest.kt` | Archived mixed WIP; review and split before reusing | `da5a303c73ea7dc1dd81492cd753435249566e6ca6e0c994634ccd0628291171` |
| `app/src/test/java/io/github/ninbyo02/lami/ui/screens/home/GpuBlockingRaceContractTest.kt` | Archived mixed WIP; review and split before reusing | `7de6113f77bd3ea1095299ee07bb8a2f15b320e99ae3d35aa8f837e0b2a22cc4` |
| `app/src/test/java/io/github/ninbyo02/lami/ui/text/PythonCodeSyntaxInspectorTest.kt` | Byte-identical; already integrated | `cb942f91b4c2439ee8d77cfa5b7218dcf4b8962eecdb0db496b92a16ff5b7952` |
| `app/src/testStandardDebug/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunnerChunkAppendTest.kt` | Archived mixed WIP; review and split before reusing | `9a2dcfa52dcae6c79829aa1e415df70354a8436a1d8b60bb356c74287b8bff16` |
| `app/src/testStandardDebug/java/io/github/ninbyo02/lami/ui/screens/home/NpuKotlinConversationProductRouteSourceContractTest.kt` | Archived mixed WIP; review and split before reusing | `1ad2312767bbe96a6398a9c987f0185d6b49e3733ae74e4e8f83a8e20979825c` |

The two PythonCodeSyntaxInspector files already match main exactly. The other eight files are not whole-file matches: some changes overlap later work, while the archive still mixes a conflated local UI update channel, transient message IDs, delta/whitespace handling, forced compatibility-mode selection, TTS code-fence behavior and NPU provisional quality-gate behavior. Whole-file reapplication would overwrite subsequent main changes and is not appropriate. Future adoption must extract one behavior with current tests and device evidence.

## Validation scope

- Read back all 25 PR states/draft flags and verify the intended dispositions.
- Verify the original directory is clean, on the new main-based work branch, and the archive is still present remotely.
- This is an investigation/documentation change; it changes no application code. The 1,717 passing tests and successful lint previously reported belong to PR #2587, not to a new CPU fix.
