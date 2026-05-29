# QAIRT244 Dev-Only NPU Conversation Plan

Date: 2026-05-29

Scope: design only. This document does not run runtime probes, does not
implement Kotlin/script/native changes, does not build or install an APK, and
does not connect Backend.NPU to the standard ChatScreen route.

## Goal

Move from hidden receiver probe evidence toward a dev-only, one-turn NPU
conversation entry that can display a single NPU result on screen without
promoting NPU to the standard product route.

This plan keeps the following boundaries:
- no standard ChatScreen main-route connection yet;
- no Backend.NPU production application yet;
- no permanent Settings entry;
- dev-only/debug-only entry only;
- one turn only;
- non-persistent;
- display only;
- no DB, TTS, Markdown, or streaming connection.

## Five-Step Plan

1. Design the dev-only NPU conversation entry in docs.
2. Implement a dev-only one-turn conversation entry fixed to `raw_dialog_tail`.
3. Confirm display-only behavior while DB, TTS, Markdown, and streaming remain
   disconnected.
4. Run one real-device one-turn NPU conversation test.
5. Verify stop, error, fallback, and rerun safety.

## Step 1: Dev-Only Entry Design

The entry must be isolated from the standard chat route. It should be reachable
only from a debug/dev-only surface, receiver, or diagnostic screen, and it must
not write a persistent backend choice.

Required fixed behavior:
- template candidate: `raw_dialog_tail`;
- app-facing native template mode: existing `raw`;
- prompt transport: existing base64 prompt transport;
- prompt length gate bypass: explicit unsafe dev-only bypass only;
- max output tokens: keep the guarded small value first, normally `16`;
- route side effects: none;
- message persistence: none;
- DB/TTS/Markdown/streaming: disconnected;
- result handling: display sanitized text and diagnostics only.

Prompt shaping:

```text
<context or user text>

ユーザー: <one-turn user prompt>
アシスタント:
```

The implementation should not introduce `simple_ja_chat` or `gemma_it_like`
as the entry template. Existing evidence shows those are still affected by
echo/sanitizer behavior. Keep the first entry fixed to `raw_dialog_tail`.

## Success Conditions

The first usable one-turn result should satisfy:
- `native=true`
- `decode=true`
- `npu_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback=false`
- `fresh_crash=false`
- `timeout=false`
- `raw_len > 0`
- `sanitized_len > 0`
- the UI displays the result

Newline-only control characters are not automatically a failure under the
current quality-policy design. Replacement characters or disallowed control
characters remain failures. `mixed_language` must be reported separately from
control-character classification.

## Failure Handling

Failures must not affect the standard route.

Required behavior:
- fallback must be explicitly shown if it occurs;
- crash, ANR, or timeout must save an artifact and stop the dev-only run;
- no silent fallback-to-success classification;
- no DB/TTS/Markdown/streaming side effects;
- no persisted Backend.NPU state;
- rerun must start from an isolated state.

## Promotion Blocks

Do not promote to standard ChatScreen routing while any of the following remain
true:
- `simple_ja_chat` echo/sanitizer behavior is unresolved;
- quality classifier policy is not implemented and tested;
- stop, error, fallback, and rerun behavior is unverified;
- DB, TTS, Markdown, and streaming behavior is unverified;
- Settings exposure and backend persistence safety are not designed;
- fallback visibility can be hidden or confused with NPU success.

## Step 2 Minimal Implementation Shape

The first implementation should stay narrow:
- add a debug-only one-turn NPU entry point;
- accept one user prompt and optional neutral context;
- always build the final input with `raw_dialog_tail`;
- send app-facing native template mode as `raw`;
- reuse base64 prompt transport and existing unsafe length-gate bypass;
- call the existing dev-only NPU route path;
- render only the sanitized result plus diagnostic metadata;
- do not insert chat messages into the normal conversation store;
- do not connect TTS, Markdown rendering, streaming, or selected backend
  persistence.

Implementation should follow docs first, classifier design second, tests
third, and dev-only display/reporting changes last. Runtime testing belongs
after the implementation is reviewed and built in a separate step.

Step 2 implementation notes:
- code location: `app/src/debug/java/io/github/ninbyo02/lami/npu/` and
  `app/src/debug/java/io/github/ninbyo02/lami/ui/screens/home/`;
- entry contract: `DevOnlyNpuOneTurnConversationEntry`;
- UI shell: `Qairt244DevOnlyNpuConversationActivity`;
- Activity registration is debug-source-set only in `app/src/debug/AndroidManifest.xml`;
- fixed template: `raw_dialog_tail`;
- app-facing template mode: `raw`;
- prompt transport policy: base64 encode/decode before entering the adapter;
- max output tokens: `16`;
- unsafe prompt-length bypass: explicit dev-only request flag;
- diagnostics include `standard_route_connected=false`,
  `backend_npu_persisted=false`, `db=false`, `tts=false`, `markdown=false`,
  and `streaming=false`.

Step 2 is not Step 3 or Step 4. It does not prove on-screen behavior on a
device, does not run runtime probes, and does not install an APK. Step 3 must
confirm display-only behavior before Step 4 runs a real-device one-turn NPU
conversation test.

## Step 3 Static Display Review

Static review result:
- the Activity is registered only in `app/src/debug/AndroidManifest.xml`;
- the standard `ChatScreen` source remains disconnected;
- Activity launch shows an idle display first and does not start NPU execution
  from `onCreate`;
- adapter execution requires the explicit dev-only button trigger;
- the display contract includes `status`, `sanitized_output`, `reason`,
  `native`, `decode`, `npu_evidence`, `fallback`, `fresh_crash`, `timeout`,
  `raw_len`, `sanitized_len`, `quality`, and `control_chars`;
- the display contract also emits `standard_route_connected=false`,
  `backend_npu_persisted=false`, `db=false`, `tts=false`, `markdown=false`,
  `streaming=false`, and `route_type=dev_only_one_turn_conversation`;
- no Backend.NPU preference is saved;
- no DB, TTS, Markdown, or streaming API is called by the dev-only entry.

Step 3 remains static only. It does not run the NPU, install an APK, or prove
device UI behavior. Step 4 is still the first real-device one-turn NPU
conversation test.

Step 3 launch-retention note:
- initial Activity launch checks showed `Qairt244DevOnlyNpuConversationActivity`
  could be started with `am start`, but immediately returned to `MainActivity`;
- dumpsys showed the dev-only Activity as the last paused Activity and logcat
  did not show a fatal exception;
- the debug manifest had `android:noHistory="true"` on the dev-only
  conversation Activity, so the Activity could not remain in the foreground
  for display-only confirmation;
- `noHistory` was removed from this debug-only Activity while preserving
  `excludeFromRecents=true`, `exported=true`, standard route disconnection,
  non-persistence, and DB/TTS/Markdown/streaming disconnection.

## Step 4 First Device One-Turn Result

The first dev-only Activity one-turn NPU conversation reached native decode
after explicit button activation. Source artifact on device:

```text
files/qairt244_short_multitoken_smoke_result.txt
```

Observed result:

```text
result=success
prompt=こんにちは
prompt_input_code_points=5
requested/effective/max_output_tokens=128
output_bytes=671
elapsed_ms=4902
prefill_elapsed_ms=34
decode_elapsed_ms=3235
npu_backend=NPU
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
run_decode_reached=true
fallback_used=false
timeout=false
fresh_crash=false
db=false
tts=false
markdown=false
streaming=false
conversation_created=no
generate_response=no
normal_ui_connected=no
selected_path_npu_normal_route=no
selected_path_npu_saved=false
sanitized_output_length=172
quality_classification=mixed_language
output_contains_control_chars=true
control_chars=U+000A x32
replacement_char_count=0
```

Interpretation:
- explicit dev-only Activity activation can reach NPU decode for a one-turn
  conversation-shaped prompt;
- the standard ChatScreen main route remains disconnected;
- DB, TTS, Markdown, streaming, and Backend.NPU persistence remain
  disconnected;
- fallback, crash, and timeout visibility remained explicit;
- this establishes minimal NPU inference conversation reachability through the
  dev-only entry.

The result is not a quality or promotion pass. It used max output `128`, which
produced long, template-like mixed output. `mixed_language` and
`control_chars=U+000A x32` must be treated as Step 5 safety/quality work, not
as a reason to promote the route. The next safe checks are to restore or
explicitly display the intended low max-output cap, and to verify stop, error,
fallback, timeout, and rerun behavior before any standard route discussion.

## Step 5 Front-Half Contract Fix

Before the next runtime check, the dev-only one-turn Activity/Entry fixes the
conversation cap to:

```text
max_output_tokens=16
```

This keeps the Activity aligned with the short-output probe path and avoids the
initial `128`-token result shape, which was long, template-like, and
`mixed_language`. The cap is not a Settings option, is not persisted as
`Backend.NPU`, and does not affect the standard ChatScreen route.

The display contract now has to show the token and safety fields explicitly:

```text
requested_max_output_tokens=16
effective_max_output_tokens=16
max_output_tokens=16
native_max_output_tokens_limit=<native reported limit or ->
fallback_used=<true|false>
timeout=<true|false>
fresh_crash=<true|false>
run_decode_reached=<true|false>
npu_backend_evidence=<evidence or ->
standard_route_connected=false
backend_npu_persisted=false
db=false
tts=false
markdown=false
streaming=false
route_type=dev_only_one_turn_conversation
```

This is still pre-runtime Step 5 preparation. It does not prove the corrected
`16`-token path on device yet, does not install an APK, does not run an NPU
probe, and does not connect DB/TTS/Markdown/streaming or the standard route.
The next runtime check should be a single explicit-button dev-only one-turn run
that verifies the displayed requested/effective token values, fallback,
timeout, fresh crash, decode evidence, and rerun behavior.

## Step 5 Broadcast Entry For Buttonless Check

Termux can take foreground focus while driving ADB, which makes the dev-only
Activity button path awkward for Step 5 runtime confirmation. Add a debug-only
broadcast entry so the same one-turn Entry can be invoked explicitly from ADB
without touching the standard ChatScreen route.

Broadcast action:

```text
io.github.ninbyo02.lami.action.DEV_ONLY_NPU_ONE_TURN_CONVERSATION
```

Extras:

```text
user_prompt=<string, default こんにちは>
context=<string, default empty>
unsafe_dev_bypass_prompt_length_gate=<boolean, default true>
```

Result file:

```text
files/dev_only_npu_one_turn_conversation_result.txt
```

The receiver is registered only in the debug manifest, is named with
`DevOnly`, and writes a dedicated key/value result file. The result contract
includes:

```text
status=<success|failure>
result=<success|failure>
success=<true|false>
requested_max_output_tokens=16
effective_max_output_tokens=16
max_output_tokens=16
native_max_output_tokens_limit=<native reported limit or ->
run_decode_reached=<true|false>
npu_backend_evidence=<evidence or ->
fallback_used=<true|false>
timeout=<true|false>
fresh_crash=<true|false>
standard_route_connected=false
backend_npu_persisted=false
db=false
tts=false
markdown=false
streaming=false
sanitized_output=<escaped output>
quality_classification=<classification>
output_first_200_chars=<escaped prefix>
timestamp=<epoch ms>
```

This is still a dev-only Step 5 check path, not a standard route promotion.
It keeps `raw_dialog_tail`, app-facing `raw` mode, max output `16`, no
Backend.NPU persistence, and no DB/TTS/Markdown/streaming connection. Runtime
broadcast execution remains a later explicit device step after compile/tests.

Step 5 broadcast static fix:
- the first broadcast reached `prompt_source=dev_only_conversation` and
  propagated `max_output_tokens=16`, but the adapter stopped before native
  engine initialization with `engine_initialize=false` and `run_decode=false`;
- the marker source is the adapter `duplicate_run_blocked` branch, which used
  the standard hidden route shared once guard
  `qairt244_chat_screen_real_npu_once_guard.txt` for dev-only conversation
  runs too;
- dev-only one-turn conversation now skips that shared once guard while the
  standard ChatScreen route still keeps it;
- the receiver writes the dedicated result file synchronously as
  `status=received`, then `status=running`, then a final success/failure
  contract, so ADB can distinguish delivery, execution start, and final result
  without depending only on `qairt244_short_multitoken_smoke_result.txt`.

Step 5 receiver delivery diagnostic fix:
- device observation showed the broadcast completed and the receiver/action was
  registered, but the dedicated result file was not created;
- the `status=received` write is now the first receiver-side operation after
  resolving `context.filesDir`, before action validation, guard checks, or
  worker-thread startup;
- `status=received` and `status=running` include `action`, `package_name`,
  `class_name`, `user_prompt_present`, and `timestamp`;
- action mismatch writes `status=ignored_action` to the same result file, so a
  delivered but mismatched broadcast still leaves a debug artifact.
- result-file inspection must use the installed application id from dumpsys or
  the merged manifest. `standardDebug` uses `io.github.ninbyo02.lami`, while
  `customBuildExperimentDebug` uses `io.github.ninbyo02.lami.customnpu`; a
  mismatched `run-as` package will inspect a different app sandbox.

Step 5 receiver result-code diagnostic:
- the debug manifest already declares the receiver with
  `android:exported="true"`;
- `DevOnlyNpuOneTurnConversationReceiver.onReceive` now calls
  `setResultCode(244)` before resolving `context.filesDir` or validating the
  action;
- `status=received` still writes synchronously to
  `files/dev_only_npu_one_turn_conversation_result.txt`, and the progress
  contract includes `result_code=244`;
- `244` is a temporary dev-only broadcast delivery diagnostic, not product
  behavior and not a standard route promotion signal.

Step 5 Activity auto-run fallback:
- because the receiver is registered but `am broadcast` still does not reach
  `onReceive` on the device path under investigation, Step 5 runtime
  confirmation moves back to the debug-only Activity;
- `Qairt244DevOnlyNpuConversationActivity` now accepts
  `auto_run=<boolean, default false>`, `user_prompt=<string, default こんにちは>`,
  and `unsafe_dev_bypass_prompt_length_gate=<boolean, default true>`;
- `auto_run=false` preserves the existing idle/manual-button behavior and does
  not execute NPU work during Activity creation;
- `auto_run=true` calls the same dev-only one-turn path once after `onCreate`,
  guarded by `runStarted`, and keeps `max_output_tokens=16`;
- Activity-triggered runs also write
  `files/dev_only_npu_one_turn_conversation_result.txt` with the same
  disconnected side-effect contract when possible;
- this remains debug-only and does not connect the standard ChatScreen route,
  Backend.NPU persistence, DB, TTS, Markdown, or streaming.

Step 5 empty-after-sanitize diagnostic:
- the auto-run path reached native NPU decode with `native=true`,
  `decode=true`, `fallback=false`, `fresh_crash=false`, and `timeout=false`,
  but returned `reason=empty_after_sanitize`;
- this means the native result was successful and non-empty before adapter
  sanitization, but the sanitizer returned an empty display string;
- the likely causes are prompt/tail echo removal, template/role-line removal,
  leading non-Japanese drift removal, or duplicate assistant line removal;
- the existing dedicated Activity result file only preserved
  `sanitized_output`, so a non-zero raw length such as `raw_len=59` could not
  be explained from that file alone;
- the dedicated result contract now mirrors the key raw/sanitizer diagnostics:
  `raw_len`, `sanitized_len`, `raw_output_first_200_chars`,
  `raw_output_last_200_chars`, `raw_unicode_summary`, `sanitizer_applied`,
  `removed_template_token_count`, `removed_prompt_echo`,
  `replacement_char_count`, and `output_contains_control_chars`;
- NPU reachability remains a pass. `empty_after_sanitize` should be treated as
  a quality/diagnostic failure for the dev-only path, not as a fallback,
  crash, timeout, or standard route promotion blocker by itself.

Step 5 Japanese-only tail shaping:
- the next auto-run observation showed the raw NPU output was Hindi:
  `नमस्कार! कैसे हैं आप? मैं आपकी किस तरह से मदद कर सकता हूँ?`;
- `native=true`, `decode=true`, `run_decode_reached=true`, NPU evidence,
  no fallback, no crash, and no timeout still confirm reachability;
- `empty_after_sanitize` in this case is a prompt/output-language quality
  failure, not a reason to relax the sanitizer;
- keep `raw_dialog_tail` and `max_output_tokens=16`, but strengthen only the
  tail with a short Japanese-only instruction:

```text
必ず日本語だけで短く返答してください。
ユーザー: <prompt>
アシスタント:
```

- this remains debug-only and does not connect the standard ChatScreen route,
  Backend.NPU persistence, DB, TTS, Markdown, or streaming.

Step 5 Japanese prefix continuation:
- the following auto-run output drifted to Korean, `안녕하세요.`, while native
  decode still succeeded;
- keep treating this as output-language quality drift after successful NPU
  reachability, not as fallback, crash, timeout, or a sanitizer relaxation
  trigger;
- update the raw dialog tail to provide the first Japanese assistant tokens and
  continue generation from there:

```text
必ず日本語だけで短く返答してください。
ユーザー: <prompt>
アシスタント: こんにちは
```

- max output remains fixed to `16`, and the dev-only path remains disconnected
  from the standard ChatScreen route, Backend.NPU persistence, DB, TTS,
  Markdown, and streaming.

Step 5 dev-only NPU conversation success:
- after the Japanese prefix continuation tail, the auto-run Activity reached
  the minimum one-turn NPU conversation success condition with max output `16`;
- observed result:

```text
status=success
requested_max_output_tokens=16
effective_max_output_tokens=16
max_output_tokens=16
run_decode_reached=true
npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag
fallback_used=false
timeout=false
fresh_crash=false
sanitized_len=1
sanitized_output=。
db=false
tts=false
markdown=false
streaming=false
route_type=dev_only_one_turn_conversation
```

- this confirms the dev-only one-turn path can perform NPU decode and return a
  sanitized non-empty result without fallback, timeout, or fresh crash;
- standard ChatScreen wiring is still not connected;
- DB, TTS, Markdown, streaming, and Backend.NPU persistence remain
  disconnected;
- output quality is still weak because the result is only `。`; treat this as
  the next phase's prompt/output quality problem, not as a reachability
  failure.
