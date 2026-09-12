# Post-response token recount ownership

## Problem and separation

Normal local completion and NPU fallback previously embedded recount scheduling, statistics construction and guarded persistence in ChatScreen. The process-wide semaphore/mutex lived inside LocalStreamingRunner, making admission and cancellation difficult to test without Android/native counting.

PostResponseTokenStatsUpdater now owns both scheduling paths. It receives the existing UI CoroutineScope, recount operation, metadata updater and accepted-result callback. ChatScreen requests an update and supplies dependencies. The updater creates no independent scope or generation engine. Its callback uses the caller scope; persistence runs on the injected IO dispatcher.

TokenRecountExecutionCoordinator owns IO dispatch, two admission slots and serialized counting. One process-wide instance is used by the existing recount entry point, so different screen instances and normal/fallback routes still share one active count and one waiter. Overflow returns the original trace. Cancellation or failure releases admission in finally; the count operation remains responsible for native cleanup. Blocking native calls are not made forcibly interruptible by this refactor.

## Preserved behavior

- Same effective prompt and original output, count-result cache and MediaPipe counter.
- Existing complete measured counts skip normal-route recount.
- Response completion and TTS request still precede normal-route recount scheduling.
- UI-scope lifetime is unchanged: leaving composition cancels its jobs; changing chat does not introduce a new cancellation policy.
- Normal and fallback paths retain their different statistics/provenance builders. Fallback counts the answer but validates the full persisted response, including any notice.
- Delayed updates pass message ID, chat ID, exact body and terminal-state checks through the existing metadata updater. No direct database writes were added.
- Queue overflow, failed counts or owner cancellation may leave estimated statistics; no retry or UI policy change.
- No native runtime, model, animation or TTS changes. No counting engine retained.

## Verification

Ten new behavior tests run in Debug and Release: admission/serialization/overflow; cancelled waiter; active cancellation cleanup; exception propagation and later reuse; owner cancellation; successful metadata publication; deleted/edited rows; updater cancellation; fallback full-body validation/provenance; already measured counts. Tests use deterministic coroutine scheduling and a fake metadata store, not Android or model execution.

StandardDebug 1,809 + StandardRelease 1,272 JVM tests passed, failures/errors/skips zero (3,081 total). The architecture test now checks the extracted update location. Both StandardDebug and StandardRelease lint passed. Clean-checkout validation used `-Plami.allowMissingQairt244Jni=true`; it does not qualify native-device packaging.

The first test run found two test assumptions: the source assertion still expected the moved patch call in ChatScreen, and exception identity ignored coroutine stack-trace recovery. The assertions now check the actual owner and propagated exception type/message; cancellation/cleanup assertions remain.

No new device test or APK installation was performed. Existing device performance numbers describe the preceding implementation; this refactor makes no new latency, memory or power claim. A subsequent device qualification can cover long/short/stop/resend before promoting a newly built APK.
