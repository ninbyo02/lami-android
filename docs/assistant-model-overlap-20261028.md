# Assistant model preparation overlapping startup

## Scope

PC-only candidate extending the installed assistant-ready v3 source. The installed
APK remains SHA-256 3ade2e2e4ee7189a4122b6b632d496a71a1de715e0bd5126f79473f2a36d4bd6.
No phone connection, install, recording, vibration or playback is part of this work.
No push/merge, model replacement, new permission, preference or service is added.

The startup splash implementation, timings, text and animations are unchanged.
Ordinary icon launches do not start model preparation. After an explicit assist
invocation with microphone permission, native model preparation starts before the
existing lifecycle/chat/splash wait. The UI wait and model work can overlap.
The existing greeting remains and must finish before command capture.

## Ownership And Safety

AssistantPreparedModelOwner verifies all four model files on each new preparation
request; simultaneously waiting invocations share one pending preparation. A warm
request still performs full verification, but reuses the verified native engine
when the model content fingerprint is unchanged. Verification is not replaced by
a size/mtime check. Changed fingerprints retire the previous engine.

The initializer constructs the native recognizer and opens/closes one empty ASR
session to initialize its buffers. It does not decode, create AudioRecord, change
audio routing/focus or record. Actual capture uses a fresh exclusive session lease.
Session close clears its PCM buffer and returns that lease, not the shared native
weights. Only one capture lease can be active; existing serial job cleanup remains.

Cancelling a request cancels its wait, not shared initialization. Stale requests
cannot acquire invalid resources or publish readiness. Memory-pressure callbacks
invalidate cached weights; active leases defer native disposal until capture ends.
An invalidated in-flight initializer must finish before a replacement initializer
starts. Replacement allocation waits for any retired capture lease and its native
disposal, rather than retaining two loaded model generations. Initialization/verification failure is retried only
on a later explicit preparation request, never in an automatic loop. An old
session failure cannot invalidate a newer cached model.

Activity destruction closes the model owner and invalidates tickets. An initializer
already inside uncancellable native work is allowed to finish and then disposes its
late result without recording. The capture job owns coordinator cleanup; onDestroy
does not close a coordinator underneath an active native operation. A recreated
Activity gets a new owner, so this is not a persistent service/process-wide cache.
Android may kill the process or omit particular trim callbacks; warm residency and
exact memory limits are not guaranteed. Cached weights consume RAM while retained.

Input still waits for the resumed/focused UI, prior manual capture and TTS cleanup,
model preparation, optional Bluetooth route and unchanged greeting. Readiness
still requires actual AudioRecord start, an OS active/non-silenced input state and
a successfully read frame. Only then may the existing once-only CLOCK_TICK fire.
No auto-send preference or ringer/privacy/assistant setting is changed.

## Measurement

Existing bounded debug trace storage is reused (1 MiB active plus one rotated file);
no raw audio, transcript or new voice/research diagnostic record is added.
LAMI_ASR_TIMING events bind a request ID to elapsedRealtime stages: invocation,
capture_owner_acquired, chat_and_splash_ready, previous_audio_cleanup_complete,
model_await_complete, cue_begin/complete, capture_started, input_active,
first_frame and ready_haptic_requested. Model verification/native initialization
also log elapsedRealtime plus Java-used/native-heap bytes. Heap figures are not
peak RSS or total application PSS. Shared initialization may serve several request
IDs; its phase timestamps must not be counted once per waiting request.
The cue_begin/complete pair brackets the existing before-capture hook; the existing
cue_started/finished or cue_suppressed trace distinguishes an actual greeting from
privacy-policy suppression. A hook event alone does not prove audible playback.

Future approved phone validation should compare cold Activity/process and repeated
invocations with the same models/settings and mode. Record splash end, native-ready,
greeting end and actual input-ready separately, plus a coordinated read-only
dumpsys meminfo snapshot if approved. Do not infer total latency by summing phases
that overlap. No seconds saved, real-device RAM or battery improvement is measured
by the PC tests. CPU contention can reduce or eliminate the overlap benefit.

## Validation Boundary

Pure Kotlin tests use fake model loaders/resources and controlled schedulers.
They cover shared initialization, cancelled waiters, warm verification, exclusive
leases, failures/retry, changed models, memory pressure, late completion after
destruction, delayed disposal and fresh owners after Activity recreation.
Contract tests check the actual source wiring and model/UI/cleanup/greeting gates.
Existing Entry Activity tests use Robolectric. These are not native ASR, OEM
lifecycle, physical-button, microphone/haptic or real memory-pressure tests.

The signed delivery receipt records final test counts, APK/source hashes and frozen
runtime/model/saved-audio compatibility checks. Feature sources are uncommitted;
the source base commit is not a feature commit. All older APKs remain preserved.

Final delivery: ../assistant-model-overlap-delivery-v2-20261028/voiceintegrationtrial.apk,
404,585,274 bytes, SHA-256
909b0223775029590be9e34b66f0221cf56912a8ff5132a86b05bb305052d5a6.
Package io.github.ninbyo02.lami.voiceintegrationtrial, versionCode 1; signer SHA-256
4a0871be6ed685755478a8d447a3a1c9b2c4743deb5dc65a4c5e016ab686b357.
Selected Gradle regressions: 232 passed, zero failure/error/skip. New cases comprise
17 prepared-model ownership tests and 5 overlap/wiring contracts; prior 210 remain.
Separate maintained host speech suite: 122 passed. These counts are separate suites,
not a claim of 354 unique cases or native model inference coverage.
Manifest, permissions, all 51 native libraries and the saved-speech identity asset
are byte-identical to the installed-v3 reference. Frozen runtime files: 10 unchanged.
Model manifest and TTS generation identity are unchanged; no settings/data migration.
The earlier assistant-model-overlap-delivery-20261028 APK is superseded, not deployed.

readiness.json binds the exact 18 cumulative changed/new app sources and the patch.
The cumulative patch includes the already-installed readiness feature and applies
cleanly to base be5cba2de (git apply --check only, no application). Do not blindly
apply it over a newer integration branch. The stable be5cba2de and separate f3059b5da
integration worktrees remained clean and unchanged. No feature commit/push/merge.

## Remaining Coordinated Device Validation

Not authorized/performed by this PC-only task. After an explicit update/test
approval and outside weekday 08:00-19:30 JST: verify endpoint, <=35 C, battery>=30%,
no conflicting use/audio, same app/signature, safe foreground, adequate space and
unchanged saved model/settings/audio identities. Install only a data-preserving
same-package update; keep the current v3 APK as rollback material. No uninstall,
permission grant, OS mode/privacy/default-assistant change or diagnostic-cap change.

Suggested finite acceptance: three user-coordinated physical invocations (cold,
repeat after manual stop, repeat while a prior capture is active), each <=10 s,
at most three readiness haptics and three existing greeting calls. Verify auto-send
OFF before any spoken input; do not silently change it. No chat submission,
answer synthesis, raw-audio file or research forward. Stop on failure; no automatic
repeat. Confirm splash unchanged, no capture before greeting/cleanup, one genuine
ready pulse, no overlapping recorders, and compare phase times without promising
instant input. Native cancellation/release and real RAM behavior remain unverified.
