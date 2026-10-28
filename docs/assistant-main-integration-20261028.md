# Minimal main-line assistant integration

Base: GitHub main 1661223189464e50bf9042eac7aab6cfb3f581ec.
Feature source: ad4adbd2cd6d449f81debd42bb9ffb9b10151593 on the isolated,
device-validated voice branch. This PR extracts source changes, not that branch's
123 unmerged ancestor commits.

## Necessary prerequisites

- ASR endpoint settings and finalize reasons: distinguish silence completion from
  explicit Stop, so stopping preserves text without sending it.
- Capture finish/drain and cancellation checks: retiring recorders release before
  the next invocation acquires one. Existing model files/native dependencies stay.
- Persistent auto-send and 1--5 second silence settings: default OFF/2 seconds;
  explicit auto-send changes finish their DataStore write after leaving Settings.
- Existing optional debug wake-word helpers referenced by the ASR settings/capture
  implementation: no background service, no automatic start or new permission.
- Assistant package policy and optional greeting privacy check: don't force the
  Android TTS greeting onto an unverifiable output in silent/vibrate mode.
- A small stopForVoiceInput interface and queue cleanup barrier: adapt to main's
  existing single TTS queue, rather than importing the experimental response/replay
  engine. No wave generation, tokenizer, sampler or model changes.

## Current feature

Shared model preparation overlaps the unchanged splash, not microphone capture.
Session ownership serializes cleanup and rejects stale/duplicate transcripts and
automatic sends. Real input readiness still requires OS-active input plus a PCM
frame before one standard haptic. The transient ASSIST entry task is separated
from the reusable chat task; ordinary launcher behavior remains unchanged.

## Explicit exclusions and validation limits

No ultra-fast TTS, NPU decoder experiment, model/weight file, APK, SDK binary,
host-specific delivery script or external research history is included.
No saved-PCM replay engine, continuous waveform pipeline or neural arithmetic
optimization is imported. Main's existing production TTS selection is retained.
ChatScreen changes were selected at ASR hunk boundaries, excluding saved replay
and response-input rewrites; small playback-vs-capture guards remain necessary.

The source branch's delivered APK b7e65bd256dff868e6e392f958f2e5de4e0b1c9d96216846ebcc1209922879be
was accepted by the user for consecutive auto-send. This main-line port is NOT
that APK: its older main-line TTS queue uses the adapted cleanup barrier. Local
and hosted CI must validate this port separately. No phone deployment or audio
test is performed as part of publication. Full native/OEM behavior is not proved
by Robolectric, source-contract tests or the non-NPU hosted smoke APK.
