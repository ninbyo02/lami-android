# Invocation-owned assistant auto-send

## Baseline and evidence

Installed baseline 44e23eb79007d3a4066bc6fca2713061fc8906bfb7c78c0f4374176890838ca9.
User confirmed auto-send OFF persistence and reopening Lami. Request: each physical
invocation should collect NEW speech and auto-send when the persisted setting is ON.
The fresh non-content trace contains one new Entry/create + Main/create + capture
sequence, not a later Entry/new_intent or Main/new_intent. This supports an entry
delivery investigation; it does not prove a vendor task-manager defect.

The previous trampoline shared Main's task affinity and finished after launching
Main. A subsequent OS NEW_TASK launch could match that existing task rather than
give the recognizer a new request. The candidate removes Entry's affinity and
excludes that transient entry task from Recents. Entry returns to Main with
NEW_TASK | CLEAR_TOP | SINGLE_TOP. It still finishes and keeps consumed-request
restoration protection. Ordinary launcher behavior is unchanged.
Task behavior reference (primary Android documentation):
https://developer.android.com/guide/components/activities/tasks-and-back-stack
https://developer.android.com/guide/topics/manifest/activity-element
The OEM physical-button delivery remains a device acceptance test, not something
Robolectric or the manifest audit can prove.

## Submission contract

Each accepted ASSIST begins a process-local monotonic submission owner, independently
of Activity-local capture request numbering. A new owner immediately invalidates
old buffered transcripts and delayed sends. The recognizer still samples the saved
auto-send flag per invocation. Only a successful, nonempty, natural-end-of-speech
result can be offered for auto-send; existing short-text policy remains unchanged.
Manual Stop preserves text but suppresses automatic sending. UI consumption and
submission each claim once and check ownership. Just opening the screen cannot
submit an old composer value. The UI rechecks the current saved flag before send.
Lifecycle stop invalidates its own owner, not the next Activity's owner.
First-chat asynchronous creation carries that owner through its pending submission.

Splash, model preparation, greeting and actual-input-ready single haptic are kept.
No new model, permission, service, setting default or always-open microphone.
Existing TTS native payload and saved-PCM identity are audited against 44e23.

## Validation boundary

PC tests cover OFF/ON per invocation, same phrase as new speech, duplicate results,
duplicate sends, replaced capture, old buffered result, pending busy response,
manual stop before/after final, no speech/failure, changed composer, lifecycle
cancellation, late old-owner cancellation, and first-chat ownership wiring.
They do not record, recognize live speech, send messages or reproduce OEM task routing.
No candidate update or phone audio test is performed in this preparation turn.

After reporting the signed candidate and obtaining update confirmation, the smallest
user-operated test is: ON + fresh short speech once, then invoke again without
removing the task and speak a DIFFERENT short phrase. Confirm one submission each.
Then OFF once should retain recognized text without sending. Manual Stop must
retain text even with ON. Existing greeting and haptic may occur during those tests.
No automated assistant invocation or speech generation is needed for deployment.

## Delivery and user acceptance

Candidate SHA256: b7e65bd256dff868e6e392f958f2e5de4e0b1c9d96216846ebcc1209922879be.
PC regression: 259 passing tests plus the separate 122-test host speech suite.
Same signer, no additional permissions, all 51 native libraries and saved-PCM
compatibility identity byte-identical to the previous delivered version.
Data-preserving update completed at 2026-10-28 21:20 JST with before/after
model, settings, databases, saved audio and research record hashes matching.
No automated recording, invocation, inference or playback was performed.
At 21:33 JST the user confirmed consecutive automatic sending works and requested
commit/push/merge. This is user acceptance evidence, not an independently timed
hardware-button benchmark or proof of all failure/lifecycle edge cases.
