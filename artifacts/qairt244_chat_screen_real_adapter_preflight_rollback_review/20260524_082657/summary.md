# ChatScreen NPU Real Adapter Preflight Rollback Review

- timestamp: 20260524_082657
- latest input commit: b44286877b0ac2a2b39061a644e4813216fc553f
- scope: docs and preflight grep only
- real NPU adapter connected: false
- NPU generation executed: false
- Engine.initialize executed: false
- RunDecode executed: false
- selectedPath=npu applied: false

## Confirmed Baseline

- `dev_enable_npu_chatscreen_route` default is false.
- Toggle ON has already been verified to trigger only the blocked transient branch.
- The blocked transient result is `adapter_not_connected`.
- Toggle OFF recovery was verified.
- DB/TTS/Markdown/streaming remain disconnected from the blocked branch.

## Rollback Conditions For Real Adapter Swap

Rollback and restore `dev_enable_npu_chatscreen_route=false` if any of these occur:

- fresh crash
- timeout
- duplicate success marker
- Engine.close or cleanup result is missing/unknown
- `selectedPath=npu` is saved or applied to normal route state
- DB/TTS/Markdown/streaming path receives NPU output
- `dev_enable_npu_chatscreen_route` does not return OFF after the run
- adapter result reports success but side-effect flags are not all false
- stale artifact or stale summary is used as execution evidence
- after-10s memory remains materially elevated versus the prior baseline
- UI freeze or button/running lock does not recover
- QNN/HTP/FastRPC evidence is missing from a claimed NPU success

## First Real Adapter Run Conditions

- customBuildExperimentDebug only
- Nubia Z70S Ultra / SM8750 device only
- `dev_enable_npu_chatscreen_route=true`
- prompt fixed to `Hello`
- `maxOutputTokens=3` fixed
- exactly one run
- timeout 30 seconds
- DB/TTS/Markdown/streaming disabled
- `selectedPath=npu` not saved
- result artifact required
- toggle OFF after success
- toggle OFF after failure

## Next-Phase Allowed Change Scope

Allowed:

- customBuildExperimentDebug adapter implementation
- `DevOnlyNpuChatScreenBlockedBranch` adapter selection/swap point
- one-shot runner/script/docs
- artifact capture around the first real adapter run

Not allowed:

- broad ChatScreen send-path rewrite
- DB/TTS/Markdown/streaming route changes
- standard/release changes
- `app/src/main/jniLibs` changes
- selected-path persistence changes

## Preflight

- `grep_safety.txt` records current references for the toggle, blocked branch, planner, selectedPath/NPU markers, and generation markers.
- This review did not start an Activity or run any inference.
