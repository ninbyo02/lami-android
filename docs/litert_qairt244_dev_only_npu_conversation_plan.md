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
