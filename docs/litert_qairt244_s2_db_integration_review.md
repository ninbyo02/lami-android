# QAIRT244 Phase S2 DB Integration Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, or change native code.

## Baseline

The current `customBuildExperimentDebug` path is:

```text
standard UI -> S1 Gate -> RealProvider -> DevOnlyEntry -> real NPU -> transient UI display
```

The confirmed S1 runtime result is:

- `run_decode_reached=true`
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- `fallback=false`
- `timeout=false`
- `fresh_crash=false`
- `sanitized_output=こんにちは。`
- `quality_classification=natural_japanese`
- `db=false`
- `tts=false`
- `markdown=false`
- `streaming=false`

In `ChatScreen.kt`, the S1 gate runs after image rejection, prompt capture, and
blank prompt rejection. It returns before chat creation, user message insert,
assistant message insert, held engine execution, streaming placeholder creation,
Markdown processing, and TTS.

`NpuStandardRouteS1Contract` currently treats side effects as disconnected:

```text
db=false
conversation_history_saved=false
tts=false
markdown=false
streaming=false
backend_npu_persisted=false
```

S2 must not silently redefine S1 success. DB persistence should be represented
as an S2 gate/result layer on top of a successful S1-style NPU response.

## S2 Goal

Persist one user message and one final assistant message for a successful
single-shot NPU response, while keeping the rest of the route disconnected:

```text
db=true
conversation_history_saved=true
tts=false
markdown=false
streaming=false
backend_npu_persisted=false
```

S2 is not a streaming route, not a TTS route, not a Markdown route, and not
`Backend.NPU` preference persistence.

## User Message Save Timing

Recommended first S2 behavior: save the user message only after the NPU result
has passed the S2 success gate.

Success flow:

1. `InferenceTarget.LOCAL` is selected.
2. Image input is rejected.
3. `requestPrompt` is captured.
4. Blank prompt is rejected.
5. S1/S2 NPU gate is selected.
6. `NpuStandardRouteS1Bridge().run()` returns a successful real NPU result.
7. Chat row is created if `effectiveChatId == null`.
8. User message is inserted with `isSendbyMe=true`.
9. Assistant message is inserted with the final sanitized response.
10. Input state is cleared and the handler returns before the normal local
    route.

This differs from the existing normal local route, which inserts the user
message before inference. The success-only S2 ordering is intentional for the
first DB integration because it avoids orphan user rows when the new NPU DB path
fails before it can produce a valid assistant response.

Later S2.x work can revisit pre-inference user persistence if the UI needs
normal chat behavior during long-running NPU execution.

## Assistant Message Save Timing

Insert the assistant message only after all S2 success conditions pass:

- NPU result status/reason is success.
- `run_decode_reached=true`.
- NPU evidence is `QNN_HTP_V79_FastRPC_native_diag`.
- `fallback_used=false`.
- `timeout=false`.
- `fresh_crash=false`.
- `sanitized_output` is non-empty.
- `quality_classification=natural_japanese`.
- The user message has been inserted successfully.

The assistant row should store the final user-facing response text:

```text
こんにちは。
```

Do not store the full diagnostic `displayText` as the assistant message body.
Diagnostics such as route type, evidence, max output tokens, fallback, timeout,
fresh crash, and quality should be attached through the existing assistant
metadata surface when possible, such as `localSourceSummary` or inference stats.
If that metadata mapping is not ready, S2 should still persist only the clean
assistant text and keep richer diagnostics transient or test-only.

S2 should not create a placeholder assistant message. There is no streaming
partial and no assistant row before the final result.

## Failure Persistence Policy

Recommended first S2 behavior: do not persist DB rows on NPU failure.

Failure examples:

- provider returns `real_provider_not_implemented`;
- provider returns `dev_only_entry_unavailable`;
- `run_decode_reached=false`;
- missing NPU evidence;
- `fallback_used=true`;
- `timeout=true`;
- `fresh_crash=true`;
- empty sanitized output;
- non-`natural_japanese` quality.

For these cases:

- show a transient failure display;
- keep `db=false`;
- keep `conversation_history_saved=false`;
- do not create a chat row;
- do not insert a user message;
- do not insert an assistant failure message;
- do not clear persisted conversation history state.

This makes rollback simple and avoids needing cleanup logic for partial S2
attempts. A later failure-history phase may intentionally persist failure rows,
but that should be a separate design because it changes conversation semantics.

## Rollback

Rollback should be gate-only:

- disable the S2 DB gate to return to S1 display-only behavior;
- disable the S1 gate to return to the existing normal local route;
- no database migration is required;
- no failure-row cleanup is required because first S2 does not write DB on
  failure;
- successful S2 rows remain ordinary chat history rows.

Rollback triggers:

- duplicate user or assistant rows;
- assistant row contains diagnostic display text instead of clean response text;
- DB rows are written when NPU result fails;
- DB rows are written before the S2 success gate;
- any TTS, Markdown, streaming, or `Backend.NPU` persistence side effect appears.

## Streaming Disconnected State

It is safe for S2 to keep streaming disconnected.

S2 is a single final-response persistence phase. It should not call:

- `LocalStreamingRunner`;
- `runWithHeldEngine`;
- streaming placeholder insert/update/finalize helpers;
- partial response callbacks;
- `localStreamingResponseText` partial update paths.

The assistant message is inserted once after the final sanitized output exists.
Streaming is Phase S4.

## TTS Disconnected State

It is safe for S2 to keep TTS disconnected.

S2 should not call:

- `ttsController.speak(...)`;
- `ttsController.speakQueued(...)`;
- streaming sentence TTS helpers;
- held-engine release paths that are specifically tied to TTS playback.

Persisting an assistant message must not automatically start speech in S2. TTS
is Phase S5.

## Markdown Disconnected State

It is safe for S2 to keep Markdown disconnected.

S2 should persist sanitized plain text. It should not call Markdown processing,
code fence repair, or streaming Markdown normalization paths. Markdown rendering
or formatting is Phase S3.

For the current successful response, plain text persistence is sufficient:

```text
こんにちは。
```

## Change Target Candidates

Initial S2 implementation candidates:

1. `ChatScreen.kt`
   - add an S2 DB gate inside the existing S1 branch or immediately around it;
   - create chat and insert messages only after S2 success;
   - return before the existing dev hidden QAIRT route and normal local route.
2. A new S2 contract file if needed
   - represent `db=true` and `conversation_history_saved=true` without changing
     S1 success semantics.
3. S2 unit tests
   - success inserts one user row and one assistant row;
   - failure inserts no rows;
   - TTS/Markdown/streaming remain false;
   - normal local route remains unchanged when the S2 gate is off.

Files that should not change for first S2:

- `RealNpuStandardRouteS1Provider.kt`, unless S2 needs additional metadata
  already present in the dev-only display contract;
- native code;
- held engine lifecycle code;
- TTS controllers;
- Markdown processors;
- streaming runner internals.

## Gate Conditions

S2 may run only when:

- S1 gate is enabled and selects the NPU path;
- RealProvider returns a result that satisfies the existing S1-style success
  checks;
- selected target is `LOCAL`;
- no image input is present;
- prompt is not blank;
- `raw_dialog_tail_variant_b`;
- requested/effective `max_output_tokens=32`;
- failure/fallback/timeout/fresh crash are all false.

S2 DB write completion should then mark:

```text
db=true
conversation_history_saved=true
```

and still mark:

```text
tts=false
markdown=false
streaming=false
backend_npu_persisted=false
```

## Open Blockers

- Decide the exact metadata field for NPU diagnostics on the assistant row.
- Confirm whether input text should be cleared on S2 failure when no DB rows are
  written.
- Add tests around `effectiveChatId == null` and existing-chat cases.
- Ensure repeated sends do not duplicate row pairs.
- Keep S1 success criteria unchanged so S2 does not weaken display-only safety
  checks.
