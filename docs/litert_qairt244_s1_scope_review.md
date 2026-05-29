# QAIRT244 Phase S1 Scope Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, or change native code.

## Baseline

Phase S1 starts from the dev-only evidence that
`raw_dialog_tail_variant_b` with `max_output_tokens=32` produced stable
natural Japanese one-turn results:

- 5/5 `status=success`
- 5/5 `run_decode_reached=true`
- 5/5 `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`
- 5/5 `fallback_used=false`
- 5/5 `timeout=false`
- 5/5 `fresh_crash=false`
- 5/5 `sanitized_output=こんにちは。`
- 5/5 `quality_classification=natural_japanese`

S1 is not a full product integration. It is the smallest standard ChatScreen
selection path that can prove the NPU route can be invoked from the standard
screen while all persistence and downstream features stay disconnected.

## In Scope

S1 may touch only the standard ChatScreen route selection and a single-shot
display path.

Allowed behavior:

- ChatScreen can select an NPU route behind an explicit S1 gate.
- The selected NPU route is one turn only.
- The prompt shape is fixed to `raw_dialog_tail_variant_b`.
- `max_output_tokens=32` is fixed.
- The response is non-streaming and final-only.
- The UI can display the sanitized assistant response and diagnostics.
- Diagnostics must show route selection, NPU evidence, fallback, timeout,
  fresh crash, output quality, and side-effect flags.
- Failure is visible as failure; fallback is not counted as NPU success.

Initial implementation surface:

- `ChatScreen.kt`
- a main-source NPU route selector/contract
- a single-shot NPU response presenter/display model
- route-selection and side-effect unit tests

The current debug-only files can be used as references, but S1 should not make
the standard route depend on `app/src/debug` classes.

## Out Of Scope

S1 must not connect these surfaces:

- DB writes
- TTS
- Markdown rendering
- streaming partial updates
- `Backend.NPU` preference persistence
- conversation history save
- held engine reuse or lifecycle changes
- normal Conversation history replay
- Settings permanent exposure
- APK/runtime/device execution as part of this design step
- native code changes

S1 should avoid the existing streaming path unless it is used only as a type
boundary. `LocalStreamingRunner` and `runWithHeldEngine` are important review
surfaces, but S1 should not add partial callbacks or held-engine conversation
reuse.

## Risk

Primary risks:

- ChatScreen currently has many coupled side effects: DB insertion, assistant
  placeholder rows, streaming partials, Markdown normalization, TTS, stop
  ownership, and local engine lifecycle hooks.
- The existing debug route uses reflection and debug-only classes; direct reuse
  would make the standard route fragile or unavailable outside debug builds.
- `LocalStreamingRunner` prefers streaming/Conversation APIs when available,
  but S1 needs a single-shot final result.
- Existing ChatScreen local inference code can insert assistant rows before the
  final result; S1 must not write DB rows.
- Fallback or empty output could be visually confused with success unless the
  display contract is explicit.
- held engine or Conversation reuse could carry state across turns and hide
  whether the S1 route is truly one-turn.
- Stop/TTS ownership can be accidentally coupled to the S1 run if the new path
  enters existing streaming UI code too early.

Risk controls:

- Keep S1 behind a disabled-by-default gate.
- Make the S1 route selector a small pure contract before editing ChatScreen
  behavior.
- Return a single immutable result object with side-effect flags.
- Display the result without inserting messages into the conversation DB.
- Keep `db=false`, `tts=false`, `markdown=false`, and `streaming=false` in the
  result contract.
- Treat fallback, timeout, fresh crash, empty output, or non-natural Japanese
  classification as S1 failure.

## Rollback

S1 rollback must be a gate-off change, not a native or data migration rollback.

Rollback behavior:

- Gate off returns ChatScreen to the current non-NPU path.
- No DB rows are created by S1, so there is no cleanup migration.
- No backend preference is saved, so standard backend selection remains
  unchanged.
- No TTS/Markdown/streaming state is started, so there is no downstream state
  to stop.
- Failure result is shown only in the transient S1 display surface.
- If NPU evidence is missing, S1 reports failure and exits without promoting
  the result to normal chat history.

Rollback triggers:

- `fallback_used=true`
- `timeout=true`
- `fresh_crash=true`
- `run_decode_reached=false`
- missing `QNN_HTP_V79_FastRPC_native_diag`
- empty sanitized output
- `quality_classification` other than `natural_japanese`
- any observed DB/TTS/Markdown/streaming side effect

## Success Criteria

S1 succeeds only if all of the following are true:

- ChatScreen enters the NPU route only through the explicit S1 selector.
- The route uses `raw_dialog_tail_variant_b`.
- requested/effective `max_output_tokens=32`.
- The result is final-only and non-streaming.
- `run_decode_reached=true`.
- `npu_backend_evidence=QNN_HTP_V79_FastRPC_native_diag`.
- `fallback_used=false`.
- `timeout=false`.
- `fresh_crash=false`.
- `sanitized_output` is non-empty.
- `quality_classification=natural_japanese`.
- UI displays the sanitized output and diagnostics.
- `db=false`.
- `tts=false`.
- `markdown=false`.
- `streaming=false`.
- `backend_npu_persisted=false`.
- no conversation history is saved.

## First Edit Priority

Recommended first files to edit for S1 implementation:

1. `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/NpuStandardRouteS1Contract.kt`
   - Add a small main-source contract for the S1 gate, fixed prompt tail,
     fixed max output, side-effect flags, success/failure criteria, and display
     metadata.
2. `app/src/test.../NpuStandardRouteS1ContractTest.kt`
   - Lock the pure contract before touching ChatScreen. Tests should prove
     gate default off, fixed `raw_dialog_tail_variant_b`, fixed `32`, and all
     side-effect flags false.
3. `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/ChatScreen.kt`
   - Add the smallest S1 selection branch and transient display hook. Do not
     connect DB, TTS, Markdown, streaming, or backend persistence.
4. `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalStreamingRunner.kt`
   - Review only at first. Edit only if a main-source single-shot runner
     boundary is needed, and keep it final-only with no partial callback use.
5. `app/src/main/java/io/github/ninbyo02/lami/ui/screens/home/LocalInferenceEngineHolder`
   or held-engine lifecycle files
   - Avoid editing in S1 unless the selected implementation requires explicit
     isolation from held Conversation reuse. Prefer no held-engine lifecycle
     changes in S1.

Do not start with DB, TTS, Markdown, streaming, Settings, or backend preference
files. Those belong to later phases.
