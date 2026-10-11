# Pioneer volume accepted checkpoint (2026-10-11)

The user accepted this version after two successive Pioneer SE-MS7BT volume-down
operations both spoke the percentage and the wait felt shorter.

- Scope: `feature/android-assistant`, assistantprobe only.
- Idle LAMI volume changes use 10-percent logical steps, with a settled maximum
  of 70 percent. Assistant conversation speech blocks additional volume commands.
- The first operation says "音量。"; a follow-up within five seconds of confirmation
  on the same route omits that cue and speaks only the percentage.
- A held Pioneer button is treated as one observed input burst. Native changes
  must stop for 900 ms before correction; confirmation waits at least 350 ms.
- Brief volume-speech tails may accept the next input. Feedback retries transient
  busy/audio conditions for a bounded period rather than dropping immediately.
- `testStandardDebugUnitTest assembleStandardDebug` and `git diff --check` passed.
  Installation and physical listening were verified on `192.168.52.52:35121`.
- Limitation: Pioneer native volume can temporarily exceed 70 percent during a
  held volume-up operation, before LAMI settles it back. This is not a hard
  instantaneous hearing-protection limit. Long-term elimination of missed
  announcements has not been proven by the short listening trial.

## Saved artifacts

`artifacts/checkpoints/pioneer-volume-20261011/lami-assistantprobe.apk`

APK SHA-256:
`0fe261bd0d473bff5bf1def714290b0a0544ec0ad98cc79fc5d5ab1030822c8b`

`artifacts/checkpoints/pioneer-volume-20261011/source.tar.gz` contains the Java/
Kotlin source, tests, manifests, debug resources, app build configuration, and
integration notes at acceptance. It supplements repository base commit
`deb8a1432b58db55fc2df88f7c399eb3c643576e`; native/model dependencies are not bundled
in this source archive. The saved APK is the complete installable checkpoint.
No branch commit or merge was made. The separate cute TTS worktree was untouched.

## Next device

Jabra Evolve2 40 SE USB reports already expose key-down/key-up transitions to
`JabraUsbButtonService`, so explicit single-step volume handling and the shared
percentage feedback are feasible. Preserve volume-minus long-press assistant
launch and normal media controls outside the LAMI conversation screen.
Jabra-specific volume behavior and listening still require physical verification.

Settings now has a USB headset entry that opens Jabra's button-standby screen.
Physical USB recognition is labeled separately from button standby. USB recognition
alone never starts the button service. The previous English `Connected` status
represented an already running button service, not a required new Connect press.
The updated UI built and installed successfully. Physical screenshots verified
`Jabra Evolve2 40 SE` in Settings, navigation to USB setup, and, after stopping
assistantprobe, `USB接続済み` alongside `ボタン待機は停止中`. USB recognition did not
restart the service. The device was left with button standby stopped.
