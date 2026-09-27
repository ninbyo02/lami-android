# GPU stop propagation and held-engine reuse

## Findings

The previous normal-package trace showed reuse-hit followed by Conversation creation failure after stop, then a legacy GPU engine initialize from 00:07:09.113 to 00:07:15.294 (6.181 seconds). The previous conversation close did not finish until 00:07:18.485. Two lifecycle problems were found: the experimental timeout wrapper launched an independent worker and did not cancel it when the caller stopped; the held conversation cleanup did not request native cancelProcess before close on cancellation.

## Changes

The operation helper now cancels its worker scope on every exit, including caller cancellation and timeout. It preserves the non-blocking watchdog return instead of joining potentially stuck native work. The held conversation requests native cancelProcess before close when its coroutine is cancelled. Cancellation is rethrown; it is not classified as a successful result or retried through another backend.

Repeated GPU callback/UI-progress diagnostic snapshots are serialized at most once per stage per second. All actual UI updates and watchdog stage updates continue per event. First occurrence, first token, lifecycle, completion and error records remain available, with aggregate callback counts retained in final diagnostics.

## Normal-screen measurements

All runs used the normal StandardDebug package on NX733J with automatic TTS. Candidate binary source is e9f1640a; adjacent JSON includes the APK hash and verified installed hash. Native OpenCL/NPU inputs are unchanged from PR #2601.

| Operation | Prior baseline | Candidate first run | Candidate repeat |
|---|---|---|---|
| Greeting | 11 chars / 18.377 s | 11 chars / 9.301 s | 11 chars / 8.328 s |
| Long answer | 334 chars / 39.369 s | 835 chars / 21.536 s | 835 chars / 21.488 s |
| Short answer | 1 char / 3.830 s | 1 char / 4.064 s | 1 char / 3.455 s |
| Stop | CANCELLED | CANCELLED before text | CANCELLED after 67 chars |
| Resend | 6 chars / 10.128 s, legacy engine | 6 chars / 3.741 s, held reuse | 6 chars / 3.510 s, held reuse |

Values are stored generationTimeMs, not pure native decode timing. Output/history and run conditions differ from the prior baseline; do not treat the table as an isolated causal benchmark or claim short-answer improvement. Both candidate runs explicitly reported held-engine reuse and no fallback for resend, and captured native cancelProcess. The repeat passed the full corrected harness.

Playback was active before the playback-stop test. Both playback and generation stops had no active audio at 2 and 8 seconds and no later speech requests/accepts/starts in that observation window. The NPU suite on the same APK also passed greeting, long, short, cancellation and resend; detailed timings are in JSON. Backend suites restarted the process, so live GPU/NPU switching is not qualified. These tests wait through the 8-second stop observation before resending; immediate same-frame resend is not claimed.

The first harness mistakenly required request-start lines from the rotated final trace and rolled the APK back. Its original failure is preserved in JSON. Response-specific stored statistics and the separately captured cancellation trace prove the actual route; the corrected repeat and NPU suite passed. Candidate APK was reinstalled and left on the device. Preferences, keyboard and timeout were restored, and ChatGPT returned to foreground.

## Verification and limits

All 1,793 Debug and 1,256 Release JVM tests passed; strict StandardDebug native-input/final-APK validation passed. New tests cover caller-stop propagation, timeout cleanup, successful null results, progress sampling and retained critical events. PR #2601 CI fix passed all 3,039 tests and GitHub checks before merge at 3ce11928.

This improves generation overhead and post-stop reuse; it does not establish minimum CPU/GPU/power consumption, Release-device parity, model reasoning quality, exact requested-length compliance or GPU/NPU live switching. Short-answer latency remains a next target. Avoid additional native/runtime swaps until separately qualified.
