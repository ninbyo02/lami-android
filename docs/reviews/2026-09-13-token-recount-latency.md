# Post-response token recount review

## Proposed final behavior

Production code: `7b0e5a7daf79ed0b59dbc6184f1ed71ce874758e` (PR #2603).

The normal chat route finalizes its response and requests speech before starting the separate MediaPipe recount. A later metadata-only update checks message identity, chat, expected response and terminal state through `PostTerminalAssistantMetadataUpdater`. Completion timestamps, text, status and route provenance remain unchanged. Existing complete measured counts are retained. Estimates remain estimates until an exact recount succeeds.

One recount may execute and one may wait. Further requests keep estimated statistics. Native counting engines and sessions are closed after each use; no second engine is retained. Up to 16 successful count outcomes are cached with model path/size/mtime and SHA-256 prompt/response digests. No prompt/response text or native object is cached. Model identity changes or different text miss the cache. Original decode duration is retained when later statistics are rebuilt.

The previously unused createSession diagnostic attempt was removed. It could fail against the conversation-owned generation engine without ever contributing counts.

## Rejected resident-resource experiment

The first candidate reused one MediaPipe inference/session until 60 seconds idle. Recount latency improved from 1,135–1,608 ms to 5–6 ms for warm long/short turns, with identical counts (long: input 16/output 455; short: 11/1). However, native heap allocations grew by approximately 0.8 GB. That tradeoff was rejected. The final commit removes the resident-resource cache and lifecycle hooks entirely.

Both the resident experiment and its NPU regression sequence passed long/short/stop/resend and TTS stop checks. These results **do not qualify the final deferred implementation**. The original rows were checked for preservation; see the JSON evidence.

## Final validation and blocker

- StandardDebug: 1,797 JVM tests passed; StandardRelease: 1,260 passed; no failures/errors/skips.
- Both lint variants passed.
- Pinned 14-library combined GPU/NPU inputs and final StandardDebug APK verified by the build.
- The source contract was updated from three to four guarded metadata-update call sites. Identity/lifecycle protections remain required.
- Final APK prepared, but installation stopped before mutation because wireless ADB disappeared. Both advertised ports refused connection. No final deferred-variant device measurements exist yet.
- Phone settings, input method and screen timeout were restored after the preceding resident/NPU experiments; ChatGPT was brought to the foreground. The phone still has the resident experiment APK, not the final deferred APK.
- Do not merge until final device qualification succeeds. GitHub CI status is recorded on the PR.

## Resume checks

After reconnecting the same NX733J: install the verified final APK; confirm response completion precedes recount completion; compare exact counts with the baseline; confirm repeated identical text uses the small result cache; measure native allocations after counting; verify GPU reuse and long/short/stop/resend with TTS; run NPU regression; verify prior rows and restore settings/ChatGPT. The prepared deferred device harness uses the original transport and must be updated if the advertised port changes.

No claim is made about CPU/GPU utilization, power, thermal behavior, Release-device parity or immediate same-frame resend. The deferred strategy removes counting from response completion but still incurs first-time counting work asynchronously.
