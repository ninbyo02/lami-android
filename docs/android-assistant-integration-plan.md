# Android assistant integration — isolated implementation plan

## Goal
Allow the app to appear as an Android default assistant candidate and accept a user-initiated assist launch, without changing TTS, ASR, or chat implementation.

## Isolation
- Worktree: ~/project/lami-android-assistant
- Branch: feature/android-assistant
- Do not edit existing TTS worktree or merge automatically.

## Phase 1
- Explore ACTION_ASSIST intent-filter on a dedicated exported activity with a safe entry point.
- Verify resolver/default-assistant eligibility on a physical Android device.
- Record cold start, locked-screen behavior and launch latency.

## Phase 2
- Evaluate VoiceInteractionService and VoiceInteractionSession if ACTION_ASSIST is insufficient.
- Connect to the conversation entry point only after voice engine changes settle.
- Test Bluetooth headset assistant button and Sony voice-control integration independently.

## Risks
- Android default-assistant selection varies by OEM and OS version.
- Headset voice commands may invoke vendor-specific assistant pathways.
- Background microphone and locked-device use are restricted.
- No promise of automatic microphone activation or arbitrary third-party app control.

## Acceptance criteria
- No changes to TTS implementation.
- Build and manifest validation pass.
- Device assistant settings show LAMI (to be verified on device).

## Jabra Evolve2 40 SE USB shortcut
- The measured USB device IDs are vendor `0x0b0e`, product `0x2e41`.
- A short volume-down press sends `KEY_VOLUMEDOWN`. A long press sends a
  `KEY_PREVIOUSSONG` down/up pulse, about 2 ms apart. Android maps it to
  `KEYCODE_MEDIA_PREVIOUS`; measuring that pulse as a long key hold is incorrect.
- A foreground MediaSession alone did not receive this pulse on NX733J when
  no media was playing (`Media button session is null`).
- The debug-only `JabraAssistKeyService` requests accessibility key filtering.
  It does not request screen content, gestures, screenshots, or microphone access.
  Android documents this callback at
  https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent).
- Enable `LAMI Jabra Assist` in Android accessibility settings after installing
  the assistantprobe APK. Disabling that service disables the shortcut.
- The shortcut is armed only in the `.assistantprobe` package, for the measured
  Jabra USB IDs, while `AssistantEntryActivity` is the configured default
  assistant, no media is playing, and audio mode is normal.
- Eligible previous-key down/up pairs are consumed together and launch
  `ACTION_ASSIST` once on release. Repeats, canceled events, orphan releases,
  releases from another device, and stale releases do not launch.
- Short volume presses always pass through. During media playback the whole
  previous-key pair passes through to Android's normal media routing. If playback
  starts between a consumed down and its up, the matching up is consumed without
  launching, preserving a consistent key stream.
- Assist launches are allowed while locked. The entry activity can wake the screen
  and passes a lock-screen display request to MainActivity. Both fresh and reused
  MainActivity instances apply it. Window visibility is revoked when MainActivity
  stops; ordinary launcher opens do not opt into lock-screen display. Android's
  keyguard is not dismissed. The normal LAMI UI, including any restored history,
  is visible during this explicit locked assist session.
- Automatic voice recording is not implemented.
- On NX733J, the accessibility bridge receives Jabra keys on a lit lock screen
  but not while the display is off. Screen waking on the assist activity cannot
  help when the key never reaches the service. The current shortcut therefore
  requires the display to be on, even though device authentication is not required.
  AOSP's AccessibilityInputFilter also skips processing when `FLAG_PASS_TO_USER`
  is absent:
  https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/accessibility/java/com/android/server/accessibility/AccessibilityInputFilter.java.

### Verification
- `./gradlew testStandardDebugUnitTest --tests 'io.github.ninbyo02.lami.assistant.*' assembleStandardDebug`
- APK: `app/build/outputs/apk/standard/debug/app-standard-debug.apk`
- Verify a short press still lowers volume, then verify a long press launches
  from the home screen. Repeat during music playback: the previous-track action
  should reach the player and LAMI should stay in the background.
- Diagnostic tag: `LamiAssistantButton`. `jabraKey=88 ... launch=true` identifies
  an assist trigger; `jabraKey=25 ... consume=false` identifies volume passthrough.

### Physical-device result (2026-10-11)
- NX733J, Jabra Evolve2 40 SE over USB-C, ADB `192.168.52.52:38741`.
  A dedicated host ADB server on port 5038 avoided the unstable default server.
- Build passed; all 11 assistant unit tests passed, including 8 Jabra policy tests.
  The updated assistantprobe APK installed successfully and the existing default
  assistant selection remained unchanged.
- With explicit user approval, `LAMI Jabra Assist` was enabled. Android reported
  the service bound with capability 8 (key filtering), no window-content access,
  and no accessibility event subscriptions.
- Music stopped: user confirmed long press opened LAMI from the home screen;
  `dumpsys activity` confirmed assistantprobe MainActivity was top resumed.
- Music playing in Sony Music Center: the user confirmed long press went to the
  previous track without opening LAMI, and short press lowered volume. Android
  showed Sony as the playing media-button session and the foreground app, and
  the physical previous-key pulse was captured with `getevent`.
- This device exposed no readable logcat entries during verification. Results
  rely on physical input capture, Android service/activity/audio state, and the
  user's observed behavior rather than missing application logs.
- The accessibility service remains enabled so the installed shortcut works.
  These initial results cover an unlocked device. The cute-TTS worktree was not
  modified. Locked-device results are recorded below after verification.

### Locked-device result (2026-10-11)
- Updated build and installation succeeded. All 12 assistant unit tests passed,
  including locked assist intent forwarding and the previous Jabra policy tests.
- Locked, display on, music stopped: user confirmed Jabra long press launched
  LAMI. The service's diagnostic counters reached `previousKeyDowns=3` and
  `assistLaunches=3`, with `lastEligible=true` and `lastMusicActive=false`.
- A direct ACTION_ASSIST launch also confirmed `MainActivity` top resumed with
  `KeyguardShowing=true`, `Occluded=true`, and `DismissalRequested=false`.
- Locked, display off, music stopped: user confirmed no launch. `getevent`
  captured the Jabra previous-key pulse, but both service counters stayed at 3.
  Before waking for diagnosis, Android reported `SCREEN_STATE_OFF` and
  `INTERACTIVE_STATE_SLEEP`. This verifies the service never received the pulse.
- Temporary host-side screen waking was used only to separate these two cases.
  It is not an application feature or a solution for display-off operation.
- Service counters can be read with `adb shell dumpsys activity service
  io.github.ninbyo02.lami.assistantprobe/io.github.ninbyo02.lami.assistant.JabraAssistKeyService`.
- The opt-in direct USB prototype below addresses the display-off input gap.

## Opt-in direct USB button prototype
- Debug-only `JabraUsbSetupActivity` provides Connect/Disconnect controls. The
  `.assistantprobe` package guard, explicit Android USB permission, exact USB IDs,
  and verified HID descriptor are required before reading buttons.
- `JabraUsbButtonService` runs as a connected-device foreground service with an
  ongoing notification and a Disconnect action. It claims only HID interface 3,
  interrupt IN endpoint `0x81`. Audio interfaces 0/1/2 are not claimed or read.
  It uses `UsbRequest`, not bulk transfers, to receive interrupt input.
  See https://developer.android.com/reference/android/hardware/usb/UsbRequest
  and https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device.
- The actual 346-byte report descriptor has SHA-256
  `c8f27a590fccec786c758fecb26e8c93fd641c0f04c9b06e9d8e24c99cabf085`.
  An unknown descriptor or malformed consumer report stops the bridge and
  releases the claimed interface instead of guessing input positions.
- Consumer report 1 is three bytes: ID, eight control bits, zero padding.
  Bit order is volume down/up, fast-forward, rewind, next, previous, stop,
  play/pause. The measured short volume-down report was `010100`, long press
  `012000`, each followed by `010000`. Duplicate reports do not repeat actions.
- Volume changes use AudioManager's suggested stream; other media keys use
  normal media-key dispatch. Previous is intercepted only under the same
  default-assistant/no-music/normal-audio-mode conditions as the accessibility
  bridge. No fake media playback or screen-waking loop is used. The initial
  prototype did not hold a CPU wake lock; the latest experiment is described
  below. Keep `LAMI Jabra Assist` enabled for background activity-launch support.
- Normal stop/error paths cancel input, close the request, release HID (Android's
  native implementation reconnects its kernel driver), and close the connection.
  Stop the bridge before replacing its APK. Abrupt process death/force-stop does
  not guarantee that cleanup runs; unplug/replug Jabra if system buttons stop
  working. USB ownership and OEM process survival need longer-duration testing.
- This is a media-button prototype, not a complete USB telephony driver.
  Telephony/vendor reports are diagnostic only; call-answer/LED synchronization
  is not implemented. Detection of non-normal audio mode stops and releases HID
  for the system. Restart explicitly after calls, unplugging, or rebooting.
  USB permission may need granting again after reconnecting. Microphone input
  and automatic voice recording remain untouched.

### Direct USB physical-device result (2026-10-11)
- User explicitly approved direct USB testing and granted Android USB access.
  The descriptor was obtained from the physical headset; its temporary claim
  was released before starting the continuous bridge.
- Build succeeded and all 18 assistant tests passed, including six USB decoder
  tests for bit mapping, duplicate suppression, release, telemetry isolation,
  malformed reports, and unknown descriptor rejection.
- Display on, music stopped: user confirmed short press lowered volume and long
  press opened LAMI. USB diagnostics confirmed volume key forwarding and one
  assist launch per previous-key press/release pair.
- Display off and locked, music stopped: user confirmed the screen lit and LAMI
  appeared. USB diagnostics received `012000`/`010000` and recorded an assist
  launch with `musicActive=false`. Android confirmed MainActivity top resumed,
  `KeyguardShowing=true`, `Occluded=true`, `DismissalRequested=false`.
- Music playing in Sony Music Center: the user confirmed short press lowered
  volume, long press returned to the previous track, and LAMI did not appear.
  USB diagnostics showed complete forwarded key-88 down/up pairs and no assist
  launch for those pairs. Sony remained the PLAYING media-button session.
- Disconnect was exercised before updating the APK. The service recorded normal
  release, and `getevent -lp` again listed the Jabra consumer/telephony input
  devices, verifying Android's kernel HID driver reattached.
- A later retest after more than 30 seconds of display-off waiting did not launch.
  The foreground service was still present, but its dump initially timed out and
  no new USB report had been written. After host-side screen waking, only the
  previous-key DOWN report appeared. Sony's session was PAUSED before the test;
  the user subsequently closed Sony. Display-off reliability is therefore not
  yet established by the earlier successful attempt.
- Music closed: a second display-off attempt also failed. The release report
  eventually appeared much later (`44409595` DOWN, `44619513` UP, elapsed-ms
  timestamps), so the stale-release guard correctly suppressed launch.
- Read the bounded (64-line), app-private USB report history using `run-as
  io.github.ninbyo02.lami.assistantprobe cat files/jabra-usb-buttons.txt`, or dump
  `JabraUsbButtonService`. Logcat remains unavailable on this device.

### Bounded CPU-awake experiment
- The user approved only a five-minute trial, not an always-on CPU setting.
  The explicit `5-minute CPU trial` toggle starts a five-minute USB session with
  a PARTIAL_WAKE_LOCK. It defaults off; normal Connect does not acquire a CPU lock.
  The screen remains off; normal stop and expiry release the CPU lock. Android's
  timeout-acquire API is also used, but timers can be delayed if the entire app
  is frozen; it is not a system-side guarantee of timely interface cleanup.
  At expiry, USB reading stops and HID is released. There is no automatic restart
  or extension of the trial, and no production-duration mode is implemented.
- Wake locks can materially increase battery consumption. See
  https://developer.android.com/develop/background-work/background-tasks/awake/wakelock.
  Battery drain and long-duration behavior have not been measured.
- Two interrupt requests are kept queued, and a completed buffer is immediately
  requeued before file I/O or media dispatch. This retains short down/up pulses
  while the app handles the previous report. Both changes are experimental;
  success would not by itself identify which resolved the earlier delayed input.
- Build and all 20 assistant tests passed, including five-minute expiry and
  explicit disconnect canceling expiry/releasing the CPU lock.
- One minute after display-off, the CPU-awake trial also failed to launch.
  `dumpsys power` showed this app's wake lock as DISABLED, and the activity process
  dump explicitly reported `isFrozen=true` despite the foreground USB service.
  A two-minute, package-only temporary device-idle exemption (within the trial's
  five-minute window) did not help and was explicitly removed. This is evidence
  of per-app/OEM freezing, not just an absence of a CPU wake lock.
- The trial was stopped before five minutes, after host assist/unlock restored
  the app process. HID release and absence of the trial wake lock were confirmed.
  Buffered DOWN/UP reports were then processed; these are not successful
  physical display-off launches because host waking was required.
- With separate explicit user approval, only assistantprobe's app-info > app
  battery usage > battery optimization > Background Running was changed from
  Auto to Allow. Auto-start and associated launch remained Auto. No other LAMI
  package settings were changed. Retesting uses ordinary USB mode, CPU trial off.

### Final normal-USB retest after OEM setting change
- Final APK installed successfully. All 20 assistant tests passed, and
  `git diff --check` passed. The CPU-trial toggle is off by default.
- Normal USB mode reported `cpuTrial=false`, `cpuTrialHeld=false`. After the
  display had been off for over one minute, the process dump showed
  `isFrozen=false`. The user then physically long-pressed Jabra volume-down,
  without touching the phone, and confirmed LAMI appeared.
- USB diagnostics recorded `012000` at elapsed-ms `45229312`, `010000` at
  `45229320`, and one assist launch at `45229322`, with `musicActive=false`.
  Android confirmed MainActivity top resumed and keyguard still showing/occluded,
  with `DismissalRequested=false`. No host-side screen wake was used for this
  successful retest.
- The temporary device-idle exemption list was empty at the end. The five-minute
  CPU trial had been stopped and was not restarted. Ordinary USB reading and the
  previously approved accessibility service remain enabled for use.
- The observed improvement follows the package-specific OEM Background Running
  change. Hours-long standby, unplug/replug, reboot, and call controls have not
  been physically verified. Another app (Termux) already held its own CPU wake
  lock throughout testing; it was not changed. True deep-suspend behavior without
  that pre-existing lock is not established by this test.
- Work stayed on `feature/android-assistant` in the requested worktree; no cute
  TTS worktree edits were made. No commit or merge was performed.

### Pioneer Bluetooth short-press trial (2026-10-11)
- Connected headset was Pioneer SE-MS7BT (A2DP/HFP). Unlike Jabra, its manual
  assigns minus short press to previous/beginning of track and minus hold to
  lower volume. The user observed no LAMI launch and a beep on holding minus
  with music stopped. Android recorded Bluetooth call-volume changes down to
  its minimum. The manual documents a beep at volume limits; this does not
  establish an assistant gesture. Central-button double press redials and must
  not be used as an experimental shortcut.
- The user subsequently observed central/power short press opening a music app
  and approved testing this route. Android routes media keys using media-session
  and recent local-audio playback state, not merely the default assistant:
  https://developer.android.com/media/legacy/media-buttons.
  Manufacturer manual: https://www.bhphotovideo.com/lit_files/330145.pdf.
- `BluetoothAssistSetupActivity` and `BluetoothAssistTrialService` are debug-only,
  package-guarded to `.assistantprobe`. Explicit Start trial requires the named
  Pioneer A2DP output, default LAMI, normal audio mode and no active music.
  It plays one low-level 250-ms confirmation using a real AudioTrack with the
  Pioneer preferred output, then sets its MediaSession to PAUSED, with no audio
  loop, recording, Bluetooth discovery, USB claim or CPU wake lock.
- The foreground special-use diagnostic trial expires after five minutes,
  checks elapsed time before accepting a trigger, stops on music/call mode,
  device removal or default-assistant change, and ends after one launch request.
  It is non-sticky and does not arm automatically after process death or reboot.
  App timers can run late during suspension/freezing; an expired key cannot
  launch even if cleanup is delayed. Stop remains available in setup/notification.
- Only matching short play/play-pause/headset-hook key pairs (<=1500 ms) are
  triggers; repeats, long/canceled/orphaned events and volume/previous/pause are
  excluded. Direct MediaSession onPlay transport requests are also observed and
  eligible only in armed mode. The Android session callback may not identify the
  physical sender: presence of Pioneer is a prerequisite, not proof that a key
  or transport command came from it. This is explicitly an opt-in one-shot test.
- Android's selected media-button session, callback delivery and actual activity
  appearance must be verified separately. A launch request is not a confirmed
  launch, and any synthetic key check is not a physical headset result. Keep the
  already-approved accessibility service for the existing background-launch
  allowance; no new overlay or lock-dismissal permission is added.
- A music app can become the preferred recipient again even after pausing it.
  Stopping this test releases the session but cannot promise restoration of the
  previous resume target. Play music normally to re-establish that app; no fake
  PLAYING state is held in standby. Long standby, cold process restart, and
  Bluetooth microphone/conversation routing are outside this initial test.
- Build succeeded, all 27 assistant tests passed (including seven Bluetooth key
  policy tests), and assistantprobe APK installation succeeded. First Start trial
  refused safely because Pioneer was physically disconnected. After the user
  reconnected, AudioManager exposed A2DP output `SE-MS7BT`, and confirmation
  routing to that output was verified. Android selected
  `LAMI-Bluetooth-Assist-Trial` as its media-button session in PAUSED state.
- Home-screen physical short press succeeded: the user confirmed LAMI opened.
  Diagnostics recorded virtual-device key 126 (MEDIA_PLAY), DOWN `46766535`,
  UP `46767477`, then one assist request `46767478` (elapsed-ms timestamps).
  Android confirmed MainActivity top resumed. The one-shot service then ended.
- A fresh trial was armed at `46838705`; host went HOME, then KEYCODE_SLEEP.
  After over a minute without screen waking, dumpsys power still showed Dozing
  and no display suspend blocker; the selected session was still the trial.
  The user then physically short-pressed without touching the phone and
  confirmed LAMI appeared. Key 126 DOWN `46945094`, UP `46946046`, request
  `46946048` were recorded. Android confirmed MainActivity top resumed,
  keyguard showing/not going away, and display Awake. No host screen wake was
  used for this successful physical test.
- With a third trial armed, the user started Sony Music Center. The service
  detected `Music active: trial stopped` at `47030022`, and Sony became the
  media-button session; its PAUSED state was observed during the check.
  The user confirmed separate short presses stopped and resumed music normally.
  No assist request was recorded in this trial. At completion, the Bluetooth
  service/session were absent and the launcher was top resumed. The user had
  closed the music session by the final check.
- These are explicitly armed, warm-process, short-duration results, not an
  always-available Bluetooth assistant. Background Running=Allow and the
  existing accessibility binding from the Jabra experiment remained unchanged.
  Termux's pre-existing CPU wake lock was still held, so full deep-suspend without
  it is not verified. No Bluetooth microphone test, real-call test, long-duration
  battery measurement, manual five-minute expiry test, reboot or process-restart
  test has been performed. The trial has no new CPU lock and is stopped now.
- All changes for this trial were debug manifest/classes/tests and this record
  in the assistant worktree. Cute TTS and other packages were not edited or
  installed. No commit/merge was made; `git diff --check` passed.

### User-controlled Pioneer standby and microphone probe (2026-10-11)
- The one-shot trial is now `BluetoothAssistStandbyService`, with explicit
  on/off controls accessible from LAMI Settings > Bluetooth standby only in
  assistantprobe. Durations are 5/30/60 minutes, default 30; repeated requests
  retain the same fixed deadline with a 1500-ms debounce. No boot/process-death
  rearming, continuous audio, recording or new CPU wake lock is introduced.
  Playback callbacks and the periodic monitor stop standby when media audio
  starts; this can also stop it for LAMI's own media speech playback. It does
  not silently rearm after music stops. Earlier successes used the one-shot
  trial; repeated physical launches of this revision are tested below.
- A foreground-only microphone screen captures five seconds after an explicit
  press and microphone permission. Permission granting does not start capture.
  It requests a Bluetooth SCO communication route and input, checks the actual
  AudioRecord routed input after every read, and refuses builtin-microphone
  fallback or Android client silencing. Capture is bounded by a 12-second
  timeout including connection setup. Leaving the screen cancels recording
  and discards speech; speech is held only in RAM, never sent or written to disk.
  The separate diagnostic file stores endpoint types/IDs and aggregate levels.
- On NX733J with SE-MS7BT connected, physical capture recorded actual input
  `type=7` (Bluetooth SCO), input ID 275, `silenced=false`, at elapsed-ms
  `48212950`. The endpoint product label was NX733J; that label alone does not
  identify a builtin mic. The actual type/ID matched the selected SCO input,
  with Pioneer the connected HFP headset. All 80000 mono PCM16 samples at
  16000 Hz were acquired: 60899 nonzero, peak 1695, RMS 137.19757. This confirms
  Bluetooth microphone data, not intelligibility or successful speech recognition.
- Replay reported actual A2DP output `type=8`, ID 259, name SE-MS7BT at
  `48220075`. The user reported captured sound but very low replay volume.
  Raw RMS was about -47.6 dBFS, raw peak about -25.7 dBFS; the probe also used
  AudioTrack volume 0.5, with system A2DP media volume observed at 60/150.
  These levels do not by themselves establish intelligible speech or ASR quality.
  Initial opt-in correction used peak target 8192, gain bounded to 1-8x and
  AudioTrack volume 0.5. Retest again used actual SCO input ID 275, no silencing,
  80000 samples, RMS 182.09233, peak 2267; replay gain 3.613586 was observed
  with A2DP output SE-MS7BT. The user said correction helped but was still quiet,
  asking to reference music-app volume. Correction now targets raw RMS 2500
  with a peak ceiling of 24576 and gain 1-16x; corrected playback uses AudioTrack
  volume 1.0 on the same system MEDIA stream (uncorrected comparison stays 0.5).
  PCM arithmetic saturates, original samples and system volume are unchanged,
  silence is not amplified, and input diagnostics remain raw. This is replay
  correction, not an input sensitivity or ASR fix; perceived music-level
  equivalence has not been established. This revision recorded actual SCO input
  with no silencing twice (RMS 229.35954/244.08704, peaks 2503/2507), and replay
  gains 9.818618/9.802952 routed to SE-MS7BT. The user reported improved audibility,
  wishing for a little more volume. The final correction target was increased
  to RMS 3000 and amplification peak ceiling 28672, gain still bounded to 16x;
  for those samples this would add about 1.3 dB. That last small increase is not
  yet separately listening-tested. The user also reported a small click heard
  directly in Pioneer just after pressing Record, not in replay. The probe
  produces no playback during capture, so SCO connection switching is a possible
  cause, not proven. Removing this direct headset click remains unverified.
  After capture/replay, dumpsys audio confirmed MODE_NORMAL with no mode owner,
  no recorder ports, no communication-route clients/preferred device, SCO
  inactive and not requested/applied, and A2DP not suspended. There was no
  continuing capture or communication-route ownership from assistantprobe.
- Initial navigation into the new standby screen exposed an OEM PhoneWindow
  null-decor crash when reading window.insetsController before decor creation.
  Both screens now initialize through window.decorView first. The corrected
  APK was built and installed, both screens opened successfully on device,
  and 35 assistant tests passed, including two screen-creation/no-auto-record
  regression tests. `git diff --check` passed.
- This probe is not yet the conversational ASR implementation. Real calls,
  microphone interruption/disconnect, hours-long battery use,
  reboot and full deep-suspend remain unverified. The previously
  approved package-only OEM Background Running=Allow and accessibility binding
  remain unchanged. Cute TTS worktree and other package settings are untouched.
- The final small volume adjustment built and installed successfully, with all
  38 assistant tests passing. Notification permission was granted by the user
  through Android UI, then a five-minute standby was armed at `48805111`.
  A physical central-button short press from Home requested assist at
  `48858862` (key 126 DOWN `48857909`, UP `48858860`), and the user confirmed
  LAMI opened. Android confirmed MainActivity top resumed; standby remained
  armed with its original deadline rather than ending or restarting.
  Host then went Home and used KEYCODE_SLEEP without rearming standby. Screen
  Dozing/no display blocker and retained armed standby were observed. After
  over a minute display-off, the user physically short-pressed without touching
  the phone and confirmed LAMI opened. Key 126 DOWN `48987504`, UP `48988461`,
  assist request `48988462`, and retained standby `48988503` were recorded.
  Android confirmed MainActivity top resumed, keyguard still showing and not
  going away. No host wake or standby rearm preceded this second success.
- The user then unlocked and started Sony Music Center. Standby stopped for
  music at `49055545`, before the original five-minute deadline, and Sony's
  PLAYING session became the media-button recipient. The user confirmed
  separate single presses paused and resumed music. No additional assist
  request appeared. The standby service was absent at completion; its screen
  showed the music-stop reason and the toggle off. Final navigation from Main
  through Settings > Bluetooth standby succeeded without a shell shortcut.
  Music was not stopped by the host, and standby was not automatically rearmed.
  Last capture speech was discarded by the APK update/screen exit. No commit
  or merge was made; all work remained in the assistant worktree.

### Direct headset click comparison (2026-10-11)
- The user asked to try removing the small click heard directly in Pioneer
  immediately after pressing Record, not in replay. An opt-in, default-off
  `Reduce connection switching (trial)` switch was added to the debug microphone
  screen. It requests the same SCO route/input but leaves MODE_NORMAL unchanged,
  rather than first selecting MODE_IN_COMMUNICATION. Capture still verifies
  actual SCO input ID/type and no silencing on every read, with no builtin
  fallback, global volume/mute changes or audio playback during capture.
- AudioManager documents communication-device selection separately from mode
  ownership, with mode ownership controlling priority between simultaneous
  requests. It does not guarantee this mode-neutral sequence will capture on
  this OEM device or remove a click; both require physical verification:
  https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo).
- The comparison APK built and installed successfully, with all 38 assistant
  tests passing, including switches defaulting off. Pioneer was connected and
  no player/recorder active before testing. Actual SCO input ID 275/type 7 and
  no silencing were observed in mode-neutral tests, but the user reported no
  improvement: a click in both ears, more pronounced on the left. Many captures
  were explicitly canceled early during comparison; this proves selected input
  during those reads, not five-second completeness for every attempt. Some
  stopped with a zero-length read, not builtin fallback. No standby/TTS change.
- A second input-source comparison was added: standard VOICE_COMMUNICATION vs
  VOICE_RECOGNITION. The latter is Android's recognition-tuned microphone source;
  it does not itself run ASR or guarantee different processing on this device.
  Default remains communication source and the mode-neutral switch off. Actual
  routed SCO input and silencing checks remain mandatory. Reference:
  https://developer.android.com/reference/android/media/MediaRecorder.AudioSource#VOICE_RECOGNITION.
  The second APK built and installed, with all 39 assistant tests passing.
  In both mode-neutral and communication-mode recognition-source trials,
  actual SCO input ID 275/type 7 and no silencing were observed. Captures were
  canceled early during listening comparisons; full five-second recognition-
  source speech quality has not been established by this test.
- The user reported the mode-neutral option appeared to shorten the blank
  interval before the click; with it off, a longer blank interval preceded
  the click. This is a subjective timing observation, not click removal or
  a measured latency improvement. The click remained with both input-source
  settings/connection approaches. No successful direct-click suppression was
  demonstrated, and headset hardware vs SCO switching vs OEM audio processing
  remains undetermined. Do not label these experimental controls a noise fix.
- The microphone screen was exited after comparison, canceling capture and
  discarding temporary speech. Final audio state confirmed MODE_NORMAL, no
  mode owner, empty recorder/player maps and communication-route client list,
  no preferred communication device, SCO inactive/not requested or applied,
  and A2DP unsuspended. New microphone screens default to communication source
  and mode-neutral trial off; no recording/connection remains active. System
  volume and other LAMI/TTS settings were not changed. No commit or merge.

### Adjustable microphone lead-in cut (2026-10-11)
- Added a numeric `Lead-in cut (ms)` field to the debug microphone screen at
  the user's request. It accepts integers 0..3000, defaults to 0 (unchanged
  capture), and remembers the last valid value submitted with Record in this
  assistantprobe package's private preferences. Invalid/empty values show an
  inline error without recording or requesting microphone permission. The
  field is disabled during capture/replay; valid submission hides the keyboard.
- Discarding starts with AudioRecord's first samples, not when Record is tapped
  or SCO negotiation starts. At 16 kHz, each requested millisecond discards 16
  samples. Partial reads and the crossing block retain only the exact suffix;
  the subsequent five seconds (80000 samples) are captured without padding.
  This adds up to three seconds of input time; the overall capture timeout is
  bounded at 15 seconds. Actual SCO input/silencing/mode and cancellation checks
  run during discarded reads as well as retained reads. Cut audio is not saved;
  retained speech remains RAM-only. Diagnostic metadata includes cut duration
  and requested/completed discard sample counts, never audio samples.
- This changes recorded/replayed PCM only. It does not mute headphone output
  or suppress a click heard directly in the headset during SCO connection.
  Noise removal is unverified; the user will investigate suitable durations.
- All 47 assistant tests passed and the updated assistantprobe APK built and
  installed successfully on 192.168.52.52:38741. Physical screen inspection
  confirmed the field, default 0, numeric keyboard, and readable controls.
  No microphone recording or replay was initiated during this installation
  check. Standby remained off; TTS worktrees/settings were not modified.

### Preconnected communication route trial (2026-10-11)
- The user tried discarding the start of capture but reported no improvement
  to the click heard directly from the Pioneer headset. This supports testing
  SCO connection timing separately from recorded PCM; it does not identify the
  exact hardware or Android component making the click.
- Added an explicit `Preconnect call audio (10 s; trial)` button on the debug
  mic screen. It requests the same Bluetooth SCO communication route before
  AudioRecord starts, waits up to 3.5 s for selection, then keeps the route for
  at most 10 s. Pressing Record during that window inherits the route instead
  of reselecting it. The existing input route and silencing checks still run
  for every recording read. Timeout, Stop, screen exit, and normal recording
  cleanup release the route and restore the mode owned by assistantprobe.
  No microphone is opened by the preconnect button itself. This is a bounded
  foreground comparison, not a background or always-on microphone feature.
- The APK built and installed on 192.168.52.52:38741 with 47 assistant tests
  passing. On device, the new control rendered and a preconnect request reached
  the Bluetooth SCO output; a following inherited-route capture verified actual
  Bluetooth SCO input ID 275/type 7 with silencing false. Whether the headset
  click moves to preconnect or repeats on Record requires the user's listening
  result. No click suppression is claimed yet.
- The user then listened to this comparison and reported the click when
  pressing `Preconnect call audio`, with no click on the subsequent `Record`
  press. This locates the audible event at creation of the Bluetooth call-audio
  connection, rather than at AudioRecord.startRecording or in captured PCM.
  Holding the connection moves the click earlier; it does not eliminate it.
  Continuous call-audio standby was not added because it would interfere with
  normal media playback and keep a higher-power Bluetooth audio path active.

### Repeated capture on a retained communication route (2026-10-11)
- Added an explicit `Same connection: 3 recordings (trial)` control to the debug
  microphone screen. It records three five-second clips, with three-second
  pauses in which the microphone is stopped. The same AudioRecord, audio focus
  request, and Bluetooth communication-route selection are retained throughout.
  This comparison selects communication input, disables the mode-neutral trial,
  and sets lead-in cut to zero. The whole trial has a 40-second timeout; it does
  not add background or permanent call-audio standby. Only the last clip is kept
  in RAM for optional replay; no speech is written to diagnostic files.
- The initial implementation incorrectly treated MODE_NORMAL during an idle
  pause as a lost call-audio connection. A subsequent 500 ms mode-report wait
  also caused intermittent stops before the second or third clip. These are
  distinct from a physical Bluetooth disconnect. The corrected guard permits
  NORMAL or the requested communication mode while requiring the selected SCO
  output to remain identical and music to remain stopped. Every capture read
  independently verifies actual SCO input identity and rejects silenced input;
  phone-call/ringtone modes, route changes, and cancellation still abort.
  Cleanup unconditionally removes assistantprobe's owned mode request even
  when Android currently reports NORMAL, then releases its route and focus.
- All 53 assistant tests passed, including five mode-guard regression tests;
  the APK built and installed successfully on 192.168.52.52:38741. The final
  on-device trial completed all three clips (80000 samples each), with actual
  SCO input ID 275/type 7 and silencing false every time, using SCO output
  ID 267. The third clip started after a pause reporting MODE_NORMAL, confirming
  that the revised guard no longer aborts merely because of that report.
- The earlier completed listening trial had one physical SCO connection event
  at 12:48:01.431, no disconnect/reconnect events during either pause, and one
  disconnection at 12:48:23.127 after cleanup. The user reported the click only
  at the beginning, not on the second or third recording, and subsequently
  reiterated that it appeared to happen only once. This is a subjective audible
  result supported by the retained-connection metadata, not proof that the
  first connection click can be eliminated or identification of its component.
- Stop during the first pause was verified: cancellation at elapsed time
  52250930 prevented recording 2/3 from starting and cleanup followed at
  52250951. Stop, screen exit, timeout, disconnection, or an error discard
  temporary speech and release resources; no partial trial is offered as a
  completed result. The final completed trial's RAM recording was also discarded
  with Stop. Final system audio state was NORMAL, with empty recorder/player
  maps and inactive SCO.
- Direction for subsequent conversation integration: create call audio once
  per conversation, retain it between listening turns, and release it when the
  conversation ends. This trial does not implement that lifecycle in production
  ASR/TTS or prove behavior during synthesized speech. The first connection
  click remains unresolved. The cute TTS worktree/settings were not modified;
  no commit or merge was made.

### Bounded conversation input/output route trial (2026-10-11)
- Added `Start conversation connection (60 s; trial)` to the debug microphone
  screen. It selects communication input with zero lead-in cut and retains one
  SCO route and one transient voice-communication focus request across manual
  five-second recordings and playback. Recording stops and AudioRecord is
  released between turns; the last clip remains RAM-only for replay. Playback
  during this trial uses USAGE_VOICE_COMMUNICATION and the same SCO output,
  rather than switching to A2DP. It consequently uses call-volume routing, not
  the previous media-volume path. Ordinary recordings/replay retain their
  existing one-shot behavior outside this explicit trial.
- A route-selection-only first prototype physically completed recording,
  SCO playback, and another recording: input ID 275/type 7, output ID 267/type 7,
  80000 samples for each recording, with no silencing. After the last recording
  ended, Android eventually deactivated the idle communication client and SCO
  disconnected (13:00:17.381). The route guard released remaining resources;
  this was not a successful full 60-second idle hold.
- The revised trial keeps a looping one-second all-zero PCM AudioTrack on the
  selected SCO output, solely during the bounded foreground conversation
  session. No microphone is opened for this hold. This keeps an actual
  voice-communication output active instead of relying only on mode/route
  requests; it is not permanent assistant-button standby or background music.
  The app verifies the hold track's playback state and actual SCO output and
  aborts rather than reconnecting if the output changes. The input route and
  silencing checks remain active on every capture read.
- Stop/discard, screen pause/exit, lost audio focus, music starting, another
  call mode, route loss, capture/playback failure, or 60 seconds from session
  creation end the session. The timeout is not extended by new recordings or
  playback. Cleanup stops/releases the silent track, microphone and replay,
  discards speech on cancellation, clears the owned route/mode request and
  abandons focus. Controls that change input/route settings are disabled while
  connected. The foreground screen stays lit during the trial; it does not
  add a wake lock or background microphone service.
- The revised APK built and installed successfully on 192.168.52.52:38741.
  Device metadata confirmed silent SCO output started with no microphone opened
  and a subsequent five-second capture used actual SCO input ID 275 without
  silencing. That trial was interrupted before full playback/idle/timeout
  verification; cancellation at elapsed time 53057510 released the connection.
  The user was then using another app, so host-driven UI actions were stopped.
  A subsequent manual foreground comparison completed the full lifecycle. The
  user heard a click when the conversation connection started and another click
  when it ended, but no repeated click between the first and second recording.
  Both recordings were audible through Pioneer when playback correction was
  enabled, and the session ended automatically after 60 seconds. This verifies
  the bounded silent-output hold on this phone/headset combination. It also
  locates the audible events at SCO connection and disconnection; neither event
  was removed by this approach.
- Unit coverage adds eight conversation-route tests for timeout, explicit
  stop even with NORMAL reported, music, route loss, call mode, focus loss,
  no microphone/speech during the hold, and rejecting startup during music;
  plus a screen test for missing headset without recording/permission prompts.
  Native PCM writing/device selection are simulated in those lifecycle tests;
  they do not substitute for physical Bluetooth behavior or listening results.
  All 62 assistant tests passed and assembleStandardDebug succeeded; the
  installed APK has the same application code as this final tested build.
- No concrete ASR adapter is connected to AssistantController in this branch.
  This is the conversation audio-route prototype, not a finished voice dialog,
  model response, or integration with the cute TTS engine. The cute TTS worktree
  and its implementation/settings remain unchanged. First-connection click
  suppression, production synthesized-voice behavior and long-term power usage
  remain unverified. The implementation direction is to establish SCO once at
  conversation start, retain it across ASR/response turns, apply the existing
  playback correction on the call path, and release once at conversation end.
  No commit or merge.

### Conversation-controller audio lifecycle and ASR priority (2026-10-11)
- `AssistantController` now accepts an `AssistantConversationAudio` lifecycle.
  It establishes the selected input/output route before starting ASR, retains
  that route after a transcript and throughout response generation, and reuses
  it when the next listening turn starts. Explicit stop releases ASR/response
  ownership first and then the route. Route-start or response failures roll the
  route back instead of leaving SCO connected. Existing callers remain source
  compatible through a no-op default until a concrete audio adapter is wired.
- Controller tests verify one route start across two listening turns, retention
  through a response, explicit release, route-start rollback before ASR, and
  response-failure cleanup. The complete assistant test selection now has 65
  passing tests; `assembleStandardDebug` also succeeds.
- The concrete ReazonSpeech implementation is not contained in this worktree.
  Static review of the clean `lami-asr-android` main checkout found that its
  production `AndroidAudioRecordInput` currently requests the ordinary MIC
  source and exposes no preferred Bluetooth SCO input. Running its existing
  noise test unchanged would therefore evaluate the phone microphone, not the
  Pioneer assistant path. It must not be used as evidence for this integration.
- Priority decision: first evaluate ASR/VAD with the actual Pioneer SCO input,
  then bind that approved adapter to `AssistantController`. Test quiet speech
  and representative household noise separately and retain endpoint failures
  as well as transcript errors. Only after that gate should the ASR engine be
  promoted into the in-process conversation flow. This avoids freezing VAD,
  endpoint and preprocessing assumptions from an irrelevant microphone path.
- The previous wireless ADB endpoint `192.168.52.52:38741` refused connection
  during preparation and no Android device was attached to the dedicated host
  ADB server. Physical Pioneer ASR testing therefore awaits a current wireless
  debugging endpoint. No changes were made to `lami-asr-android`, its clean main
  checkout, or the cute TTS worktree.

### Jabra USB assistant volume (2026-10-11)
- The accepted Pioneer volume checkpoint is saved separately under
  `artifacts/checkpoints/pioneer-volume-20261011`; its APK/source were not replaced.
- Jabra USB volume keys now use the shared 10-percent media-volume step policy
  while the chat is resumed, music is stopped, and normal audio mode is active.
  The target is bounded to 0-80 percent (updated at the user's request on
  2026-10-11). The HID bridge receives these keys
  before normal forwarding, so it sets one target rather than correcting a
  native continuous volume burst after release.
- Speech/inference/listening block additional commands. Matching key-up is
  consumed even if foreground or audio eligibility changes. A 300-ms startup
  guard covers the hand-off before TTS reports active playback.
- One utterance reads the first level with the volume prefix. Commands within
  five seconds omit the prefix and read only the percentage. Existing bounded
  feedback retry and TTS playback tracking are reused. Jabra's volume-minus
  long-press assistant policy and normal controls during music/outside chat
  retain their existing paths.
- Five regression tests cover held input, speech blocking, owned releases,
  forwarding outside scope, long-press media-key separation, and boundaries.
  `testStandardDebugUnitTest assembleStandardDebug` and `git diff --check` passed.
  The APK installed on `192.168.52.52:35121`; verified Jabra HID 3 standby was
  started without the CPU wake-lock trial. Physical HID presses changed one
  logical 10-percent target, but the user heard no voice from either Jabra or
  the phone despite TTS start/done callbacks and a reported USB output route.
  Music was audible from Jabra. USB microphone routing for ASR is not established.
- A probe-only Jabra speech playback investigation is in progress. Direct TTS
  playback and synthesized-WAV MediaPlayer playback both remained inaudible.
  The latter completed a roughly 2.5-second utterance in about 16 seconds;
  reported routing alone is therefore not accepted as audible success.
  The current trial extracts the engine's PCM with Android MediaExtractor,
  converts to native 48-kHz stereo without amplification, and uses an explicitly
  routed AudioTrack. Temporary speech files are deleted on completion/stop,
  and timeouts and route changes end playback. Non-Jabra speech retains its
  existing engine path. Four lifecycle tests and four PCM conversion tests pass;
  before USB reattachment, native stereo playback advanced at roughly half
  the requested clock rate; one
  utterance hit the bounded playback timeout before its final frames. This is
  an investigation build, not an accepted Jabra voice release.
- After the user physically detached/reattached USB, they confirmed both normal
  TTS and volume announcements were audible from Jabra. The output device ID
  changed from 471 to 506. A 170160-frame utterance then completed in about
  3.53 seconds, and a 124080-frame volume utterance in about 2.61 seconds, close
  to their expected 48-kHz durations. Later number-only utterances also ended
  normally. Reattachment changed the outcome without another APK modification;
  stale USB/output state is suspected, but the exact platform cause is not
  established. Initial presses during conversation speech were deliberately
  consumed without changing volume; after speech ended a 70-percent command
  was accepted and announced. The user subsequently reported that announcements
  now sound normal, and clarified that they had not initially started USB button
  standby in the UI. Physical USB recognition does not mean HID standby is
  active: detachment ends the non-sticky service and this prototype requires
  manual standby restart. Missing announcements while standby is inactive are
  separate from the earlier routed-but-inaudible TTS playback. Success of a
  specifically reported two-number sequence/prefix check is not yet established.

### Jabra Central Button and ASR Follow-up (2026-10-11)

- The user accepted the current Jabra volume behavior and requested central-button
  assist activation for both Jabra USB and Pioneer Bluetooth. The connected device
  for this follow-up is Jabra. Its measured `018000`/`010000` HID reports map to a
  matching short `KEYCODE_MEDIA_PLAY_PAUSE` press/release. The USB bridge now uses
  this pair to launch assist when LAMI is the default assistant, music is inactive,
  and no conversation work is active. Held/repeated presses do not launch.
- A music reservation latches when a central press is forwarded during external
  playback. Subsequent pause/resume presses remain music controls; stopping and
  restarting USB standby explicitly clears the reservation. The setup status
  distinguishes central-button assist mode from music mode. Six policy tests cover
  ownership, matching release, long/repeat rejection, and music reservation.
- The user confirmed central-button LAMI launch, but not transcription. Diagnostic
  records confirm assist extras, successful model preparation, and capture using
  device 575, Android input type 22 (USB headset), source MIC. This is not the phone
  built-in microphone. No successful utterance is established yet. On the clean
  restart trial the user did not see the listening prompt; recording ended with an
  empty, cancelled result. Timing, mute state, and input level still need verification.
- Capture readiness now has a process-local StateFlow separate from the one-shot
  transcript channel. A replacement chat composer can display the current state
  without depending on receiving the earlier listening event. Preparation is shown
  separately; listening is announced to the UI only after capture actually starts.
  Three regression tests cover preparation, late state collection, and completion.
  Diagnostic logging includes input interruption and aggregate peak level, not raw
  recordings or transcript content. The full unit-test/build tasks and whitespace
  check passed, and the updated APK installed successfully. In the next physical
  central-button trial Android's UI hierarchy contained `聞いています…`; however,
  the user did not see input text. The session ended without a transcript and its
  aggregate input peak was -120 dBFS (digital silence). AudioService recorded the
  client as `not silenced`. Hardware mute/USB capture state remains unverified;
  do not treat this as a successful Jabra ASR test or compensate with input gain.
- Pioneer central-button behavior and accepted volume playback are unchanged in
  this follow-up; no Pioneer hardware retest was performed while Jabra was attached.
- The user subsequently clarified that Settings opened during the earlier trial,
  so those attempts are not valid spoken-utterance tests. Inspection found that
  MainActivity restored the last route even during explicit assist entry. Assist
  now bypasses cold-start route restoration and redirects non-chat screens to
  CHAT_ROOT on warm entry; an existing chat is preserved. Two route-policy tests
  were added. Full unit tests/build and the whitespace check passed; the APK was
  installed. A warm assist launch from Settings displayed the chat composer.
- After physical USB reattachment, a setup verification capture used USB headset
  device 629 and completed with a one-character transcript; aggregate peak was
  about -7.18 dBFS. This establishes non-silent input, not correct recognition of
  the requested phrase. Earlier digital silence alone does not prove hardware
  failure or mute. The probe alone was then restarted, USB permission renewed,
  HID standby verified, and Android's launcher confirmed foreground for a fresh
  user central-button trial.
- The fresh physical central-button trial from Android's home screen succeeded;
  the user reported success including transcription. This was a cold MainActivity
  entry after the last visible app route had been Settings. The HID pair launched
  assist with autoSubmit=true. Local recognition ended FINAL with nine characters,
  USB headset input 629, and aggregate peak about -3.03 dBFS. Response TTS played
  110640 frames at 48 kHz through USB output 620 (type 22) and ended normally.
  Home-screen central launch, cold-entry chat routing, transcription, automatic
  submission, and response playback are now confirmed for the attached Jabra.
  Do not extend this result to lock-screen launch or a fresh Pioneer regression
  test; neither was performed in this follow-up.

### Spoken Readiness and Repeated Central-button Turns (2026-10-11)

- Each explicit assist request now prepares the local model and, for Bluetooth,
  establishes the conversation route before playing `どうしましたか。` with LAMI's
  Android voice settings. A dedicated cue engine keeps chat playback listeners
  intact. Capture starts only after actual cue playback completion, not synthesis
  completion; the cue itself is not passed into recognition. Initialization is
  bounded to ten seconds and cancellation stops cue playback. Failed/cancelled USB
  playback is not treated as a successful readiness announcement.
- A new central press after the response can request the next utterance without
  restarting USB standby. Presses while preparing, listening, or responding are
  consumed without another launch or an accidental music toggle. The existing
  Jabra music-reservation behavior and volume stepping remain unchanged.
- Pioneer standby now tolerates LAMI's own speech and microphone communication
  route rather than ending on every media playback or communication-mode change.
  Its opt-in time limit, disconnect/default-assistant checks, and phone-call stop
  behavior remain. External music when LAMI is idle still ends standby; music
  started during assistant work is detected when that work completes. Pioneer
  hardware behavior still needs a connected-device regression test.
- Eleven new tests cover cue completion, repeated cues, initialization timeout,
  cancellation, playback failure, standby audio reasons, and busy central presses.
  Full `testStandardDebugUnitTest assembleStandardDebug` and `git diff --check`
  passed. The new APK installed on 192.168.52.52:35121. Jabra physical readiness
  timing was tested with the attached Jabra. The user confirmed that both requested
  turns had the cue and a spoken answer. Logs show repeated cue completion followed
  by USB capture, successful recognition, and response playback without standby
  restart. An intermediate attempt also ended without a transcript, so this is not
  a claim of comprehensive recognition/noise robustness. Pioneer remains untested
  with this update.
- The user separately confirmed that a displayed `1 2` was read correctly as
  `じゅうに`. That display spacing issue is not changed by this readiness work.

### Number Display and Voice-operation Notifications (2026-10-11)

- The user confirmed repeated cue/response operation and reported that numbers
  still appeared spaced. The UI hierarchy confirmed `一日は2 4時間です。`. Display
  and full-copy text now share the existing speech counter-number correction:
  spaced digits immediately before Japanese counters become contiguous. Numeric
  lists, line breaks, inline code, fenced code, and incomplete streaming fences
  are preserved. Stored model output is not rewritten; TTS pronunciation is unchanged.
- The initial implementation incorrectly treated Compose's Short/Long durations
  as the requested LAMI standard. The user clarified that this means LAMI's own
  setting. `ui/common/SnackbarTimings.kt` defines `PROJECT_SNACKBAR_SHORT_MS = 900L`.
  The assistant feedback now uses this existing 900-ms value, not Compose Short.
  This rule is recorded in `docs/ui/LAMI_STANDARD_LAYOUT.md`.
- Successful automatic voice submission no longer posts a redundant snackbar.
  Rejected short transcripts and manual voice insertion retain project-short feedback.
  Voice errors use the project short timing without a dummy ERROR action; other app notification
  timings are unchanged. Voice feedback runs in a child job rather than blocking
  event collection, and is cancelled on the next voice event to prevent backlog.
- Five number-display tests and three transcript-feedback policy tests were added.
  One further preview test verifies that only assistant replies are normalized;
  user text and code remain intact. The latest-message query includes the existing
  sender flag for this display decision without changing the database schema.
- Full `testStandardDebugUnitTest assembleStandardDebug` passed and the final APK
  installed successfully on 192.168.52.52:35121. The actual chat UI displayed
  `一日は24時間です。`; the final chat-list UI displayed both `一年は12ヶ月です。`
  and `一日は24時間です。`, including previously saved spaced replies. Jabra HID
  standby was restarted with `cpuTrial=false` and a successful Connected record.
  The spoken two-turn test was confirmed before these display/notification edits;
  no new physical speech test was performed after the final UI-only update.

### Chat Entry and Screen-off Follow-up (2026-10-11)

- Normal cold startup now restores only a chat route; saved Settings, history,
  About, and other non-chat routes resolve to CHAT_ROOT. Launcher reentry and
  explicit assistant entry redirect non-chat screens to CHAT_ROOT while keeping
  an existing conversation. The launch version also closes an open chat-history
  drawer, which is not itself a navigation route. Rotation alone does not request
  another launch or close Settings.
- A first device test found that normal launcher reentry could bring the existing
  task forward without calling onNewIntent. MainActivity now uses singleTop so an
  existing top instance receives the entry request and can select CHAT explicitly.
- Full unit tests and the final APK build passed, and the APK installed on
  192.168.52.52:35121. Device UI checks confirmed CHAT on cold launch after Settings,
  CHAT on warm launcher reentry from Settings, and closure of the open chat-history
  drawer on launcher reentry. These launcher checks are not physical headset tests.
- Assistant feedback uses a scoped 900-ms request. Cancelling it removes only
  that request, without a delayed dismiss affecting a later unrelated notification.
  Two timing tests and two startup-policy tests were added.
- Existing explicit assist entry sets show-when-locked and turn-screen-on while
  the keyguard is locked. No keyguard dismissal or authentication bypass is added.
  USB/Bluetooth button standby and the probe's background permission are still
  prerequisites. Jabra HID standby was restarted with cpuTrial=false. Settings
  was then made the last visible app screen before HOME and explicit screen-off.
  Current Jabra screen-off central-button speech verification awaits the user's
  physical test; older screen-off tests alone do not establish this build's result.

### Stable Chat Surface and Legacy History Removal (2026-10-11)

- The user reported hearing the readiness cue but then seeing recognition cancelled,
  alongside the Creating new chat display. They subsequently clarified that they
  had forgotten Jabra's volume-minus long-press trigger and confirmed that launch
  itself works. This does not establish the cause of recognition cancellation.
- Removed the legacy Chats screen and its navigation destination. Settings back
  fallback now goes to CHAT_ROOT, and stale saved `chats` routes resolve there.
  The swipe drawer remains the history UI; no persisted conversation is deleted.
- CHAT_ROOT renders the normal empty chat immediately, without Creating/Preparing
  chat or Loading messages text. Startup no longer creates and navigates to an
  empty stored chat. The first actual submission creates its ID, then resumes the
  unchanged submitted text/images in recomposed chat-bound state. Inference does
  not start while draft state is being replaced. Subsequent sends reuse the ID.
  Automatic first-send creation does not navigate away from the current chat UI.
- Assistant capture waits for MainActivity to be RESUMED and for navigation to
  reach a chat route before model preparation and the spoken cue. This wait is
  bounded to ten seconds; no lock dismissal, permanent wake lock, or microphone
  standby is added.
- VAD previously mapped NO_SPEECH and user cancellation to the same cancellation
  wording. Endpoint diagnostics now distinguish them without retaining audio or
  transcript content. No-speech feedback asks the user to speak after the cue.
  VAD thresholds and duration limits are unchanged. Diagnostic observers cannot
  change endpoint behavior. Physical recognition cancellation remains under test.
- Full `testStandardDebugUnitTest assembleStandardDebug` passed (3m 8s) and the
  whitespace check passed. New/updated tests cover draft rendering, first-send
  chat creation and reuse, creation failure, correct-chat message rendering, stale
  legacy route fallback, and a once-only no-speech terminal observation unaffected
  by observer failure. The updated APK was subsequently installed on
  192.168.52.52:35121 after cleanly stopping Jabra HID standby.
- Device UI verification confirmed the normal welcome/composer on cold startup,
  with no intermediate chat-creation display. Manual first submission (1+1)
  produced one user message and one reply (2), with USB speech playback completed.
  A second submission (2+2) produced 4 in the same stored chat (ID 72), again with
  completed playback. A fresh cold startup returned to the empty chat surface.
  These checks establish first-send and continued-send behavior, not physical ASR.
- Jabra HID standby was restarted with cpuTrial=false. Settings was made the last
  visible screen before HOME and screen-off. This build's locked/screen-off ASR
  verification remains pending a physical volume-minus long press (the user's
  confirmed launch gesture), followed by speech after the readiness cue.
- The user subsequently confirmed this screen-off/locked test had no problems.
  Device trace agrees: chat_ready, completed cue, capture on USB device 629/type
  22, END_SILENCE endpoint, FINAL recognition, and automatic send in chat 73.
  This verifies the Jabra path; Pioneer has not been retested with this build.

### Assistant Volume Cap Update (2026-10-11)

- At the user's request, the shared assistant volume ceiling changes from 70 to
  80 percent for Jabra USB, Pioneer key input, and native-volume burst settlement.
  Ten-percent steps, held-button repeat suppression, conversation blocking, and
  normal music controls remain unchanged. Idle feedback uses the same ceiling.
- The accepted Pioneer checkpoint APK and its historical 70-percent description
  are intentionally preserved. This change does not raise the device's current
  volume automatically and is not a measured acoustic hearing-safety guarantee.
- Full unit tests and APK build passed (18s), including 26 focused key/USB/burst/
  feedback tests, all with zero failures. The APK installed successfully on
  192.168.52.52:35121; Jabra HID standby was restarted with cpuTrial=false and
  CHAT was reopened. The user subsequently reported that the volume was just
  right and accepted the 80-percent setting. Jabra device logs confirm an accepted
  plus press applied target=120, actual=120, percent=80 (device maximum 150).
  This is Jabra physical verification; Pioneer has not been retested at this cap.

### Common Assistant Reply Display and Main Integration (2026-10-12)

- Checkpoint 65c6bcd5 preserves the accepted headset conversation work. Current
  main (92dc042b) was integrated in the assistant checkout; the separate cute-TTS
  worktree was not edited. Main's generation ownership, TTS callback locking,
  diagnostics, and refactoring remain in place alongside assistant integration.
- Room rows, transient streaming text, and the NPU final/phase text now feed one
  message-list display path. A request-scoped response uses its owned message ID;
  completing or failing updates that ID and bridges delayed Room emissions with
  a terminal snapshot. Late partials cannot replace a terminal row. The separate
  NPU answer append surfaces and text-equality reply suppression are removed.
  Equal answers in separate turns remain separate legitimate messages.
- Seven focused tests cover placeholder replacement, terminal precedence,
  delayed Room updates, equal answers with different IDs, foreign chats/stale
  requests, cancellation, and failure. Full standard-debug unit tests and APK
  assembly passed. The original intermittent report does not establish whether
  historical duplicates were persisted; no conversation-database copy was made.
- Updated assistantprobe installed successfully on 192.168.52.52:35121. Launcher
  opened CHAT (cold launch 625 ms). Manual submissions 1+1, 2+2, and 2+2 in chat
  75 produced exactly one reply per submission (2, 4, 4); screenshot and UI tree
  agree. Completion traces for the later turns each update a single owned row.
  Headsets were unplugged for this check, so headset button/ASR regression on
  this merged build is not physically established. Earlier accepted Jabra and
  Pioneer results above remain evidence for their corresponding builds only.
- The pinned Sherpa AAR remains model-free. Upstream Sherpa and ONNX Runtime
  license texts are recorded with its checksum and also bundled in APK/AAB
  assets. Required main debug/release checks must pass before normal PR merge;
  no branch-protection bypass is intended.

### Jabra Verification on the Main-Integrated Build (2026-10-12)

- Implementation PR #2751 merged to main as 997e4bdf after all required debug/
  release checks and the additional tokenizer reproducibility/packaging check
  passed. Local verification also passed 2,253 standard-debug and 1,621
  standard-release tests, both lint tasks, debug/release APK and release AAB
  builds, and release packaging/alignment checks.
- The user reconnected Jabra Evolve2 40 SE and granted the new USB permission
  prompt. Button standby restarted successfully with cpuTrial=false and no CPU
  trial wake lock. The existing assistantprobe remained the default assistant.
- From the home-screen test, the user confirmed that replies worked normally
  and were not displayed twice. This supersedes the earlier physical-Jabra
  verification gap for the installed main-integrated implementation; it does
  not establish Pioneer regression, release-app headset parity, or a new
  locked/screen-off test on this build.
- Device diagnostics confirm physical assist launches with musicActive=false,
  chat_ready, completed readiness cues, capture on USB input device 838/type 22,
  END_SILENCE finalization, and FINAL recognition. An earlier no-speech attempt
  produced the expected NO_SPEECH feedback before later successful captures.
  Reply completion traces updated owned message IDs 84, 86 and 88 individually.
  USB speech playback used output device 829/type 22 and completed with reason
  done. No recordings, transcript text, or conversation database were copied
  into this record.
- This follow-up changes verification documentation only. The tested runtime
  is unchanged; no new APK installation or separate cute-TTS worktree edit is
  required.
