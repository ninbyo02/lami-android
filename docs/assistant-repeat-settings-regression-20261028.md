# Assistant repeat and settings regression (2026-10-28)

## Evidence and limits

Installed baseline: voiceintegrationtrial SHA256
`909b0223775029590be9e34b66f0221cf56912a8ff5132a86b05bb305052d5a6`.
The persisted `voice_input_auto_send` flag was ON after the reported attempt.
The actual setting label is "発話終了後に自動送信" under "音声入力";
the user has not yet confirmed that this is the reported toggle.
Previously changing that switch only modified Compose state until Save.
The save coroutine belonged to the screen, so navigating away could cancel it.
Controls were enabled before the initial persisted value had loaded.

Three retained successful calls reached real-input-ready/haptic after 2139,
4292, and 3926 ms. Each began as request 1 in a new Activity/process.
Android exit metadata records REMOVE TASK at 20:33:38 and 20:34:27.
These are not measurements of warm consecutive invocation. No failed repeat
was observed entering MainActivity in the retained trace. The OS exit entries
for a signalled voice_decoder process do not establish a main-process crash.
Read-only metadata is retained in
`../assistant-overlap-regression-readonly-20261028/metadata.json`.

## Targeted changes

- Auto-send writes its own preference immediately on an explicit switch change;
  defaults and the current stored value are not changed by opening the screen.
- The switch is disabled until its stored value is loaded and during a pending
  write. Save buttons cannot race that write. An already-entered small preference
  transaction finishes even when the screen is disposed; failures are displayed.
- Entry marks an invocation consumed before forwarding it. A fresh ACTION_ASSIST
  with restoration state is accepted; restoration of the consumed request is not.
  Previously every non-null saved state was unconditionally discarded.
- Entry/Main emit only fixed lifecycle classifications and timing/flags, never
  input text, intent payloads or captured audio.

The Entry defect is PC-reproducible but NOT established as this user's actual
repeat-call cause. A passing mock Activity test is not OEM hardware-button proof.
No generic onResume auto-recording, permission/default-assistant change or
microphone prestart is introduced. Splash, greeting and real-input-ready haptic
logic remain unchanged. Ultra-fast TTS work is paused and retained separately.

## Remaining manual validation (not executed)

First identify the exact toggle and whether Save was pressed in the failing
attempt. After reporting candidate/tests, obtain confirmation before updating.
Then a user-operated test can change auto-send OFF, leave/re-enter settings,
invoke the physical assistant once, finish input, and invoke it once again
without removing its task. If the repeat fails, retrieve only Entry/Main stage
metadata to distinguish OS delivery, restoration, or capture ownership.
Do not proxy physical-button presses, record, play audio or update automatically.
