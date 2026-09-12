# NPU polite response rejection and duplicate checkpoint fix — 2026-09-12

## Findings

The 18:00 JST incident reached NPU generation but rejected its result with `business_template_leak`. The legacy quality gate rejects three Japanese business politeness phrases by substring. This is inappropriate for ordinary model-owned Conversation API replies. GPU fallback separately failed to invoke the compiled model (status 13); CPU returned the final answer.

The database contains completed row 174 followed by pending row 175 with the same answer body. The pending row was created about 6 ms after completion. A periodic checkpoint can wait on the UI persistence mutex while completion releases the assistant ID; its former upsert then inserts a new row. Cancellation can leave that insertion pending.

## Changes

- Permit business politeness phrases in the Conversation API quality wrapper. Other rejection reasons remain enforced; the legacy adapter contract is unchanged.
- Capture the checkpoint's message ID and recheck ownership under the same mutex as completion.
- Make periodic checkpoints update-only at the coordinator/store boundary. Missing and terminal rows cannot be inserted or overwritten.
- Test polite introductions and business requests, retained role contamination rejection, and a checkpoint queued behind a suspending completion.

## Validation

- StandardDebug: 1,761 tests, zero failures.
- StandardRelease: 1,231 tests, zero failures.
- Strict StandardDebug APK build, with all 13 verified native libraries and the patched Conversation API marker. No allowMissingQairt244Jni override.
- Installed APK SHA-256: `208067c6d409c84fa08b81b2996405278c1cdf4e5b7475bfab76541f4dbd88ad`; installed package matched.
- NX733J normal ChatScreen UI, new chat 206, native streaming enabled: greeting then self-introduction. Both completed on NPU; second turn reused both engine and conversation, 10 native chunks / 10 visible updates, application TTFT 124 ms.
- The second answer contained the formerly rejected politeness phrase and completed without fallback. UI reported 57.9 token/s and 0.5 s (short-response application statistics, not a general performance benchmark).
- DB rows 176–179 contain exactly two user messages and two completed assistant messages; no additional pending row.
- Input method restored, screen timeout unchanged, ChatGPT returned to foreground. Existing user history, including old duplicate rows, was not edited.

## Limits

The actual GPU fallback failure is not fixed by this PR. The completion/checkpoint fallback race is covered deterministically in a unit test; a forced GPU/CPU fallback was not repeated on device. This two-turn check does not establish long-conversation or all-output quality stability. Other legacy quality heuristics may need separate review.
