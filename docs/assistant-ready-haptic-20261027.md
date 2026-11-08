# Assistant input readiness and one short haptic

## Behavior

This isolated change extends the existing integrated default-assistant path.
Each explicit AssistantEntryActivity invocation enters voice input, including
MainActivity reuse and a repeated invocation while already listening. Icon
launch and manual composer microphone behavior remain separate. No auto-send
preference is changed; manual stop retains its existing no-auto-send semantics.

AssistantEntryActivity forwards onCreate and explicit onNewIntent. A restored
old Entry is finished without replaying its old invocation. MainActivity creates
a new request identity, invalidating the previous request immediately. A serial
job owner retains EVERY retiring session until cleanup ends, so three rapid
invocations cannot cancel an intermediate waiter and bypass the first recorder's
native cleanup. The recognizer's existing active-controller guard remains.

Before input route selection/model capture, the current chat registers a
preparation callback. It cancels/joins manual composer input and awaits the
neural TTS queue/response/replay cleanup. Missing/replaced chat or preparation
timeout aborts rather than starting conflicting input. Response and manual
playback requests are suppressed while assistant input is active. Existing
optional startup TTS cue still finishes/stops before command capture starts.
No voice, model precision, decoder or runtime arithmetic change is made.

## Genuine Readiness Boundary

There is no Android SpeechRecognizer.onReadyForSpeech in this local Sherpa path.
The request gate instead requires all three existing capture observations:

1. AudioCaptureEvent.Started after native AudioRecord.start returns.
2. InputResumed from the OS recording configuration marked active and not
   client-silenced.
3. A metered frame from a successful real PCM read after that active state.

Zero-level but valid microphone frames count: the user need not speak before
the ready signal. Start/model preparation alone does not qualify. Missing active
configuration remains conservative: no fabricated readiness pulse.

Capture observations invalidate readiness on the originating thread, before
posting UI work. Thus a queued ready callback cannot vibrate after capture has
already ended. Request IDs reject previous-session callbacks. Stop/inactive,
permission failure, cancellation, initialization failure and lifecycle stop
cannot resurrect readiness. Results still retain their request ownership until
the job finishes, so stopping input does not silently discard recognized text.

On the main thread, only the current live request with microphone permission,
resumed/focused Activity and active job may claim a pulse. Claim is once-only,
including duplicate callbacks and repeated focus changes. It calls exactly one
View.performHapticFeedback(CLOCK_TICK), with no ignore-setting flags and no new
VIBRATE permission. OS/view haptic policy may suppress it; no global settings,
ringer mode, media volume or privacy/default-assistant setting is modified.
The physical duration/intensity is the device's standard short tick, not a
forced millisecond waveform. No physical vibration has been tested here.

## PC Validation And Artifacts

Base: clean integration checkout be5cba2de, isolated worktree
`../lami-assistant-ready-20261027`, branch
`fix/assistant-ready-haptic-20261027`. The original checkout and the daemon's
integration worktree are not edited. Feature changes are uncommitted; the
packager's source_commit field denotes the BASE, not a feature commit.
`readiness.json` binds exact feature source hashes and an applicable patch.
No push, merge or device action is part of this change.

Offline :app:testStandardDebugUnitTest and :app:assembleStandardDebug with
lami.voiceIntegrationTrial=true pass. Final selected regression suite:210 tests,
zero failures/errors/skips. This includes12 readiness-gate tests,3 retiring-job
ownership tests,3 preparation-handshake tests,4 Entry Activity tests and4 source
wiring checks, plus existing assistant, capture, privacy, replay and TTS tests.
The independent Android-free speech suite also passes122 tests.

These test types have different limits: Entry uses Robolectric; the state/job
tests use fake events/cleanup; source tests check integration wiring. They do
not run actual local-ASR models, physical keys, OEM recording callbacks or phone
vibration. An actual MainActivity-to-ASR/haptic acceptance remains unverified.

The stable same-runtime packager preserves all10 frozen runtime payloads, the
same B model manifest/generation identity and signed saved-PCM compatibility.
The signer and permissions match the known installed Settings APK
4ed34d8456523c490e49e0776e1980756e9ac33d45e8f105f49f8726d344a4e9.
No research JNI derivative is included in this normal-app update.

Final delivery: `../assistant-ready-delivery-v3-20261027/voiceintegrationtrial.apk`,
404,552,506 bytes, SHA-256
`3ade2e2e4ee7189a4122b6b632d496a71a1de715e0bd5126f79473f2a36d4bd6`.
`readiness.json` contains the source hashes, test suites, signer and proposed scope;
`assistant-ready.patch` applies cleanly to the unchanged base (git apply --check).
Earlier intermediate delivery
directories are preserved and superseded; do not deploy an intermediate APK.

## Proposed Device Check, Not Executed

First obtain approval for a same-ID data-preserving update and microphone/haptic
test. Freshly verify endpoint/device, actual selected assistant, <=35C,
battery>=30%, no conflicting work/audio, safe foreground and sufficient install
space (suggest at least1GiB for the roughly405MB APK/native extraction). Keep
weekday08:00-19:30JST phone prohibition and all data-preservation guards. Snapshot
settings/models/saved-audio hashes; do not clear data or alter assistant/privacy
selection or OS haptic/mute settings.

Then at most3 coordinated physical invocations: cold entry, repeat after stop,
and repeat while listening. Each session at most10s, expected ready tick at most
once per invocation (up to3 total), actual counts recorded. Existing startup cue
may be audible, so include it in the coordinated approval. Do not run new answer
TTS generation. Confirm auto-send is OFF before any spoken test; if ON, do not
silently change it or risk chat submission, instead agree a separate safe plan.
Use manual stop to end capture and preserve text, no Send action. Raw capture
PCM remains transient, as in the existing ASR implementation; no audio recording
file or new research/voice diagnostic record is added by this feature.

No permission is automatically granted/revoked. An ungranted RECORD_AUDIO uses
the normal user confirmation UI, with no readiness pulse on denial. OS-disabled
haptics are a respected policy result, not an instruction to enable them.
On failure, stop without automatic repeats and retain existing bounded logs.
Existing audio/model files, normal diagnostic cap and research records remain.
