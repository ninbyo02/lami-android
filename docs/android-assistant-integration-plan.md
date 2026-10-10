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
