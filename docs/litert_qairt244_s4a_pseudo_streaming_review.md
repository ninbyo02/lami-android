# QAIRT244 Phase S4-A Pseudo Streaming Review

Date: 2026-05-30

Scope: design review only. This document does not implement code, run runtime
probes, install APKs, change native code, connect real token streaming, connect
TTS, or persist `Backend.NPU`.

## Baseline

The NPU standard route is currently promoted in single-response phases:

```text
S1: real NPU result -> transient display
S2: real NPU result -> DB user/assistant rows
S3: real NPU result -> optional final Markdown normalization -> DB/UI body
```

The important constraint is that the current NPU route returns the whole
assistant output at once. It does not expose per-token or per-chunk streaming
callbacks.

Existing local/Ollama streaming uses:

- `LocalStreamingRunner`;
- `onPartial`;
- `localStreamingResponseText`;
- `streamingResponseTextForRender`;
- streaming placeholder insert/update/finalize helpers;
- final persistence through `finalizeStreamingAssistantMessage(...)`.

S4-A must not claim to be that route.

## S4-A Goal

S4-A is pseudo streaming:

```text
whole NPU answer -> split into 3 to 5 UI chunks -> staged UI display -> final DB save
```

It is intended to make the UI feel progressive while preserving the safety of
the current single-response NPU execution.

It is explicitly not real token streaming:

- no model token callback;
- no LiteRT flow connection;
- no `LocalStreamingRunner` connection;
- no partial DB persistence;
- no TTS sentence playback.

## Chunking Policy

S4-A should split the final text only after S1/S2/S3 success conditions have
passed.

Recommended initial policy:

- split into 3 to 5 chunks;
- prefer paragraph boundaries when present;
- otherwise prefer sentence punctuation boundaries;
- otherwise split by character count;
- never emit an empty chunk;
- never emit more text than the known final answer;
- final staged text must equal the final answer exactly.

For the current minimal output:

```text
こんにちは。
```

S4-A should probably display it in one step because splitting a very short
answer adds no value.

## UI Display Surface

Recommended first implementation:

- use a new NPU-specific transient display state rather than the existing
  `LocalStreamingRunner` path;
- display cumulative text, not delta-only chunks;
- mark the display as pseudo streaming in diagnostics;
- keep the `NPU STANDARD ROUTE S1` debug block separate.

Avoid using existing streaming placeholder rows for S4-A initially. Those paths
have DB placeholder/finalize behavior and TTS interactions that are outside
S4-A scope.

If a later implementation reuses `localStreamingResponseText`, it must be
guarded so that it does not call:

- `upsertStreamingAssistantPlaceholder(...)`;
- `finalizeStreamingAssistantMessage(...)`;
- streaming sentence TTS helpers;
- `LocalStreamingRunner`.

## DB Persistence

DB save remains final-only.

S4-A should not write partial chunks to DB. The only persisted assistant body is
the final full text after S3 finalization:

```text
assistant DB body = final S3 finalized text
```

Ordering stays:

```text
S1 success
-> S2 save candidate
-> S3 final Markdown text
-> S4-A staged UI display
-> create/resolve chat
-> insert user row
-> insert assistant row with final full text
```

An alternative ordering is to insert final DB rows before staged display, but
that would make the UI less representative of the staged response. The first
S4-A design should stage UI first, then persist final rows once the staged
display completes.

## Markdown Policy

Markdown processing remains final-only.

S4-A should not run Markdown repair per pseudo chunk. It should use:

```text
S3 markdownCandidate.finalizedText
```

as the source for chunking and final DB save.

This avoids code fence corruption from splitting or repairing partial fenced
blocks. It also keeps DB text and final visible text consistent.

## Failure Policy

Failure must not pseudo stream.

Do not start S4-A staged display when:

- S1 route is not selected;
- `s1Result.successCriteriaMet=false`;
- S2 has no save candidate;
- S3 final text is empty;
- fallback, timeout, or fresh crash is present.

Failure path remains:

```text
transient failure diagnostics only
no DB rows for failure
no pseudo chunks
no Markdown finalization beyond existing gate behavior
```

## TTS Policy

TTS remains disconnected in S4-A.

Pseudo chunks must not feed:

- `ttsController.speak(...)`;
- `ttsController.speakQueued(...)`;
- streaming sentence playback buffers;
- held-engine release-for-TTS paths.

TTS is a later phase.

## Rollback

Rollback should be gate-only:

- S4-A gate off: return to S3 final-response behavior.
- S3 gate off: return to S2 sanitized text.
- S2 gate off: return to S1 display-only.
- S1 gate off: return to existing local/Ollama route.

No DB migration is required because S4-A should persist only the same final
assistant row as S3.

Rollback triggers:

- partial chunks are persisted to DB;
- final DB body differs from final staged text;
- pseudo streaming starts for a failure result;
- real token streaming path is accidentally invoked;
- TTS starts;
- streaming placeholder rows appear;
- normal local/Ollama route changes when S4-A gate is off.

## Test Items

Pure/unit tests:

- splitter returns one chunk for very short text;
- splitter returns 3 to 5 chunks for longer text;
- cumulative final chunk equals original text exactly;
- no empty chunks;
- paragraph/sentence boundaries are preferred when possible;
- code fence text is not mutated by chunking;
- failure result produces no pseudo streaming candidate;
- S4-A gate off returns final text without staged chunks.

ChatScreen-level tests:

- S4-A gate off preserves S3 behavior.
- S4-A gate on updates only the NPU pseudo streaming UI state.
- S4-A gate on inserts exactly one user row and one assistant row.
- assistant DB body is final full text.
- no streaming placeholder row is inserted.
- failure writes no DB rows and emits no pseudo chunks.
- TTS/Backend persistence are not called.

Manual runtime checks after implementation:

- with a longer fixed response, UI reveals 3 to 5 cumulative stages;
- final visible assistant text equals DB assistant body;
- `こんにちは。` still behaves as a single short response;
- no `LocalStreamingRunner` traces appear for the NPU S4-A route;
- no TTS starts.

## Open Decisions

- Whether S4-A should stage before DB insert or after DB insert. First design
  recommends before DB insert, with DB persistence only after the final staged
  text is visible.
- Whether staged UI should be shown in the normal message list or a dedicated
  NPU transient panel. First design recommends a dedicated NPU transient state
  to avoid streaming placeholder side effects.
- Whether chunk delays should be fixed or proportional to text length.
- Whether to build S4-A as pure contract/mapper/bridge first, following the
  S1/S2/S3 promotion pattern.
